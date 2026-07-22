/*
 * DarkSSH SFTP Client - cbssh Migration
 * Tests for TransferState progress arithmetic.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport.cbssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TransferState] progress calculation.
 *
 * Covers edge cases: empty files, partial progress, complete transfers.
 * These are pure value tests — no mocking required.
 */
class TransferStateTest {

    @Test
    fun `progress is zero when total is zero`() {
        val state = TransferState(
            remotePath = "/remote/file",
            localPath = "/local/file",
            totalBytes = 0L,
            transferredBytes = 0L,
            isUpload = false,
        )
        // No division by zero — must return 0, not NaN or crash.
        assertEquals(0f, state.progress, 0.0001f)
        assertFalse(state.isComplete)
    }

    @Test
    fun `progress reflects ratio of transferred to total`() {
        val state = TransferState(
            remotePath = "/r",
            localPath = "/l",
            totalBytes = 1000L,
            transferredBytes = 250L,
            isUpload = true,
        )
        assertEquals(0.25f, state.progress, 0.0001f)
        assertFalse(state.isComplete)
    }

    @Test
    fun `isComplete is true when transferred equals total`() {
        val state = TransferState(
            remotePath = "/r",
            localPath = "/l",
            totalBytes = 1000L,
            transferredBytes = 1000L,
            isUpload = false,
        )
        assertEquals(1.0f, state.progress, 0.0001f)
        assertTrue(state.isComplete)
    }

    @Test
    fun `isComplete is true when transferred exceeds total`() {
        val state = TransferState(
            remotePath = "/r",
            localPath = "/l",
            totalBytes = 100L,
            transferredBytes = 150L, // over-reporting shouldn't be a problem
            isUpload = true,
        )
        assertTrue(state.isComplete)
    }
}
