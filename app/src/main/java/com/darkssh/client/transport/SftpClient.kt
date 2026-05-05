package com.darkssh.client.transport

import com.darkssh.client.data.entity.Host
import com.trilead.ssh2.Connection
import com.trilead.ssh2.SFTPv3Client
import com.trilead.ssh2.SFTPv3FileHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.KeyPair

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
    
    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1_048_576 -> "%.1f MB/s".format(bytesPerSecond / 1_048_576.0)
            bytesPerSecond >= 1024 -> "%.1f KB/s".format(bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }
    }
}

sealed class SftpAuthState {
    data object Idle : SftpAuthState()
    data object Connecting : SftpAuthState()
    data class NeedsPassword(val hostname: String, val username: String) : SftpAuthState()
    data object Authenticating : SftpAuthState()
    data object Authenticated : SftpAuthState()
    data class Failed(val message: String) : SftpAuthState()
}

class SftpClient(private val host: Host) {

    private var connection: Connection? = null
    private var sftpClient: SFTPv3Client? = null

    val isConnected: Boolean get() = sftpClient != null

    suspend fun connectWithPassword(password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = Connection(host.hostname, if (host.port <= 0) 22 else host.port)
            conn.connect()
            val authenticated = conn.authenticateWithPassword(host.username, password)
            if (!authenticated) {
                conn.close()
                return@withContext Result.failure(IOException("Authentication failed"))
            }
            connection = conn
            openSftp(conn)
        } catch (e: Exception) {
            Timber.e(e, "SFTP connect+auth failed")
            Result.failure(e)
        }
    }

    suspend fun connectWithKey(keyPair: KeyPair): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = Connection(host.hostname, if (host.port <= 0) 22 else host.port)
            conn.connect()
            val authenticated = conn.authenticateWithPublicKey(host.username, keyPair)
            if (!authenticated) {
                conn.close()
                return@withContext Result.failure(IOException("Public key authentication failed"))
            }
            connection = conn
            openSftp(conn)
        } catch (e: Exception) {
            Timber.e(e, "SFTP connect+key auth failed")
            Result.failure(e)
        }
    }

    private fun openSftp(conn: Connection): Result<Unit> {
        return try {
            val client = SFTPv3Client(conn)
            client.setCharset("UTF-8")
            sftpClient = client
            Timber.d("SFTP session opened for ${host.hostname}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open SFTP subsystem")
            conn.close()
            connection = null
            Result.failure(e)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            sftpClient?.close()
            sftpClient = null
            connection?.close()
            connection = null
            Timber.d("SFTP session closed for ${host.hostname}")
        } catch (e: Exception) {
            Timber.e(e, "Error closing SFTP session")
        }
    }

    suspend fun pwd(): String = withContext(Dispatchers.IO) {
        try {
            sftpClient?.canonicalPath(".") ?: "/"
        } catch (e: Exception) {
            Timber.e(e, "Failed to get working directory")
            "/"
        }
    }

    suspend fun ls(path: String): Result<List<SftpEntry>> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(IOException("SFTP not connected"))
            val entries = client.ls(path)
            val result = entries.mapNotNull { entry ->
                val name = entry.filename
                if (name == "." || name == "..") return@mapNotNull null

                val attrs = entry.attributes
                val fullPath = if (path.endsWith("/")) "$path$name" else "$path/$name"

                SftpEntry(
                    name = name,
                    path = fullPath,
                    isDirectory = attrs.isDirectory,
                    isSymlink = attrs.isSymlink,
                    size = attrs.size ?: 0L,
                    permissions = attrs.getOctalPermissions(),
                    modifiedTime = attrs.mtime,
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            Result.success(result)
        } catch (e: Exception) {
            Timber.e(e, "Failed to list directory: $path")
            Result.failure(e)
        }
    }

    suspend fun downloadFile(
        remotePath: String,
        localFile: File,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(IOException("SFTP not connected"))
            val attrs = client.stat(remotePath)
            val totalSize = attrs?.size ?: 0L
            val startTime = System.currentTimeMillis()

            val handle = client.openFileRO(remotePath)
            val buffer = ByteArray(SFTP_MAX_READ)
            val fos = BufferedOutputStream(FileOutputStream(localFile), DISK_BUFFER_SIZE)

            try {
                var offset = 0L
                var bytesRead: Int
                while (true) {
                    bytesRead = client.readPipelined(handle, offset, buffer, buffer.size, 64)
                    if (bytesRead <= 0) break
                    fos.write(buffer, 0, bytesRead)
                    offset += bytesRead
                    val currentTime = System.currentTimeMillis()
                    onProgress?.invoke(TransferProgress(offset, totalSize, remotePath, startTime, currentTime))
                }
            } finally {
                fos.flush()
                fos.close()
                client.closeFile(handle)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to download file: $remotePath")
            if (localFile.exists()) localFile.delete()
            Result.failure(e)
        }
    }

    suspend fun downloadToStream(
        remotePath: String,
        outputStream: OutputStream,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(IOException("SFTP not connected"))
            val attrs = client.stat(remotePath)
            val totalSize = attrs?.size ?: 0L
            val startTime = System.currentTimeMillis()

            val handle = client.openFileRO(remotePath)
            val buffer = ByteArray(SFTP_MAX_READ)
            val bos = BufferedOutputStream(outputStream, DISK_BUFFER_SIZE)

            try {
                var offset = 0L
                var bytesRead: Int
                while (true) {
                    bytesRead = client.readPipelined(handle, offset, buffer, buffer.size, 16)
                    if (bytesRead <= 0) break
                    bos.write(buffer, 0, bytesRead)
                    offset += bytesRead
                    val currentTime = System.currentTimeMillis()
                    onProgress?.invoke(TransferProgress(offset, totalSize, remotePath, startTime, currentTime))
                }
            } finally {
                bos.flush()
                bos.close()
                client.closeFile(handle)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to download file to stream: $remotePath")
            Result.failure(e)
        }
    }

    suspend fun uploadFile(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(IOException("SFTP not connected"))
            val totalSize = localFile.length()
            val startTime = System.currentTimeMillis()

            val handle = client.createFile(remotePath)
            val fis = BufferedInputStream(FileInputStream(localFile), DISK_BUFFER_SIZE)
            val buffer = ByteArray(DISK_BUFFER_SIZE)

            try {
                var offset = 0L
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    client.write(handle, offset, buffer, 0, bytesRead)
                    offset += bytesRead
                    val currentTime = System.currentTimeMillis()
                    onProgress?.invoke(TransferProgress(offset, totalSize, localFile.name, startTime, currentTime))
                }
            } finally {
                fis.close()
                client.closeFile(handle)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload file: ${localFile.name}")
            Result.failure(e)
        }
    }

    suspend fun mkdir(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(IOException("SFTP not connected"))
            client.mkdir(path, 448)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create directory: $path")
            Result.failure(e)
        }
    }

    suspend fun rm(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(IOException("SFTP not connected"))
            client.rm(path)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete file: $path")
            Result.failure(e)
        }
    }

    suspend fun rmdir(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(IOException("SFTP not connected"))
            client.rmdir(path)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove directory: $path")
            Result.failure(e)
        }
    }

    suspend fun stat(path: String): Result<SftpEntry?> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(IOException("SFTP not connected"))
            val attrs = client.stat(path) ?: return@withContext Result.success(null)
            val name = path.substringAfterLast('/')
            Result.success(
                SftpEntry(
                    name = name,
                    path = path,
                    isDirectory = attrs.isDirectory,
                    isSymlink = attrs.isSymlink,
                    size = attrs.size ?: 0L,
                    permissions = attrs.getOctalPermissions(),
                    modifiedTime = attrs.mtime,
                ),
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to stat: $path")
            Result.failure(e)
        }
    }

    companion object {
        private const val SFTP_MAX_READ = 32768
        private const val DISK_BUFFER_SIZE = 262144  // 256KB buffer for disk I/O
    }
}