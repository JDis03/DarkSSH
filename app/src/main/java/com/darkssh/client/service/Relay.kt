package com.darkssh.client.service

import com.darkssh.client.transport.AbsTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class Relay(
    private val transport: AbsTransport,
    private val bridge: TerminalBridge,
) {
    private val relayJob = Job()
    private val relayScope = CoroutineScope(Dispatchers.IO + relayJob)

    fun start() {
        android.util.Log.d("Relay", "🚀 Relay.start() called")
        relayScope.launch {
            val buffer = ByteArray(8192)
            android.util.Log.d("Relay", "🔄 Relay coroutine running on ${Thread.currentThread().name}")

            try {
                while (isActive) {
                    val bytesRead = transport.read(buffer, 0, buffer.size)

                    if (bytesRead == -1) {
                        // EOF: remote side closed the connection or interface was torn down
                        if (!bridge.isClosed) {
                            val inGrace = bridge.inGracePeriodFlow.value
                            Timber.w("Relay: EOF (inGracePeriod=$inGrace)")
                            if (inGrace) {
                                bridge.dispatchDisconnect("Network connection lost", DisconnectReason.NETWORK_LOST)
                            } else {
                                bridge.dispatchDisconnect("Connection closed by remote", DisconnectReason.REMOTE_EOF)
                            }
                        }
                        break
                    }
                    if (bytesRead > 0) {
                        bridge.onRelayData(buffer.copyOf(bytesRead))
                    }
                }
            } catch (e: CancellationException) {
                // Normal: bridge.close() cancelled relayScope
                android.util.Log.d("Relay", "🚫 Relay cancelled (bridge closed)")
            } catch (e: Exception) {
                // IOException from closed transport: only dispatch if bridge is
                // still alive (not already being closed by the user).
                if (!bridge.isClosed) {
                    val inGrace = bridge.inGracePeriodFlow.value
                    Timber.e(e, "Relay error (inGracePeriod=$inGrace): ${e.message}")
                    if (inGrace) {
                        bridge.dispatchDisconnect(e.message ?: "Network connection lost", DisconnectReason.NETWORK_LOST)
                    } else {
                        bridge.dispatchDisconnect(e.message ?: "Connection lost", DisconnectReason.IO_ERROR)
                    }
                } else {
                    android.util.Log.d("Relay", "Relay IO error ignored (bridge already closed): ${e.message}")
                }
            }
        }
    }

    fun stop() {
        relayJob.cancel()
    }
}
