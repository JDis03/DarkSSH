package com.darkssh.client.transport

import android.util.Base64
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.entity.KnownHost
import com.darkssh.client.data.repository.KnownHostRepository
import com.darkssh.client.service.CredentialStore
import com.darkssh.client.service.TerminalBridge
import com.darkssh.client.service.TerminalService
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
    }

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

    private fun startKeepAlive() {
        keepAliveRunning = true
        Thread {
            while (keepAliveRunning && connected) {
                try {
                    Thread.sleep(30000)
                    connection?.sendIgnorePacket()
                    Timber.d("$TAG: Keepalive sent")
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Timber.d(e, "$TAG: Keepalive failed")
                }
            }
        }.apply {
            name = "SSH-KeepAlive"
            isDaemon = true
            start()
        }
    }

    override fun connect() {
        val hostname = host.hostname
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
            bridge.dispatchDisconnect(e.message ?: "Connection failed")
            throw e
        }
    }

    private fun authenticate(conn: Connection): Boolean {
        val username = host.username

        val initial =
            conn.getRemainingAuthMethods(username)
                ?: return false

        if (initial.isEmpty()) return true

        var canTryPubkey = "publickey" in initial
        var canTryKeyboardInteractive = "keyboard-interactive" in initial
        var canTryPassword = "password" in initial

        for (attempt in 0 until AUTH_TRIES) {
            if (conn.isAuthMethodAvailable(username, "none")) {
                try {
                    if (conn.authenticateWithNone(username)) return true
                } catch (e: IOException) {
                    Timber.d(e, "$TAG: none auth failed")
                }
            }

            if (canTryPubkey && conn.isAuthMethodAvailable(username, "publickey")) {
                if (tryPublicKeyAuth(conn, username)) return true
            }

            if (canTryKeyboardInteractive && conn.isAuthMethodAvailable(username, "keyboard-interactive")) {
                if (conn.authenticateWithKeyboardInteractive(username, this)) {
                    return true
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
            android.util.Log.e(
                "SSH",
                "❌ SSH.write(): stdin is NULL! Dropping ${buffer.size} bytes. sessionOpen=$sessionOpen, connected=$connected",
            )
            return
        }
        android.util.Log.d("SSH", "📤 SSH.write(): writing ${buffer.size} bytes, first byte=${"%02x".format(buffer[0])}")
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

            // Use IO dispatcher for database operations in blocking context
            val existing =
                runBlocking(Dispatchers.IO) {
                    knownHostRepository.getByHostIdAndAlgo(host.id, serverHostKeyAlgorithm)
                }

            return if (existing.isEmpty()) {
                val fingerprints = buildFingerprints(serverHostKeyAlgorithm, serverHostKey)
                val accepted = bridge.promptForHostKeyVerificationBlocking(hostname, port, fingerprints)
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
                }
                accepted
            } else {
                existing.any { it.hostKey == keyData }
            }
        }
    }

    private fun buildFingerprints(
        algo: String,
        key: ByteArray,
    ): String {
        val md5 =
            java.security.MessageDigest
                .getInstance("MD5")
                .digest(key)
                .joinToString(":") { "%02x".format(it) }
        val sha256 =
            Base64.encodeToString(
                java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest(key),
                Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
            )
        return "$algo\nSHA256:$sha256\nMD5:$md5"
    }
}
