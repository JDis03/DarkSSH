/*
 * DarkSSH SFTP - cbssh end-to-end integration test against a real SSH server
 *
 * Spins up a real OpenSSH server via Testcontainers (linuxserver/openssh-server)
 * and exercises the cbssh stack (SshClient + SftpClient + TransferEngine)
 * end-to-end: connect, authenticate, list, create/rename/remove directories
 * and files, upload, download. No mocks — this is the strongest signal we
 * have that cbssh's SFTP implementation is a safe replacement for sshj
 * before sshj is removed.
 *
 * Requires a running Docker daemon. Tagged @Category(DockerIntegrationTest)
 * so it is excluded from the regular `./gradlew test` / `./init.sh` run —
 * see app/build.gradle.kts for the task wiring. Run explicitly with:
 *
 *   ./gradlew integrationTest
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport.cbssh

import kotlinx.coroutines.runBlocking
import org.connectbot.sshlib.AuthResult
import org.connectbot.sshlib.ConnectResult
import org.connectbot.sshlib.HostKeyVerifier
import org.connectbot.sshlib.PublicKey
import org.connectbot.sshlib.SftpOpenFlag
import org.connectbot.sshlib.SftpResult
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.SshClientConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.IOException
import java.time.Duration

@Category(DockerIntegrationTest::class)
class SftpClient2DockerIntegrationTest {

    companion object {
        private const val USERNAME = "testuser"
        private const val PASSWORD = "testpass123"
        private const val CONTAINER_SSH_PORT = 2222
    }

    private lateinit var container: GenericContainer<*>
    private lateinit var client: SshClient

    @Before
    fun startContainerAndConnect() {
        container =
            GenericContainer(DockerImageName.parse("linuxserver/openssh-server:latest"))
                .withEnv("PUID", "1000")
                .withEnv("PGID", "1000")
                .withEnv("TZ", "Etc/UTC")
                .withEnv("PASSWORD_ACCESS", "true")
                .withEnv("USER_NAME", USERNAME)
                .withEnv("USER_PASSWORD", PASSWORD)
                .withExposedPorts(CONTAINER_SSH_PORT)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)))
        container.start()

        val host = container.host
        val port = container.getMappedPort(CONTAINER_SSH_PORT)

        client = connectAndAuthenticate(host, port)
    }

    @After
    fun disconnectAndStopContainer() {
        if (::client.isInitialized) {
            runBlocking { runCatching { client.disconnect() } }
        }
        if (::container.isInitialized) {
            container.stop()
        }
    }

    /**
     * Connects and authenticates, retrying a few times: the container's
     * exposed port can start accepting TCP connections slightly before
     * sshd inside is fully ready to negotiate, causing the first attempt
     * to occasionally fail with a transport error.
     */
    private fun connectAndAuthenticate(
        host: String,
        port: Int,
    ): SshClient {
        var lastError: Throwable? = null

        repeat(5) { attempt ->
            val verifier =
                object : HostKeyVerifier {
                    override suspend fun verify(key: PublicKey): Boolean = true
                }

            val candidate =
                SshClient(
                    config =
                        SshClientConfig {
                            this.host = host
                            this.port = port
                            this.hostKeyVerifier = verifier
                        },
                )

            try {
                val connectResult = runBlocking { candidate.connect() }
                if (connectResult !is ConnectResult.Success) {
                    lastError = IOException("Connect failed (attempt ${attempt + 1}): $connectResult")
                    return@repeat
                }

                val authResult = runBlocking { candidate.authenticatePassword(USERNAME, PASSWORD) }
                if (authResult !is AuthResult.Success) {
                    runBlocking { runCatching { candidate.disconnect() } }
                    lastError = IOException("Auth failed (attempt ${attempt + 1}): $authResult")
                    return@repeat
                }

                return candidate
            } catch (e: Exception) {
                lastError = e
                runBlocking { runCatching { candidate.disconnect() } }
                Thread.sleep(1000)
            }
        }

        throw IllegalStateException("Could not connect+authenticate to test SSH server", lastError)
    }

    @Test
    fun `connects and authenticates with password against a real SSH server`() {
        // Connection + auth already happened in @Before; getting here means
        // it succeeded. Also verify we can open an SFTP subsystem on top.
        val sftpResult = runBlocking { client.openSftp() }
        assertTrue("Expected openSftp to succeed, got: $sftpResult", sftpResult is SftpResult.Success)
    }

    @Test
    fun `lists the home directory and finds it non-null`() {
        runBlocking {
            val sftp = (client.openSftp() as SftpResult.Success).value

            val entries = sftp.listdir(".")
            assertTrue("Expected listdir to succeed, got: $entries", entries is SftpResult.Success)
        }
    }

    @Test
    fun `creates a directory and it appears in the listing`() {
        runBlocking {
            val sftp = (client.openSftp() as SftpResult.Success).value
            val dirName = "itest-dir-${System.nanoTime()}"

            val mkdirResult = sftp.mkdir(dirName)
            assertTrue("Expected mkdir to succeed, got: $mkdirResult", mkdirResult is SftpResult.Success)

            val listResult = sftp.listdir(".")
            assertTrue(listResult is SftpResult.Success)
            val names = (listResult as SftpResult.Success).value.map { it.filename }
            assertTrue("Expected $dirName in listing: $names", names.contains(dirName))
        }
    }

    @Test
    fun `uploads and downloads a file with byte-for-byte integrity via TransferEngine`() {
        runBlocking {
            val sftp = (client.openSftp() as SftpResult.Success).value
            val engine = TransferEngine(sftp)
            val remotePath = "itest-upload-${System.nanoTime()}.bin"
            val originalBytes = ByteArray(200_000) { (it % 256).toByte() } // 200KB, multi-chunk

            val localUpload = java.io.File.createTempFile("itest-upload", ".bin")
            localUpload.deleteOnExit()
            localUpload.writeBytes(originalBytes)

            val uploadResult = engine.upload(localUpload, remotePath)
            assertTrue("Expected upload Success, got: $uploadResult", uploadResult is TransferResult.Success)
            assertEquals(
                originalBytes.size.toLong(),
                (uploadResult as TransferResult.Success).bytesTransferred,
            )

            val localDownload = java.io.File.createTempFile("itest-download", ".bin")
            localDownload.deleteOnExit()

            val downloadResult = engine.download(remotePath, localDownload)
            assertTrue("Expected download Success, got: $downloadResult", downloadResult is TransferResult.Success)
            assertEquals(originalBytes.size.toLong(), (downloadResult as TransferResult.Success).bytesTransferred)

            assertTrue(
                "Downloaded bytes must match the originally uploaded bytes",
                originalBytes.contentEquals(localDownload.readBytes()),
            )

            localUpload.delete()
            localDownload.delete()

            // Clean up the remote file
            sftp.remove(remotePath)
        }
    }

    @Test
    fun `renames a remote file`() {
        runBlocking {
            val sftp = (client.openSftp() as SftpResult.Success).value
            val originalName = "itest-rename-src-${System.nanoTime()}.txt"
            val newName = "itest-rename-dst-${System.nanoTime()}.txt"

            val handle =
                (
                    sftp.open(originalName, setOf(SftpOpenFlag.WRITE, SftpOpenFlag.CREATE, SftpOpenFlag.TRUNCATE))
                        as SftpResult.Success
                ).value
            sftp.write(handle, 0L, "hello".toByteArray())
            sftp.close(handle)

            val renameResult = sftp.rename(originalName, newName)
            assertTrue("Expected rename to succeed, got: $renameResult", renameResult is SftpResult.Success)

            val listResult = sftp.listdir(".") as SftpResult.Success
            val names = listResult.value.map { it.filename }
            assertTrue("Old name should no longer exist: $names", !names.contains(originalName))
            assertTrue("New name should exist: $names", names.contains(newName))

            sftp.remove(newName)
        }
    }

    @Test
    fun `removes a remote file`() {
        runBlocking {
            val sftp = (client.openSftp() as SftpResult.Success).value
            val fileName = "itest-remove-${System.nanoTime()}.txt"

            val handle =
                (
                    sftp.open(fileName, setOf(SftpOpenFlag.WRITE, SftpOpenFlag.CREATE, SftpOpenFlag.TRUNCATE))
                        as SftpResult.Success
                ).value
            sftp.write(handle, 0L, "to be removed".toByteArray())
            sftp.close(handle)

            val removeResult = sftp.remove(fileName)
            assertTrue("Expected remove to succeed, got: $removeResult", removeResult is SftpResult.Success)

            val listResult = sftp.listdir(".") as SftpResult.Success
            assertTrue(
                "File should no longer be listed after remove",
                listResult.value.none { it.filename == fileName },
            )
        }
    }

    @Test
    fun `rejects password authentication with the wrong password`() {
        runBlocking {
            val verifier =
                object : HostKeyVerifier {
                    override suspend fun verify(key: PublicKey): Boolean = true
                }
            val badClient =
                SshClient(
                    config =
                        SshClientConfig {
                            this.host = container.host
                            this.port = container.getMappedPort(CONTAINER_SSH_PORT)
                            this.hostKeyVerifier = verifier
                        },
                )

            assertTrue(badClient.connect() is ConnectResult.Success)

            val authResult = badClient.authenticatePassword(USERNAME, "definitely-wrong-password")
            assertTrue(
                "Expected auth failure with a wrong password, got: $authResult",
                authResult !is AuthResult.Success,
            )
            assertNotNull(authResult)

            runCatching { badClient.disconnect() }
        }
    }
}
