/*
 * DarkSSH SFTP - TransferEngine.upload tests
 * Tests for the upload pipeline: happy path, missing local file, open
 * failures, write retry-and-exhaustion, and resume-past-EOF short-circuit.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport.cbssh

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException

/**
 * Tests for [TransferEngine.upload] — the write loop in `uploadInternal`.
 *
 * Public suspend function, exercised directly (no internal accessor needed).
 * Strategy mirrors [TransferEngineDownloadTest]: mock [SftpClient] to control
 * open/write responses, drive the engine against a real temp file containing
 * known bytes, and assert on the returned [TransferResult].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferEngineUploadTest {

    private lateinit var sftp: SftpClient
    private lateinit var handle: SftpFileHandle
    private lateinit var localFile: File

    @Before
    fun setup() {
        sftp = mock(SftpClient::class.java)
        handle = mock(SftpFileHandle::class.java)
        localFile = File.createTempFile("transfer-engine-upload-test", ".tmp")
        localFile.deleteOnExit()
    }

    @After
    fun tearDown() {
        localFile.delete()
    }

    private suspend fun stubOpenAndCloseSucceed() {
        whenever(sftp.open(any(), any(), any())).thenReturn(SftpResult.Success(handle))
        whenever(sftp.close(any())).thenReturn(SftpResult.Success(Unit))
    }

    @Test
    fun `upload succeeds and sends all bytes for a single chunk`() = runTest {
        stubOpenAndCloseSucceed()
        val data = ByteArray(50) { it.toByte() }
        localFile.writeBytes(data)

        whenever(sftp.write(eq(handle), any(), any())).thenReturn(SftpResult.Success(Unit))

        val engine = TransferEngine(sftp, TransferConfig(chunkSize = 1024))
        val result = engine.upload(localFile, "remote.bin")

        assertTrue("Expected Success, got: $result", result is TransferResult.Success)
        result as TransferResult.Success
        assertEquals(50L, result.bytesTransferred)
        assertEquals(0, result.retriesUsed)
    }

    @Test
    fun `upload across multiple chunks sends them all`() = runTest {
        stubOpenAndCloseSucceed()
        val data = ByteArray(25) { it.toByte() } // 3 chunks of size 10: 10,10,5
        localFile.writeBytes(data)

        whenever(sftp.write(eq(handle), any(), any())).thenReturn(SftpResult.Success(Unit))

        val engine = TransferEngine(sftp, TransferConfig(chunkSize = 10))
        val result = engine.upload(localFile, "remote.bin")

        assertTrue(result is TransferResult.Success)
        result as TransferResult.Success
        assertEquals(25L, result.bytesTransferred)
        assertEquals(3, result.chunksTransferred)

        // Verify writes happened at the expected offsets
        verify(sftp).write(eq(handle), eq(0L), any())
        verify(sftp).write(eq(handle), eq(10L), any())
        verify(sftp).write(eq(handle), eq(20L), any())
    }

    @Test
    fun `upload fails fast when local file does not exist`() = runTest {
        val missingFile = File(localFile.parentFile, "does-not-exist-${System.nanoTime()}.tmp")
        val engine = TransferEngine(sftp, TransferConfig())

        val result = engine.upload(missingFile, "remote.bin")

        assertTrue("Expected Failed, got: $result", result is TransferResult.Failed)
        result as TransferResult.Failed
        assertTrue(!result.canResume)
        verify(sftp, never()).open(any(), any(), any())
    }

    @Test
    fun `upload fails when remote open fails`() = runTest {
        localFile.writeBytes(ByteArray(10))
        whenever(sftp.open(any(), any(), any()))
            .thenReturn(SftpResult.ServerError(SftpStatusCode.PERMISSION_DENIED, "denied"))

        val engine = TransferEngine(sftp, TransferConfig())
        val result = engine.upload(localFile, "remote.bin")

        assertTrue(result is TransferResult.Failed)
        result as TransferResult.Failed
        assertTrue(!result.canResume)
    }

    @Test
    fun `upload with an empty local file succeeds with zero bytes`() = runTest {
        // localFile is already empty (freshly created temp file)
        val engine = TransferEngine(sftp, TransferConfig())

        val result = engine.upload(localFile, "remote.bin")

        assertTrue("Expected Success, got: $result", result is TransferResult.Success)
        result as TransferResult.Success
        assertEquals(0L, result.bytesTransferred)
        // resumeFrom (0) >= totalBytes (0) short-circuits before ever opening
        verify(sftp, never()).open(any(), any(), any())
    }

    @Test
    fun `upload retries a failed write and succeeds on a later attempt`() = runTest {
        stubOpenAndCloseSucceed()
        localFile.writeBytes(ByteArray(10))

        whenever(sftp.write(eq(handle), any(), any()))
            .thenReturn(SftpResult.IoError(IOException("transient")))
            .thenReturn(SftpResult.Success(Unit))

        val engine = TransferEngine(sftp, TransferConfig(chunkSize = 1024, maxRetries = 3, retryDelayMs = 10))
        val result = engine.upload(localFile, "remote.bin")

        assertTrue("Expected Success after retry, got: $result", result is TransferResult.Success)
        result as TransferResult.Success
        assertEquals(10L, result.bytesTransferred)
        assertEquals(1, result.retriesUsed)
    }

    @Test
    fun `upload fails with canResume when write retries are exhausted`() = runTest {
        stubOpenAndCloseSucceed()
        localFile.writeBytes(ByteArray(10))

        whenever(sftp.write(eq(handle), any(), any()))
            .thenReturn(SftpResult.IoError(IOException("still failing")))

        val engine = TransferEngine(sftp, TransferConfig(chunkSize = 1024, maxRetries = 2, retryDelayMs = 10))
        val result = engine.upload(localFile, "remote.bin")

        assertTrue("Expected Failed, got: $result", result is TransferResult.Failed)
        result as TransferResult.Failed
        assertTrue("Write failures should be resumable", result.canResume)
    }

    @Test
    fun `upload fails fast on a server error without retrying`() = runTest {
        stubOpenAndCloseSucceed()
        localFile.writeBytes(ByteArray(10))

        whenever(sftp.write(eq(handle), any(), any()))
            .thenReturn(SftpResult.ServerError(SftpStatusCode.PERMISSION_DENIED, "quota exceeded"))

        val engine = TransferEngine(sftp, TransferConfig(chunkSize = 1024, maxRetries = 3, retryDelayMs = 10))
        val result = engine.upload(localFile, "remote.bin")

        assertTrue(result is TransferResult.Failed)
        result as TransferResult.Failed
        // Only 1 attempt: ServerError inside the write loop is not retried
        verify(sftp, org.mockito.kotlin.times(1)).write(eq(handle), any(), any())
    }

    @Test
    fun `upload closes the remote handle after a successful transfer`() = runTest {
        stubOpenAndCloseSucceed()
        localFile.writeBytes(ByteArray(10))

        whenever(sftp.write(eq(handle), any(), any())).thenReturn(SftpResult.Success(Unit))

        val engine = TransferEngine(sftp, TransferConfig())
        engine.upload(localFile, "remote.bin")

        verify(sftp).close(handle)
    }

    @Test
    fun `upload uses create-truncate flags for a fresh upload (resumeFrom 0)`() = runTest {
        stubOpenAndCloseSucceed()
        localFile.writeBytes(ByteArray(10))
        whenever(sftp.write(eq(handle), any(), any())).thenReturn(SftpResult.Success(Unit))

        val engine = TransferEngine(sftp, TransferConfig())
        engine.upload(localFile, "remote.bin", resumeFrom = 0)

        verify(sftp).open(
            eq("remote.bin"),
            eq(
                setOf(
                    org.connectbot.sshlib.SftpOpenFlag.WRITE,
                    org.connectbot.sshlib.SftpOpenFlag.CREATE,
                    org.connectbot.sshlib.SftpOpenFlag.TRUNCATE,
                ),
            ),
            any(),
        )
    }

    @Test
    fun `upload uses write-only flags when resuming (resumeFrom greater than 0)`() = runTest {
        stubOpenAndCloseSucceed()
        localFile.writeBytes(ByteArray(20))
        whenever(sftp.write(eq(handle), any(), any())).thenReturn(SftpResult.Success(Unit))

        val engine = TransferEngine(sftp, TransferConfig())
        engine.upload(localFile, "remote.bin", resumeFrom = 10)

        verify(sftp).open(
            eq("remote.bin"),
            eq(setOf(org.connectbot.sshlib.SftpOpenFlag.WRITE)),
            any(),
        )
    }
}
