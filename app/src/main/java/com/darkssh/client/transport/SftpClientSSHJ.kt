package com.darkssh.client.transport

import com.darkssh.client.data.entity.Host
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hierynomus.sshj.key.KeyAlgorithms
import net.schmizz.sshj.AndroidConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.StreamCopier
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.xfer.LocalDestFile
import net.schmizz.sshj.xfer.TransferListener
import timber.log.Timber
import java.io.File
import java.io.OutputStream

class SftpClientSSHJ(private val host: Host) {

    private var sshClient: SSHClient? = null
    private var sftpClient: SFTPClient? = null

    val isConnected: Boolean get() = sftpClient != null

    suspend fun connectWithPassword(password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Custom config extending AndroidConfig with full algorithm support
            val config = object : AndroidConfig() {
                override fun initKeyAlgorithms() {
                    // Override AndroidConfig's limited set with all algorithms supported by Conscrypt
                    keyAlgorithms = listOf(
                        KeyAlgorithms.RSASHA512(),
                        KeyAlgorithms.RSASHA256(),
                        KeyAlgorithms.ECDSASHANistp256(),
                        KeyAlgorithms.ECDSASHANistp384(),
                        KeyAlgorithms.ECDSASHANistp521(),
                        KeyAlgorithms.SSHRSA(),
                    )
                }
            }

            // Filter out X25519 key exchange (not available in SpongyCastle 1.58)
            val kex = config.keyExchangeFactories.toMutableList()
            kex.removeAll { it.name.contains("curve25519", ignoreCase = true) }
            config.keyExchangeFactories = kex

            val ssh = SSHClient(config)
            ssh.addHostKeyVerifier(PromiscuousVerifier())
            ssh.connect(host.hostname, if (host.port <= 0) 22 else host.port)
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

    suspend fun disconnect() = withContext(Dispatchers.IO) {
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

    suspend fun pwd(): String = withContext(Dispatchers.IO) {
        try {
            sftpClient?.canonicalize(".") ?: "/"
        } catch (e: Exception) {
            Timber.e(e, "Failed to get working directory")
            "/"
        }
    }

    suspend fun ls(path: String): Result<List<SftpEntry>> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(Exception("SFTP not connected"))
            val entries: List<RemoteResourceInfo> = client.ls(path)
            val result = entries.mapNotNull { entry ->
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

    suspend fun downloadToStream(
        remotePath: String,
        outputStream: OutputStream,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(Exception("SFTP not connected"))
            val attrs = client.stat(remotePath)
            val totalSize = attrs?.size ?: 0L
            val startTime = System.currentTimeMillis()

            val transfer = client.fileTransfer
            transfer.transferListener = object : TransferListener {
                override fun directory(name: String): TransferListener = this
                override fun file(name: String, size: Long): StreamCopier.Listener {
                    return object : StreamCopier.Listener {
                        override fun reportProgress(transferred: Long) {
                            val currentTime = System.currentTimeMillis()
                            onProgress?.invoke(TransferProgress(transferred, totalSize, remotePath, startTime, currentTime))
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

    suspend fun downloadFile(
        remotePath: String,
        localFile: File,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(Exception("SFTP not connected"))
            val attrs = client.stat(remotePath)
            val totalSize = attrs?.size ?: 0L
            val startTime = System.currentTimeMillis()

            val transfer = client.fileTransfer
            transfer.transferListener = object : TransferListener {
                override fun directory(name: String): TransferListener = this
                override fun file(name: String, size: Long): StreamCopier.Listener {
                    return object : StreamCopier.Listener {
                        override fun reportProgress(transferred: Long) {
                            val currentTime = System.currentTimeMillis()
                            onProgress?.invoke(TransferProgress(transferred, totalSize, remotePath, startTime, currentTime))
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

    suspend fun uploadFile(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(Exception("SFTP not connected"))
            val totalSize = localFile.length()
            val startTime = System.currentTimeMillis()

            val transfer = client.fileTransfer
            transfer.transferListener = object : TransferListener {
                override fun directory(name: String): TransferListener = this
                override fun file(name: String, size: Long): StreamCopier.Listener {
                    return object : StreamCopier.Listener {
                        override fun reportProgress(transferred: Long) {
                            val currentTime = System.currentTimeMillis()
                            onProgress?.invoke(TransferProgress(transferred, totalSize, localFile.name, startTime, currentTime))
                        }
                    }
                }
            }

            transfer.upload(localFile.absolutePath, remotePath)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload file: ${localFile.name}")
            Result.failure(e)
        }
    }

    suspend fun mkdir(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            sftpClient?.mkdir(path)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create directory: $path")
            Result.failure(e)
        }
    }

    suspend fun rm(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            sftpClient?.rm(path)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete file: $path")
            Result.failure(e)
        }
    }

    suspend fun rmdir(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            sftpClient?.rmdir(path)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove directory: $path")
            Result.failure(e)
        }
    }

    suspend fun stat(path: String): Result<SftpEntry?> = withContext(Dispatchers.IO) {
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
}

class OutputStreamDestFile(private val outputStream: OutputStream) : LocalDestFile {
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