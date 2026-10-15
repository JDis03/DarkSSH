package com.darkssh.client.transport

import android.util.Base64
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.KnownHostRepository
import com.darkssh.client.service.TerminalBridge
import com.darkssh.client.service.TerminalService
import com.darkssh.client.transport.cbssh.DarkSshHostKeyVerifier
import com.darkssh.client.transport.cbssh.TerminalAuthHandler
import com.darkssh.client.transport.cbssh.TerminalOutputStream
import com.darkssh.client.transport.cbssh.withNetworkDeadline
import com.darkssh.client.util.TextSanitizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.connectbot.sshlib.AuthResult
import org.connectbot.sshlib.ConnectResult
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.SshClientConfig
import org.connectbot.sshlib.SshSession
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Experimental cbssh terminal transport, preserving Relay's blocking contract.
 * Opt-in debug builds only; retain SSH/Trilead until Android, MFA and legacy-key
 * compatibility are validated. Errors must never trigger a silent backend fallback.
 */
class CbsshTerminalTransport(
    host: Host,
    bridge: TerminalBridge,
    service: TerminalService,
    private val knownHostRepository: KnownHostRepository,
) : AbsTransport(host, bridge, service) {
    companion object {
        private const val AUTH_TRIES = 20
        private const val SESSION_TIMEOUT_MS = 15_000L

        const val HOST_KEY_CHANGED_MESSAGE =
            "⚠️ The server's host key changed. This can be a man-in-the-middle attack. " +
                "Connection refused and no password was sent. If the server key was rotated " +
                "legitimately, open Edit Host → Forget host key and connect again."
    }

    // close() cancels pending connect, prompts, reads and writes. Unlike a bare
    // runBlocking {}, each blocking adapter remains a child of this lifecycle.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private val lifecycleLock = Any()
    @Volatile private var connection: SshClient? = null
    @Volatile private var session: SshSession? = null
    @Volatile private var output: TerminalOutputStream? = null
    @Volatile private var connected = false

    /** Used by OS detection to open a separate exec channel on this connection. */
    fun getConnection(): SshClient? = connection

    override fun connect() {
        try {
            runBlocking(scope.coroutineContext) {
                ensureActive()
                val hostname = TextSanitizer.sanitizeStrict(host.hostname)
                val port = host.port.takeIf { it > 0 } ?: 22
                val prompting = MutableStateFlow(false)
                val verifier = DarkSshHostKeyVerifier(host, knownHostRepository) { _, fingerprints ->
                    prompting.value = true
                    try {
                        bridge.promptForHostKeyVerification(hostname, port, fingerprints)
                    } finally {
                        prompting.value = false
                    }
                }
                val knownTypes = knownHostRepository.getAllForHost(host.id).mapNotNull { entry ->
                    runCatching {
                        HostKeyPolicy.keyTypeFromBlob(Base64.decode(entry.hostKey, Base64.NO_WRAP))
                    }.getOrNull()
                }
                val client = SshClient(SshClientConfig {
                    this.host = hostname
                    this.port = port
                    hostKeyVerifier = verifier
                    enableCompression = this@CbsshTerminalTransport.host.compression
                    keepAliveIntervalMs = 30_000L
                    // A short-lived OS detection channel must not retire the transport.
                    autoDisconnectOnLastChannelClose = false
                    hostKeyAlgorithms = HostKeyPolicy.preferredAlgorithms(
                        knownTypes, hostKeyAlgorithms.split(',').filter { it.isNotEmpty() },
                    ).joinToString(",")
                })
                synchronized(lifecycleLock) {
                    if (closed.get()) throw CancellationException("Terminal closed")
                    connection = client
                }
                val result = withNetworkDeadline(prompting) { client.connect() }
                ensureActive() // Some library entry points convert cancellation to a Result.
                when (result) {
                    ConnectResult.Success -> Unit
                    is ConnectResult.HostKeyRejected -> throw IOException(
                        if (verifier.hostKeyChanged) HOST_KEY_CHANGED_MESSAGE else "Host key rejected",
                    )
                    is ConnectResult.TransportError -> throw IOException("SSH connection failed", result.cause)
                    is ConnectResult.AlgorithmMismatch -> throw IOException("Algorithm mismatch: ${result.message}")
                    is ConnectResult.ProtocolError -> throw IOException("Protocol error: ${result.message}", result.cause)
                }
                authenticate(client)
                withTimeout(SESSION_TIMEOUT_MS) {
                    val shell = client.openSession() ?: throw IOException("Could not open SSH session")
                    synchronized(lifecycleLock) {
                        if (closed.get()) {
                            shell.close()
                            throw CancellationException("Terminal closed")
                        }
                        session = shell
                    }
                    ensureActive()
                    if (!shell.requestPty("xterm-256color", bridge.columns, bridge.rows)) {
                        throw IOException("Server refused terminal PTY")
                    }
                    if (!shell.requestShell()) throw IOException("Server refused shell")
                    synchronized(lifecycleLock) {
                        if (closed.get()) throw CancellationException("Terminal closed")
                        output = TerminalOutputStream(scope, shell.stdout, shell.stderr)
                    }
                    if (host.postLoginScript.isNotBlank()) {
                        shell.write("${host.postLoginScript}\n".toByteArray())
                    }
                }
                ensureActive()
                synchronized(lifecycleLock) {
                    if (closed.get() || bridge.isClosed) throw CancellationException("Terminal closed")
                    connected = true
                    // Serialize publication with transport.close(). A concurrent
                    // bridge.close must finish resetting its state after this notification.
                    bridge.onConnected()
                }
            }
        } catch (e: CancellationException) {
            close()
            if (!bridge.isClosed) bridge.dispatchDisconnect(e.message ?: "Connection cancelled")
            throw e
        } catch (e: Exception) {
            close()
            if (!bridge.isClosed) bridge.dispatchDisconnect(e.message ?: "Connection failed")
            throw e
        }
    }

    private suspend fun authenticate(client: SshClient) {
        val handler = TerminalAuthHandler(host, bridge, service)
        val username = TextSanitizer.sanitize(host.username)
        repeat(AUTH_TRIES) {
            val passwordRequests = handler.passwordRequests
            var result = withNetworkDeadline(handler.prompting) { client.authenticate(username, handler) }
            currentCoroutineContext().ensureActive()
            handler.onAttemptFinished(result is AuthResult.Success)
            // cbssh 0.5.1's strategy selects *only* KBI when both methods are
            // advertised. Some sshd/PAM setups advertise KBI but reject it without
            // a challenge. Preserve our existing KBI -> password fallback instead
            // of retrying KBI until sshd's MaxAuthTries disconnects the client.
            if (result is AuthResult.Failure && "password" in result.allowedMethods &&
                handler.passwordRequests == passwordRequests && !handler.cancelled
            ) {
                val password = handler.onPasswordNeeded() ?: throw CancellationException("Authentication cancelled")
                result = withNetworkDeadline(handler.prompting) { client.authenticatePassword(username, password) }
                currentCoroutineContext().ensureActive()
                handler.onAttemptFinished(result is AuthResult.Success)
            }
            when (result) {
                AuthResult.Success -> return
                is AuthResult.Error -> throw IOException("Authentication failed: ${result.message}", result.cause)
                is AuthResult.Failure -> {
                    if (handler.cancelled || result.allowedMethods.none { it == "password" || it == "keyboard-interactive" }) {
                        throw IOException("Authentication failed")
                    }
                }
            }
        }
        throw IOException("Authentication failed")
    }

    override fun read(): Int {
        val value = output?.read() ?: -1
        if (value == -1) connected = false
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = output?.read(buffer, offset, length) ?: -1
        if (count == -1) connected = false
        return count
    }

    override fun write(buffer: ByteArray) {
        val shell = session ?: throw IOException("SSH session is not open")
        // Never log input: individual writes can reconstruct typed secrets.
        runBlocking(scope.coroutineContext) { shell.write(buffer) }
    }

    // cbssh's write already waits for transport backpressure; there is no buffered stdin.
    override fun flush() = Unit

    override fun setDimensions(cols: Int, rows: Int, width: Int, height: Int) {
        val shell = session ?: return
        runBlocking(scope.coroutineContext) {
            withTimeout(SESSION_TIMEOUT_MS) {
                shell.resizeTerminal(cols, rows, width, height)
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel()
        val resources = synchronized(lifecycleLock) {
            connected = false
            val snapshot = Triple(output, session, connection)
            output = null
            session = null
            connection = null
            snapshot
        }
        resources.first?.close()
        try {
            resources.second?.close()
        } catch (e: Exception) {
            Timber.d(e, "SSH: Session cleanup ended")
        }
        val client = resources.third
        // The service may call close on Main. Never block it on network teardown.
        if (client != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    withTimeout(5_000L) { client.disconnect() }
                } catch (e: Exception) {
                    Timber.d(e, "SSH: Connection cleanup ended")
                }
            }
        }
    }

    override fun isConnected(): Boolean = connected
    override fun isSessionOpen(): Boolean = connected && session?.isOpen == true
    override fun getNamespace(): String = "ssh://${host.hostname}:${host.port}"
}
