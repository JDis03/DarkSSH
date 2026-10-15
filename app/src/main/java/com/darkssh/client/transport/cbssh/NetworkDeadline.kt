package com.darkssh.client.transport.cbssh

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException

/** Bound network stalls without charging the user's fingerprint/password dialog time. */
internal suspend fun <T> withNetworkDeadline(
    prompting: StateFlow<Boolean>,
    timeoutMs: Long = 30_000L,
    block: suspend () -> T,
): T = coroutineScope {
    val watchdog = launch {
        prompting.collectLatest { waitingForUser ->
            if (!waitingForUser) {
                delay(timeoutMs)
                throw IOException("SSH operation timed out")
            }
        }
    }
    try {
        block()
    } finally {
        watchdog.cancel()
    }
}
