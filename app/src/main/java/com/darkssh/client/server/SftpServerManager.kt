package com.darkssh.client.server

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.sshd.common.NamedFactory
import org.apache.sshd.common.cipher.BuiltinCiphers
import org.apache.sshd.common.cipher.Cipher
import org.apache.sshd.common.compression.BuiltinCompressions
import org.apache.sshd.common.compression.Compression
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.common.kex.BuiltinDHFactories
import org.apache.sshd.common.kex.KeyExchange
import org.apache.sshd.common.mac.BuiltinMacs
import org.apache.sshd.common.mac.Mac
import org.apache.sshd.common.signature.BuiltinSignatures
import org.apache.sshd.common.signature.Signature
import org.apache.sshd.common.util.io.PathUtils
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import timber.log.Timber
import java.io.File
import java.nio.file.Paths

/**
 * Manages SFTP server lifecycle and configuration.
 *
 * Features:
 * - SFTP server on port 22
 * - Password authentication
 * - RSA host key generation
 * - Virtual file system rooted at /sdcard/
 * - Upload completion broadcasts for DarkDev integration
 */
class SftpServerManager(
    private val context: Context,
) {
    private var sshServer: SshServer? = null
    private var isRunning = false

    companion object {
        private const val DEFAULT_PORT = 2222 // Port 22 requires root on Android
        private const val DEFAULT_USER = "root"
        private const val DEFAULT_PASSWORD = "darkssh"
        private const val HOST_KEY_PATH = "hostkey.ser"

        // Broadcast action for upload completion
        const val ACTION_FILE_UPLOADED = "org.dark.ssh.FILE_UPLOADED"
        const val EXTRA_PATH = "path"
        const val EXTRA_SIZE = "size"
        const val EXTRA_TIMESTAMP = "timestamp"
    }

    /**
     * Starts the SFTP server.
     *
     * @param port Server port (default: 22)
     * @param username Authentication username (default: "root")
     * @param password Authentication password (default: "darkssh")
     * @param rootPath Virtual file system root (default: /sdcard/)
     */
    suspend fun start(
        port: Int = DEFAULT_PORT,
        username: String = DEFAULT_USER,
        password: String = DEFAULT_PASSWORD,
        rootPath: String = "/sdcard/",
    ) = withContext(Dispatchers.IO) {
        if (isRunning) {
            Timber.w("SFTP server already running")
            return@withContext
        }

        try {
            Timber.i("Starting SFTP server on port $port...")

            sshServer =
                SshServer.setUpDefaultServer().apply {
                    // Set port
                    this.port = port

                    // Manually configure factories (Android doesn't support ServiceLoader properly)
                    // With SpongyCastle registered, most algorithms should be supported
                    @Suppress("UNCHECKED_CAST")
                    signatureFactories =
                        BuiltinSignatures.VALUES
                            .filter { it.isSupported }
                            .toList() as List<NamedFactory<Signature>>

                    @Suppress("UNCHECKED_CAST")
                    cipherFactories =
                        BuiltinCiphers.VALUES
                            .filter { it.isSupported }
                            .toList() as List<NamedFactory<Cipher>>

                    // Use built-in server key exchange setup (uses all available algorithms)
                    // This works because SpongyCastle is registered as security provider
                    keyExchangeFactories =
                        org.apache.sshd.server.ServerBuilder
                            .setUpDefaultKeyExchanges(true)

                    @Suppress("UNCHECKED_CAST")
                    macFactories =
                        BuiltinMacs.VALUES
                            .filter { it.isSupported }
                            .toList() as List<NamedFactory<Mac>>

                    @Suppress("UNCHECKED_CAST")
                    compressionFactories =
                        BuiltinCompressions.VALUES
                            .filter { it.isSupported }
                            .toList() as List<NamedFactory<Compression>>

                    Timber.i(
                        "Configured factories - Signatures: ${signatureFactories.size}, KEX: ${keyExchangeFactories.size}, Ciphers: ${cipherFactories.size}, MACs: ${macFactories.size}",
                    )
                    Timber.d("  Signatures: ${signatureFactories.map { it.name }}")
                    Timber.d("  KEX: ${keyExchangeFactories.map { it.name }}")

                    // Set host key
                    val hostKeyFile = File(context.filesDir, HOST_KEY_PATH)
                    keyPairProvider = SimpleGeneratorHostKeyProvider(hostKeyFile.toPath())

                    // Set password authenticator
                    passwordAuthenticator =
                        PasswordAuthenticator { user, pass, _ ->
                            (user == username && pass == password).also { authenticated ->
                                Timber.d("Auth attempt: user=$user, success=$authenticated")
                            }
                        }

                    // Set virtual file system
                    fileSystemFactory = VirtualFileSystemFactory(Paths.get(rootPath))

                    // Enable SFTP subsystem with upload listener
                    subsystemFactories =
                        listOf(
                            CustomSftpSubsystemFactory(context),
                        )
                }

            sshServer?.start()
            isRunning = true

            Timber.i("✓ SFTP server started successfully on port $port")
            Timber.i("  Root: $rootPath")
            Timber.i("  User: $username")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start SFTP server")
            isRunning = false
            throw e
        }
    }

    /**
     * Stops the SFTP server.
     */
    suspend fun stop() =
        withContext(Dispatchers.IO) {
            if (!isRunning) {
                Timber.w("SFTP server not running")
                return@withContext
            }

            try {
                Timber.i("Stopping SFTP server...")
                sshServer?.stop()
                sshServer = null
                isRunning = false
                Timber.i("✓ SFTP server stopped")
            } catch (e: Exception) {
                Timber.e(e, "Error stopping SFTP server")
            }
        }

    /**
     * Returns true if server is currently running.
     */
    fun isRunning(): Boolean = isRunning

    /**
     * Broadcasts upload completion event for DarkDev integration.
     *
     * @param path Uploaded file path
     * @param size File size in bytes
     */
    internal fun broadcastUploadComplete(
        path: String,
        size: Long,
    ) {
        try {
            val intent =
                Intent(ACTION_FILE_UPLOADED).apply {
                    putExtra(EXTRA_PATH, path)
                    putExtra(EXTRA_SIZE, size)
                    putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
                }

            context.sendBroadcast(intent)
            Timber.d("Broadcast upload complete: $path ($size bytes)")
        } catch (e: Exception) {
            Timber.w(e, "Failed to broadcast upload event")
        }
    }
}
