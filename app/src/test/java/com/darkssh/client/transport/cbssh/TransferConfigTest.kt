/*
 * DarkSSH SFTP Client - cbssh Migration
 * Tests for TransferEngine config defaults and data classes.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport.cbssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TransferConfig], [TransferState], and [TransferResult] data classes.
 *
 * These are pure value classes — no mocking needed. They establish the contract
 * that TransferEngine relies on for adaptive pipelining and progress reporting.
 */
class TransferConfigTest {

    @Test
    fun `TransferConfig has production defaults`() {
        val config = TransferConfig()

        // Lock in current defaults so silent changes are caught by CI.
        assertEquals(12, config.initialPipelineDepth)
        assertEquals(6, config.minPipelineDepth)
        assertEquals(32, config.maxPipelineDepth)
        assertEquals(32 * 1024, config.chunkSize)
        assertEquals(3, config.maxRetries)
        assertEquals(1000L, config.retryDelayMs)
        assertEquals(60_000L, config.operationTimeoutMs)
        assertEquals(256L * 1024, config.progressIntervalBytes)
        assertEquals(100L, config.targetRttMs)
        assertEquals(2000L, config.panicRttMs)
    }

    @Test
    fun `TransferConfig pipeline bounds are consistent`() {
        val config = TransferConfig()

        // min <= initial <= max is required for adaptive logic to work.
        assertTrue(
            "minPipelineDepth (${config.minPipelineDepth}) must be <= initial (${config.initialPipelineDepth})",
            config.minPipelineDepth <= config.initialPipelineDepth,
        )
        assertTrue(
            "initialPipelineDepth (${config.initialPipelineDepth}) must be <= max (${config.maxPipelineDepth})",
            config.initialPipelineDepth <= config.maxPipelineDepth,
        )
    }
}
