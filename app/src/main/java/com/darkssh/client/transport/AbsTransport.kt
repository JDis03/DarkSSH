package com.darkssh.client.transport

import com.darkssh.client.data.entity.Host
import com.darkssh.client.service.TerminalBridge
import com.darkssh.client.service.TerminalService
import java.io.IOException

abstract class AbsTransport(
    val host: Host,
    val bridge: TerminalBridge,
    val service: TerminalService,
) {
    abstract fun connect()

    @Throws(IOException::class)
    abstract fun read(): Int

    @Throws(IOException::class)
    abstract fun read(buffer: ByteArray, offset: Int, length: Int): Int

    @Throws(IOException::class)
    abstract fun write(buffer: ByteArray)

    @Throws(IOException::class)
    abstract fun flush()

    abstract fun close()

    abstract fun isConnected(): Boolean

    abstract fun isSessionOpen(): Boolean

    abstract fun setDimensions(cols: Int, rows: Int, width: Int, height: Int)

    abstract fun getNamespace(): String
}