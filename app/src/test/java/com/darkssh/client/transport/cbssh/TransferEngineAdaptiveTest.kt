/*
 * DarkSSH SFTP - TransferEngine adaptive pipeline tests
 * Tests for the RTT-based pipeline depth adaptation logic.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport.cbssh

import org.connectbot.sshlib.SftpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Tests for [TransferEngine.updateRtt] — the exponential moving average
 * RTT tracker that adapts pipeline depth.
 *
 * These tests use a Mockito mock of [SftpClient] (no network) because
 * we only exercise the math, not the I/O path.
 *
 * The adaptive logic:
 * - First sample initializes the average
 * - Subsequent samples use EMA: (avg * 7 + sample) / 8
 * - Pipeline depth adjusts gradually toward target based on RTT buckets
 *   (2026-08-18 calibration): <30ms = 12, <60ms = 16, <120ms = 24, else = max (default 32)
 * - Adjustments are ±1 per sample (gradual), clamped to [min, max]
 * - Circuit breaker: avgRttMs >= panicRttMs (default 2000ms) snaps depth straight
 *   to min instead of the gradual step — see [TransferConfig.panicRttMs]
 */
class TransferEngineAdaptiveTest {

    private lateinit var sftp: SftpClient

    @Before
    fun setup() {
        sftp = mock(SftpClient::class.java)
    }

    @Test
    fun `first sample initializes average to that value`() {
        val engine = TransferEngine(sftp, TransferConfig())

        engine.updateRttForTest(150L)

        assertEquals(150L, engine.avgRttMsForTest)
    }

    @Test
    fun `subsequent samples use exponential moving average`() {
        val engine = TransferEngine(sftp, TransferConfig())

        // First sample: avg = 100
        engine.updateRttForTest(100L)
        // Second sample: avg = (100*7 + 200) / 8 = 900/8 = 112
        engine.updateRttForTest(200L)
        // Third sample: avg = (112*7 + 100) / 8 = 884/8 = 110
        engine.updateRttForTest(100L)

        assertEquals(110L, engine.avgRttMsForTest)
    }

    @Test
    fun `low RTT adapts pipeline toward the calibrated floor`() {
        // Bucket <30ms target = 12 (2026-08-18 calibration: a 32KB-chunk pipeline of 12
        // fills a ~7MB/s link at ~30ms RTT; even low-RTT links benefit from parallelism)
        val engine = TransferEngine(sftp, TransferConfig(initialPipelineDepth = 8))

        // Sample 10ms RTT many times — pipeline should ramp to 12
        repeat(20) { engine.updateRttForTest(10L) }

        assertEquals(12, engine.currentPipelineDepthForTest)
    }

    @Test
    fun `medium RTT adapts pipeline gradually toward target`() {
        // RTT 40ms target = 16 (the <60ms bucket), initial = 8 → ramp up to 16
        val engine = TransferEngine(sftp, TransferConfig(initialPipelineDepth = 8))

        repeat(20) { engine.updateRttForTest(40L) }

        assertEquals(16, engine.currentPipelineDepthForTest)
    }

    @Test
    fun `high RTT adapts pipeline toward max depth`() {
        // RTT >= 120ms (sub-panic) target = max (default 32), initial = 8 → gradual ramp up
        val engine = TransferEngine(sftp, TransferConfig(initialPipelineDepth = 8))

        // 40 samples is well past the 24 steps needed to ramp 8 → 32
        repeat(40) { engine.updateRttForTest(500L) }

        assertEquals(32, engine.currentPipelineDepthForTest)
    }

    @Test
    fun `pipeline depth never exceeds max`() {
        val engine = TransferEngine(
            sftp,
            TransferConfig(initialPipelineDepth = 8, maxPipelineDepth = 16),
        )

        repeat(100) { engine.updateRttForTest(500L) }

        assertTrue(
            "Pipeline depth (${engine.currentPipelineDepthForTest}) must not exceed max (16)",
            engine.currentPipelineDepthForTest <= 16,
        )
    }

