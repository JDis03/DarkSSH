/*
 * DarkSSH SFTP Transfer Engine - Kotlin-first design
 * Copyright 2026 DarkSSH
 *
 * Modern transfer engine leveraging Kotlin coroutines for:
 * - Adaptive pipeline depth based on RTT
 * - Automatic retry with exponential backoff
 * - Flow-based progress with backpressure
 * - Resumable transfers
 * - Structured concurrency (no resource leaks)
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport.cbssh

import com.darkssh.client.transport.TransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.connectbot.sshlib.SftpClient
import org.connectbot.sshlib.SftpFileHandle
import org.connectbot.sshlib.SftpOpenFlag
import org.connectbot.sshlib.SftpResult
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * Transfer state for resumable operations.
 */
data class TransferState(
    val remotePath: String,
    val localPath: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val isUpload: Boolean,
    val startTime: Long = System.currentTimeMillis(),
) {
    val progress: Float get() = if (totalBytes > 0) transferredBytes.toFloat() / totalBytes else 0f

    /**
     * Whether the transfer has finished writing all expected bytes.
     *
     * Returns false when [totalBytes] is zero — an empty file transfer
     * has not produced any work yet, even though the trivial inequality
     * `0 >= 0` would otherwise report it as complete.
     */
    val isComplete: Boolean get() = totalBytes > 0 && transferredBytes >= totalBytes
}

/**
 * Transfer result with detailed stats.
 */
sealed class TransferResult {
    data class Success(
        val bytesTransferred: Long,
        val durationMs: Long,
        val avgSpeedKBps: Long,
        val chunksTransferred: Int,
        val retriesUsed: Int,
    ) : TransferResult()

    data class Failed(
        val error: Throwable,
        val bytesTransferred: Long,
        val canResume: Boolean,
    ) : TransferResult()

    data object Cancelled : TransferResult()
}

/**
 * Configuration for transfer behavior.
 */
data class TransferConfig(
    /** Initial pipeline depth (adapts based on RTT) */
    val initialPipelineDepth: Int = 8,
    /** Min pipeline depth (won't go below this) */
    val minPipelineDepth: Int = 2,
    /** Max pipeline depth (won't exceed this) */
    val maxPipelineDepth: Int = 32,
    /** Chunk size for reads/writes */
    val chunkSize: Int = 32 * 1024,
    /** Max retries per chunk */
    val maxRetries: Int = 3,
    /** Initial retry delay (doubles each retry) */
    val retryDelayMs: Long = 1000,
    /** Timeout for individual operations */
    val operationTimeoutMs: Long = 60_000,
    /** Progress report interval */
    val progressIntervalBytes: Long = 256 * 1024,
    /** Target RTT for adaptive pipelining (ms) */
    val targetRttMs: Long = 100,
)

/**
 * Modern Kotlin-first SFTP transfer engine.
 *
 * Features:
 * - Adaptive pipeline depth based on measured RTT
 * - Automatic retry with exponential backoff
 * - Flow-based progress reporting
 * - Resumable transfers (start from offset)
 * - Proper cancellation support
 */
