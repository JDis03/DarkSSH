/*
 * DarkSSH SFTP Client - cbssh Migration
 * Copyright 2026 DarkSSH
 *
 * High-level transfer operations on top of cbssh SFTP client.
 * Fills the gap left by sshj's FileTransfer and TransferListener.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.darkssh.client.transport.cbssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.connectbot.sshlib.SftpClient
import org.connectbot.sshlib.SftpResult
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * High-level file transfer operations on top of cbssh SFTP client.
 * Provides progress tracking similar to sshj's FileTransfer.
 *
 * Throttling matches sshj behavior:
 * - Download: 100KB intervals
 * - Upload: 256KB intervals
 * - Server-side copy: 5MB intervals
 *
 * Buffer size: 32KB (good balance between throughput and memory)
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
            downloadToStreamInternal(remotePath, outputStream, onProgress)
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
    ): SftpResult<Unit> = withContext(Dispatchers.IO) {
        downloadToStreamInternal(remotePath, outputStream, onProgress)
    }

    /**
     * Internal download implementation.
     */
    private suspend fun downloadToStreamInternal(
        remotePath: String,
        outputStream: OutputStream,
        onProgress: ((TransferProgress) -> Unit)?,
    ): SftpResult<Unit> {
        // Get file size
        val attrs: org.connectbot.sshlib.SftpAttributes = when (val result = sftp.stat(remotePath)) {
            is SftpResult.Success -> result.value
            is SftpResult.ServerError -> return SftpResult.ServerError(result.statusCode, result.message)
            is SftpResult.ProtocolError -> return SftpResult.ProtocolError(result.message)
            is SftpResult.IoError -> return SftpResult.IoError(result.cause ?: java.io.IOException("I/O error"))
        }
        val totalBytes = attrs.size ?: 0L
        val startTime = System.currentTimeMillis()

        // Open file for reading
        val handle: org.connectbot.sshlib.SftpFileHandle = when (val result = sftp.open(remotePath, setOf(org.connectbot.sshlib.SftpOpenFlag.READ))) {
            is SftpResult.Success -> result.value
            is SftpResult.ServerError -> return SftpResult.ServerError(result.statusCode, result.message)
            is SftpResult.ProtocolError -> return SftpResult.ProtocolError(result.message)
            is SftpResult.IoError -> return SftpResult.IoError(result.cause ?: java.io.IOException("I/O error"))
        }

        try {
            val bufferSize = BUFFER_SIZE
            val reportInterval = DOWNLOAD_REPORT_INTERVAL
            var bytesRead = 0L
            var lastReportedBytes = 0L

            // Read in chunks
            while (true) {
                val chunkResult = sftp.read(handle, bytesRead, bufferSize)
                val chunk: ByteArray = when (chunkResult) {
                    is SftpResult.Success -> chunkResult.value ?: break  // EOF
                    is SftpResult.ServerError -> return SftpResult.ServerError(chunkResult.statusCode, chunkResult.message)
                    is SftpResult.ProtocolError -> return SftpResult.ProtocolError(chunkResult.message)
                    is SftpResult.IoError -> return SftpResult.IoError(chunkResult.cause ?: java.io.IOException("I/O error"))
                }

                outputStream.write(chunk)
                bytesRead += chunk.size

                // Throttled progress callback
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
            Timber.d("Download completed: ${bytesRead} bytes from $remotePath")
            return SftpResult.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Download failed: $remotePath")
            return SftpResult.IoError(e)
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

        // Open remote file for writing
        val handle = when (val result = sftp.open(
            remotePath,
            setOf(
                org.connectbot.sshlib.SftpOpenFlag.WRITE,
                org.connectbot.sshlib.SftpOpenFlag.CREATE,
                org.connectbot.sshlib.SftpOpenFlag.TRUNCATE,
            ),
        )) {
            is SftpResult.Success -> result.value
            else -> return@withContext result.toUnit()
        }

        try {
            val reportInterval = UPLOAD_REPORT_INTERVAL
            var bytesWritten = 0L
            var lastReportedBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            localFile.inputStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break

                    val writeResult = sftp.write(handle, bytesWritten, buffer.copyOf(read))
                    when (writeResult) {
                        is SftpResult.Success -> { /* continue */ }
                        else -> return@withContext writeResult
                    }
                    bytesWritten += read

                    // Throttled progress callback
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

            Timber.d("Upload completed: ${bytesWritten} bytes to $remotePath")
            return@withContext SftpResult.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Upload failed: $remotePath")
            return@withContext SftpResult.IoError(e)
        } finally {
            sftp.close(handle)
        }
    }

    /**
     * Upload a local file to the remote server using parallel chunks.
     * Faster for large files but uses more bandwidth.
     *
     * @param chunkSize Size of each chunk (default: 10MB)
     * @param parallelChunks Number of concurrent chunks (default: 4)
     */
    suspend fun uploadParallel(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
        chunkSize: Long = DEFAULT_CHUNK_SIZE,
        parallelChunks: Int = DEFAULT_PARALLEL_CHUNKS,
    ): SftpResult<Unit> = withContext(Dispatchers.IO) {
        if (!localFile.exists()) {
            return@withContext SftpResult.IoError(
                IllegalArgumentException("Local file does not exist: ${localFile.absolutePath}"),
            )
        }

        val totalBytes = localFile.length()
        val startTime = System.currentTimeMillis()
        val totalProgress = java.util.concurrent.atomic.AtomicLong(0)
        val reportInterval = UPLOAD_REPORT_INTERVAL

        // Open remote file for writing (no TRUNCATE for parallel writes)
        val handle = when (val result = sftp.open(
            remotePath,
            setOf(
                org.connectbot.sshlib.SftpOpenFlag.WRITE,
                org.connectbot.sshlib.SftpOpenFlag.CREATE,
                org.connectbot.sshlib.SftpOpenFlag.TRUNCATE,
            ),
        )) {
            is SftpResult.Success -> result.value
            else -> return@withContext result.toUnit()
        }

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
                        val buffer = ByteArray(BUFFER_SIZE)
                        localFile.inputStream().use { input ->
                            input.skip(chunk.first)
                            var pos = chunk.first
                            while (pos < chunk.last) {
                                val toRead = minOf(buffer.size.toLong(), chunk.last - pos).toInt()
                                val read = input.read(buffer, 0, toRead)
                                if (read == -1) break

                                val writeResult = sftp.write(handle, pos, buffer.copyOf(read))
                                when (writeResult) {
                                    is SftpResult.Success -> { /* continue */ }
                                    else -> throw writeResult.toException()
                                }
                                pos += read

                                val written = totalProgress.addAndGet(read.toLong())
                                if (written % reportInterval < read || written >= totalBytes) {
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
                    }
                }.awaitAll()
            }

            Timber.d("Parallel upload completed: $totalBytes bytes to $remotePath")
            return@withContext SftpResult.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Parallel upload failed: $remotePath")
            return@withContext SftpResult.IoError(e)
        } finally {
            sftp.close(handle)
        }
    }

    /**
     * Server-side file copy using SFTP streams.
     * No data is transferred through the client - efficient for same-server copies.
     */
    suspend fun copy(
        sourcePath: String,
        destPath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): SftpResult<Unit> = withContext(Dispatchers.IO) {
        val totalBytes = when (val result = sftp.stat(sourcePath)) {
            is SftpResult.Success -> result.value.size ?: 0L
            else -> return@withContext result.toUnit()
        }
        val startTime = System.currentTimeMillis()

        val sourceHandle = when (val result = sftp.open(sourcePath, setOf(org.connectbot.sshlib.SftpOpenFlag.READ))) {
            is SftpResult.Success -> result.value
            else -> return@withContext result.toUnit()
        }

        val destHandle = when (val result = sftp.open(
            destPath,
            setOf(
                org.connectbot.sshlib.SftpOpenFlag.WRITE,
                org.connectbot.sshlib.SftpOpenFlag.CREATE,
                org.connectbot.sshlib.SftpOpenFlag.TRUNCATE,
            ),
        )) {
            is SftpResult.Success -> result.value
            else -> {
                sftp.close(sourceHandle)
                return@withContext result.toUnit()
            }
        }

        try {
            val reportInterval = COPY_REPORT_INTERVAL
            var offset = 0L
            var lastReportedBytes = 0L

            while (true) {
                val chunkResult = sftp.read(sourceHandle, offset, COPY_BUFFER_SIZE)
                val chunk: ByteArray = when (chunkResult) {
                    is SftpResult.Success -> chunkResult.value ?: break
                    is SftpResult.ServerError -> {
                        sftp.close(sourceHandle)
                        sftp.close(destHandle)
                        return@withContext SftpResult.ServerError(chunkResult.statusCode, chunkResult.message)
                    }
                    is SftpResult.ProtocolError -> {
                        sftp.close(sourceHandle)
                        sftp.close(destHandle)
                        return@withContext SftpResult.ProtocolError(chunkResult.message)
                    }
                    is SftpResult.IoError -> {
                        sftp.close(sourceHandle)
                        sftp.close(destHandle)
                        return@withContext SftpResult.IoError(chunkResult.cause ?: IOException("I/O error"))
                    }
                }

                val writeResult = sftp.write(destHandle, offset, chunk)
                when (writeResult) {
                    is SftpResult.Success -> { /* continue */ }
                    else -> {
                        sftp.close(sourceHandle)
                        sftp.close(destHandle)
                        return@withContext writeResult
                    }
                }
                offset += chunk.size

                // Throttled progress (every 5MB for copy)
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

            Timber.d("Server-side copy completed: ${offset} bytes from $sourcePath to $destPath")
            return@withContext SftpResult.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Server-side copy failed: $sourcePath -> $destPath")
            return@withContext SftpResult.IoError(e)
        } finally {
            sftp.close(sourceHandle)
            sftp.close(destHandle)
        }
    }

    companion object {
        /** Buffer size for read/write operations (32KB) */
        private const val BUFFER_SIZE = 32 * 1024

        /** Larger buffer for server-side copy (128KB) */
        private const val COPY_BUFFER_SIZE = 128 * 1024

        /** Download progress report interval (100KB) */
        private const val DOWNLOAD_REPORT_INTERVAL = 100L * 1024

        /** Upload progress report interval (256KB) */
        private const val UPLOAD_REPORT_INTERVAL = 256L * 1024

        /** Copy progress report interval (5MB) */
        private const val COPY_REPORT_INTERVAL = 5L * 1024 * 1024

        /** Default chunk size for parallel uploads (10MB) */
        private const val DEFAULT_CHUNK_SIZE = 10L * 1024 * 1024

        /** Default number of parallel chunks */
        private const val DEFAULT_PARALLEL_CHUNKS = 4
    }
}

/**
 * Convenience extension to convert any SftpResult to a Unit version preserving error info.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> SftpResult<T>.toUnit(): SftpResult<Unit> = when (this) {
    is SftpResult.Success<*> -> SftpResult.Success(Unit) as SftpResult<Unit>
    is SftpResult.ServerError -> SftpResult.ServerError(statusCode, message)
    is SftpResult.ProtocolError -> SftpResult.ProtocolError(message)
    is SftpResult.IoError -> SftpResult.IoError(cause ?: IOException("I/O error"))
}

/**
 * Convenience extension to convert SftpResult to Exception.
 */
private fun SftpResult<*>.toException(): Exception = when (this) {
    is SftpResult.Success<*> -> IOException("SFTP success cannot be converted to exception")
    is SftpResult.ServerError -> IOException("SFTP server error: ${message}")
    is SftpResult.ProtocolError -> IOException("SFTP protocol error: ${message}")
    is SftpResult.IoError -> (cause as? Exception) ?: IOException(cause?.message ?: "SFTP I/O error")
}