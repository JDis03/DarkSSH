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
        // maxPipelineDepth: 32 desde 2026-08-18 (medición de RTT corregida dentro del
        // async; depth 16 dejaba el pipeline corto para llenar un link de ~7MB/s a RTT
        // ~60ms: BDP ≈ 420KB = 14 chunks de 32KB).
        // chunkSize: 128KB desde 2026-08-18 (usuario en VPN con RTT ~250ms: BDP de un
        // link de 7MB/s = 1.75MB en vuelo; 32×32KB=1MB no alcanzaba → 4-5MB/s reales.
        // 32×128KB=4MB en vuelo → ~16MB/s teórico).
        // panicRttMs: 4000 desde 2026-08-18 (con VPN los picos de RTT de 2-3s son
        // transitorios; 2000ms provocaba colapsos innecesarios del pipeline).
        assertEquals(12, config.initialPipelineDepth)
        assertEquals(6, config.minPipelineDepth)
        assertEquals(32, config.maxPipelineDepth)
        assertEquals(128 * 1024, config.chunkSize)
        assertEquals(3, config.maxRetries)
        assertEquals(1000L, config.retryDelayMs)
        assertEquals(60_000L, config.operationTimeoutMs)
        assertEquals(256L * 1024, config.progressIntervalBytes)
        assertEquals(100L, config.targetRttMs)
        assertEquals(4000L, config.panicRttMs)
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
