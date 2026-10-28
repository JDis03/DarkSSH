package com.darkssh.client.service

import android.content.ClipboardManager
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.KnownHostRepository
import com.darkssh.client.data.repository.TabRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TerminalBridgeDisconnectTest {
    private val host = Host(
        id = 1L,
        nickname = "test-host",
        hostname = "127.0.0.1",
        username = "root",
        port = 22,
    )
    private val terminalService = mock(TerminalService::class.java)
    private val knownHostRepository = mock(KnownHostRepository::class.java)
    private val tabRepository = mock(TabRepository::class.java)
    private val clipboardManager = mock(ClipboardManager::class.java)

    @Test
    fun `dispatchDisconnect is idempotent and fires onBridgeDisconnected only once`() {
        val bridge = TerminalBridge(
            host = host,
            terminalService = terminalService,
            knownHostRepository = knownHostRepository,
            tabRepository = tabRepository,
            clipboardManager = clipboardManager,
            tabId = "tab-123",
        )

        bridge.dispatchDisconnect("First error", DisconnectReason.UNKNOWN)
        bridge.dispatchDisconnect("Second error", DisconnectReason.UNKNOWN)

        assertTrue(bridge.isDisconnected.value)
        assertEquals("First error", bridge.disconnectMessage.value)
        verify(terminalService, times(1)).onBridgeDisconnected(bridge, DisconnectReason.UNKNOWN)
    }

    @Test
    fun `dispatchDisconnect is ignored if bridge already closed`() {
        val bridge = TerminalBridge(
            host = host,
            terminalService = terminalService,
            knownHostRepository = knownHostRepository,
            tabRepository = tabRepository,
            clipboardManager = clipboardManager,
            tabId = "tab-123",
        )

        bridge.close()
        bridge.dispatchDisconnect("Should be ignored", DisconnectReason.UNKNOWN)

        // onBridgeDisconnected should NOT be called from dispatchDisconnect after close()
        verify(terminalService, times(0)).onBridgeDisconnected(bridge, DisconnectReason.UNKNOWN)
    }
}
