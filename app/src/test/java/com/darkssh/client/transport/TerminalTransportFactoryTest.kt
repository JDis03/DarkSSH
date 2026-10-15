package com.darkssh.client.transport

import com.darkssh.client.BuildConfig
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.KnownHostRepository
import com.darkssh.client.service.TerminalBridge
import com.darkssh.client.service.TerminalService
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class TerminalTransportFactoryTest {
    private val bridge = mock<TerminalBridge>()
    private val service = mock<TerminalService>()
    private val repository = mock<KnownHostRepository>()

    @Test
    fun `stable backend remains available independently of migration flag`() {
        val transport = TerminalTransportFactory.create(Host(), bridge, service, repository, useCbssh = false)
        assertTrue(transport is SSH)
        verifyNoInteractions(bridge, service, repository)
        transport.close()
    }

    @Test
    fun `experimental backend is only constructed on explicit opt in`() {
        val transport = TerminalTransportFactory.create(Host(), bridge, service, repository, useCbssh = true)
        assertTrue(transport is CbsshTerminalTransport)
        verifyNoInteractions(bridge, service, repository)
        transport.close()
    }

    @Test
    fun `production selection follows build flag without opening a connection`() {
        val transport = TerminalTransportFactory.create(Host(), bridge, service, repository)
        assertTrue(if (BuildConfig.USE_CBSSH_TERMINAL) transport is CbsshTerminalTransport else transport is SSH)
        verifyNoInteractions(bridge, service, repository)
        transport.close()
    }
}
