# cbssh Transfer Wrapper Design

## Purpose

Provide high-level download/upload operations with progress tracking on top of cbssh's low-level SFTP API. This fills the gap left by sshj's `FileTransfer` and `TransferListener`.

## API Design

### `CbsshTransfer.kt`

```kotlin
package com.darkssh.client.transport.cbssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.connectbot.sshlib.*
import org.connectbot.sshlib.client.sftp.SftpOpenFlag
import java.io.File
import java.io.OutputStream

/**
 * High-level file transfer operations on top of cbssh SFTP client.
 * Provides progress tracking similar to sshj's FileTransfer.
 */
class CbsshTransfer(
    private val sftp: SftpClient,
) {
    /**
     * Download a file from the remote server to a local file.
     *
     * @param remotePath Path on the remote server
     * @param localFile Target local file
     * @param onProgress Optional progress callback (throttled to 100KB intervals)
     * @return SftpResult indicating success or error
     */
    suspend fun download(
        remotePath: String,
        localFile: File,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): SftpResult<Unit> = withContext(Dispatchers.IO) {
        val outputStream = localFile.outputStream()
        try {
            downloadToStream(remotePath, outputStream, onProgress)
        } finally {
            outputStream.close()
        }
    }

    /**
     * Download a file from the remote server to an OutputStream.
     * Useful for piping to other consumers without writing to disk.
     */
    suspend fun downloadToStream(
        remotePath: String,
        outputStream: OutputStream,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): SftpResult<Unit> {
        val attrs = sftp.stat(remotePath)
            .onError { return it }
            .getOrThrow()

        val totalBytes = attrs.size ?: 0L
        val startTime = System.currentTimeMillis()
        val handle = sftp.open(remotePath, setOf(SftpOpenFlag.READ))
            .onError { return it }
            .getOrThrow()

        try {
            val bufferSize = 32 * 1024  // 32KB chunks (good balance)
            val reportInterval = 100 * 1024L  // Report every 100KB
            var bytesRead = 0L
            var lastReportedBytes = 0L

            while (true) {
                val chunk = sftp.read(handle, bytesRead, bufferSize)
                    .onError { return it }
                    .getOrThrow() ?: break  // EOF

                outputStream.write(chunk)
                bytesRead += chunk.size

                if (bytesRead - lastReportedBytes >= reportInterval || bytesRead >= totalBytes) {
                    onProgress?.invoke(
                        TransferProgress(
                            bytesTransferred = bytesRead,
                            totalBytes = totalBytes,
                            filename = remotePath.substringAfterLast('/'),
                            startTime = startTime,
                            currentTime = System.currentTimeMillis(),
                        )
                    )
                    lastReportedBytes = bytesRead
                }
            }

            outputStream.flush()
            return SftpResult.Success(Unit)
        } finally {
            sftp.close(handle)
        }
    }

    /**
     * Upload a local file to the remote server.
     *
     * @param localFile Source local file
     * @param remotePath Target path on remote server
     * @param onProgress Optional progress callback (throttled to 256KB intervals)
     * @return SftpResult indicating success or error
     */
    suspend fun upload(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): SftpResult<Unit> = withContext(Dispatchers.IO) {
        if (!localFile.exists()) {
            return@withContext SftpResult.IoError(
                IllegalArgumentException("Local file does not exist: ${localFile.absolutePath}"),
            )
        }

        val totalBytes = localFile.length()
        val startTime = System.currentTimeMillis()
        val handle = sftp.open(
            remotePath,
            setOf(SftpOpenFlag.WRITE, SftpOpenFlag.CREATE, SftpOpenFlag.TRUNCATE),
        ).onError { return@withContext it }
            .getOrThrow()

        try {
            val bufferSize = 32 * 1024
            val reportInterval = 256 * 1024L  // 256KB (less frequent than download)
            var bytesWritten = 0L
            var lastReportedBytes = 0L
            val buffer = ByteArray(bufferSize)

            localFile.inputStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break

                    sftp.write(handle, bytesWritten, buffer.copyOf(read))
                        .onError { return@withContext it }
                        .getOrThrow()
                    bytesWritten += read

                    if (bytesWritten - lastReportedBytes >= reportInterval || bytesWritten >= totalBytes) {
                        onProgress?.invoke(
                            TransferProgress(
                                bytesTransferred = bytesWritten,
                                totalBytes = totalBytes,
                                filename = localFile.name,
                                startTime = startTime,
                                currentTime = System.currentTimeMillis(),
                            )
                        )
                        lastReportedBytes = bytesWritten
                    }
                }
            }

            return@withContext SftpResult.Success(Unit)
        } finally {
            sftp.close(handle)
        }
    }

    /**
     * Upload a local file to the remote server using parallel chunks.
     * Faster for large files but uses more bandwidth.
     */
    suspend fun uploadParallel(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
        chunkSize: Long = 10L * 1024 * 1024,  // 10MB
        parallelChunks: Int = 4,
    ): SftpResult<Unit> = withContext(Dispatchers.IO) {
        val totalBytes = localFile.length()
        val startTime = System.currentTimeMillis()
        val totalProgress = java.util.concurrent.atomic.AtomicLong(0)

        val handle = sftp.open(
            remotePath,
            setOf(SftpOpenFlag.WRITE, SftpOpenFlag.CREATE, SftpOpenFlag.TRUNCATE),
        ).onError { return@withContext it }.getOrThrow()

        try {
            // Calculate chunk ranges
            val chunks = (0 until ((totalBytes + chunkSize - 1) / chunkSize).toInt()).map { i ->
                val start = i * chunkSize
                val end = minOf(start + chunkSize, totalBytes)
                start until end
            }

            // Upload chunks concurrently
            coroutineScope {
                chunks.map { chunk ->
                    async(Dispatchers.IO) {
                        val buffer = ByteArray(32 * 1024)
                        localFile.inputStream().use { input ->
                            input.skip(chunk.first)
                            var pos = chunk.first
                            while (pos < chunk.last) {
                                val toRead = minOf(buffer.size.toLong(), chunk.last - pos).toInt()
                                val read = input.read(buffer, 0, toRead)
                                if (read == -1) break

                                sftp.write(handle, pos, buffer.copyOf(read))
                                    .getOrThrow()
                                pos += read

                                val written = totalProgress.addAndGet(read.toLong())
                                onProgress?.invoke(
                                    TransferProgress(
                                        bytesTransferred = written,
                                        totalBytes = totalBytes,
                                        filename = localFile.name,
                                        startTime = startTime,
                                        currentTime = System.currentTimeMillis(),
                                    )
                                )
                            }
                        }
                    }
                }.awaitAll()
            }

            return@withContext SftpResult.Success(Unit)
        } finally {
            sftp.close(handle)
        }
    }

    /**
     * Server-side file copy using SFTP streams.
     * No data is transferred through the client.
     */
    suspend fun copy(
        sourcePath: String,
        destPath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): SftpResult<Unit> = withContext(Dispatchers.IO) {
        val totalBytes = sftp.stat(sourcePath).getOrThrow().size ?: 0L
        val startTime = System.currentTimeMillis()

        val sourceHandle = sftp.open(sourcePath, setOf(SftpOpenFlag.READ))
            .onError { return@withContext it }
            .getOrThrow()
        val destHandle = try {
            sftp.open(
                destPath,
                setOf(SftpOpenFlag.WRITE, SftpOpenFlag.CREATE, SftpOpenFlag.TRUNCATE),
            ).onError {
                sftp.close(sourceHandle)
                return@withContext it
            }.getOrThrow()
        } catch (e: Exception) {
            sftp.close(sourceHandle)
            throw e
        }

        try {
            var offset = 0L
            val bufferSize = 128 * 1024  // 128KB (larger for server-side copy)
            val reportInterval = 5L * 1024 * 1024  // Report every 5MB
            var lastReportedBytes = 0L

            while (true) {
                val chunk = sftp.read(sourceHandle, offset, bufferSize)
                    .onError { return@withContext it }
                    .getOrThrow() ?: break

                sftp.write(destHandle, offset, chunk)
                    .onError { return@withContext it }
                    .getOrThrow()
                offset += chunk.size

                if (offset - lastReportedBytes >= reportInterval || offset >= totalBytes) {
                    onProgress?.invoke(
                        TransferProgress(
                            bytesTransferred = offset,
                            totalBytes = totalBytes,
                            filename = sourcePath.substringAfterLast('/'),
                            startTime = startTime,
                            currentTime = System.currentTimeMillis(),
                        )
                    )
                    lastReportedBytes = offset
                }
            }

            return@withContext SftpResult.Success(Unit)
        } finally {
            sftp.close(sourceHandle)
            sftp.close(destHandle)
        }
    }
}

/**
 * Convenience extension to handle SftpResult error mapping.
 */
private inline fun <T> SftpResult<T>.onError(action: (SftpResult<T>) -> Unit): SftpResult<T> {
    if (this !is SftpResult.Success) action(this)
    return this
}
```

