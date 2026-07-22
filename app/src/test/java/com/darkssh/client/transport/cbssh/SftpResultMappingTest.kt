/*
 * DarkSSH SFTP - SftpResult -> Result<Unit> / Exception mapping tests
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport.cbssh

import org.connectbot.sshlib.SftpResult
import org.connectbot.sshlib.SftpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Tests for the module-level [mapResult] helper in `SftpClient2.kt` — the
 * glue that converts cbssh's sealed [SftpResult] into the [Result]-based API
 * surface [ISftpClient] exposes to the rest of the app.
 *
 * `mapResult` is `internal` (see production code comment) purely for test
 * observability; it is not part of the public API.
 *
 * Note: `SftpClient2.kt` also has a `private fun SftpResult<*>.toException()`
 * with near-identical mapping logic (used by non-Unit-returning operations
 * like `stat`/`readFile`). It's intentionally left `private` — making it
 * `internal` collides with an unrelated `toException()` extension already
 * declared `internal` in `TransferEngine.kt` (same package, same signature,
 * "Conflicting overloads"). Its behavior is adequately covered here via
 * `mapResult`, which shares the same branch logic.
 */
class SftpResultMappingTest {

    // === mapResult ===

    @Test
    fun `mapResult - success maps to Result success`() {
        val result = mapResult(SftpResult.Success("anything"))

        assertTrue(result.isSuccess)
    }

    @Test
    fun `mapResult - success with Unit value maps to Result success`() {
        val result = mapResult(SftpResult.Success(Unit))

        assertTrue(result.isSuccess)
    }

    @Test
    fun `mapResult - serverError maps to Result failure with server message`() {
        val result = mapResult(
            SftpResult.ServerError(SftpStatusCode.PERMISSION_DENIED, "permission denied"),
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(
            "Expected error message to mention server error, got: ${error?.message}",
            error?.message?.contains("SFTP server error") == true,
        )
        assertTrue(
            "Expected original message to be preserved",
            error?.message?.contains("permission denied") == true,
        )
    }

    @Test
    fun `mapResult - protocolError maps to Result failure with protocol message`() {
        val result = mapResult(SftpResult.ProtocolError("malformed packet"))

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(
            "Expected error message to mention protocol error, got: ${error?.message}",
            error?.message?.contains("SFTP protocol error") == true,
        )
        assertTrue(error?.message?.contains("malformed packet") == true)
    }

    @Test
    fun `mapResult - ioError with Exception cause preserves the original exception`() {
        val original = IllegalStateException("disk full")
        val result = mapResult(SftpResult.IoError(original))

        assertTrue(result.isFailure)
        assertEquals(original, result.exceptionOrNull())
    }

    @Test
    fun `mapResult - ioError with non-Exception Throwable wraps it in IOException`() {
        val original = OutOfMemoryError("heap exhausted")
        val result = mapResult(SftpResult.IoError(original))

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is IOException)
        assertEquals("heap exhausted", error?.message)
    }
}
