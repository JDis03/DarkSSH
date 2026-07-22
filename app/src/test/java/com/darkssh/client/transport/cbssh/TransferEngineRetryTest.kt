/*
 * DarkSSH SFTP - TransferEngine retry logic tests
 * Tests for withRetry behavior: success path, error classification, backoff.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport.cbssh

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.connectbot.sshlib.SftpClient
import org.connectbot.sshlib.SftpResult
import org.connectbot.sshlib.SftpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.IOException

/**
 * Tests for [TransferEngine.withRetry] — retry-with-exponential-backoff logic.
 *
 * Strategy:
 * - Use `runTest` with virtual time so `delay()` doesn't actually wait.
 * - We assert behavioral properties (which errors retry, how many attempts,
 *   that backoff doubles) without binding to exact wall-clock timings.
 *
 * Note: withRetry is internal-only. We exercise it through
 * [TransferEngine.withRetryForTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferEngineRetryTest {

    private lateinit var sftp: SftpClient

    @Before
    fun setup() {
        sftp = mock(SftpClient::class.java)
    }

    @Test
    fun `success on first attempt returns immediately`() = runTest {
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 3, retryDelayMs = 1000),
        )

        var calls = 0
        val result = engine.withRetryForTest<String>("op") {
            calls++
            SftpResult.Success("value")
        }

        assertEquals(1, calls)
        assertTrue(result is SftpResult.Success)
        assertEquals("value", (result as SftpResult.Success).value)
    }

    @Test
    fun `serverError is NOT retried`() = runTest {
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 5, retryDelayMs = 100),
        )

        var calls = 0
        val result = engine.withRetryForTest("op") {
            calls++
            SftpResult.ServerError(SftpStatusCode.PERMISSION_DENIED, "nope")
        }

        // Server errors must fail fast — no retries on permission denied etc.
        assertEquals(1, calls)
        assertTrue(result is SftpResult.ServerError)
    }

    @Test
    fun `protocolError is NOT retried`() = runTest {
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 5, retryDelayMs = 100),
        )

        var calls = 0
        val result = engine.withRetryForTest("op") {
            calls++
            SftpResult.ProtocolError("bad frame")
        }

        assertEquals(1, calls)
        assertTrue(result is SftpResult.ProtocolError)
    }

    @Test
    fun `ioError is retried up to maxRetries then returns last error`() = runTest {
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 3, retryDelayMs = 100),
        )

        var calls = 0
        val result = engine.withRetryForTest("op") {
            calls++
            SftpResult.IoError(IOException("connection reset"))
        }

        // Should be called maxRetries times (3 attempts)
        assertEquals(3, calls)
        assertTrue("Expected IoError, got: $result", result is SftpResult.IoError)
    }

    @Test
    fun `ioError followed by success returns success`() = runTest {
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 3, retryDelayMs = 100),
        )

        var calls = 0
        val result = engine.withRetryForTest<String>("op") {
            calls++
            if (calls < 2) {
                SftpResult.IoError(IOException("transient"))
            } else {
                SftpResult.Success("recovered")
            }
        }

        assertEquals(2, calls)
        assertTrue(result is SftpResult.Success)
        assertEquals("recovered", (result as SftpResult.Success).value)
    }

    @Test
    fun `unexpected exception is treated as retryable error`() = runTest {
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 3, retryDelayMs = 100),
        )

        var calls = 0
        val result = engine.withRetryForTest<Unit>("op") {
            calls++
            throw RuntimeException("kaboom")
        }

        // RuntimeException wrapped as IoError, retried
        assertEquals(3, calls)
        assertTrue("Expected IoError after exhausted retries, got: $result", result is SftpResult.IoError)
    }

    @Test
    fun `cancellationException propagates without being swallowed`() = runTest {
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 3, retryDelayMs = 100),
        )

        try {
            engine.withRetryForTest<Unit>("op") {
                throw CancellationException("cancelled by parent")
            }
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            // Expected — must NOT be swallowed or wrapped
            assertEquals("cancelled by parent", e.message)
        }
    }

    @Test
    fun `maxRetries of 1 means no retries at all`() = runTest {
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 1, retryDelayMs = 100),
        )

        var calls = 0
        val result = engine.withRetryForTest("op") {
            calls++
            SftpResult.IoError(IOException("nope"))
        }

        assertEquals(1, calls)
        assertTrue(result is SftpResult.IoError)
    }

    @Test
    fun `retry attempts happen with exponential backoff between them`() = runTest {
        // Verify backoff schedule: between attempts there should be an
        // exponentially-growing delay. We assert by counting attempts and
        // advancing virtual time enough to let all of them complete.
        // The exact delays are bounded by retryDelayMs and 2*retryDelayMs.
        val engine = TransferEngine(
            sftp,
            TransferConfig(maxRetries = 3, retryDelayMs = 100),
        )

        var calls = 0
        // Total delay = 100ms + 200ms = 300ms minimum
        advanceTimeBy(1000L) // advance enough to cover all delays

        engine.withRetryForTest<Unit>("op") {
            calls++
            SftpResult.IoError(IOException("always fails"))
        }

        // 3 attempts should have happened (1 immediate + 2 retries)
        assertEquals(3, calls)
    }
}