## Usage Example

```kotlin
// In SftpClient2.kt
class SftpClient2(host: Host, ...) {
    private var sshClient: SshClient? = null
    private var sftpClient: SftpClient? = null
    private var transfer: CbsshTransfer? = null

    suspend fun connectWithPassword(password: String): Result<Unit> {
        val client = SshClient(
            SshClientConfig().apply {
                // Configure window sizes, keepalive, etc.
            }
        )
        client.connect(host.hostname, host.port)
        client.authenticatePassword(host.username, password)
        sshClient = client

        val sftp = client.openSftp().getOrThrow()
        sftpClient = sftp
        transfer = CbsshTransfer(sftp)
        return Result.success(Unit)
    }

    suspend fun downloadFile(
        remotePath: String,
        localFile: File,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): Result<Unit> {
        return when (val result = transfer?.download(remotePath, localFile, onProgress)) {
            is SftpResult.Success -> Result.success(Unit)
            is SftpResult.ServerError -> Result.failure(Exception(result.message))
            is SftpResult.ProtocolError -> Result.failure(Exception(result.message))
            is SftpResult.IoError -> Result.failure(result.cause ?: Exception("I/O error"))
            null -> Result.failure(IllegalStateException("SFTP not connected"))
        }
    }
}
```

## Testing Strategy

1. **Unit tests** with mock SftpClient (using mockk)
2. **Integration tests** against testcontainers OpenSSH server (similar to cbssh's tests)
3. **Parallel testing** with sshj implementation (verify same behavior)
4. **Performance benchmarks** vs sshj (throughput, latency)
5. **Large file tests** (>1GB files with progress throttling)

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Progress callback throttling differences | Low | Match sshj intervals exactly |
| Error type mapping | Medium | Centralized mapping in SftpClient2 |
| Concurrent upload races | Medium | Use AtomicLong for progress tracking |
| Connection state management | Low | Follow sshj patterns |
| Performance regression | Medium | Benchmark before/after |

## Next Phase

1. Create `CbsshTransfer.kt` with the wrapper
2. Write unit tests for the wrapper
3. Create `SftpClient2.kt` as drop-in replacement
4. Add feature flag in `AppPreferences`
5. Test in parallel with sshj
