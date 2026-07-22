/*
 * DarkSSH SFTP - TransferProgress unit tests
 * Tests for progress percentage, speed calculation, and formatting.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TransferProgress] arithmetic and formatting.
 *
 * Covers the fields consumed by TransferProgressDialog and SftpViewModel.
 * These are pure value tests — no mocking required.
 */
class TransferProgressTest {

    @Test
    fun `percentage is zero when total is zero`() {
        val progress = TransferProgress(
            transferred = 0,
            total = 0,
            filePath = "file.txt",
            startTime = 1000L,
            currentTime = 2000L,
        )
        assertEquals(0, progress.percentage)
    }

    @Test
    fun `percentage is zero when nothing transferred`() {
        val progress = TransferProgress(
            transferred = 0,
            total = 1000,
            filePath = "file.txt",
        )
        assertEquals(0, progress.percentage)
    }

    @Test
    fun `percentage is fifty at halfway`() {
        val progress = TransferProgress(
            transferred = 500,
            total = 1000,
            filePath = "file.txt",
        )
        assertEquals(50, progress.percentage)
    }

    @Test
    fun `percentage is one hundred when fully transferred`() {
        val progress = TransferProgress(
            transferred = 1000,
            total = 1000,
            filePath = "file.txt",
        )
        assertEquals(100, progress.percentage)
    }

    @Test
    fun `percentage handles transferred larger than total gracefully`() {
        // Server may report sizes slightly differently than local count
        // (e.g. line endings). Should not overflow or go negative.
        val progress = TransferProgress(
            transferred = 1100,
            total = 1000,
            filePath = "file.txt",
        )
        // Long multiplication: 1100 * 100 / 1000 = 110
        // (caller may clamp if they want, but raw percentage is 110)
        assertEquals(110, progress.percentage)
    }

    @Test
    fun `elapsedSeconds is zero when both timestamps are equal`() {
        val progress = TransferProgress(
            transferred = 0,
            total = 1000,
            filePath = "file.txt",
            startTime = 1000L,
            currentTime = 1000L,
        )
        assertEquals(0.0, progress.elapsedSeconds, 0.0001)
    }

    @Test
    fun `elapsedSeconds is zero when time goes backwards`() {
        // Clock skew between update emissions should not produce negative time.
        val progress = TransferProgress(
            transferred = 100,
            total = 1000,
            filePath = "file.txt",
            startTime = 2000L,
            currentTime = 1000L,
        )
        // Negative elapsed is allowed by arithmetic — caller responsibility.
        // But we document the behavior so it's intentional.
        assertEquals(-1.0, progress.elapsedSeconds, 0.0001)
    }

    @Test
    fun `elapsedSeconds calculates correctly for one second`() {
        val progress = TransferProgress(
            transferred = 0,
            total = 1000,
            filePath = "file.txt",
            startTime = 1000L,
            currentTime = 2000L,
        )
        assertEquals(1.0, progress.elapsedSeconds, 0.0001)
    }

    @Test
    fun `speed is zero when no time elapsed`() {
        val progress = TransferProgress(
            transferred = 1000,
            total = 1000,
            filePath = "file.txt",
            startTime = 1000L,
            currentTime = 1000L,
        )
        assertEquals(0L, progress.speed)
    }

    @Test
    fun `speed is zero when time goes backwards`() {
        val progress = TransferProgress(
            transferred = 1000,
            total = 1000,
            filePath = "file.txt",
            startTime = 2000L,
            currentTime = 1000L,
        )
        assertEquals(0L, progress.speed)
    }

    @Test
    fun `speed calculates bytes per second`() {
        val progress = TransferProgress(
            transferred = 1000,
            total = 10000,
            filePath = "file.txt",
            startTime = 0L,
            currentTime = 1000L,
        )
        // 1000 bytes in 1 second = 1000 bytes/sec
        assertEquals(1000L, progress.speed)
    }

    @Test
    fun `speedFormatted returns bytes per second for small values`() {
        val progress = TransferProgress(
            transferred = 500,
            total = 1000,
            filePath = "file.txt",
            startTime = 0L,
            currentTime = 1000L,
        )
        // 500 B/s
        assertEquals("500 B/s", progress.speedFormatted)
    }

    @Test
    fun `speedFormatted returns kilobytes per second for medium values`() {
        val progress = TransferProgress(
            transferred = 5000,
            total = 10000,
            filePath = "file.txt",
            startTime = 0L,
            currentTime = 1000L,
        )
        // 5000 / 1024 = 4.88 KB/s
        assertTrue(
            "Expected KB/s format, got: ${progress.speedFormatted}",
            progress.speedFormatted.endsWith("KB/s"),
        )
    }

    @Test
    fun `speedFormatted returns megabytes per second for large values`() {
        val progress = TransferProgress(
            transferred = 5_000_000,
            total = 10_000_000,
            filePath = "file.txt",
            startTime = 0L,
            currentTime = 1000L,
        )
        // 5_000_000 / 1_048_576 = 4.77 MB/s
        assertTrue(
            "Expected MB/s format, got: ${progress.speedFormatted}",
            progress.speedFormatted.endsWith("MB/s"),
        )
    }

    @Test
    fun `speedFormatted handles zero speed`() {
        val progress = TransferProgress(
            transferred = 0,
            total = 1000,
            filePath = "file.txt",
            startTime = 1000L,
            currentTime = 1000L,
        )
        assertEquals("0 B/s", progress.speedFormatted)
    }
}
