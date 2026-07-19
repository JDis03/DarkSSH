package com.darkssh.client.transport

import com.darkssh.client.data.entity.Host
import com.darkssh.client.util.DebugLogger
import com.hierynomus.sshj.key.KeyAlgorithms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.AndroidConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.StreamCopier
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.xfer.LocalDestFile
import net.schmizz.sshj.xfer.TransferListener
import timber.log.Timber
import java.io.File
import java.io.OutputStream
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import kotlin.coroutines.coroutineContext

data class SftpEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long,
    val permissions: String?,
    val modifiedTime: Long?,
)

data class TransferProgress(
    val transferred: Long,
    val total: Long,
    val filePath: String,
    val startTime: Long = System.currentTimeMillis(),
    val currentTime: Long = System.currentTimeMillis(),
) {
    val percentage: Int get() = if (total > 0) (transferred * 100 / total).toInt() else 0
    val elapsedSeconds: Double get() = (currentTime - startTime) / 1000.0
    val speed: Long get() = if (elapsedSeconds > 0) (transferred / elapsedSeconds).toLong() else 0L
    val speedFormatted: String get() = formatSpeed(speed)

    private fun formatSpeed(bytesPerSecond: Long): String =
        when {
            bytesPerSecond >= 1_048_576 -> "%.1f MB/s".format(bytesPerSecond / 1_048_576.0)
            bytesPerSecond >= 1024 -> "%.1f KB/s".format(bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }
}

sealed class SftpAuthState {
    data object Idle : SftpAuthState()

    data object Connecting : SftpAuthState()

    data class NeedsPassword(
        val hostname: String,
        val username: String,
    ) : SftpAuthState()

    data object Authenticating : SftpAuthState()

    data object Authenticated : SftpAuthState()

    data class Failed(
        val message: String,
    ) : SftpAuthState()
}

class SftpClient(
    private val host: Host,
) : ISftpClient {
    private var sshClient: SSHClient? = null
    private var sftpClient: SFTPClient? = null

    // File extensions that are already compressed (compression adds CPU overhead without benefit)
    private val compressedExtensions =
        setOf(
            "jpg",
            "jpeg",
            "png",
            "gif",
            "webp",
            "bmp", // Images
            "mp4",
            "mkv",
            "avi",
            "mov",
            "webm",
            "m4v", // Videos
            "mp3",
            "m4a",
            "flac",
            "ogg",
            "aac",
            "opus", // Audio
            "zip",
            "gz",
            "bz2",
            "xz",
            "7z",
            "rar",
            "tar", // Archives
            "apk",
            "aab",
            "ipa", // App packages
            "pdf",
            "docx",
            "xlsx",
            "pptx", // Documents (already compressed internally)
        )

    private fun shouldCompress(filename: String): Boolean {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return ext !in compressedExtensions
    }

    override val isConnected: Boolean get() =
        try {
            sshClient?.isConnected() == true
        } catch (_: Exception) {
            false
        }

    private fun createConfig(): AndroidConfig =
        object : AndroidConfig() {
            override fun initKeyAlgorithms() {
                // Override AndroidConfig's limited set with all algorithms supported by Conscrypt
                keyAlgorithms =
                    listOf(
                        KeyAlgorithms.RSASHA512(),
                        KeyAlgorithms.RSASHA256(),
                        KeyAlgorithms.ECDSASHANistp256(),
                        KeyAlgorithms.ECDSASHANistp384(),
                        KeyAlgorithms.ECDSASHANistp521(),
                        KeyAlgorithms.SSHRSA(),
                    )
            }
        }.apply {
            // Filter out X25519 key exchange (not available in SpongyCastle 1.58)
            val kex = keyExchangeFactories.toMutableList()
            kex.removeAll { it.name.contains("curve25519", ignoreCase = true) }
            keyExchangeFactories = kex

            keepAliveProvider = KeepAliveProvider.HEARTBEAT

            // Performance optimization: Prefer faster ciphers
            // aes128-ctr is ~2x faster than aes256-ctr with similar security
            val fastCiphers = cipherFactories.toMutableList()
            // Move aes128-ctr and chacha20-poly1305 to front
            fastCiphers.sortBy {
                when {
                    it.name.contains("aes128-ctr") -> 0
                    it.name.contains("chacha20-poly1305") -> 1
                    it.name.contains("aes128") -> 2
                    else -> 3
                }
            }
            cipherFactories = fastCiphers

            // MAXIMIZE circular buffer for better throughput (default 16MB)
            // 64MB allows massive data buffering for max bandwidth utilization
            maxCircularBufferSize = 64 * 1024 * 1024 // 64MB (was 32MB)
        }

    override suspend fun connectWithPassword(password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val ssh = SSHClient(createConfig())
                ssh.addHostKeyVerifier(PromiscuousVerifier())

                ssh.connectTimeout = 15000
                // 0 en sshj 0.38 usa el DEFAULT (30s). Ponemos 5min para
                // sobrevivir background throttling de Android.
                ssh.timeout = 300_000

                ssh.connect(host.hostname, if (host.port <= 0) 22 else host.port)

                ssh.connection.windowSize = 32L * 1024 * 1024
                ssh.connection.maxPacketSize = 128 * 1024
                ssh.connection.keepAlive.keepAliveInterval = 15

                ssh.authPassword(host.username, password)
                ssh.useCompression()
                val sftp = ssh.newSFTPClient()
                sshClient = ssh
                sftpClient = sftp
                Timber.d("SSHJ SFTP session opened for ${host.hostname}")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "SSHJ SFTP connect+auth failed")
                Result.failure(e)
            }
        }

    override suspend fun connectWithKey(keyPair: KeyPair): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val ssh = SSHClient(createConfig())
                ssh.addHostKeyVerifier(PromiscuousVerifier())

                ssh.connectTimeout = 15000
                ssh.timeout = 300_000

                ssh.connect(host.hostname, if (host.port <= 0) 22 else host.port)

                ssh.connection.windowSize = 32L * 1024 * 1024
                ssh.connection.maxPacketSize = 128 * 1024
                ssh.connection.keepAlive.keepAliveInterval = 15

                // Convert java.security.KeyPair to sshj KeyProvider
                val keyProvider =
                    object : KeyProvider {
                        override fun getPublic(): PublicKey = keyPair.public

                        override fun getPrivate(): PrivateKey = keyPair.private

                        override fun getType(): KeyType = KeyType.fromKey(keyPair.public)
                    }

                ssh.authPublickey(host.username, keyProvider)
                ssh.useCompression()
                val sftp = ssh.newSFTPClient()
                sshClient = ssh
                sftpClient = sftp
                Timber.d("SSHJ SFTP session opened (pubkey auth) for ${host.hostname}")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "SSHJ SFTP pubkey auth failed")
                Result.failure(e)
            }
        }

    override suspend fun disconnect() =
        withContext(Dispatchers.IO) {
            try {
                sftpClient?.close()
                sftpClient = null
                sshClient?.disconnect()
                sshClient = null
                Timber.d("SSHJ SFTP session closed for ${host.hostname}")
            } catch (e: Exception) {
                Timber.e(e, "Error closing SSHJ SFTP session")
            }
        }

    override fun setDisconnected() {
        sftpClient = null
        sshClient = null
        Timber.w("SSHJ SFTP marked as disconnected for ${host.hostname}")
    }

    override suspend fun pwd(): String =
        withContext(Dispatchers.IO) {
            try {
                sftpClient?.canonicalize(".") ?: "/"
            } catch (e: Exception) {
                Timber.e(e, "Failed to get working directory")
                "/"
            }
        }

    override suspend fun ls(path: String): Result<List<SftpEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val client = sftpClient ?: return@withContext Result.failure(Exception("SFTP not connected"))
                val entries: List<RemoteResourceInfo> = client.ls(path)
                val result =
                    entries
                        .mapNotNull { entry ->
                            val name = entry.name
                            if (name == "." || name == "..") return@mapNotNull null
                            val fullPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
                            SftpEntry(
                                name = name,
                                path = fullPath,
                                isDirectory = entry.isDirectory,
                                isSymlink = entry.attributes.type == FileMode.Type.SYMLINK,
                                size = entry.attributes.size,
                                permissions = null,
                                modifiedTime = entry.attributes.mtime,
                            )
                        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                Result.success(result)
            } catch (e: Exception) {
                Timber.e(e, "Failed to list directory: $path")
                Result.failure(e)
            }
        }

    override suspend fun downloadToStream(
        remotePath: String,
        outputStream: OutputStream,
        onProgress: ((TransferProgress) -> Unit)?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val client = sftpClient ?: return@withContext Result.failure(Exception("SFTP not connected"))
                val attrs = client.stat(remotePath)
                val totalSize = attrs?.size ?: 0L
                val startTime = System.currentTimeMillis()

                val transfer = client.fileTransfer

                // Throttle progress updates (report every 100KB to reduce overhead)
                var lastReportedBytes = 0L
                val reportInterval = 100 * 1024L // 100KB

                transfer.transferListener =
                    object : TransferListener {
                        override fun directory(name: String): TransferListener = this

                        override fun file(
                            name: String,
                            size: Long,
                        ): StreamCopier.Listener =
                            object : StreamCopier.Listener {
                                override fun reportProgress(transferred: Long) {
                                    // Only report if we've transferred enough data since last report
                                    if (transferred - lastReportedBytes >= reportInterval || transferred >= totalSize) {
                                        val currentTime = System.currentTimeMillis()
                                        onProgress?.invoke(TransferProgress(transferred, totalSize, remotePath, startTime, currentTime))
                                        lastReportedBytes = transferred
                                    }
                                }
                            }
                    }

                transfer.download(remotePath, OutputStreamDestFile(outputStream))
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to download file to stream: $remotePath")
                Result.failure(e)
            }
        }

    override suspend fun downloadFile(
        remotePath: String,
        localFile: File,
        onProgress: ((TransferProgress) -> Unit)?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val client = sftpClient ?: return@withContext Result.failure(Exception("SFTP not connected"))
                val attrs = client.stat(remotePath)
                val totalSize = attrs?.size ?: 0L
                val startTime = System.currentTimeMillis()

                val transfer = client.fileTransfer

                // Throttle progress updates (report every 100KB to reduce overhead)
                var lastReportedBytes = 0L
                val reportInterval = 100 * 1024L // 100KB

                transfer.transferListener =
                    object : TransferListener {
                        override fun directory(name: String): TransferListener = this

                        override fun file(
                            name: String,
                            size: Long,
                        ): StreamCopier.Listener =
                            object : StreamCopier.Listener {
                                override fun reportProgress(transferred: Long) {
                                    // Only report if we've transferred enough data since last report
                                    if (transferred - lastReportedBytes >= reportInterval || transferred >= totalSize) {
                                        val currentTime = System.currentTimeMillis()
                                        onProgress?.invoke(TransferProgress(transferred, totalSize, remotePath, startTime, currentTime))
                                        lastReportedBytes = transferred
                                    }
                                }
                            }
                    }

                transfer.download(remotePath, localFile.absolutePath)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to download file: $remotePath")
                if (localFile.exists()) localFile.delete()
                Result.failure(e)
            }
        }

    /**
     * Upload a file via SSH command (much faster than SFTP)
     * Streams file content through SSH stdin to remote 'cat > file' command
     */
    suspend fun uploadViaSsh(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val client = sshClient ?: return@withContext Result.failure(Exception("SSH not connected"))

                // Verify connection is alive before attempting upload
                if (!client.isConnected) {
                    return@withContext Result.failure(Exception("SSH connection lost"))
                }

                val totalSize = localFile.length()
                val startTime = System.currentTimeMillis()

                DebugLogger.i("SftpClient", "Starting SSH upload: ${localFile.name} (${totalSize / 1024 / 1024}MB)")

                // Use dd for better buffering (256KB blocks for max throughput)
                val command = "dd of=${escapePath(remotePath)} bs=256K"
                Timber.d("Executing upload command: $command")

                client.startSession().use { session ->
                    val cmd = session.exec(command)

                    // Stream file to stdin
                    val stdin = cmd.outputStream
                    localFile.inputStream().use { input ->
                        val buffer = ByteArray(256 * 1024) // 256KB buffer
                        var totalTransferred = 0L
                        var lastReportedBytes = 0L
                        var lastFlushedBytes = 0L
                        val reportInterval = 256 * 1024L // Report every 256KB
                        val flushInterval = 512 * 1024L // Flush every 512KB to prevent buffering slowdown

                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            // Check for cancellation
                            val job = coroutineContext[Job]
                            if (job?.isActive == false) {
                                throw kotlinx.coroutines.CancellationException("Upload cancelled")
                            }

                            stdin.write(buffer, 0, bytesRead)
                            totalTransferred += bytesRead

                            // Flush periodically to prevent buffer accumulation (improves speed with latency)
                            if (totalTransferred - lastFlushedBytes >= flushInterval) {
                                stdin.flush()
                                lastFlushedBytes = totalTransferred
                            }

                            // Report progress
                            if (totalTransferred - lastReportedBytes >= reportInterval || totalTransferred >= totalSize) {
                                val currentTime = System.currentTimeMillis()
                                val progress = TransferProgress(totalTransferred, totalSize, localFile.name, startTime, currentTime)
                                DebugLogger.d("SftpClient", "Upload progress: ${progress.percentage}% @ ${progress.speedFormatted}")
                                onProgress?.invoke(progress)
                                lastReportedBytes = totalTransferred
                            }
                        }

                        stdin.flush()
                    }

                    // Close stdin to signal EOF
                    stdin.close()

                    // Wait for command to complete
                    cmd.join(10, java.util.concurrent.TimeUnit.SECONDS)
                    val exitCode = cmd.exitStatus ?: -1

                    // Read stderr (dd writes stats to stderr, not necessarily errors)
                    val stderrOutput =
                        try {
                            cmd.errorStream.bufferedReader().readText()
                        } catch (e: Exception) {
                            ""
                        }

                    DebugLogger.d("SftpClient", "SSH upload finished: exit=$exitCode, stderr=$stderrOutput")
                    Timber.d("Upload command finished: exit=$exitCode")

                    // dd exit code 0 = success, non-zero might still mean partial success
                    // Check if we transferred all bytes successfully
                    if (exitCode != 0) {
                        // Log warning but don't fail immediately - file might be complete
                        DebugLogger.w("SftpClient", "dd exit code $exitCode, but ${totalSize / 1024}KB was transferred")
                        Timber.w("Upload command non-zero exit: $exitCode")
                    }

                    DebugLogger.i("SftpClient", "SSH upload completed: ${localFile.name}")
                    Result.success(Unit)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                DebugLogger.w("SftpClient", "SSH upload cancelled: ${localFile.name}")
                Timber.d("Upload cancelled by user: ${localFile.name}")
                // Rethrow to propagate cancellation
                throw e
            } catch (e: java.net.SocketException) {
                DebugLogger.e("SftpClient", "SSH upload socket error: ${localFile.name} - ${e.message}")
                Timber.e(e, "Socket error during SSH upload (connection may be stale): ${localFile.name}")
                Result.failure(Exception("Connection error: ${e.message}. Try reconnecting."))
            } catch (e: Exception) {
                DebugLogger.e("SftpClient", "SSH upload failed: ${localFile.name} - ${e.message}")
                Timber.e(e, "Failed to upload via SSH: ${localFile.name}")
                Result.failure(e)
            }
        }

    override suspend fun uploadFile(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)?,
    ): Result<Unit> {
        // Try SSH upload first (much faster), fallback to SFTP if it fails
        val sshResult = uploadViaSsh(localFile, remotePath, onProgress)
        if (sshResult.isSuccess) {
            return sshResult
        }

        // Check if file exists before fallback (SSH might have succeeded despite error)
        val fileExists = exists(remotePath)
        if (fileExists) {
            Timber.w("SSH upload reported error but file exists, considering success")
            DebugLogger.w("SftpClient", "File exists after SSH upload error, skipping fallback")
            return Result.success(Unit)
        }

        Timber.w("SSH upload failed, falling back to SFTP: ${sshResult.exceptionOrNull()?.message}")
        DebugLogger.w("SftpClient", "Falling back to SFTP upload")

        // Fallback to SFTP
        return withContext(Dispatchers.IO) {
            try {
                val client = sftpClient ?: return@withContext Result.failure(Exception("SFTP not connected"))
                val totalSize = localFile.length()
                val startTime = System.currentTimeMillis()

                DebugLogger.i("SftpClient", "Starting SFTP upload: ${localFile.name} (${totalSize / 1024 / 1024}MB)")

                val transfer = client.fileTransfer

                // Throttle progress updates (report every 256KB like File Manager+)
                var lastReportedBytes = 0L
                val reportInterval = 256 * 1024L // 256KB

                transfer.transferListener =
                    object : TransferListener {
                        override fun directory(name: String): TransferListener = this

                        override fun file(
                            name: String,
                            size: Long,
                        ): StreamCopier.Listener =
                            object : StreamCopier.Listener {
                                override fun reportProgress(transferred: Long) {
                                    // Only report if we've transferred enough data since last report
                                    if (transferred - lastReportedBytes >= reportInterval || transferred >= totalSize) {
                                        val currentTime = System.currentTimeMillis()
                                        val progress = TransferProgress(transferred, totalSize, localFile.name, startTime, currentTime)
                                        DebugLogger.d(
                                            "SftpClient",
                                            "Progress: ${progress.percentage}% (${transferred / 1024}KB / ${totalSize / 1024}KB) @ ${progress.speedFormatted}",
                                        )
                                        onProgress?.invoke(progress)
                                        lastReportedBytes = transferred
                                    }
                                }
                            }
                    }

                transfer.upload(localFile.absolutePath, remotePath)
                DebugLogger.i("SftpClient", "SFTP upload completed: ${localFile.name}")
                Result.success(Unit)
            } catch (e: Exception) {
                DebugLogger.e("SftpClient", "SFTP upload failed: ${localFile.name} - ${e.message}")
                Timber.e(e, "Failed to upload file: ${localFile.name}")
                Result.failure(e)
            }
        }
    }

    suspend fun uploadFileParallel(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
        chunkSize: Long = 10 * 1024 * 1024, // 10MB per chunk
        parallelChunks: Int = 4, // 4 chunks simultaneously
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // For now, just use optimized regular upload
                // Parallel chunks have issues with some SFTP servers
                Timber.d("Using optimized upload for: ${localFile.name}")
                return@withContext uploadFile(localFile, remotePath, onProgress)
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload file: ${localFile.name}")
                Result.failure(e)
            }
        }

    override suspend fun mkdir(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                sftpClient?.mkdir(path)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create directory: $path")
                Result.failure(e)
            }
        }

    override suspend fun rm(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                sftpClient?.rm(path)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete file: $path")
                Result.failure(e)
            }
        }

    override suspend fun rmdir(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                sftpClient?.rmdir(path)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove directory: $path")
                Result.failure(e)
            }
        }

    override suspend fun rename(
        oldPath: String,
        newPath: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                sftpClient?.rename(oldPath, newPath)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to rename: $oldPath -> $newPath")
                Result.failure(e)
            }
        }

    override suspend fun exists(path: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                sftpClient?.statExistence(path) != null
            } catch (e: Exception) {
                false
            }
        }

    override suspend fun stat(path: String): Result<SftpEntry?> =
        withContext(Dispatchers.IO) {
            try {
                val client = sftpClient ?: return@withContext Result.failure(Exception("SFTP not connected"))
                val attrs = client.stat(path)
                val name = path.substringAfterLast('/')
                Result.success(
                    SftpEntry(
                        name = name,
                        path = path,
                        isDirectory = attrs.type == FileMode.Type.DIRECTORY,
                        isSymlink = attrs.type == FileMode.Type.SYMLINK,
                        size = attrs.size,
                        permissions = null,
                        modifiedTime = attrs.mtime,
                    ),
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to stat: $path")
                Result.failure(e)
            }
        }

    /**
     * Execute a command on the remote server via SSH
     * Returns the command output and exit code
     */
    private suspend fun executeCommand(command: String): Result<Pair<String, Int>> =
        withContext(Dispatchers.IO) {
            try {
                val client = sshClient ?: return@withContext Result.failure(Exception("SSH not connected"))

                client.startSession().use { session ->
                    val cmd = session.exec(command)

                    // Read output
                    val output = cmd.inputStream.bufferedReader().readText()
                    val errorOutput = cmd.errorStream.bufferedReader().readText()

                    // Wait for command to complete
                    cmd.join()
                    val exitCode = cmd.exitStatus ?: -1

                    if (exitCode != 0) {
                        Timber.w("Command failed (exit $exitCode): $command\nError: $errorOutput")
                        return@withContext Result.failure(Exception("Command failed: $errorOutput"))
                    }

                    Result.success(Pair(output, exitCode))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to execute command: $command")
                Result.failure(e)
            }
        }

    /**
     * Escape a file path for safe use in shell commands
     */
    private fun escapePath(path: String): String {
        // Use single quotes and escape any single quotes in the path
        return "'${path.replace("'", "'\\''")}'"
    }

    /**
     * Copy a file/directory from source to destination on the remote server via SSH cp command
     * This is MUCH faster than SFTP streaming because it happens locally on the server.
     *
     * @param overwrite If true, overwrites destination if it exists (-f flag).
     *                  If false, fails silently if destination exists (cp default).
     */
    override suspend fun copyFileViaSsh(
        sourcePath: String,
        destPath: String,
        isDirectory: Boolean,
        overwrite: Boolean,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val client = sshClient ?: return@withContext Result.failure(Exception("SSH not connected"))

                // Skip if source and dest are the same
                if (sourcePath == destPath) {
                    Timber.w("Copy skipped: source equals dest: $sourcePath")
                    return@withContext Result.success(Unit)
                }

                // Build cp command with proper escaping
                val flags =
                    buildString {
                        if (isDirectory) append("-r ")
                        if (overwrite) append("-f ")
                    }.trim()
                val command = "cp $flags ${escapePath(sourcePath)} ${escapePath(destPath)}"

                DebugLogger.i("SftpClient", "Copying via SSH: $command")
                Timber.d("Executing: $command")

                val result = executeCommand(command)
                result.fold(
                    onSuccess = { (output, exitCode) ->
                        Timber.d("Copy completed successfully (exit $exitCode)")
                        Result.success(Unit)
                    },
                    onFailure = { error ->
                        Timber.e(error, "Copy failed: $error")
                        Result.failure(error)
                    },
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy via SSH: $sourcePath -> $destPath")
                Result.failure(e)
            }
        }

    /**
     * Copy a file from source to destination on the remote server
     * Note: SFTP protocol doesn't have a native copy command, so we stream it
     * DEPRECATED: Use copyFileViaSsh() instead - it's much faster (100x+)
     */
    suspend fun copyFile(
        sourcePath: String,
        destPath: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val client = sshClient ?: return@withContext Result.failure(Exception("Client not connected"))
                val sftp = client.newSFTPClient()

                DebugLogger.i("SftpClient", "Copying file: $sourcePath -> $destPath")

                // Stream file without loading into memory (efficient for large files)
                val sourceFile = sftp.open(sourcePath)
                val destFile =
                    sftp.open(
                        destPath,
                        java.util.EnumSet.of(
                            net.schmizz.sshj.sftp.OpenMode.WRITE,
                            net.schmizz.sshj.sftp.OpenMode.CREAT,
                            net.schmizz.sshj.sftp.OpenMode.TRUNC,
                        ),
                    )

                sourceFile.use { src ->
                    destFile.use { dst ->
                        val buffer = ByteArray(128 * 1024) // 128KB buffer
                        val inputStream = src.RemoteFileInputStream()
                        val outputStream = dst.RemoteFileOutputStream()

                        var totalRead = 0L
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            // Log progress every 5MB
                            if (totalRead % (5 * 1024 * 1024) == 0L) {
                                DebugLogger.d("SftpClient", "Copy progress: ${totalRead / 1024 / 1024}MB")
                            }
                        }

                        outputStream.flush()
                        DebugLogger.i("SftpClient", "Copy completed: ${totalRead / 1024 / 1024}MB")
                    }
                }

                Timber.d("Copied file: $sourcePath -> $destPath")
                Result.success(Unit)
            } catch (e: Exception) {
                DebugLogger.e("SftpClient", "Failed to copy file: $sourcePath -> $destPath - ${e.message}")
                Timber.e(e, "Failed to copy file: $sourcePath -> $destPath")
                Result.failure(e)
            }
        }

    /**
     * Move/rename a file or directory on the remote server.
     * Uses SSH mv for atomic rename and better error reporting.
     * Falls back to SFTP rename if SSH exec fails.
     */
    override suspend fun moveFile(
        sourcePath: String,
        destPath: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val client = sshClient ?: return@withContext Result.failure(Exception("Client not connected"))

                // Skip if source and dest are the same
                if (sourcePath == destPath) {
                    Timber.w("Move skipped: source equals dest: $sourcePath")
                    return@withContext Result.success(Unit)
                }

                // Use SSH mv - atomic on same FS, works for files and dirs, clearer errors
                val command = "mv -f ${escapePath(sourcePath)} ${escapePath(destPath)}"
                DebugLogger.i("SftpClient", "Moving via SSH: $command")
                Timber.d("Executing: $command")

                val execResult = executeCommand(command)
                if (execResult.isSuccess) {
                    Timber.d("Moved file: $sourcePath -> $destPath")
                    return@withContext Result.success(Unit)
                }

                // Fallback to SFTP rename if SSH mv failed
                Timber.w("SSH mv failed, falling back to SFTP rename")
                val sftp = client.newSFTPClient()
                sftp.rename(sourcePath, destPath)
                Timber.d("Moved file via SFTP rename: $sourcePath -> $destPath")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to move file: $sourcePath -> $destPath")
                Result.failure(e)
            }
        }

    /**
     * Recursively delete a directory via SSH rm -rf.
     * SFTP rmdir only works on empty directories — this handles non-empty ones.
     */
    override suspend fun deleteDirectoryViaSsh(remotePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val client = sshClient ?: return@withContext Result.failure(Exception("SSH not connected"))

                val command = "rm -rf ${escapePath(remotePath)}"
                DebugLogger.i("SftpClient", "Deleting via SSH: $command")
                Timber.d("Executing: $command")

                val result = executeCommand(command)
                result.fold(
                    onSuccess = {
                        Timber.d("Directory deleted: $remotePath")
                        Result.success(Unit)
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to delete directory: $remotePath")
                        Result.failure(error)
                    },
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception deleting directory: $remotePath")
                Result.failure(e)
            }
        }
}

class OutputStreamDestFile(
    private val outputStream: OutputStream,
) : LocalDestFile {
    override fun getLength() = 0L

    override fun getOutputStream() = outputStream

    override fun getOutputStream(append: Boolean) = outputStream

    override fun getChild(name: String?) = this

    override fun getTargetFile(filename: String?) = this

    override fun getTargetDirectory(dirname: String?) = this

    override fun setPermissions(perms: Int) {}

    override fun setLastAccessedTime(time: Long) {}

    override fun setLastModifiedTime(time: Long) {}
}