    @Test
    fun `pipeline depth never goes below min`() {
        val engine = TransferEngine(
            sftp,
            TransferConfig(initialPipelineDepth = 16, minPipelineDepth = 4),
        )

        repeat(100) { engine.updateRttForTest(1L) }

        assertTrue(
            "Pipeline depth (${engine.currentPipelineDepthForTest}) must not go below min (4)",
            engine.currentPipelineDepthForTest >= 4,
        )
    }

    @Test
    fun `RTT boundary at 30ms switches target`() {
        // 29ms → target = 12 (the <30ms bucket)
        // 31ms → target = 16 (the <60ms bucket)
        val engine1 = TransferEngine(sftp, TransferConfig(initialPipelineDepth = 4))
        repeat(30) { engine1.updateRttForTest(29L) }
        assertEquals(12, engine1.currentPipelineDepthForTest)

        val engine2 = TransferEngine(sftp, TransferConfig(initialPipelineDepth = 8))
        repeat(30) { engine2.updateRttForTest(31L) }
        assertEquals(16, engine2.currentPipelineDepthForTest)
    }

    @Test
    fun `panic threshold snaps depth straight down instead of gradual step`() {
        // Default panicRttMs = 2000. A single very-high sample (first sample sets the
        // average directly, no EMA smoothing needed) should snap depth to min immediately
        // — not the usual -1-per-sample crawl the normal branch uses.
        val engine = TransferEngine(sftp, TransferConfig(initialPipelineDepth = 32))

        engine.updateRttForTest(5000L)

        assertEquals(6, engine.currentPipelineDepthForTest) // default minPipelineDepth
    }

    @Test
    fun `just below panic threshold still uses the normal gradual ramp`() {
        val engine = TransferEngine(sftp, TransferConfig(initialPipelineDepth = 8))

        // 1999ms is just under the default 2000ms panic threshold — should follow the
        // normal "else" bucket (target = max) with a gradual +1 step, not snap anywhere.
        engine.updateRttForTest(1999L)

        assertEquals(9, engine.currentPipelineDepthForTest) // 8 + 1, ramping toward max
    }

    @Test
    fun `depth recovers gradually after a panic snap once RTT normalizes`() {
        val engine = TransferEngine(sftp, TransferConfig(initialPipelineDepth = 32))

        engine.updateRttForTest(5000L) // panic — snaps to min (6)
        assertEquals(6, engine.currentPipelineDepthForTest)

        // RTT recovers to a low value; EMA needs a few samples to pull the average down
        // from 5000, then depth ramps back up gradually (+1 per sample) once it does.
        repeat(60) { engine.updateRttForTest(10L) }

        // Calibrated low-RTT target (2026-08-18) is 12, not the old min-floor of 6.
        assertEquals(12, engine.currentPipelineDepthForTest)
    }

    @Test
    fun `custom panicRttMs threshold is respected`() {
        val engine = TransferEngine(
            sftp,
            TransferConfig(initialPipelineDepth = 20, panicRttMs = 500),
        )

        engine.updateRttForTest(600L)

        assertEquals(6, engine.currentPipelineDepthForTest) // snapped to min at a lower bar
    }

    @Test
    fun `rttSamples counter increments per sample`() {
        val engine = TransferEngine(sftp, TransferConfig())

        // rttSamples is private; verify behavior via initial avg behavior.
        // After 1 sample: avg = sample exactly.
        // After 2 samples: avg = (sample1 * 7 + sample2) / 8.
        engine.updateRttForTest(100L)
        assertEquals(100L, engine.avgRttMsForTest)

        // Different second sample should produce EMA, not overwrite
        engine.updateRttForTest(1000L)
        // Expected: (100 * 7 + 1000) / 8 = 1700/8 = 212
        assertEquals(212L, engine.avgRttMsForTest)
    }
}
