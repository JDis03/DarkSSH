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
                    android.util.Log.d("Relay", "📥 Read $bytesRead bytes from transport")
                    
                    if (bytesRead == -1) {
                        android.util.Log.d("Relay", "🏁 EOF reached")
                        bridge.dispatchDisconnect("EOF")
                        break
                    }
                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        android.util.Log.d("Relay", "📨 Forwarding ${data.size} bytes to bridge")
                        bridge.onRelayData(data)
                    }
                }
                android.util.Log.d("Relay", "🛑 Relay loop ended")
            } catch (e: CancellationException) {
                android.util.Log.d("Relay", "🚫 Relay cancelled")
            } catch (e: Exception) {
                android.util.Log.e("Relay", "💥 Error: ${e.message}", e)
                bridge.dispatchDisconnect(e.message ?: "Relay error")
            }
        }
    }

    fun stop() {
        relayJob.cancel()
    }
}