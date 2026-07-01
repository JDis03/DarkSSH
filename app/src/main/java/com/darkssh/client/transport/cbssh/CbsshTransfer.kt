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

import com.darkssh.client.transport.TransferProgress
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
    ): SftpResult<Unit> =
        withContext(Dispatchers.IO) {
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
    ): SftpResult<Unit> =
        withContext(Dispatchers.IO) {
            downloadToStreamInternal(remotePath, outputStream, onProgress)
        }

    /**
     * Internal download implementation with pipelined reads.
     *
     * Sends [DOWNLOAD_PIPELINE_DEPTH] read requests concurrently and collects them
     * in order — similar to sshj's SFTPFileTransfer window-based strategy.
     * This avoids one full round-trip per chunk and is ~5-10× faster on high-latency
     * connections compared to sequential reads.
     */
    private suspend fun downloadToStreamInternal(
        remotePath: String,
        outputStream: OutputStream,
        onProgress: ((TransferProgress) -> Unit)?,
    ): SftpResult<Unit> {
        Timber.d("[DL] stat: $remotePath")

        // Get file size
        val totalBytes: Long =
            when (val result = sftp.stat(remotePath)) {
                is SftpResult.Success -> result.value.size ?: 0L
                is SftpResult.ServerError -> {
                    Timber.e("[DL] stat FAILED ServerError ${result.statusCode}: ${result.message}")
                    return SftpResult.ServerError(result.statusCode, result.message)
                }
                is SftpResult.ProtocolError -> {
                    Timber.e("[DL] stat FAILED ProtocolError: ${result.message}")
                    return SftpResult.ProtocolError(result.message)
                }
                is SftpResult.IoError -> {
                    Timber.e(result.cause, "[DL] stat FAILED IoError")
                    return SftpResult.IoError(result.cause)
                }
            }
        Timber.d("[DL] stat OK: totalBytes=$totalBytes")
        val startTime = System.currentTimeMillis()

        // Open file for reading
        Timber.d("[DL] open handle: $remotePath")
        val handle =
            when (val result = sftp.open(remotePath, setOf(org.connectbot.sshlib.SftpOpenFlag.READ))) {
                is SftpResult.Success -> result.value
                is SftpResult.ServerError -> {
                    Timber.e("[DL] open FAILED ServerError ${result.statusCode}: ${result.message}")
                    return SftpResult.ServerError(result.statusCode, result.message)
                }
                is SftpResult.ProtocolError -> {
                    Timber.e("[DL] open FAILED ProtocolError: ${result.message}")
                    return SftpResult.ProtocolError(result.message)
                }
                is SftpResult.IoError -> {
                    Timber.e(result.cause, "[DL] open FAILED IoError")
                    return SftpResult.IoError(result.cause)
                }
            }
        Timber.d("[DL] handle opened OK, starting pipeline depth=$DOWNLOAD_PIPELINE_DEPTH chunk=${DOWNLOAD_CHUNK_SIZE / 1024}KB")

        try {
            val chunkSize = DOWNLOAD_CHUNK_SIZE
            val pipelineDepth = DOWNLOAD_PIPELINE_DEPTH
            val reportInterval = DOWNLOAD_REPORT_INTERVAL
            var bytesWritten = 0L
            var lastReportedBytes = 0L
            var readOffset = 0L
            var done = false
            var chunksReceived = 0
            var firstChunkTime: Long = 0
            var lastChunkTime: Long = 0

            // Sliding window of in-flight read requests
            val window = ArrayDeque<kotlinx.coroutines.Deferred<SftpResult<ByteArray?>>>(pipelineDepth)

            coroutineScope {
                // Pre-fill the pipeline — measure how long it takes to dispatch N requests
                val prefillStart = System.nanoTime()
                while (window.size < pipelineDepth && !done) {
                    if (totalBytes > 0 && readOffset >= totalBytes) { done = true; break }
                    val offset = readOffset
                    readOffset += chunkSize
                    window.addLast(async { sftp.read(handle, offset, chunkSize) })
                }
                val prefillMs = (System.nanoTime() - prefillStart) / 1_000_000
                Timber.d("[DL] pipeline pre-filled: ${window.size} requests in-flight (dispatch took ${prefillMs}ms)")

                var chunkGaps = StringBuilder()

                while (window.isNotEmpty()) {
                    // Drain the oldest request — measure round-trip latency
                    val chunkStart = System.nanoTime()
                    val chunkResult = window.removeFirst().await()
                    val rttMs = (System.nanoTime() - chunkStart) / 1_000_000
                    val now = System.currentTimeMillis()
                    if (firstChunkTime == 0L) firstChunkTime = now
                    if (lastChunkTime != 0L) {
                        val gap = now - lastChunkTime
                        if (chunksReceived < 10) {
                            chunkGaps.append("$gap,")
                        }
                    }
                    lastChunkTime = now
                    val chunk: ByteArray =
                        when (chunkResult) {
                            is SftpResult.Success -> {
                                val data = chunkResult.value
                                if (data == null) {
                                    Timber.d("[DL] EOF after $bytesWritten bytes ($chunksReceived chunks)")
                                    break
                                }
                                data
                            }
                            is SftpResult.ServerError -> {
                                Timber.e("[DL] read FAILED ServerError ${chunkResult.statusCode}: ${chunkResult.message} after ${bytesWritten}B")
                                return@coroutineScope SftpResult.ServerError(chunkResult.statusCode, chunkResult.message)
                            }
                            is SftpResult.ProtocolError -> {
                                Timber.e("[DL] read FAILED ProtocolError: ${chunkResult.message} after ${bytesWritten}B")
                                return@coroutineScope SftpResult.ProtocolError(chunkResult.message)
                            }
                            is SftpResult.IoError -> {
                                Timber.e(chunkResult.cause, "[DL] read FAILED IoError after ${bytesWritten}B")
                                return@coroutineScope SftpResult.IoError(chunkResult.cause)
                            }
                        }

                    outputStream.write(chunk)
                    bytesWritten += chunk.size
                    chunksReceived++

                    // Log first few chunks with RTT to diagnose latency
                    if (chunksReceived <= 5) {
                        Timber.d("[DL] chunk #$chunksReceived: ${chunk.size}B, total=${bytesWritten}B, rtt=${rttMs}ms")
                    }
                    // Every 64 chunks, dump timing stats
                    if (chunksReceived % 64 == 0) {
                        Timber.d("[DL] latency gaps first 10 chunks: ${chunkGaps}ms")
                    }

                    // Progress callback (throttled)
                    if (bytesWritten - lastReportedBytes >= reportInterval || bytesWritten >= totalBytes) {
                        val elapsed = System.currentTimeMillis() - startTime
                        val speedKBs = if (elapsed > 0) bytesWritten / elapsed else 0
                        Timber.d("[DL] progress: ${bytesWritten}/${totalBytes}B (${speedKBs}KB/s)")
                        onProgress?.invoke(
                            TransferProgress(
                                transferred = bytesWritten,
                                total = totalBytes,
                                filePath = remotePath.substringAfterLast('/'),
                                startTime = startTime,
                                currentTime = System.currentTimeMillis(),
                            ),
                        )
                        lastReportedBytes = bytesWritten
                    }

                    // Enqueue the next chunk to keep the pipeline full
                    if (!done) {
                        if (totalBytes > 0 && readOffset >= totalBytes) {
                            done = true
                        } else {
                            val offset = readOffset
                            readOffset += chunkSize
                            window.addLast(async { sftp.read(handle, offset, chunkSize) })
                        }
                    }
                }
            }

            outputStream.flush()
            val elapsed = System.currentTimeMillis() - startTime
            val speedKBs = if (elapsed > 0) bytesWritten * 1000 / elapsed / 1024 else 0
            val avgChunkMs = if (chunksReceived > 0) elapsed.toDouble() / chunksReceived else 0.0
            val avgGapMs = if (chunksReceived > 1) (lastChunkTime - firstChunkTime).toDouble() / (chunksReceived - 1) else 0.0
            Timber.d("[DL] DONE: $bytesWritten bytes in ${elapsed}ms = ${speedKBs}KB/s")
            Timber.d("[DL] STATS: $chunksReceived chunks, avg ${"%.1f".format(avgChunkMs)}ms/chunk, avg gap ${"%.1f".format(avgGapMs)}ms between chunks")
            return SftpResult.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            Timber.d("[DL] cancelled: $remotePath")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "[DL] exception: $remotePath")
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
    ): SftpResult<Unit> =
        withContext(Dispatchers.IO) {
            if (!localFile.exists()) {
                return@withContext SftpResult.IoError(
                    IllegalArgumentException("Local file does not exist: ${localFile.absolutePath}"),
                )
            }

            val totalBytes = localFile.length()
            val startTime = System.currentTimeMillis()

            // Open remote file for writing
            val handle =
                when (
                    val result =
                        sftp.open(
                            remotePath,
                            setOf(
                                org.connectbot.sshlib.SftpOpenFlag.WRITE,
                                org.connectbot.sshlib.SftpOpenFlag.CREATE,
                                org.connectbot.sshlib.SftpOpenFlag.TRUNCATE,
                            ),
                        )
                ) {
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

                            else -> {
                                return@withContext writeResult
                            }
                        }
                        bytesWritten += read

                        // Throttled progress callback
                        if (bytesWritten - lastReportedBytes >= reportInterval || bytesWritten >= totalBytes) {
                            onProgress?.invoke(
                                TransferProgress(
                                    transferred = bytesWritten,
                                    total = totalBytes,
                                    filePath = localFile.name,
                                    startTime = startTime,
                                    currentTime = System.currentTimeMillis(),
                                ),
                            )
                            lastReportedBytes = bytesWritten
                        }
                    }
                }

                Timber.d("Upload completed: $bytesWritten bytes to $remotePath")
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
    ): SftpResult<Unit> =
        withContext(Dispatchers.IO) {
            if (!localFile.exists()) {
                return@withContext SftpResult.IoError(
                    IllegalArgumentException("Local file does not exist: ${localFile.absolutePath}"),
                )
            }

            val totalBytes = localFile.length()
            val startTime = System.currentTimeMillis()
            val totalProgress =
                java.util.concurrent.atomic
                    .AtomicLong(0)
            val reportInterval = UPLOAD_REPORT_INTERVAL

            // Open remote file for writing (no TRUNCATE for parallel writes)
            val handle =
                when (
                    val result =
                        sftp.open(
                            remotePath,
                            setOf(
                                org.connectbot.sshlib.SftpOpenFlag.WRITE,
                                org.connectbot.sshlib.SftpOpenFlag.CREATE,
                                org.connectbot.sshlib.SftpOpenFlag.TRUNCATE,
                            ),
                        )
                ) {
                    is SftpResult.Success -> result.value
                    else -> return@withContext result.toUnit()
                }

            try {
                // Calculate chunk ranges
                val chunks =
                    (0 until ((totalBytes + chunkSize - 1) / chunkSize).toInt()).map { i ->
                        val start = i * chunkSize
                        val end = minOf(start + chunkSize, totalBytes)
                        start until end
                    }

                // Upload chunks concurrently
                coroutineScope {
                    chunks
                        .map { chunk ->
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

                                            else -> {
                                                throw writeResult.toException()
                                            }
                                        }
                                        pos += read

                                        val written = totalProgress.addAndGet(read.toLong())
                                        if (written % reportInterval < read || written >= totalBytes) {
                                            onProgress?.invoke(
                                                TransferProgress(
                                                    transferred = written,
                                                    total = totalBytes,
                                                    filePath = localFile.name,
                                                    startTime = startTime,
                                                    currentTime = System.currentTimeMillis(),
                                                ),
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
    ): SftpResult<Unit> =
        withContext(Dispatchers.IO) {
            val totalBytes =
                when (val result = sftp.stat(sourcePath)) {
                    is SftpResult.Success -> result.value.size ?: 0L
                    else -> return@withContext result.toUnit()
                }
            val startTime = System.currentTimeMillis()

            val sourceHandle =
                when (val result = sftp.open(sourcePath, setOf(org.connectbot.sshlib.SftpOpenFlag.READ))) {
                    is SftpResult.Success -> result.value
                    else -> return@withContext result.toUnit()
                }

            val destHandle =
                when (
                    val result =
                        sftp.open(
                            destPath,
                            setOf(
                                org.connectbot.sshlib.SftpOpenFlag.WRITE,
                                org.connectbot.sshlib.SftpOpenFlag.CREATE,
                                org.connectbot.sshlib.SftpOpenFlag.TRUNCATE,
                            ),
                        )
                ) {
                    is SftpResult.Success -> {
                        result.value
                    }

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
                    val chunk: ByteArray =
                        when (chunkResult) {
                            is SftpResult.Success -> {
                                chunkResult.value ?: break
                            }

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
                                return@withContext SftpResult.IoError(chunkResult.cause)
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
                                transferred = offset,
                                total = totalBytes,
                                filePath = sourcePath.substringAfterLast('/'),
                                startTime = startTime,
                                currentTime = System.currentTimeMillis(),
                            ),
                        )
                        lastReportedBytes = offset
                    }
                }

                Timber.d("Server-side copy completed: $offset bytes from $sourcePath to $destPath")
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
        /** Buffer size for sequential read/write operations (32KB) */
        private const val BUFFER_SIZE = 32 * 1024

        /**
         * Chunk size for pipelined downloads (32KB).
         * Must be ≤ SSH channel maxPacketSize so the server can return each
         * SFTP SSH_FXP_DATA response in a single SSH_MSG_CHANNEL_DATA packet.
         * Larger values cause the server to split the response into multiple
         * SSH packets which can trigger ChannelClosedException in the reader.
         */
        private const val DOWNLOAD_CHUNK_SIZE = 32 * 1024

        /**
         * Number of concurrent in-flight SFTP read requests (pipeline depth).
         * 8 × 32KB = 256KB of data requested at a time.
         *
         * Empirically: depth=32 over VPN triggers 30s SFTP request timeouts
         * (server drops responses under high concurrent request load).
         * depth=8 is the safest default. If latency is low (LAN), 16-32 is fine.
         */
        private const val DOWNLOAD_PIPELINE_DEPTH = 8

        /** Larger buffer for server-side copy (128KB) */
        private const val COPY_BUFFER_SIZE = 128 * 1024

        /** Download progress report interval (512KB) */
        private const val DOWNLOAD_REPORT_INTERVAL = 512L * 1024

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
private fun <T> SftpResult<T>.toUnit(): SftpResult<Unit> =
    when (this) {
        is SftpResult.Success<*> -> SftpResult.Success(Unit) as SftpResult<Unit>
        is SftpResult.ServerError -> SftpResult.ServerError(statusCode, message)
        is SftpResult.ProtocolError -> SftpResult.ProtocolError(message)
        is SftpResult.IoError -> SftpResult.IoError(cause)
    }

/**
 * Convenience extension to convert SftpResult to Exception.
 */
private fun SftpResult<*>.toException(): Exception =
    when (this) {
        is SftpResult.Success<*> -> IOException("SFTP success cannot be converted to exception")
        is SftpResult.ServerError -> IOException("SFTP server error: $message")
        is SftpResult.ProtocolError -> IOException("SFTP protocol error: $message")
        is SftpResult.IoError -> (cause as? Exception) ?: IOException(cause.message ?: "SFTP I/O error")
    }
