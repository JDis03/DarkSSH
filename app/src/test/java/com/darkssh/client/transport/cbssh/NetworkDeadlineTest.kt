package com.darkssh.client.transport.cbssh

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkDeadlineTest {
    @Test
    fun `a stalled network operation is bounded`() = runTest {
        val started = testScheduler.currentTime
        try {
            withNetworkDeadline(MutableStateFlow(false), 1000) { awaitCancellation() }
        } catch (e: IOException) {
            assertEquals("SSH operation timed out", e.message)
        }
        assertEquals(1000, testScheduler.currentTime - started)
    }

    @Test
    fun `waiting for fingerprint or password does not consume network timeout`() = runTest {
        val prompting = MutableStateFlow(false)
        val completed = CompletableDeferred<Unit>()
        val result = async {
            withNetworkDeadline(prompting, 1000) { completed.await() }
        }
        runCurrent()
        advanceTimeBy(500)
        prompting.value = true
        runCurrent()
        advanceTimeBy(120_000)
        assertFalse(result.isCompleted)
        prompting.value = false
        runCurrent()
        advanceTimeBy(500)
        completed.complete(Unit)
        result.await()
    }

    @Test
    fun `parent cancellation still interrupts a UI prompt`() = runTest {
        val result = async {
            withNetworkDeadline(MutableStateFlow(true), 1000) { awaitCancellation() }
        }
        runCurrent()
        result.cancel()
        try {
            result.await()
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            assertTrue(result.isCancelled)
        }
    }

    @Test
    fun `watchdog is retired after successful operation`() = runTest {
        assertEquals("ok", withNetworkDeadline(MutableStateFlow(false), 1000) { "ok" })
        delay(2000)
        // No late watchdog exception can cancel the next operation.
    }
}