class TransferEngine(
    private val sftp: SftpClient,
    private val config: TransferConfig = TransferConfig(),
) {
    // Adaptive state
    private var currentPipelineDepth = config.initialPipelineDepth
    private var avgRttMs = 0L
    private var rttSamples = 0

    // === Internal accessors for unit testing ===
    // These are `internal` so test code in the same module can observe state
    // without exposing it to external callers. Not part of the public API.

    internal val currentPipelineDepthForTest: Int get() = currentPipelineDepth
    internal val avgRttMsForTest: Long get() = avgRttMs

    internal fun updateRttForTest(rttMs: Long) {
        updateRtt(rttMs)
    }

    /**
     * Download with Flow-based progress.
     * Returns a Flow that emits progress updates.
     * Collect the flow to drive the download; it completes when transfer finishes.
     */
    fun downloadWithProgress(
        remotePath: String,
        localFile: File,
        resumeFrom: Long = 0,
    ): Flow<TransferProgress> =
        flow {
            val channel = kotlinx.coroutines.channels.Channel<TransferProgress>(kotlinx.coroutines.channels.Channel.BUFFERED)

            coroutineScope {
                // Launch download in background, sending progress to channel
                val job =
                    async {
                        downloadInternal(remotePath, localFile, resumeFrom) { progress ->
                            channel.trySend(progress)
                        }
                    }

                // Emit from channel until download completes
                try {
                    for (progress in channel) {
                        emit(progress)
                    }
                } finally {
                    job.await()
                }
            }
        }.flowOn(Dispatchers.IO)

    /**
     * Download to file with callback progress.
     */
    suspend fun download(
        remotePath: String,
        localFile: File,
        resumeFrom: Long = 0,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): TransferResult =
        withContext(Dispatchers.IO) {
            downloadInternal(remotePath, localFile, resumeFrom, onProgress)
        }

    /**
     * Download to OutputStream.
     */
    suspend fun downloadToStream(
        remotePath: String,
        outputStream: OutputStream,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): TransferResult =
        withContext(Dispatchers.IO) {
            downloadToStreamInternal(remotePath, outputStream, 0, onProgress)
        }

    /**
     * Upload with Flow-based progress.
     * Collect the flow to drive the upload; it completes when transfer finishes.
     */
    fun uploadWithProgress(
        localFile: File,
        remotePath: String,
        resumeFrom: Long = 0,
    ): Flow<TransferProgress> =
        flow {
            val channel = kotlinx.coroutines.channels.Channel<TransferProgress>(kotlinx.coroutines.channels.Channel.BUFFERED)

            coroutineScope {
                val job =
                    async {
                        uploadInternal(localFile, remotePath, resumeFrom) { progress ->
                            channel.trySend(progress)
                        }
                    }

                try {
                    for (progress in channel) {
                        emit(progress)
                    }
                } finally {
                    job.await()
                }
            }
        }.flowOn(Dispatchers.IO)

    /**
     * Upload file with callback progress.
     */
    suspend fun upload(
        localFile: File,
        remotePath: String,
        resumeFrom: Long = 0,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): TransferResult =
        withContext(Dispatchers.IO) {
            uploadInternal(localFile, remotePath, resumeFrom, onProgress)
        }

    // === Internal Implementation ===

    private suspend fun downloadInternal(
        remotePath: String,
        localFile: File,
        resumeFrom: Long,
        onProgress: ((TransferProgress) -> Unit)?,
    ): TransferResult {
        // If resuming, append mode; otherwise truncate
        val outputStream =
            if (resumeFrom > 0) {
                localFile.outputStream() // Will seek in downloadToStreamInternal
            } else {
                localFile.outputStream()
            }

        return try {
            downloadToStreamInternal(remotePath, outputStream, resumeFrom, onProgress)
        } finally {
            runCatching { outputStream.close() }
        }
    }

    private suspend fun downloadToStreamInternal(
        remotePath: String,
        outputStream: OutputStream,
        resumeFrom: Long,
        onProgress: ((TransferProgress) -> Unit)?,
    ): TransferResult {
        val startTime = System.currentTimeMillis()
        var bytesWritten = resumeFrom
        var chunksReceived = 0
        var totalRetries = 0

        // Get file size with retry
        val totalBytes =
            when (val result = withRetry("stat") { sftp.stat(remotePath) }) {
                is SftpResult.Success -> result.value.size ?: 0L

                else -> return TransferResult.Failed(
                    result.toException(),
                    bytesWritten,
                    canResume = false,
                )
            }

        if (resumeFrom >= totalBytes) {
            return TransferResult.Success(0, 0, 0, 0, 0)
        }

        Timber.d("[DL] Starting: $remotePath, total=$totalBytes, resume=$resumeFrom, pipeline=$currentPipelineDepth")

        // Open file with retry
        val handle =
            when (
                val result =
                    withRetry("open") {
                        sftp.open(remotePath, setOf(SftpOpenFlag.READ))
                    }
            ) {
                is SftpResult.Success -> result.value

                else -> return TransferResult.Failed(
                    result.toException(),
                    bytesWritten,
                    canResume = false,
                )
            }

        try {
            var readOffset = resumeFrom
            var lastProgressBytes = resumeFrom
            val chunkSize = config.chunkSize

            // Adaptive sliding window
            val window = ArrayDeque<PipelineRequest>(config.maxPipelineDepth)

            return coroutineScope {
                // Pre-fill pipeline
                while (window.size < currentPipelineDepth && readOffset < totalBytes) {
                    val offset = readOffset
                    readOffset += chunkSize
                    window.addLast(
                        PipelineRequest(
                            offset = offset,
                            deferred = async { sftp.read(handle, offset, chunkSize) },
                            dispatchTime = System.nanoTime(),
                        ),
                    )
                }

                while (window.isNotEmpty()) {
                    coroutineContext.ensureActive()

                    val request = window.removeFirst()
                    val chunkStart = System.nanoTime()

                    // Await with timeout and retry
                    val chunk =
                        try {
                            val result =
                                withTimeout(config.operationTimeoutMs) {
                                    request.deferred.await()
                                }

                            // Measure RTT and adapt pipeline
                            val rttMs = (System.nanoTime() - request.dispatchTime) / 1_000_000
                            updateRtt(rttMs)

                            when (result) {
                                is SftpResult.Success -> {
                                    result.value ?: break // EOF
                                }

                                is SftpResult.IoError -> {
                                    // Retry this chunk
                                    val retryResult = retryChunk(handle, request.offset, chunkSize)
                                    if (retryResult != null) {
                                        totalRetries++
                                        retryResult
                                    } else {
                                        return@coroutineScope TransferResult.Failed(
                                            result.cause,
                                            bytesWritten,
                                            canResume = true,
                                        )
                                    }
                                }

                                else -> {
                                    return@coroutineScope TransferResult.Failed(
                                        result.toException(),
                                        bytesWritten,
                                        canResume = result is SftpResult.IoError,
                                    )
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Timeout or other error - try to retry
                            val retryResult = retryChunk(handle, request.offset, chunkSize)
                            if (retryResult != null) {
                                totalRetries++
                                retryResult
                            } else {
                                return@coroutineScope TransferResult.Failed(
                                    e,
                                    bytesWritten,
                                    canResume = true,
                                )
                            }
                        }

                    // Write chunk
                    outputStream.write(chunk)
                    bytesWritten += chunk.size
                    chunksReceived++

                    // Progress callback
                    if (bytesWritten - lastProgressBytes >= config.progressIntervalBytes ||
                        bytesWritten >= totalBytes
                    ) {
                        onProgress?.invoke(
                            TransferProgress(
                                transferred = bytesWritten,
                                total = totalBytes,
                                filePath = remotePath.substringAfterLast('/'),
                                startTime = startTime,
                                currentTime = System.currentTimeMillis(),
                            ),
                        )
                        lastProgressBytes = bytesWritten
                    }

                    // Refill pipeline (adaptive depth)
                    while (window.size < currentPipelineDepth && readOffset < totalBytes) {
                        val offset = readOffset
                        readOffset += chunkSize
                        window.addLast(
                            PipelineRequest(
                                offset = offset,
                                deferred = async { sftp.read(handle, offset, chunkSize) },
                                dispatchTime = System.nanoTime(),
                            ),
                        )
                    }
                }

                outputStream.flush()

                val elapsed = System.currentTimeMillis() - startTime
                val speedKBps = if (elapsed > 0) (bytesWritten - resumeFrom) * 1000 / elapsed / 1024 else 0

                Timber.d(
                    "[DL] Complete: ${bytesWritten - resumeFrom}B in ${elapsed}ms = ${speedKBps}KB/s, retries=$totalRetries, finalDepth=$currentPipelineDepth",
                )

                TransferResult.Success(
                    bytesTransferred = bytesWritten - resumeFrom,
                    durationMs = elapsed,
                    avgSpeedKBps = speedKBps,
                    chunksTransferred = chunksReceived,
                    retriesUsed = totalRetries,
                )
            }
        } catch (e: CancellationException) {
            Timber.d("[DL] Cancelled at $bytesWritten bytes")
            return TransferResult.Cancelled
        } catch (e: Exception) {
            Timber.e(e, "[DL] Failed at $bytesWritten bytes")
            return TransferResult.Failed(e, bytesWritten, canResume = true)
        } finally {
            runCatching { sftp.close(handle) }
        }
    }

    private suspend fun uploadInternal(
        localFile: File,
        remotePath: String,
        resumeFrom: Long,
        onProgress: ((TransferProgress) -> Unit)?,
    ): TransferResult {
        if (!localFile.exists()) {
            return TransferResult.Failed(
                IOException("Local file does not exist: ${localFile.absolutePath}"),
                0,
                canResume = false,
            )
        }

        val startTime = System.currentTimeMillis()
        val totalBytes = localFile.length()
        var bytesWritten = resumeFrom
        var chunksWritten = 0
        var totalRetries = 0

        if (resumeFrom >= totalBytes) {
            return TransferResult.Success(0, 0, 0, 0, 0)
        }

        Timber.d("[UL] Starting: ${localFile.name} -> $remotePath, total=$totalBytes, resume=$resumeFrom")

        // Open remote file
        val flags =
            if (resumeFrom > 0) {
                setOf(SftpOpenFlag.WRITE) // Append mode
            } else {
                setOf(SftpOpenFlag.WRITE, SftpOpenFlag.CREATE, SftpOpenFlag.TRUNCATE)
            }

        val handle =
            when (val result = withRetry("open") { sftp.open(remotePath, flags) }) {
                is SftpResult.Success -> result.value

                else -> return TransferResult.Failed(
                    result.toException(),
                    bytesWritten,
                    canResume = false,
                )
            }

        try {
            val buffer = ByteArray(config.chunkSize)
            var lastProgressBytes = resumeFrom

            localFile.inputStream().use { input ->
                // Skip to resume point
                if (resumeFrom > 0) {
                    input.skip(resumeFrom)
                }

                while (coroutineContext.isActive) {
                    coroutineContext.ensureActive()

                    val read = input.read(buffer)
                    if (read == -1) break

                    val data = if (read == buffer.size) buffer else buffer.copyOf(read)

                    // Write with retry
                    var written = false
                    var retries = 0
                    var lastError: Throwable? = null

                    while (!written && retries < config.maxRetries) {
                        coroutineContext.ensureActive()

                        try {
                            val result =
                                withTimeout(config.operationTimeoutMs) {
                                    sftp.write(handle, bytesWritten, data)
                                }

                            when (result) {
                                is SftpResult.Success -> {
                                    written = true
                                }

                                is SftpResult.IoError -> {
                                    lastError = result.cause
                                    retries++
                                    totalRetries++
                                    if (retries < config.maxRetries) {
                                        delay(config.retryDelayMs * (1 shl (retries - 1)))
                                    }
                                }

                                else -> {
                                    return TransferResult.Failed(
                                        result.toException(),
                                        bytesWritten,
                                        canResume = true,
                                    )
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            lastError = e
                            retries++
                            totalRetries++
                            if (retries < config.maxRetries) {
                                delay(config.retryDelayMs * (1 shl (retries - 1)))
                            }
                        }
                    }

                    if (!written) {
                        return TransferResult.Failed(
                            lastError ?: IOException("Write failed after $retries retries"),
                            bytesWritten,
                            canResume = true,
                        )
                    }

                    bytesWritten += read
                    chunksWritten++

                    // Progress
                    if (bytesWritten - lastProgressBytes >= config.progressIntervalBytes ||
                        bytesWritten >= totalBytes
                    ) {
                        onProgress?.invoke(
                            TransferProgress(
                                transferred = bytesWritten,
                                total = totalBytes,
                                filePath = localFile.name,
                                startTime = startTime,
                                currentTime = System.currentTimeMillis(),
                            ),
                        )
                        lastProgressBytes = bytesWritten
                    }
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            val speedKBps = if (elapsed > 0) (bytesWritten - resumeFrom) * 1000 / elapsed / 1024 else 0

            Timber.d("[UL] Complete: ${bytesWritten - resumeFrom}B in ${elapsed}ms = ${speedKBps}KB/s, retries=$totalRetries")

            return TransferResult.Success(
                bytesTransferred = bytesWritten - resumeFrom,
                durationMs = elapsed,
                avgSpeedKBps = speedKBps,
                chunksTransferred = chunksWritten,
                retriesUsed = totalRetries,
            )
        } catch (e: CancellationException) {
            Timber.d("[UL] Cancelled at $bytesWritten bytes")
            return TransferResult.Cancelled
        } catch (e: Exception) {
            Timber.e(e, "[UL] Failed at $bytesWritten bytes")
            return TransferResult.Failed(e, bytesWritten, canResume = true)
        } finally {
            runCatching { sftp.close(handle) }
        }
    }

    // === Adaptive Pipeline ===

    private fun updateRtt(rttMs: Long) {
        // Exponential moving average
        avgRttMs =
            if (rttSamples == 0) {
                rttMs
            } else {
                (avgRttMs * 7 + rttMs) / 8
            }
        rttSamples++

        // Adapt pipeline depth based on RTT
        // High latency = deeper pipeline, low latency = shallower
        val targetDepth =
            when {
                avgRttMs < 20 -> config.minPipelineDepth

                // LAN - shallow is fine
                avgRttMs < 50 -> 4

                avgRttMs < 100 -> 8

                avgRttMs < 200 -> 16

                else -> config.maxPipelineDepth // High latency - max pipeline
            }

        // Gradual adjustment
        currentPipelineDepth =
            when {
                targetDepth > currentPipelineDepth -> min(currentPipelineDepth + 1, targetDepth)
                targetDepth < currentPipelineDepth -> max(currentPipelineDepth - 1, targetDepth)
                else -> currentPipelineDepth
            }

        if (rttSamples % 50 == 0) {
            Timber.d("[ADAPTIVE] avgRtt=${avgRttMs}ms, depth=$currentPipelineDepth")
        }
    }

    // === Retry Logic ===

    private suspend fun <T> withRetry(
        name: String,
        operation: suspend () -> SftpResult<T>,
    ): SftpResult<T> {
        var lastError: SftpResult<T>? = null
        var delayMs = config.retryDelayMs

        repeat(config.maxRetries) { attempt ->
            coroutineContext.ensureActive()

            try {
                val result = withTimeout(config.operationTimeoutMs) { operation() }

                when (result) {
                    is SftpResult.Success -> {
                        return result
                    }

                    is SftpResult.ServerError -> {
                        return result
                    }

                    // Not retryable
                    is SftpResult.ProtocolError -> {
                        return result
                    }

                    // Not retryable
                    is SftpResult.IoError -> {
                        lastError = result
                        if (attempt < config.maxRetries - 1) {
                            Timber.w("[$name] IoError attempt ${attempt + 1}, retry in ${delayMs}ms")
                            delay(delayMs)
                            delayMs *= 2
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = SftpResult.IoError(e)
                if (attempt < config.maxRetries - 1) {
                    Timber.w("[$name] Exception attempt ${attempt + 1}, retry in ${delayMs}ms: ${e.message}")
                    delay(delayMs)
                    delayMs *= 2
                }
            }
        }

        return lastError ?: SftpResult.IoError(IOException("$name failed"))
    }

    private suspend fun retryChunk(
        handle: SftpFileHandle,
        offset: Long,
        size: Int,
    ): ByteArray? {
        var delayMs = config.retryDelayMs

        repeat(config.maxRetries - 1) { attempt ->
            coroutineContext.ensureActive()
            delay(delayMs)
            delayMs *= 2

            try {
                val result =
                    withTimeout(config.operationTimeoutMs) {
                        sftp.read(handle, offset, size)
                    }
                if (result is SftpResult.Success && result.value != null) {
                    Timber.d("[RETRY] Chunk at $offset succeeded on attempt ${attempt + 2}")
                    return result.value
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("[RETRY] Chunk at $offset attempt ${attempt + 2} failed: ${e.message}")
            }
        }
        return null
    }

    private data class PipelineRequest(
        val offset: Long,
        val deferred: kotlinx.coroutines.Deferred<SftpResult<ByteArray?>>,
        val dispatchTime: Long,
    )
    // === CbsshTransfer-compatible API ===
    // These methods return SftpResult for drop-in replacement of CbsshTransfer

    /**
     * Download to file, returning SftpResult for CbsshTransfer compatibility.
     */
    suspend fun downloadCompat(
        remotePath: String,
        localFile: File,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): SftpResult<Unit> =
        when (val result = download(remotePath, localFile, 0, onProgress)) {
            is TransferResult.Success -> {
                SftpResult.Success(Unit)
            }

            is TransferResult.Failed -> {
                SftpResult.IoError(result.error)
            }

            is TransferResult.Cancelled -> {
                SftpResult.IoError(
                    kotlinx.coroutines.CancellationException("Transfer cancelled"),
                )
            }
        }

    /**
     * Download to stream, returning SftpResult for CbsshTransfer compatibility.
     */
    suspend fun downloadToStreamCompat(
        remotePath: String,
        outputStream: OutputStream,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): SftpResult<Unit> =
        when (val result = downloadToStream(remotePath, outputStream, onProgress)) {
            is TransferResult.Success -> {
                SftpResult.Success(Unit)
            }

            is TransferResult.Failed -> {
                SftpResult.IoError(result.error)
            }

            is TransferResult.Cancelled -> {
                SftpResult.IoError(
                    kotlinx.coroutines.CancellationException("Transfer cancelled"),
                )
            }
        }

    /**
     * Upload file, returning SftpResult for CbsshTransfer compatibility.
     */
    suspend fun uploadCompat(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): SftpResult<Unit> =
        when (val result = upload(localFile, remotePath, 0, onProgress)) {
            is TransferResult.Success -> {
                SftpResult.Success(Unit)
            }

            is TransferResult.Failed -> {
                SftpResult.IoError(result.error)
            }

            is TransferResult.Cancelled -> {
                SftpResult.IoError(
                    kotlinx.coroutines.CancellationException("Transfer cancelled"),
                )
            }
        }
}

// === Extensions ===

private fun SftpResult<*>.toException(): Throwable =
    when (this) {
        is SftpResult.Success -> IllegalStateException("Success is not an exception")
        is SftpResult.ServerError -> IOException("SFTP error $statusCode: $message")
        is SftpResult.ProtocolError -> IOException("Protocol error: $message")
        is SftpResult.IoError -> cause
    }
