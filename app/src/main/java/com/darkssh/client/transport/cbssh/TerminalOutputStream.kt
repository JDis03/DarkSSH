package com.darkssh.client.transport.cbssh

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.InputStream

/**
 * Adapts cbssh output chunks to Relay's blocking InputStream contract. Both streams
 * are drained concurrently; EOF is reported only after both have been exhausted.
 * A small Relay buffer must not discard the tail of a larger SSH channel packet.
 */
internal class TerminalOutputStream(
    private val scope: CoroutineScope,
    stdout: ReceiveChannel<ByteArray>,
    stderr: ReceiveChannel<ByteArray>,
) : InputStream() {
    private val chunks = Channel<ByteArray>(16)
    private var pending = ByteArray(0)
    private var position = 0

    private val pump = scope.launch {
        try {
            coroutineScope {
                launch { for (chunk in stdout) if (chunk.isNotEmpty()) chunks.send(chunk) }
                launch { for (chunk in stderr) if (chunk.isNotEmpty()) chunks.send(chunk) }
            }
            chunks.close()
        } catch (e: CancellationException) {
            chunks.cancel(e)
            throw e
        } catch (e: Exception) {
            // Preserve already-received output, then surface the original failure.
            chunks.close(e)
        }
    }

    @Synchronized
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        if (position == pending.size) {
            pending = runBlocking(scope.coroutineContext) {
                val result = chunks.receiveCatching()
                result.exceptionOrNull()?.let { throw it }
                result.getOrNull()
            } ?: return -1
            position = 0
        }
        val count = minOf(length, pending.size - position)
        pending.copyInto(buffer, offset, position, position + count)
        position += count
        return count
    }

    override fun read(): Int {
        val byte = ByteArray(1)
        return if (read(byte, 0, 1) == -1) -1 else byte[0].toInt() and 0xff
    }

    override fun close() {
        pump.cancel()
        chunks.cancel()
    }
}
