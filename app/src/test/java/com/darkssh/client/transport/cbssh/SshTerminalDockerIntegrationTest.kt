package com.darkssh.client.transport.cbssh

import android.app.Application
import android.util.Base64
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.entity.KnownHost
import com.darkssh.client.data.entity.Pubkey
import com.darkssh.client.data.repository.KnownHostRepository
import com.darkssh.client.data.repository.PubkeyRepository
import com.darkssh.client.service.CredentialStore
import com.darkssh.client.service.TerminalBridge
import com.darkssh.client.service.TerminalService
import com.darkssh.client.service.OsDetector
import com.darkssh.client.data.model.OsType
import com.darkssh.client.transport.CbsshTerminalTransport as SSH
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import kotlinx.coroutines.CancellationException
import java.security.KeyPair
import java.security.KeyPairGenerator
import org.connectbot.sshlib.SshSigning
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import timber.log.Timber

/**
 * Exercises the actual terminal transport, not a separately authenticated SshClient.
 * The bridge deliberately does not start its relay: all output is read here with a deadline.
 * Requires Docker; excluded from ordinary unit tests by the DockerIntegrationTest category.
 */
@Category(DockerIntegrationTest::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
// Keep JVM ChaCha20 parameter handling consistent. With Robolectric's injected Conscrypt,
// cbssh's decrypt-mode capability probe falls back to SunJCE (ChaCha20ParameterSpec),
// but encrypt-mode init selects Conscrypt, which ignores that spec and generates a random IV.
// SunJCE supports the same secure cipher without this mixed-provider JVM-only behavior.
@ConscryptMode(ConscryptMode.Mode.OFF)
class SshTerminalDockerIntegrationTest {
    companion object {
        private const val USERNAME = "testuser"
        private const val PASSWORD = "testpass123"
        private const val SSH_PORT = 2222
        private const val READ_TIMEOUT_SECONDS = 15L
    }

    private lateinit var container: GenericContainer<*>
    private lateinit var host: Host
    private lateinit var bridge: TerminalBridge
    private lateinit var service: TerminalService
    private lateinit var repository: KnownHostRepository
    private lateinit var authorizedKey: KeyPair
    private var transport: SSH? = null
    private val diagnosticTree = object : Timber.DebugTree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            println("$tag: $message")
        }
    }

    // Daemon workers are a final safeguard if a broken transport ignores both close and interrupt.
    private val ioExecutor = daemonExecutor("ssh-terminal-integration-io")
    private val closeExecutor = daemonExecutor("ssh-terminal-integration-close")

    @Before
    fun startServer() {
        Timber.plant(diagnosticTree)
        CredentialStore.clearAll()
        authorizedKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = SshSigning.encodePublicKey(authorizedKey)
        val authorizedLine = "${publicKey.algorithmName} ${Base64.encodeToString(publicKey.publicKeyBlob, Base64.NO_WRAP)}"
        container =
            GenericContainer(DockerImageName.parse("linuxserver/openssh-server:latest"))
                .withEnv("PUID", "1000")
                .withEnv("PGID", "1000")
                .withEnv("TZ", "Etc/UTC")
                .withEnv("PASSWORD_ACCESS", "true")
                .withEnv("USER_NAME", USERNAME)
                .withEnv("USER_PASSWORD", PASSWORD)
                .withEnv("PUBLIC_KEY", authorizedLine)
                .withExposedPorts(SSH_PORT)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)))
        container.start()

        host =
            Host(
                id = 71001L,
                hostname = container.host,
                port = container.getMappedPort(SSH_PORT),
                username = USERNAME,
            )
        bridge = mock(TerminalBridge::class.java)
        service = mock(TerminalService::class.java)
        repository = mock(KnownHostRepository::class.java)
        whenever(bridge.columns).thenReturn(80)
        whenever(bridge.rows).thenReturn(24)
        whenever(bridge.isClosed).thenReturn(false)
        // The service exposes ConcurrentHashMap, rather than the more general HashMap type.
        whenever(service.loadedKeypairs).thenReturn(ConcurrentHashMap<String, KeyPair>())
        doNothing().whenever(bridge).onConnected()
        runBlocking {
            whenever(bridge.promptForPassword()).thenReturn(PASSWORD)
            whenever(bridge.promptForHostKeyVerification(any(), any(), any())).thenReturn(true)
            whenever(repository.getAllForHost(host.id)).thenReturn(emptyList())
            whenever(repository.insert(any())).thenReturn(1L)
        }
    }

    @After
    fun closeTransportAndServer() {
        try {
            closeTransport()
        } finally {
            ioExecutor.shutdownNow()
            closeExecutor.shutdownNow()
            try {
                if (::container.isInitialized) container.stop()
            } finally {
                CredentialStore.clearAll()
                Timber.uproot(diagnosticTree)
            }
        }
    }

    @Test
    fun `password authentication opens an interactive PTY shell`() {
        val ssh = connect()
        assertTrue("Transport must report connected after authentication", ssh.isConnected())
        verify(bridge).onConnected()
        runBlocking {
            verify(bridge).promptForPassword()
            verify(bridge, never()).promptForInput(any(), any())
        }

        val first = marker("PTY")
        send(ssh, "test -t 0 && test -t 1 && ${printMarker(first)}")
        readUntil(ssh, "$first\r\n")

        // A second command proves that the shell remains interactive after the first read/write.
        val second = marker("INTERACTIVE")
        send(ssh, printMarker(second))
        readUntil(ssh, "$second\r\n")

        closeTransport()
        assertFalse("close must clear the public connection state", ssh.isConnected())
    }

    @Test
    fun `PTY starts at bridge dimensions and setDimensions reaches stty`() {
        val ssh = connect()
        val initial = marker("INITIAL_SIZE")
        send(ssh, "${printMarker(initial, newline = false)}; stty size")
        readUntil(ssh, "$initial:24 80\r\n")

        boundedIo { ssh.setDimensions(132, 43, 1056, 688) }
        val resized = marker("RESIZED")
        // Window-change has no SSH acknowledgement. Allow the server a bounded interval to
        // process it, but print the success marker only when the real PTY has the requested size.
        send(
            ssh,
            "n=0; while [ \"\$(stty size)\" != '43 132' ] && [ \"\$n\" -lt 50 ]; do " +
                "n=\$((n + 1)); sleep 0.1; done; " +
                "${printMarker(resized, newline = false)}; stty size",
        )
        readUntil(ssh, "$resized:43 132\r\n")
    }

    @Test
    fun `post login script executes without manual terminal input`() {
        val completed = marker("POST_LOGIN")
        val ssh = connect(host.copy(postLoginScript = printMarker(completed)))

        // No write from the test: only SSH's postLoginScript path can produce this output.
        readUntil(ssh, "$completed\r\n")
        assertTrue(ssh.isConnected())
    }

    @Test
    fun `accepting an unknown host prompts and persists the presented server key`() {
        val ssh = connect()
        val authenticated = marker("HOST_ACCEPTED")
        send(ssh, printMarker(authenticated))
        readUntil(ssh, "$authenticated\r\n")

        runBlocking {
            verify(repository, atLeastOnce()).getAllForHost(host.id)
            val fingerprints = argumentCaptor<String>()
            verify(bridge).promptForHostKeyVerification(eq(host.hostname), eq(host.port), fingerprints.capture())
            assertTrue("Host verification must display a fingerprint", fingerprints.firstValue.contains("SHA256:"))

            val saved = argumentCaptor<KnownHost>()
            verify(repository).insert(saved.capture())
            assertEquals(host.id, saved.firstValue.hostId)
            assertEquals(host.hostname, saved.firstValue.hostname)
            assertEquals(host.port, saved.firstValue.port)
            val keyBytes = Base64.decode(saved.firstValue.hostKey, Base64.NO_WRAP)
            assertTrue("Persist a real SSH public-key blob, not an empty placeholder", keyBytes.size > 32)
            assertEquals(saved.firstValue.hostKeyAlgo, com.darkssh.client.transport.HostKeyPolicy.keyTypeFromBlob(keyBytes))
        }
    }

    @Test
    fun `a mismatched pinned host key rejects before password or input prompts`() {
        val pinned =
            KnownHost(
                hostId = host.id,
                hostname = host.hostname,
                port = host.port,
                hostKeyAlgo = "ssh-ed25519",
                hostKey = differentEd25519Key(),
            )
        runBlocking {
            whenever(repository.getAllForHost(host.id)).thenReturn(listOf(pinned))
        }

        val ssh = SSH(host, bridge, service, repository)
        transport = ssh
        var failure: IOException? = null
        try {
            boundedIo { ssh.connect() }
        } catch (expected: IOException) {
            failure = expected
        }
        assertTrue("A host-key mismatch must fail connect, not merely skip authentication", failure != null)
        assertFalse("A rejected host must never become connected", ssh.isConnected())
        verify(bridge).dispatchDisconnect(SSH.HOST_KEY_CHANGED_MESSAGE)
        verify(bridge, never()).onConnected()
        runBlocking {
            // Prove verification was reached, rather than passing on an unrelated startup failure.
            verify(repository, atLeastOnce()).getAllForHost(host.id)
            verify(repository, never()).insert(any())
            verify(bridge, never()).promptForHostKeyVerification(any(), any(), any())
            verify(bridge, never()).promptForPassword()
            verify(bridge, never()).promptForInput(any(), any())
        }
        assertEquals("Rejected handshakes must not cache credentials", null, CredentialStore.getPassword(host.id))
    }

    private fun connect(connectionHost: Host = host): SSH {
        val ssh = SSH(connectionHost, bridge, service, repository)
        transport = ssh
        boundedIo { ssh.connect() }
        assertTrue("connect must establish the real terminal session", ssh.isConnected())
        return ssh
    }

    @Test
    fun `OS detection uses a separate channel without closing the terminal`() {
        val ssh = connect()
        val detected = runBlocking { OsDetector.detectOs(checkNotNull(ssh.getConnection())) }
        assertTrue("Real OpenSSH server must have detectable OS", detected != OsType.UNKNOWN)
        assertTrue("Closing detection channel must retain terminal session", ssh.isSessionOpen())
        val marker = marker("AFTER_OS")
        send(ssh, printMarker(marker))
        readUntil(ssh, marker)
    }

    @Test
    fun `remote shell exit preserves final output then reports EOF`() {
        val ssh = connect()
        val marker = marker("EXIT")
        send(ssh, "${printMarker(marker)}; exit")
        readUntil(ssh, marker)
        boundedIo(READ_TIMEOUT_SECONDS) {
            val buffer = ByteArray(4096)
            while (ssh.read(buffer, 0, buffer.size) != -1) { /* drain final shell output */ }
        }
        assertFalse(ssh.isConnected())
    }

    @Test
    fun `loaded RSA key authenticates with negotiated SHA2 without a password`() {
        service.loadedKeypairs["authorized"] = authorizedKey
        val ssh = connect()
        runBlocking {
            verify(bridge, never()).promptForPassword()
            verify(bridge, never()).promptForInput(any(), any())
        }
        val marker = marker("RSA")
        send(ssh, printMarker(marker))
        readUntil(ssh, marker)
    }

    @Test
    fun `assigned cached encrypted key authenticates without unlocking or using other loaded keys`() {
        val keys = mock(PubkeyRepository::class.java)
        whenever(service.pubkeyRepository).thenReturn(keys)
        service.loadedKeypairs["assigned"] = authorizedKey
        service.loadedKeypairs["other"] = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        runBlocking {
            whenever(keys.getPubkeyById(41L)).thenReturn(Pubkey(id = 41L, nickname = "assigned", encrypted = true))
        }
        connect(host.copy(pubkeyId = 41L))
        runBlocking {
            verify(keys).getPubkeyById(41L)
            verify(bridge, never()).promptForPassword()
            verify(bridge, never()).promptForInput(any(), any())
        }
    }

    @Test
    fun `close interrupts a waiting terminal read and remains idempotent`() {
        val ssh = connect()
        val ready = CountDownLatch(1)
        val read = ioExecutor.submit(Callable {
            ready.countDown()
            try {
                val buffer = ByteArray(4096)
                while (ssh.read(buffer, 0, buffer.size) != -1) { /* wait for output or close */ }
            } catch (_: CancellationException) {
                // The transport lifecycle must cancel Relay's blocking adapter.
            }
            true
        })
        assertTrue(ready.await(2, TimeUnit.SECONDS))
        closeTransport()
        assertTrue(read.get(5, TimeUnit.SECONDS))
        closeTransport()
        assertFalse(ssh.isConnected())
        assertFalse(ssh.isSessionOpen())
    }

    @Test
    fun `closing a tab cancels connection even while server never sends its banner`() {
        ServerSocket(0).use { listener ->
            listener.soTimeout = 5000
            val ssh = SSH(host.copy(hostname = "127.0.0.1", port = listener.localPort), bridge, service, repository)
            transport = ssh
            val connecting = ioExecutor.submit(Callable {
                try {
                    ssh.connect()
                    false
                } catch (_: CancellationException) {
                    true
                }
            })
            listener.accept().use {
                whenever(bridge.isClosed).thenReturn(true)
                closeTransport()
                assertTrue(connecting.get(5, TimeUnit.SECONDS))
                assertFalse(ssh.isConnected())
                verify(bridge, never()).onConnected()
            }
        }
    }

    private fun send(ssh: SSH, command: String) {
        boundedIo {
            ssh.write("$command\n".toByteArray(Charsets.UTF_8))
            ssh.flush()
        }
    }

    private fun readUntil(ssh: SSH, expected: String): String {
        val output = StringBuffer()
        try {
            return boundedIo(READ_TIMEOUT_SECONDS) {
                val buffer = ByteArray(4096)
                while (!output.contains(expected)) {
                    val count = ssh.read(buffer, 0, buffer.size)
                    if (count < 0) throw AssertionError("SSH reached EOF before <$expected>. Output:\n$output")
                    if (count > 0) output.append(String(buffer, 0, count, Charsets.UTF_8))
                }
                output.toString()
            }
        } catch (timeout: TimeoutException) {
            throw AssertionError("SSH read timed out waiting for <$expected>. Output:\n$output", timeout)
        }
    }

    private fun <T> boundedIo(
        seconds: Long = 45L,
        operation: () -> T,
    ): T {
        val future = ioExecutor.submit(Callable { operation() })
        try {
            return future.get(seconds, TimeUnit.SECONDS)
        } catch (failure: ExecutionException) {
            throw (failure.cause ?: failure)
        } catch (timeout: TimeoutException) {
            // Interrupt alone need not unblock a socket read. Close on a separate worker first.
            closeTransport()
            throw timeout
        } finally {
            future.cancel(true)
        }
    }

    private fun closeTransport() {
        val ssh = transport ?: return
        val future = closeExecutor.submit { ssh.close() }
        try {
            future.get(5, TimeUnit.SECONDS)
        } finally {
            future.cancel(true)
        }
    }

    private fun marker(label: String): String = "DARKSSH_${label}_${UUID.randomUUID().toString().replace("-", "")}"

    /** Split marker arguments so PTY echo of the command can never satisfy an output assertion. */
    private fun printMarker(
        marker: String,
        newline: Boolean = true,
    ): String {
        val suffix = marker.removePrefix("DARKSSH_")
        return if (newline) {
            "printf '%s%s\\n' 'DARKSSH_' '$suffix'"
        } else {
            "printf '%s%s:' 'DARKSSH_' '$suffix'"
        }
    }

    private fun differentEd25519Key(): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { stream ->
            val algorithm = "ssh-ed25519".toByteArray(Charsets.US_ASCII)
            stream.writeInt(algorithm.size)
            stream.write(algorithm)
            stream.writeInt(32)
            stream.write(ByteArray(32) { 0x5a.toByte() })
        }
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }

    private fun daemonExecutor(name: String): ExecutorService =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, name).apply { isDaemon = true } }
}
