/*
 * DarkSSH SFTP Client - cbssh Migration
 * Copyright 2026 DarkSSH
 *
 * Unit tests for TransferProgress data class.
 * Uses a local stub because TransferProgress.kt is pending cbssh dependency.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.darkssh.client.transport.cbssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TransferProgress] data class.
 * These tests don't require mocking and can run without cbssh dependency.
 *
 * We use a local copy of TransferProgress to avoid dependency on the pending file.
 */
class TransferProgressTest {
    @Test
    fun `percentage is zero when totalBytes is zero`() {
        val progress = makeProgress(bytesTransferred = 0L, totalBytes = 0L)
        assertEquals(0f, progress.percentage, 0.001f)
    }

    @Test
    fun `percentage is zero when no bytes transferred`() {
        val progress = makeProgress(bytesTransferred = 0L, totalBytes = 1000L)
        assertEquals(0f, progress.percentage, 0.001f)
    }

    @Test
    fun `percentage is 50 percent at half transfer`() {
        val progress = makeProgress(bytesTransferred = 500L, totalBytes = 1000L)
        assertEquals(0.5f, progress.percentage, 0.001f)
    }

    @Test
    fun `percentage is 100 percent when fully transferred`() {
        val progress = makeProgress(bytesTransferred = 1000L, totalBytes = 1000L)
        assertEquals(1f, progress.percentage, 0.001f)
    }

    @Test
    fun `speedBytesPerSecond is zero when no time elapsed`() {
        val progress = makeProgress(bytesTransferred = 1000L, totalBytes = 1000L, startTime = 1000L, currentTime = 1000L)
        assertEquals(0L, progress.speedBytesPerSecond)
    }

    @Test
    fun `speedBytesPerSecond is zero when time goes backwards`() {
        val progress = makeProgress(bytesTransferred = 1000L, totalBytes = 1000L, startTime = 2000L, currentTime = 1000L)
        assertEquals(0L, progress.speedBytesPerSecond)
    }

    @Test
    fun `speedBytesPerSecond calculates correctly`() {
        // 1000 bytes in 1 second = 1000 bytes/second
        val progress = makeProgress(bytesTransferred = 1000L, totalBytes = 2000L, currentTime = 1000L)
        assertEquals(1000L, progress.speedBytesPerSecond)
    }

    @Test
    fun `speedBytesPerSecond handles KB per second`() {
        // 5000 bytes in 1 second = 5000 bytes/second
        val progress = makeProgress(bytesTransferred = 5000L, totalBytes = 10000L, currentTime = 1000L)
        assertEquals(5000L, progress.speedBytesPerSecond)
    }

    @Test
    fun `speedFormatted returns B per s for small values`() {
        val progress = makeProgress(bytesTransferred = 500L, totalBytes = 1000L, currentTime = 1000L)
        assertEquals("500 B/s", progress.speedFormatted)
    }

    @Test
    fun `speedFormatted returns KB per s for medium values`() {
        val progress = makeProgress(bytesTransferred = 5000L, totalBytes = 10000L, currentTime = 1000L)
        assertEquals("4 KB/s", progress.speedFormatted)
    }

    @Test
    fun `speedFormatted returns MB per s for large values`() {
        // 5 MB in 1 second
        val fiveMB = 5L * 1024 * 1024
        val progress = makeProgress(bytesTransferred = fiveMB, totalBytes = 10L * 1024 * 1024, currentTime = 1000L)
        assertEquals("5 MB/s", progress.speedFormatted)
    }

    @Test
    fun `speedFormatted returns 0 B per s for zero speed`() {
        val progress = makeProgress(bytesTransferred = 0L, totalBytes = 1000L, currentTime = 0L)
        assertEquals("0 B/s", progress.speedFormatted)
    }

    @Test
    fun `progress can be created with all parameters`() {
        val progress = makeProgress(
            bytesTransferred = 100L,
            totalBytes = 200L,
            startTime = 1000L,
            currentTime = 2000L,
        )
        assertEquals(100L, progress.bytesTransferred)
        assertEquals(200L, progress.totalBytes)
        assertEquals("test.txt", progress.filename)
        assertEquals(1000L, progress.startTime)
        assertEquals(2000L, progress.currentTime)
    }

    @Test
    fun `percentage handles edge case of larger transferred than total`() {
        // This could happen due to race conditions
        val progress = makeProgress(bytesTransferred = 1500L, totalBytes = 1000L)
        // Should still be > 1.0 in this edge case (not clamped)
        assertTrue(progress.percentage > 1f)
    }

    /**
     * Helper to create a TransferProgress with sensible defaults.
     * Tests can override specific fields.
     */
    private fun makeProgress(
        bytesTransferred: Long,
        totalBytes: Long,
        filename: String = "test.txt",
        startTime: Long = 0L,
        currentTime: Long = 0L,
    ) = TransferProgress(
        bytesTransferred = bytesTransferred,
        totalBytes = totalBytes,
        filename = filename,
        startTime = startTime,
        currentTime = currentTime,
    )
}

/**
 * Local copy of TransferProgress for testing without cbssh dependency.
 * This should be removed when CbsshTransfer.kt.pending is activated.
 */
data class TransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val filename: String,
    val startTime: Long,
    val currentTime: Long,
) {
    val percentage: Float
        get() = if (totalBytes > 0) bytesTransferred.toFloat() / totalBytes else 0f

    val speedBytesPerSecond: Long
        get() {
            val elapsedMs = currentTime - startTime
            if (elapsedMs <= 0) return 0L
            return (bytesTransferred * 1000L) / elapsedMs
        }

    val speedFormatted: String
        get() = formatSpeed(speedBytesPerSecond)

    private fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0) return "0 B/s"
        return when {
            bytesPerSecond >= 1024L * 1024L -> "${bytesPerSecond / (1024L * 1024L)} MB/s"
            bytesPerSecond >= 1024L -> "${bytesPerSecond / 1024L} KB/s"
            else -> "$bytesPerSecond B/s"
        }
    }
}