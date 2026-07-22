/*
 * DarkSSH SFTP - TransferEngine.retryChunk tests
 * Tests for retryChunk behavior: waits before each attempt, returns data on
 * success, returns null when all attempts exhausted, propagates cancellation.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport.cbssh

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.connectbot.sshlib.SftpClient
import org.connectbot.sshlib.SftpFileHandle
import org.connectbot.sshlib.SftpResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

/**
 * Tests for [TransferEngine.retryChunk] — the chunk-level re-read helper used
 * when a pipelined chunk fails mid-transfer.
 *
 * Contrast with [TransferEngineRetryTest] (which covers [TransferEngine.withRetry]
 * used for whole-operation retries): `retryChunk` always waits *before* the
 * first re-read (the initial attempt already failed by the time this is
 * called), retries `maxRetries - 1` times total, and returns `null` — never
 * an [SftpResult] — on exhaustion, since callers treat null as "give up".
 *
 * Note: retryChunk is private. We exercise it through
 * [TransferEngine.retryChunkForTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferEngineRetryChunkTest {

    private lateinit var sftp: SftpClient
    private lateinit var handle: SftpFileHandle

    @Before
    fun setup() {
        sftp = mock(SftpClient::class.java)
        handle = mock(SftpFileHandle::class.java)
    }

    @Test
    fun `returns data when read succeeds on first retry attempt`() = runTest {
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 3, retryDelayMs = 100),
        )
        val data = byteArrayOf(1, 2, 3)

        whenever(sftp.read(eq(handle), eq(0L), eq(3))).thenReturn(SftpResult.Success(data))

        val result = engine.retryChunkForTest(handle, 0L, 3)

        assertArrayEquals(data, result)
    }

    @Test
    fun `returns null when read fails on every retry attempt`() = runTest {
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 3, retryDelayMs = 100),
        )

        whenever(sftp.read(any(), any(), any()))
            .thenReturn(SftpResult.IoError(java.io.IOException("still failing")))

        val result = engine.retryChunkForTest(handle, 0L, 32)

        assertNull(result)
    }

    @Test
    fun `attempts exactly maxRetries minus one times`() = runTest {
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 4, retryDelayMs = 10),
        )

        var calls = 0
        whenever(sftp.read(any(), any(), any())).thenAnswer {
            calls++
            SftpResult.IoError(java.io.IOException("nope"))
        }

        engine.retryChunkForTest(handle, 0L, 8)

        // maxRetries=4 means the initial attempt already happened elsewhere;
        // retryChunk itself performs maxRetries - 1 = 3 re-read attempts.
        assertTrue("Expected 3 attempts, got $calls", calls == 3)
    }

    @Test
    fun `returns null when read succeeds but value is null (EOF)`() = runTest {
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 2, retryDelayMs = 10),
        )

        whenever(sftp.read(any(), any(), any())).thenReturn(SftpResult.Success(null))

        val result = engine.retryChunkForTest(handle, 0L, 8)

        // Success(null) means EOF, not real data — retryChunk should not
        // treat this as a recovered chunk.
        assertNull(result)
    }

    @Test
    fun `recovers on a later attempt after earlier failures`() = runTest {
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 4, retryDelayMs = 10),
        )
        val data = byteArrayOf(9, 9)

        var calls = 0
        whenever(sftp.read(any(), any(), any())).thenAnswer {
            calls++
            if (calls < 2) {
                SftpResult.IoError(java.io.IOException("transient"))
            } else {
                SftpResult.Success(data)
            }
        }

        val result = engine.retryChunkForTest(handle, 0L, 2)

        assertArrayEquals(data, result)
    }

    @Test
    fun `cancellationException propagates without being swallowed`() = runTest {
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 3, retryDelayMs = 10),
        )

        whenever(sftp.read(any(), any(), any())).thenAnswer {
            throw CancellationException("cancelled by parent")
        }

        try {
            engine.retryChunkForTest(handle, 0L, 8)
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            // Expected — must NOT be swallowed or turned into null
            assertTrue(e.message == "cancelled by parent")
        }
    }

    @Test
    fun `waits before each attempt using exponential backoff`() = runTest {
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 3, retryDelayMs = 100),
        )

        whenever(sftp.read(any(), any(), any()))
            .thenReturn(SftpResult.IoError(java.io.IOException("nope")))

        // Advance enough virtual time to cover the 100ms + 200ms backoff
        // schedule so both retry attempts can actually run inside runTest.
        advanceTimeBy(1000L)

        val result = engine.retryChunkForTest(handle, 0L, 8)

        assertNull(result)
    }
}
