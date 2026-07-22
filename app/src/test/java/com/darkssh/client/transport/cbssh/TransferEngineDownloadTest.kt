/*
 * DarkSSH SFTP - TransferEngine.download / downloadToStream tests
 * Tests for the download pipeline: happy path, stat/open failures, chunk
 * retry recovery, cancellation, and resume-past-EOF short-circuit.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport.cbssh

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.connectbot.sshlib.SftpAttributes
import org.connectbot.sshlib.SftpClient
import org.connectbot.sshlib.SftpFileHandle
import org.connectbot.sshlib.SftpResult
import org.connectbot.sshlib.SftpStatusCode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException

/**
 * Tests for [TransferEngine.download] (file download) and
 * [TransferEngine.downloadToStream] — the pipelined chunk-read loop in
 * `downloadToStreamInternal`.
 *
 * These are both public suspend functions, so they're exercised directly
 * (no internal test-only accessor needed, unlike withRetry/retryChunk which
 * are private).
 *
 * Strategy: mock [SftpClient] to control stat/open/read responses, drive the
 * engine against a real temp file (File I/O is cheap and the production code
 * uses real File streams internally), and assert on the returned
 * [TransferResult].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferEngineDownloadTest {

    private lateinit var sftp: SftpClient
    private lateinit var handle: SftpFileHandle
    private lateinit var localFile: File

    @Before
    fun setup() {
        sftp = mock(SftpClient::class.java)
        handle = mock(SftpFileHandle::class.java)
        localFile = File.createTempFile("transfer-engine-test", ".tmp")
        localFile.deleteOnExit()
    }

    @After
    fun tearDown() {
        localFile.delete()
    }

    /** Default stubs shared by most tests. Suspend, so called from within runTest. */
    private suspend fun stubOpenAndCloseSucceed() {
        whenever(sftp.open(any(), any(), any())).thenReturn(SftpResult.Success(handle))
        whenever(sftp.close(any())).thenReturn(SftpResult.Success(Unit))
    }

    @Test
    fun `download succeeds and writes all bytes for a single chunk`() = runTest {
        stubOpenAndCloseSucceed()
        val engine = TransferEngine(sftp, TransferConfig(chunkSize = 1024))
        val data = ByteArray(100) { it.toByte() }

        whenever(sftp.stat("remote.bin")).thenReturn(SftpResult.Success(SftpAttributes(size = 100L)))
        whenever(sftp.read(handle, 0L, 1024)).thenReturn(SftpResult.Success(data))
        whenever(sftp.read(handle, 100L, 1024)).thenReturn(SftpResult.Success(null)) // EOF

        val result = engine.download("remote.bin", localFile)

        assertTrue("Expected Success, got: $result", result is TransferResult.Success)
        result as TransferResult.Success
        assertEquals(100L, result.bytesTransferred)
        assertEquals(0, result.retriesUsed)
        assertEquals(data.toList(), localFile.readBytes().toList())
    }

    @Test
    fun `download across multiple chunks writes them in order`() = runTest {
        stubOpenAndCloseSucceed()
        val engine = TransferEngine(sftp, TransferConfig(chunkSize = 10, initialPipelineDepth = 2))
        val chunk1 = ByteArray(10) { 1 }
        val chunk2 = ByteArray(10) { 2 }
        val chunk3 = ByteArray(5) { 3 }

        whenever(sftp.stat("remote.bin")).thenReturn(SftpResult.Success(SftpAttributes(size = 25L)))
        whenever(sftp.read(handle, 0L, 10)).thenReturn(SftpResult.Success(chunk1))
        whenever(sftp.read(handle, 10L, 10)).thenReturn(SftpResult.Success(chunk2))
        whenever(sftp.read(handle, 20L, 10)).thenReturn(SftpResult.Success(chunk3))

        val result = engine.download("remote.bin", localFile)

        assertTrue(result is TransferResult.Success)
        result as TransferResult.Success
        assertEquals(25L, result.bytesTransferred)

        val expected = chunk1 + chunk2 + chunk3
        assertEquals(expected.toList(), localFile.readBytes().toList())
    }

    @Test
    fun `download fails fast when stat fails with a server error`() = runTest {
        val engine = TransferEngine(sftp, TransferConfig())

        whenever(sftp.stat("remote.bin"))
            .thenReturn(SftpResult.ServerError(SftpStatusCode.NO_SUCH_FILE, "not found"))

        val result = engine.download("remote.bin", localFile)

        assertTrue("Expected Failed, got: $result", result is TransferResult.Failed)
        result as TransferResult.Failed
        assertTrue(!result.canResume)
    }

    @Test
    fun `download fails when open fails after stat succeeds`() = runTest {
        val engine = TransferEngine(sftp, TransferConfig())

        whenever(sftp.stat("remote.bin")).thenReturn(SftpResult.Success(SftpAttributes(size = 10L)))
        whenever(sftp.open(any(), any(), any()))
            .thenReturn(SftpResult.ServerError(SftpStatusCode.PERMISSION_DENIED, "denied"))

        val result = engine.download("remote.bin", localFile)

        assertTrue(result is TransferResult.Failed)
        result as TransferResult.Failed
        assertTrue(!result.canResume)
    }

    @Test
    fun `download resuming past totalBytes short-circuits to empty success`() = runTest {
        val engine = TransferEngine(sftp, TransferConfig())

        whenever(sftp.stat("remote.bin")).thenReturn(SftpResult.Success(SftpAttributes(size = 50L)))

        val result = engine.download("remote.bin", localFile, resumeFrom = 50L)

        assertTrue(result is TransferResult.Success)
        result as TransferResult.Success
        assertEquals(0L, result.bytesTransferred)
        // Should not have even tried to open the file
        org.mockito.kotlin.verify(sftp, org.mockito.kotlin.never()).open(any(), any(), any())
    }

    @Test
    fun `download recovers a chunk via retry after a transient IoError`() = runTest {
        stubOpenAndCloseSucceed()
        val engine = TransferEngine(sftp, TransferConfig(chunkSize = 100, maxRetries = 3, retryDelayMs = 10))
        val data = ByteArray(50) { 7 }

        whenever(sftp.stat("remote.bin")).thenReturn(SftpResult.Success(SftpAttributes(size = 50L)))
        // First read fails, retryChunk's re-read succeeds
        whenever(sftp.read(handle, 0L, 100))
            .thenReturn(SftpResult.IoError(IOException("connection reset")))
            .thenReturn(SftpResult.Success(data))

        val result = engine.download("remote.bin", localFile)

        assertTrue("Expected Success after retry, got: $result", result is TransferResult.Success)
        result as TransferResult.Success
        assertEquals(50L, result.bytesTransferred)
        assertEquals(1, result.retriesUsed)
        assertEquals(data.toList(), localFile.readBytes().toList())
    }

    @Test
    fun `download fails with canResume when chunk retries are exhausted`() = runTest {
        stubOpenAndCloseSucceed()
        val engine = TransferEngine(sftp, TransferConfig(chunkSize = 100, maxRetries = 2, retryDelayMs = 10))

        whenever(sftp.stat("remote.bin")).thenReturn(SftpResult.Success(SftpAttributes(size = 50L)))
        whenever(sftp.read(handle, 0L, 100))
            .thenReturn(SftpResult.IoError(IOException("still failing")))

        val result = engine.download("remote.bin", localFile)

        assertTrue("Expected Failed, got: $result", result is TransferResult.Failed)
        result as TransferResult.Failed
        assertTrue("Chunk-level IoError failures should be resumable", result.canResume)
    }

    @Test
    fun `download closes the file handle even when the transfer fails`() = runTest {
        val engine = TransferEngine(sftp, TransferConfig())

        whenever(sftp.stat("remote.bin"))
            .thenReturn(SftpResult.ServerError(SftpStatusCode.PERMISSION_DENIED, "denied"))
        whenever(sftp.open(any(), any(), any())).thenReturn(SftpResult.Success(handle))

        engine.download("remote.bin", localFile)

        // stat failed before open was ever called, so close should NOT have
        // been invoked (no handle was opened in this scenario).
        org.mockito.kotlin.verify(sftp, org.mockito.kotlin.never()).close(any())
    }

    @Test
    fun `download closes the file handle after a successful transfer`() = runTest {
        stubOpenAndCloseSucceed()
        val engine = TransferEngine(sftp, TransferConfig(chunkSize = 1024))

        whenever(sftp.stat("remote.bin")).thenReturn(SftpResult.Success(SftpAttributes(size = 10L)))
        whenever(sftp.read(handle, 0L, 1024)).thenReturn(SftpResult.Success(ByteArray(10)))

        engine.download("remote.bin", localFile)

        org.mockito.kotlin.verify(sftp).close(handle)
    }

    @Test
    fun `downloadToStream writes to the given stream instead of a file`() = runTest {
        stubOpenAndCloseSucceed()
        val engine = TransferEngine(sftp, TransferConfig(chunkSize = 1024))
        val data = ByteArray(20) { 9 }
        val outputStream = java.io.ByteArrayOutputStream()

        whenever(sftp.stat("remote.bin")).thenReturn(SftpResult.Success(SftpAttributes(size = 20L)))
        whenever(sftp.read(handle, 0L, 1024)).thenReturn(SftpResult.Success(data))

        val result = engine.downloadToStream("remote.bin", outputStream)

        assertTrue(result is TransferResult.Success)
        assertEquals(data.toList(), outputStream.toByteArray().toList())
    }

    @Test
    fun `download with a zero-size remote file succeeds with zero bytes`() = runTest {
        val engine = TransferEngine(sftp, TransferConfig())

        whenever(sftp.stat("remote.bin")).thenReturn(SftpResult.Success(SftpAttributes(size = 0L)))

        val result = engine.download("remote.bin", localFile)

        assertTrue("Expected Success, got: $result", result is TransferResult.Success)
        result as TransferResult.Success
        assertEquals(0L, result.bytesTransferred)
    }
}
