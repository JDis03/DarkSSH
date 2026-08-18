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
import kotlinx.coroutines.TimeoutCancellationException
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
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
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
    val initialPipelineDepth: Int = 12,
    /**
     * Min pipeline depth (won't go below this). Even on a very-low-RTT connection (LAN /
     * fast home internet), a floor of 2 left throughput capped near
     * `2 * chunkSize / RTT`. Raised so "low latency" doesn't mean "barely any
     * parallelism" — extra in-flight requests essentially never hurt on a fast link and
     * help absorb per-operation overhead (crypto, JNI, dispatcher) that isn't pure network
     * RTT.
     */
    val minPipelineDepth: Int = 6,
    /**
     * Max pipeline depth (won't exceed this).
     *
     * Was 32, lowered to 16 on 2026-07-23 because the circuit breaker kept firing at
     * depth 32 — but that was an artifact of the RTT measurement including the coroutine
     * pool queueing delay (dispatchTime was taken when the async was *enqueued*, not when
     * the read/write actually started), so the engine was measuring its own self-congestion
     * and sawtoothing between depths. With the measurement fixed (timestamp taken inside
     * the async, right before the SFTP call), depth 32 with 32KB chunks = 1MB in flight —
     * enough to fill a ~7MB/s link at ~150ms RTT — without the runaway panic loop. See the
     * real-device log (2026-08-18): 712MB at 3.4MB/s average with depth oscillating 12<->16
     * and a panic snap, on a link capable of ~7MB/s.
     */
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
    /**
     * Circuit-breaker threshold (ms). A healthy link/server never takes multiple
     * *seconds* to answer a single [chunkSize] read/write — an average RTT this high is
     * the signature of self-induced congestion (our own pipeline depth overwhelming the
     * phone's WiFi radio / CPU / coroutine dispatcher), not real network latency that
     * deeper pipelining would help with. Above this threshold [updateRtt] snaps the
     * depth straight down to [minPipelineDepth] instead of the normal gradual ±1 step,
     * so a runaway (depth pinned at max while RTT climbs into the seconds/tens-of-seconds)
     * actually recovers instead of digging in.
     */
    val panicRttMs: Long = 2000,
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
     * Test-only accessor for [withRetry]. Wraps the private retry helper so
     * unit tests can exercise retry behavior without going through full I/O.
     */
    internal suspend fun <T> withRetryForTest(
        name: String,
        operation: suspend () -> SftpResult<T>,
    ): SftpResult<T> = withRetry(name, operation)

    /**
     * Test-only accessor for [retryChunk]. See [withRetryForTest] for rationale.
     */
    internal suspend fun retryChunkForTest(
        handle: SftpFileHandle,
        offset: Long,
        size: Int,
    ): ByteArray? = retryChunk(handle, offset, size)

    /**
     * Download with Flow-based progress.
     * Returns a Flow that emits progress updates.
     * Collect the flow to drive the download; it completes when transfer finishes.
     *
     * quality-005: el channel se cierra SIEMPRE (éxito, fallo o cancelación) para que el
     * collector nunca quede colgado, y el resultado de la transferencia se propaga
     * (Failed/Cancelled se lanzan al collector).
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
                        try {
                            downloadInternal(remotePath, localFile, resumeFrom) { progress ->
                                channel.trySend(progress)
                            }
                        } finally {
                            channel.close()
                        }
                    }

                // Emit from channel until download completes
                try {
                    for (progress in channel) {
                        emit(progress)
                    }
                    // Channel closed: propagate the transfer result to the collector.
                    when (val result = job.await()) {
                        is TransferResult.Success -> Unit
                        is TransferResult.Failed -> throw result.error
                        is TransferResult.Cancelled ->
                            throw kotlinx.coroutines.CancellationException("Transfer cancelled")
                    }
                } finally {
                    // If the collector cancels, cancel the in-flight transfer.
                    job.cancel()
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
     *
     * quality-005: el channel se cierra SIEMPRE (éxito, fallo o cancelación) para que el
     * collector nunca quede colgado, y el resultado de la transferencia se propaga
     * (Failed/Cancelled se lanzan al collector).
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
                        try {
                            uploadInternal(localFile, remotePath, resumeFrom) { progress ->
                                channel.trySend(progress)
                            }
                        } finally {
                            channel.close()
                        }
                    }

                try {
                    for (progress in channel) {
                        emit(progress)
                    }
                    // Channel closed: propagate the transfer result to the collector.
                    when (val result = job.await()) {
                        is TransferResult.Success -> Unit
                        is TransferResult.Failed -> throw result.error
                        is TransferResult.Cancelled ->
                            throw kotlinx.coroutines.CancellationException("Transfer cancelled")
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
        val startTime = System.currentTimeMillis()

        // quality-002: validar el remoto (stat) y abrirlo ANTES de truncar el archivo local,
        // para que una descarga fallida (remoto inexistente, auth, permission) no destruya
        // una copia local existente (FileOutputStream() trunca al abrir).
        val totalBytes =
            when (val result = statRemoteForDownload(remotePath)) {
                is SftpResult.Success -> result.value
                else ->
                    return TransferResult.Failed(
                        result.toException(),
                        resumeFrom,
                        canResume = false,
                    )
            }

        // Sin trabajo pendiente: no abrir el handle ni tocar el archivo local (contrato
        // verificado por TransferEngineDownloadTest). Único matiz: descargar un remoto de
        // 0 bytes debe producir un archivo local vacío — comportamiento histórico del engine.
        if (resumeFrom >= totalBytes) {
            if (resumeFrom == 0L && totalBytes == 0L) {
                try {
                    localFile.outputStream().close()
                } catch (e: Exception) {
                    Timber.e(e, "[DL] Cannot create empty local file ${localFile.absolutePath}")
                    return TransferResult.Failed(e, 0L, canResume = false)
                }
            }
            return TransferResult.Success(0, 0, 0, 0, 0)
        }

        val handle =
            when (val result = openRemoteForDownload(remotePath)) {
                is SftpResult.Success -> result.value
                else ->
                    return TransferResult.Failed(
                        result.toException(),
                        resumeFrom,
                        canResume = false,
                    )
            }

        val outputStream =
            try {
                if (resumeFrom > 0) {
                    // quality-004: reanudar — NO truncar el archivo local. Recorta la cola
                    // basura si el local es más largo y posiciona el puntero en resumeFrom.
                    openResumableOutputStream(localFile, resumeFrom)
                } else {
                    localFile.outputStream()
                }
            } catch (e: Exception) {
                Timber.e(e, "[DL] Cannot open local file ${localFile.absolutePath}")
                runCatching { sftp.close(handle) }
                return TransferResult.Failed(e, resumeFrom, canResume = false)
            }

        return try {
            downloadChunksToStream(
                handle = handle,
                outputStream = outputStream,
                remotePath = remotePath,
                totalBytes = totalBytes,
                resumeFrom = resumeFrom,
                startTime = startTime,
                onProgress = onProgress,
            )
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

        // quality-002: el remoto se valida/abre antes de escribir nada en el stream, de
        // modo que un fallo (remoto inexistente, auth) no deja el destino a medio escribir.
        val totalBytes =
            when (val result = statRemoteForDownload(remotePath)) {
                is SftpResult.Success -> result.value
                else ->
                    return TransferResult.Failed(
                        result.toException(),
                        resumeFrom,
                        canResume = false,
                    )
            }

        if (resumeFrom >= totalBytes) {
            return TransferResult.Success(0, 0, 0, 0, 0)
        }

        val handle =
            when (val result = openRemoteForDownload(remotePath)) {
                is SftpResult.Success -> result.value
                else ->
                    return TransferResult.Failed(
                        result.toException(),
                        resumeFrom,
                        canResume = false,
                    )
            }

        return downloadChunksToStream(
            handle = handle,
            outputStream = outputStream,
            remotePath = remotePath,
            totalBytes = totalBytes,
            resumeFrom = resumeFrom,
            startTime = startTime,
            onProgress = onProgress,
        )
    }

    /**
     * stat del remoto para descarga (quality-002: validación previa a truncar el local).
     */
    private suspend fun statRemoteForDownload(remotePath: String): SftpResult<Long> =
        when (val result = withRetry("stat") { sftp.stat(remotePath) }) {
            is SftpResult.Success -> SftpResult.Success(result.value.size ?: 0L)
            else -> SftpResult.IoError(result.toException())
        }

    /**
     * open del remoto para descarga (solo después de que stat validó el archivo).
     */
    private suspend fun openRemoteForDownload(remotePath: String): SftpResult<SftpFileHandle> =
        when (val result = withRetry("open") { sftp.open(remotePath, setOf(SftpOpenFlag.READ)) }) {
            is SftpResult.Success -> SftpResult.Success(result.value)
            else -> SftpResult.IoError(result.toException())
        }

    /**
     * quality-004: OutputStream que reanuda una descarga desde [resumeFrom] bytes sin
     * truncar el archivo local: falla si el archivo no existe, recorta cualquier cola más
     * larga que [resumeFrom] (setLength) y posiciona el puntero (seek) para que el loop de
     * chunks escriba secuencialmente desde el punto de reanudación.
     */
    private fun openResumableOutputStream(file: File, resumeFrom: Long): OutputStream {
        if (!file.exists()) {
            throw IOException("Cannot resume download: local file does not exist: ${file.absolutePath}")
        }
        val raf = RandomAccessFile(file, "rw")
        raf.setLength(resumeFrom)
        raf.seek(resumeFrom)
        return object : OutputStream() {
            override fun write(b: Int) {
                raf.write(b)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                raf.write(b, off, len)
            }

            override fun close() {
                raf.close()
            }
        }
    }

    /**
     * quality-004: salta exactamente [n] bytes; InputStream.skip() puede saltar menos
     * (EOF o stream no-seekable). Lanza EOFException si el archivo local es más corto
     * que el punto de reanudación.
     */
    private fun InputStream.skipFully(n: Long) {
        var remaining = n
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            // sin progreso — leer un byte para avanzar (EOF = archivo más corto que resume)
            if (read() == -1) {
                throw EOFException("Local file shorter than resume point ($n bytes)")
            }
            remaining--
        }
    }

    /**
     * Sliding-window download loop escribiendo chunks a [outputStream].
     * Owns closing [handle].
     */
    private suspend fun downloadChunksToStream(
        handle: SftpFileHandle,
        outputStream: OutputStream,
        remotePath: String,
        totalBytes: Long,
        resumeFrom: Long,
        startTime: Long,
        onProgress: ((TransferProgress) -> Unit)?,
    ): TransferResult {
        var bytesWritten = resumeFrom
        var chunksReceived = 0
        var totalRetries = 0

        try {
            Timber.d("[DL] Starting: $remotePath, total=$totalBytes, resume=$resumeFrom, pipeline=$currentPipelineDepth")

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
                            // El timestamp se toma DENTRO del async, justo antes del read real:
                            // medir desde el encolado incluiría la espera de thread del pool de
                            // coroutines, inflando el "RTT" y provocando auto-congestión medida
                            // (sawtooth depth 12<->16 + panics, throughput 3.4MB/s en un link de
                            // ~7MB/s — ver log real 2026-08-18).
                            deferred =
                                async {
                                    val start = System.nanoTime()
                                    sftp.read(handle, offset, chunkSize) to start
                                },
                        ),
                    )
                }

                while (window.isNotEmpty()) {
                    coroutineContext.ensureActive()

                    val request = window.removeFirst()

                    // Await with timeout and retry
                    val chunk =
                        try {
                            val (result, readStartNanos) =
                                withTimeout(config.operationTimeoutMs) {
                                    request.deferred.await()
                                }

                            // Measure RTT (tiempo real del read de red, sin cola de thread) and
                            // adapt pipeline
                            val rttMs = (System.nanoTime() - readStartNanos) / 1_000_000
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
                        } catch (e: TimeoutCancellationException) {
                            // Real per-chunk operation timeout (withTimeout expiry).
                            // MUST be caught before CancellationException — it IS a
                            // CancellationException, so an unguarded rethrow here would
                            // kill the retry-on-timeout path and misreport network stalls
                            // as user cancellation (quality-001).
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
                                deferred =
                                    async {
                                        val start = System.nanoTime()
                                        sftp.read(handle, offset, chunkSize) to start
                                    },
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
            // quality-003: re-lanzar la cancelación para respetar structured concurrency
            // (no convertirla en un resultado). Los finally cierran handle/stream; el
            // caller (SftpViewModel) captura CancellationException en su frontera.
            Timber.d("[DL] Cancelled at $bytesWritten bytes")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "[DL] Failed at $bytesWritten bytes")
            return TransferResult.Failed(e, bytesWritten, canResume = true)
        } finally {
            runCatching { sftp.close(handle) }
        }
    }

    /**
     * Pipelined upload — mirrors [downloadInternal]'s sliding window, but for writes.
     *
     * The original implementation wrote one chunk, awaited the server's ack, *then* read
     * and sent the next — fully serial. Throughput was capped at `chunkSize / RTT`
     * regardless of bandwidth (e.g. ~1MB/s at a 30ms RTT with 32KB chunks), which is why
     * uploads felt slow even on fast connections. Local file reads are cheap/sequential so
     * they still happen synchronously in order, but each chunk's SFTP write is dispatched
     * asynchronously and up to [currentPipelineDepth] writes are kept in flight at once —
     * write order on the wire doesn't matter since every write carries an explicit offset.
     */
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

        Timber.d(
            "[UL] Starting: ${localFile.name} -> $remotePath, total=$totalBytes, " +
                "resume=$resumeFrom, pipeline=$currentPipelineDepth",
        )

        // quality-004: validar que el remoto ya tenga al menos resumeFrom bytes antes de
        // abrirlo en modo WRITE — un remoto más corto dejaría un gap de ceros sin detectar.
        if (resumeFrom > 0) {
            val remoteSize =
                when (val result = withRetry("stat") { sftp.stat(remotePath) }) {
                    is SftpResult.Success -> result.value.size
                    else -> return TransferResult.Failed(
                        result.toException(),
                        bytesWritten,
                        canResume = false,
                    )
                }
            if (remoteSize != null && remoteSize < resumeFrom) {
                return TransferResult.Failed(
                    IOException("Remote file smaller than resume point: $remoteSize < $resumeFrom"),
                    bytesWritten,
                    canResume = false,
                )
            }
        }

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
            val chunkSize = config.chunkSize
            var lastProgressBytes = resumeFrom

            return localFile.inputStream().use { input ->
                if (resumeFrom > 0) {
                    input.skipFully(resumeFrom) // quality-004: skip parcial ya no es posible
                }

                var readOffset = resumeFrom
                var eof = false
                val window = ArrayDeque<PipelineWriteRequest>(config.maxPipelineDepth)

                coroutineScope {
                    // Reads the next chunk synchronously (fast local disk I/O — no need to
                    // pipeline reads) and dispatches its SFTP write asynchronously. Each
                    // chunk gets its own freshly-allocated buffer since several writes can
                    // be in flight at once (unlike the old serial loop, reusing one shared
                    // buffer would let an in-flight write see data overwritten by the next
                    // read).
                    fun dispatchNextWrite() {
                        if (eof) return
                        val buf = ByteArray(chunkSize)
                        val read = input.read(buf)
                        if (read <= 0) {
                            eof = true
                            return
                        }
                        val data = if (read == buf.size) buf else buf.copyOf(read)
                        val offset = readOffset
                        readOffset += read
                        window.addLast(
                            PipelineWriteRequest(
                                offset = offset,
                                size = read,
                                data = data,
                                // timestamp dentro del async — RTT real del write, sin cola de thread
                                deferred =
                                    async {
                                        val start = System.nanoTime()
                                        sftp.write(handle, offset, data) to start
                                    },
                            ),
                        )
                    }

                    // Pre-fill pipeline
                    while (window.size < currentPipelineDepth && !eof) {
                        dispatchNextWrite()
                    }

                    while (window.isNotEmpty()) {
                        coroutineContext.ensureActive()

                        val request = window.removeFirst()

                        val ok =
                            try {
                                val (result, writeStartNanos) =
                                    withTimeout(config.operationTimeoutMs) {
                                        request.deferred.await()
                                    }

                                val rttMs = (System.nanoTime() - writeStartNanos) / 1_000_000
                                updateRtt(rttMs)

                                when (result) {
                                    is SftpResult.Success -> true

                                    is SftpResult.IoError -> {
                                        val retried = retryWriteChunk(handle, request.offset, request.data)
                                        if (retried) totalRetries++
                                        retried
                                    }

                                    else -> {
                                        return@coroutineScope TransferResult.Failed(
                                            result.toException(),
                                            bytesWritten,
                                            canResume = true,
                                        )
                                    }
                                }
                            } catch (e: TimeoutCancellationException) {
                                // Real per-chunk write timeout — retry, don't rethrow as
                                // cancellation (quality-001, see download loop).
                                val retried = retryWriteChunk(handle, request.offset, request.data)
                                if (retried) totalRetries++
                                retried
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                val retried = retryWriteChunk(handle, request.offset, request.data)
                                if (retried) totalRetries++
                                retried
                            }

                        if (!ok) {
                            return@coroutineScope TransferResult.Failed(
                                IOException("Write failed at offset ${request.offset} after retries"),
                                bytesWritten,
                                canResume = true,
                            )
                        }

                        bytesWritten += request.size
                        chunksWritten++

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

                        // Refill pipeline (adaptive depth)
                        while (window.size < currentPipelineDepth && !eof) {
                            dispatchNextWrite()
                        }
                    }

                    val elapsed = System.currentTimeMillis() - startTime
                    val speedKBps = if (elapsed > 0) (bytesWritten - resumeFrom) * 1000 / elapsed / 1024 else 0

                    Timber.d(
                        "[UL] Complete: ${bytesWritten - resumeFrom}B in ${elapsed}ms = " +
                            "${speedKBps}KB/s, retries=$totalRetries, finalDepth=$currentPipelineDepth",
                    )

                    TransferResult.Success(
                        bytesTransferred = bytesWritten - resumeFrom,
                        durationMs = elapsed,
                        avgSpeedKBps = speedKBps,
                        chunksTransferred = chunksWritten,
                        retriesUsed = totalRetries,
                    )
                }
            }
        } catch (e: CancellationException) {
            // quality-003: re-lanzar la cancelación (ver downloadChunksToStream).
            Timber.d("[UL] Cancelled at $bytesWritten bytes")
            throw e
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

        // Circuit breaker: an average RTT past config.panicRttMs means the pipeline is
        // congesting itself, not that the network needs deeper pipelining (see its doc).
        // Snap down immediately instead of running the normal gradual adjustment below —
        // crawling down by 1 per sample from depth 32 would keep hammering an already
        // self-congested link for many more samples before relief arrives.
        if (avgRttMs >= config.panicRttMs) {
            if (currentPipelineDepth > config.minPipelineDepth) {
                Timber.w(
                    "[ADAPTIVE] avgRtt=${avgRttMs}ms exceeds panic threshold " +
                        "(${config.panicRttMs}ms) — backing off depth=$currentPipelineDepth " +
                        "-> ${config.minPipelineDepth}",
                )
            }
            currentPipelineDepth = config.minPipelineDepth
            return
        }

        // Adapt pipeline depth based on measured RTT (real read/write time, no queueing —
        // the timestamp is taken inside the async; see the loop dispatch comments).
        // High latency = deeper pipeline, low latency = shallower — but even "low
        // latency" still benefits from real parallelism, so the floor is
        // config.minPipelineDepth (see its doc) rather than a near-serial depth of 2.
        //
        // Calibration (2026-08-18, real-device log): a ~7MB/s WiFi link with RTT ~40-60ms
        // needs >= 12 chunks of 32KB in flight to fill (BDP ≈ 280-420KB). Buckets are
        // sized so the engine reaches a depth that actually fills the link, with margin.
        val targetDepth =
            when {
                avgRttMs < 30 -> 12

                avgRttMs < 60 -> 16

                avgRttMs < 120 -> 24

                else -> config.maxPipelineDepth // Genuinely high latency (sub-panic)
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
            } catch (e: TimeoutCancellationException) {
                // withTimeout expiry = transient stall, retryable (quality-001: without
                // this explicit catch the exception was rethrown as a CancellationException,
                // making every timeout look like user cancellation and skipping the retry).
                lastError = SftpResult.IoError(e)
                if (attempt < config.maxRetries - 1) {
                    Timber.w("[$name] Timeout attempt ${attempt + 1}, retry in ${delayMs}ms")
                    delay(delayMs)
                    delayMs *= 2
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
            } catch (e: TimeoutCancellationException) {
                // Transient read timeout — keep trying on the next attempt (quality-001).
                Timber.w("[RETRY] Chunk at $offset attempt ${attempt + 2} timed out")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("[RETRY] Chunk at $offset attempt ${attempt + 2} failed: ${e.message}")
            }
        }
        return null
    }

    /** Write-side counterpart of [retryChunk], used by the pipelined upload path. */
    private suspend fun retryWriteChunk(
        handle: SftpFileHandle,
        offset: Long,
        data: ByteArray,
    ): Boolean {
        var delayMs = config.retryDelayMs

        repeat(config.maxRetries - 1) { attempt ->
            coroutineContext.ensureActive()
            delay(delayMs)
            delayMs *= 2

            try {
                val result =
                    withTimeout(config.operationTimeoutMs) {
                        sftp.write(handle, offset, data)
                    }
                if (result is SftpResult.Success) {
                    Timber.d("[RETRY] Write chunk at $offset succeeded on attempt ${attempt + 2}")
                    return true
                }
            } catch (e: TimeoutCancellationException) {
                // Transient write timeout — keep trying on the next attempt (quality-001).
                Timber.w("[RETRY] Write chunk at $offset attempt ${attempt + 2} timed out")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("[RETRY] Write chunk at $offset attempt ${attempt + 2} failed: ${e.message}")
            }
        }
        return false
    }

    private data class PipelineRequest(
        val offset: Long,
        /** (resultado del read, nanoTime al INICIAR el read real dentro del async). */
        val deferred: kotlinx.coroutines.Deferred<Pair<SftpResult<ByteArray?>, Long>>,
    )

    private data class PipelineWriteRequest(
        val offset: Long,
        val size: Int,
        /** Kept so a failed write can be retried without re-reading the local file. */
        val data: ByteArray,
        /** (resultado del write, nanoTime al INICIAR el write real dentro del async). */
        val deferred: kotlinx.coroutines.Deferred<Pair<SftpResult<Unit>, Long>>,
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
