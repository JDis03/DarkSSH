package com.darkssh.client.transport.cbssh

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TerminalOutputStreamTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stdout = Channel<ByteArray>(Channel.UNLIMITED)
    private val stderr = Channel<ByteArray>(Channel.UNLIMITED)

    @After
    fun cleanup() = scope.cancel()

    @Test
    fun `packets larger than Relay buffer retain every byte including control sequences`() {
        val bytes = ByteArray(32768) { it.toByte() }
        stdout.trySend(bytes)
        stdout.close()
        stderr.close()
        val stream = TerminalOutputStream(scope, stdout, stderr)
        assertArrayEquals(bytes, stream.readBytes())
        assertEquals(-1, stream.read())
    }

    @Test
    fun `stderr is drained even when stdout is already at EOF`() {
        stdout.close()
        stderr.trySend("remote error\r\n".toByteArray())
        stderr.close()
        val stream = TerminalOutputStream(scope, stdout, stderr)
        assertEquals("remote error\r\n", stream.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun `empty packets are skipped and reads respect offset and unsigned bytes`() {
        stdout.trySend(byteArrayOf())
        stdout.trySend(byteArrayOf(0xff.toByte(), 0, 27))
        stdout.close()
        stderr.close()
        val stream = TerminalOutputStream(scope, stdout, stderr)
        assertEquals(255, stream.read())
        val target = byteArrayOf(42, 42, 42, 42)
        assertEquals(0, stream.read(target, 0, 0))
        assertEquals(2, stream.read(target, 1, 3))
        assertArrayEquals(byteArrayOf(42, 0, 27, 42), target)
        assertEquals(-1, stream.read())
    }

    @Test(timeout = 5000)
    fun `closing transport job interrupts a blocked Relay read`() {
        val stream = TerminalOutputStream(scope, stdout, stderr)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val read = executor.submit<Boolean> {
                try {
                    stream.read()
                    false
                } catch (_: CancellationException) {
                    true
                }
            }
            scope.cancel()
            assertTrue(read.get(2, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test(timeout = 5000)
    fun `channel failure is surfaced instead of silently returning EOF`() {
        stdout.close(IOException("socket lost"))
        val stream = TerminalOutputStream(scope, stdout, stderr)
        try {
            stream.read()
            throw AssertionError("Expected failed read")
        } catch (e: IOException) {
            assertEquals("socket lost", e.message)
        }
    }
}
