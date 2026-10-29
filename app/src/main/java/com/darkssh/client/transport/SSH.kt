package com.darkssh.client.transport

import android.util.Base64
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.entity.KnownHost
import com.darkssh.client.data.repository.KnownHostRepository
import com.darkssh.client.service.CredentialStore
import com.darkssh.client.service.DisconnectReason
import com.darkssh.client.service.TerminalBridge
import com.darkssh.client.service.TerminalService
import com.darkssh.client.util.PubkeyUtils
import com.darkssh.client.util.SshFingerprint
import com.darkssh.client.util.TextSanitizer
import com.trilead.ssh2.Connection
import com.trilead.ssh2.ConnectionMonitor
import com.trilead.ssh2.InteractiveCallback
import com.trilead.ssh2.ServerHostKeyVerifier
import com.trilead.ssh2.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPair

class SSH(
    host: Host,
    bridge: TerminalBridge,
    service: TerminalService,
    private val knownHostRepository: KnownHostRepository,
) : AbsTransport(host, bridge, service),
    ConnectionMonitor,
    InteractiveCallback {
    companion object {
        private const val AUTH_TRIES = 20
        private const val DEFAULT_PORT = 22
        private const val TAG = "SSH"
        private const val CONNECT_TIMEOUT_MS = 15000 // 15 seconds
        private const val KEX_TIMEOUT_MS = 30000 // 30 seconds for key exchange

        const val HOST_KEY_CHANGED_MESSAGE =
            "⚠️ The server's host key changed. This can be a man-in-the-middle attack. " +
                "Connection refused and no password was sent. If the server key was rotated " +
                "legitimately, open Edit Host → Forget host key and connect again."
    }

    /** Set by the verifier when a pinned host presented an unknown key. */
    @Volatile
    private var hostKeyChanged = false

    @Volatile
    private var connection: Connection? = null

    /** Expose connection for OS detection (read-only) */
    fun getConnection(): Connection? = connection

    @Volatile
    private var session: Session? = null

    @Volatile
    private var stdin: OutputStream? = null

    @Volatile
    private var stdout: InputStream? = null

    @Volatile
    private var stderr: InputStream? = null

    @Volatile
    private var connected = false

    @Volatile
    private var sessionOpen = false

    @Volatile
    private var keepAliveRunning = true

    /** Saved password is offered to keyboard-interactive at most once per connection. */
    private var savedPasswordOfferedInteractively = false

    /** Password used in the current keyboard-interactive attempt. */
    private var interactivePassword: String? = null
    private var interactivePasswordWasSaved = false

    private fun startKeepAlive() {
        keepAliveRunning = true
        Thread {
            var consecutiveFailures = 0
            while (keepAliveRunning && connected) {
                try {
                    Thread.sleep(30000)
                    connection?.sendIgnorePacket()
                    consecutiveFailures = 0
                    Timber.d("$TAG: Keepalive sent")
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    consecutiveFailures++
                    Timber.w(e, "$TAG: Keepalive failed (attempt $consecutiveFailures)")
                    if (consecutiveFailures >= 2) {
                        Timber.e("$TAG: Keepalive failed $consecutiveFailures times, disconnecting bridge")
                        keepAliveRunning = false
                        connected = false
                        bridge.dispatchDisconnect("Connection timed out (keepalive failed)", DisconnectReason.IO_ERROR)
                        break
                    }
                }
            }
        }.apply {
            name = "SSH-KeepAlive"
            isDaemon = true
            start()
        }
    }

    override fun connect() {
        // Sanitize even though HostEditorViewModel already does on save (bug-012):
        // this unblocks hosts saved *before* that fix without requiring the user to
        // retype them, and guards against any other path that could write a Host
        // (import, future editors, etc). A hostname with a stray invisible character
        // stops matching InetAddress's strict IPv4-literal check and silently turns a
        // connect() to a valid IP into a doomed DNS lookup instead.
        val hostname = TextSanitizer.sanitizeStrict(host.hostname)
        val port = if (host.port <= 0) DEFAULT_PORT else host.port

        try {
            // Check if coroutine is still active before starting
            runBlocking {
                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("Connection cancelled before start")
                }
            }

            val conn = Connection(hostname, port)
            connection = conn

            if (host.compression) {
                conn.setCompression(true)
            }

            preferKnownHostKeyAlgorithms(conn)
            hostKeyChanged = false
            val verifier = HostKeyVerifier()

            // Connect with timeout (prevents indefinite hang)
            Timber.d("$TAG: Connecting to $hostname:$port (timeout: ${CONNECT_TIMEOUT_MS}ms)...")
            conn.connect(verifier, CONNECT_TIMEOUT_MS, KEX_TIMEOUT_MS)
            Timber.d("$TAG: Connected to $hostname:$port")

            // Check cancellation after network operation
            runBlocking {
                if (!currentCoroutineContext().isActive) {
                    conn.close()
                    throw CancellationException("Connection cancelled after connect")
                }
            }

            Timber.d("$TAG: Starting authentication...")
            if (!authenticate(conn)) {
                throw IOException("Authentication failed")
            }
            Timber.d("$TAG: Authentication successful, finishing connection...")
            finishConnection(conn)
            Timber.d("$TAG: Connection fully established")
        } catch (e: CancellationException) {
            Timber.d("$TAG: Connection cancelled to $hostname:$port")
            connection?.close()
            connection = null
            throw e
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Connection failed to $hostname:$port")
            // Only dispatch disconnect if bridge isn't already being closed.
            // When the user closes a tab during connection, bridge.close() calls
            // transport?.close() which interrupts conn.connect() with an IOException –
            // we must NOT call dispatchDisconnect again in that case (double-close crash).
            if (!bridge.isClosed) {
                val reason =
                    if (hostKeyChanged) {
                        HOST_KEY_CHANGED_MESSAGE
                    } else {
                        e.message ?: "Connection failed"
                    }
                bridge.dispatchDisconnect(reason)
            }
            throw e
        }
    }

    private fun authenticate(conn: Connection): Boolean {
        // Same rationale as connect(): strip stray invisible characters a stored
        // username might carry (bug-012), which would otherwise make every auth
        // attempt fail against a username the server has genuinely never heard of.
        val username = TextSanitizer.sanitize(host.username)

        val initial =
            conn.getRemainingAuthMethods(username)
                ?: return false

        if (initial.isEmpty()) return true

        var canTryPubkey = "publickey" in initial
        var canTryKeyboardInteractive = "keyboard-interactive" in initial
        var canTryPassword = "password" in initial

        // A host with a specific key assigned (Host.pubkeyId) should only ever be tried with
        // THAT key, not any other loaded key. Guard so we don't re-prompt for its password on
        // every AUTH_TRIES iteration if the server keeps listing "publickey" as available.
        var hostPubkeyAttempted = false

        for (attempt in 0 until AUTH_TRIES) {
            if (conn.isAuthMethodAvailable(username, "none")) {
                try {
                    if (conn.authenticateWithNone(username)) return true
                } catch (e: IOException) {
                    Timber.d(e, "$TAG: none auth failed")
                }
            }

            if (canTryPubkey && conn.isAuthMethodAvailable(username, "publickey")) {
                val hostPubkeyId = host.pubkeyId
                if (hostPubkeyId != null) {
                    if (!hostPubkeyAttempted) {
                        hostPubkeyAttempted = true
                        if (tryHostPubkeyAuth(conn, username, hostPubkeyId)) return true
                    }
                } else if (tryPublicKeyAuth(conn, username)) {
                    return true
                }
            }

            if (canTryKeyboardInteractive && conn.isAuthMethodAvailable(username, "keyboard-interactive")) {
                interactivePassword = null
                interactivePasswordWasSaved = false
                if (conn.authenticateWithKeyboardInteractive(username, this)) {
                    interactivePassword?.let { password ->
                        CredentialStore.putPassword(host.id, password, remember = host.stayConnected)
                    }
                    return true
                }
                if (interactivePasswordWasSaved) {
                    Timber.d("$TAG: Saved password rejected via keyboard-interactive, removing...")
                    CredentialStore.remove(host.id)
                }
            }

            if (canTryPassword && conn.isAuthMethodAvailable(username, "password")) {
                // Try saved password first
                val savedPassword = CredentialStore.getPassword(host.id)
                if (savedPassword != null) {
                    Timber.d("$TAG: Trying saved password...")
                    if (conn.authenticateWithPassword(username, savedPassword)) {
                        Timber.d("$TAG: Saved password authentication successful!")
                        return true
                    } else {
                        Timber.d("$TAG: Saved password authentication failed, removing...")
                        CredentialStore.remove(host.id)
                    }
                }

                // Prompt for password
                Timber.d("$TAG: Prompting for password...")
                val password = bridge.promptForPasswordBlocking()
                Timber.d("$TAG: Password received: ${if (password != null) "[HIDDEN]" else "null"}")
                if (password != null) {
                    Timber.d("$TAG: Attempting password authentication...")
                    if (conn.authenticateWithPassword(username, password)) {
                        Timber.d("$TAG: Password authentication successful!")
                        CredentialStore.putPassword(host.id, password, remember = host.stayConnected)
                        return true
                    } else {
                        Timber.d("$TAG: Password authentication failed")
                    }
                } else {
                    Timber.d("$TAG: No password provided, breaking auth loop")
                    break
                }
            }

            val remaining = conn.getRemainingAuthMethods(username) ?: break
            canTryPubkey = "publickey" in remaining
            canTryKeyboardInteractive = "keyboard-interactive" in remaining
            canTryPassword = "password" in remaining

            if (!canTryPubkey && !canTryKeyboardInteractive && !canTryPassword) break
        }

        return false
    }

    private fun tryPublicKeyAuth(
        conn: Connection,
        username: String,
    ): Boolean {
        val loadedKeys = service.loadedKeypairs.values.toList()
        for (keyPair in loadedKeys) {
            try {
                if (conn.authenticateWithPublicKey(username, keyPair)) {
                    return true
                }
            } catch (e: IOException) {
                Timber.d(e, "$TAG: Public key auth failed")
            }
        }
        return false
    }

    /**
     * Try the specific pubkey assigned to this host (Host.pubkeyId), unlocking it
     * (prompting for its password if encrypted) when it isn't already loaded.
     * Unlike [tryPublicKeyAuth], this does NOT fall back to any other loaded key —
     * a host with an explicitly assigned key should only ever authenticate with it.
     */
    private fun tryHostPubkeyAuth(
        conn: Connection,
        username: String,
        pubkeyId: Long,
    ): Boolean {
        val pubkey =
            runBlocking { service.pubkeyRepository.getPubkeyById(pubkeyId) } ?: run {
                Timber.w("$TAG: Host has pubkeyId=$pubkeyId but no matching key was found (deleted?)")
                return false
            }

        val keyPair =
            service.loadedKeypairs[pubkey.nickname] ?: run {
                var password: String? = null
                if (pubkey.encrypted) {
                    password = bridge.promptForInputBlocking("Password for key \"${pubkey.nickname}\":", false)
                    if (password == null) {
                        Timber.d("$TAG: Key unlock prompt cancelled for ${pubkey.nickname}")
                        return false
                    }
                }
                val unlocked =
                    PubkeyUtils.convertToKeyPair(pubkey, password) ?: run {
                        Timber.w("$TAG: Failed to unlock key ${pubkey.nickname}")
                        return false
                    }
                // Cache it in-memory so reconnects don't need to re-prompt for the password.
                service.loadedKeypairs[pubkey.nickname] = unlocked
                unlocked
            }

        return try {
            val success = conn.authenticateWithPublicKey(username, keyPair)
            if (!success) Timber.d("$TAG: Public key auth failed for host-assigned key ${pubkey.nickname}")
            success
        } catch (e: IOException) {
            Timber.d(e, "$TAG: Public key auth failed for host-assigned key ${pubkey.nickname}")
            false
        }
    }

    private fun finishConnection(conn: Connection) {
        Timber.d("$TAG: Opening SSH session...")
        val sess = conn.openSession()
        session = sess
        Timber.d("$TAG: Session opened successfully")

        val cols = bridge.columns
        val rows = bridge.rows
        Timber.d("$TAG: Requesting PTY (${cols}x$rows)...")
        sess.requestPTY("xterm-256color", cols, rows, 0, 0, null)
        Timber.d("$TAG: PTY requested successfully")

        Timber.d("$TAG: Starting shell...")
        sess.startShell()
        Timber.d("$TAG: Shell started successfully")

        stdin = sess.stdin
        stdout = sess.stdout
        stderr = sess.stderr

        connected = true
        sessionOpen = true

        Timber.d("$TAG: Setting up I/O streams...")
        if (host.postLoginScript.isNotBlank()) {
            try {
                Timber.d("$TAG: Sending post-login script...")
                stdin?.write("${host.postLoginScript}\n".toByteArray())
                stdin?.flush()
            } catch (e: IOException) {
                Timber.w(e, "$TAG: Failed to send post-login script")
            }
        }

        Timber.d("$TAG: Notifying bridge of connection...")
        bridge.onConnected()
        Timber.d("$TAG: Adding connection monitor...")
        conn.addConnectionMonitor(this)
        startKeepAlive()
        Timber.d("$TAG: Connection setup completed!")
    }

    override fun read(): Int = stdout?.read() ?: -1

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = stdout?.read(buffer, offset, length) ?: -1

    override fun write(buffer: ByteArray) {
        val stream = stdin
        if (stream == null) {
            Timber.e("$TAG: write() without stdin, dropping ${buffer.size} bytes (sessionOpen=$sessionOpen, connected=$connected)")
            return
        }
        // Never log keyboard input: every keystroke is its own write, so even a single
        // byte per call would reconstruct typed secrets (sudo/su passwords, tokens).
        stream.write(buffer)
    }

    override fun flush() {
        val stream = stdin
        if (stream == null) {
            android.util.Log.w("SSH", "⚠️ SSH.flush(): stdin is NULL!")
            return
        }
        stream.flush()
    }

    override fun close() {
        keepAliveRunning = false
        connected = false
        sessionOpen = false
        try {
            session?.close()
        } catch (e: Exception) {
            Timber.d(e, "$TAG: Error closing session")
        }
        try {
            connection?.close()
        } catch (e: Exception) {
            Timber.d(e, "$TAG: Error closing connection")
        }
        session = null
        connection = null
        stdin = null
        stdout = null
        stderr = null
    }

    override fun isConnected(): Boolean = connected

    override fun isSessionOpen(): Boolean = sessionOpen

    override fun setDimensions(
        cols: Int,
        rows: Int,
        width: Int,
        height: Int,
    ) {
        try {
            session?.resizePTY(cols, rows, width, height)
        } catch (e: IOException) {
            Timber.w(e, "$TAG: Error resizing PTY")
        }
    }

    override fun getNamespace(): String = "ssh://${host.hostname}:${host.port}"

    override fun connectionLost(reason: Throwable?) {
        connected = false
        sessionOpen = false
        Timber.w(reason, "$TAG: SSH connection lost")
        bridge.dispatchDisconnect(reason?.message ?: "Connection lost")
    }

    override fun replyToChallenge(
        name: String?,
        instruction: String?,
        numPrompts: Int,
        prompt: Array<out String>?,
        echo: BooleanArray?,
    ): Array<String>? {
        if (numPrompts == 0) return arrayOf()

        val firstPrompt = prompt?.firstOrNull()
        val firstEcho = echo?.firstOrNull() ?: false
        if (KeyboardInteractivePassword.isPasswordPrompt(numPrompts, firstPrompt, firstEcho)) {
            val saved = if (!savedPasswordOfferedInteractively) CredentialStore.getPassword(host.id) else null
            if (saved != null) {
                savedPasswordOfferedInteractively = true
                interactivePassword = null
                interactivePasswordWasSaved = true
                Timber.d("$TAG: Answering keyboard-interactive with saved password")
                return arrayOf(saved)
            }
            val typed = bridge.promptForInputBlocking(firstPrompt!!, false) ?: return null
            interactivePassword = typed
            interactivePasswordWasSaved = false
            return arrayOf(typed)
        }

        val responses = mutableListOf<String>()
        prompt?.forEachIndexed { i, p ->
            val response =
                bridge.promptForInputBlocking(p, echo?.get(i) ?: false)
                    ?: return null
            responses.add(response)
        }
        return responses.toTypedArray()
    }

    inner class HostKeyVerifier : ServerHostKeyVerifier {
        override fun verifyServerHostKey(
            hostname: String,
            port: Int,
            serverHostKeyAlgorithm: String,
            serverHostKey: ByteArray,
        ): Boolean {
            val keyData = Base64.encodeToString(serverHostKey, Base64.NO_WRAP)

            // All pinned keys for the host, regardless of algorithm (see HostKeyPolicy).
            val existing =
                runBlocking(Dispatchers.IO) {
                    knownHostRepository.getAllForHost(host.id)
                }

            return when (HostKeyPolicy.evaluate(existing.map { it.hostKey }, keyData)) {
                HostKeyPolicy.Decision.TRUSTED -> true
                HostKeyPolicy.Decision.CHANGED -> {
                    hostKeyChanged = true
                    Timber.w(
                        "$TAG: Host key for host ${host.id} does not match any of ${existing.size} pinned key(s) " +
                            "(presented $serverHostKeyAlgorithm) — rejecting (possible MITM or key rotation)",
                    )
                    false
                }
                HostKeyPolicy.Decision.UNKNOWN_HOST -> promptAndPin(hostname, port, serverHostKeyAlgorithm, serverHostKey, keyData)
            }
        }

        private fun promptAndPin(
            hostname: String,
            port: Int,
            serverHostKeyAlgorithm: String,
            serverHostKey: ByteArray,
            keyData: String,
        ): Boolean {
            return run {
                Timber.d("$TAG: No known host key found for ${host.id}, prompting user...")
                val fingerprints = SshFingerprint.build(serverHostKeyAlgorithm, serverHostKey)
                val accepted = bridge.promptForHostKeyVerificationBlocking(hostname, port, fingerprints)
                Timber.d("$TAG: User response to host key prompt: $accepted")
                if (accepted) {
                    runBlocking(Dispatchers.IO) {
                        knownHostRepository.insert(
                            KnownHost(
                                hostId = host.id,
                                hostname = hostname,
                                port = port,
                                hostKeyAlgo = serverHostKeyAlgorithm,
                                hostKey = keyData,
                            ),
                        )
                    }
                    Timber.d("$TAG: Host key saved to database")
                } else {
                    Timber.w("$TAG: User rejected host key, connection will fail")
                }
                accepted
            }
        }
    }

    /**
     * Put already-pinned key types first so a legitimate server with several host keys
     * negotiates the one we know, instead of tripping the CHANGED rejection.
     */
    private fun preferKnownHostKeyAlgorithms(conn: Connection) {
        try {
            val known = runBlocking(Dispatchers.IO) { knownHostRepository.getAllForHost(host.id) }
            if (known.isEmpty()) return
            val knownTypes =
                known.mapNotNull { entry ->
                    runCatching { HostKeyPolicy.keyTypeFromBlob(Base64.decode(entry.hostKey, Base64.NO_WRAP)) }.getOrNull()
                }
            val available = Connection.getAvailableServerHostKeyAlgorithms().toList()
            val ordered = HostKeyPolicy.preferredAlgorithms(knownTypes, available)
            conn.setServerHostKeyAlgorithms(ordered.toTypedArray())
            Timber.d("$TAG: Host key algorithm preference: ${ordered.joinToString(",")}")
        } catch (e: Exception) {
            // Keep library defaults; the verifier still enforces pinning.
            Timber.w(e, "$TAG: Could not set host key algorithm preference")
        }
    }

}
