package com.darkssh.client.service

import android.content.ClipboardManager
import android.net.Network
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.KnownHostRepository
import com.darkssh.client.data.repository.TabRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TerminalBridgeNetworkTest {
    private val host = Host(
        id = 1L,
        nickname = "test-host",
        hostname = "192.168.1.100",
        username = "dark",
        port = 22,
    )
    private val terminalService = mock(TerminalService::class.java)
    private val knownHostRepository = mock(KnownHostRepository::class.java)
    private val tabRepository = mock(TabRepository::class.java)
    private val clipboardManager = mock(ClipboardManager::class.java)

    private fun createBridge(): TerminalBridge = TerminalBridge(
        host = host,
        terminalService = terminalService,
        knownHostRepository = knownHostRepository,
        tabRepository = tabRepository,
        clipboardManager = clipboardManager,
        tabId = "tab-net-1",
    )

    @Test
    fun `captureNetworkState captures network ID and IP addresses`() {
        val bridge = createBridge()
        val info = ConnectivityMonitor.NetworkInfo(
            isConnected = true,
            ipAddresses = setOf("192.168.1.50"),
            networkId = "100",
            networkType = 1,
        )

        bridge.captureNetworkState(info)
        assertFalse(bridge.inGracePeriodFlow.value)
    }

    @Test
    fun `onNetworkLost for unrelated network leaves bridge unaffected`() {
        val bridge = createBridge()
        val info = ConnectivityMonitor.NetworkInfo(
            isConnected = true,
            ipAddresses = setOf("192.168.1.50"),
            networkId = "100",
            networkType = 1,
        )
        bridge.captureNetworkState(info)

        val unrelatedNetwork = mock(Network::class.java)
        bridge.onNetworkLost(unrelatedNetwork, setOf("10.0.0.5"))

        assertFalse(bridge.inGracePeriodFlow.value)
        assertNull(bridge.networkStatusMessage.value)
        assertFalse(bridge.isDisconnected.value)
    }

    @Test
    fun `onNetworkLost for affected network enters grace period`() {
        val bridge = createBridge()
        val info = ConnectivityMonitor.NetworkInfo(
            isConnected = true,
            ipAddresses = setOf("192.168.1.50"),
            networkId = "100",
            networkType = 1,
        )
        bridge.captureNetworkState(info)

        val lostNetwork = mock(Network::class.java)
        bridge.onNetworkLost(lostNetwork, setOf("192.168.1.50"))

        assertTrue(bridge.inGracePeriodFlow.value)
        assertNotNull(bridge.networkStatusMessage.value)
        assertFalse(bridge.isDisconnected.value)
    }

    @Test
    fun `onNetworkRestored with same IP cancels grace period and resumes`() {
        val bridge = createBridge()
        val info = ConnectivityMonitor.NetworkInfo(
            isConnected = true,
            ipAddresses = setOf("192.168.1.50"),
            networkId = "100",
            networkType = 1,
        )
        bridge.captureNetworkState(info)

        val lostNetwork = mock(Network::class.java)
        bridge.onNetworkLost(lostNetwork, setOf("192.168.1.50"))
        assertTrue(bridge.inGracePeriodFlow.value)

        val restoredInfo = ConnectivityMonitor.NetworkInfo(
            isConnected = true,
            ipAddresses = setOf("192.168.1.50"),
            networkId = "101",
            networkType = 1,
        )
        bridge.onNetworkRestored(restoredInfo)

        assertFalse(bridge.inGracePeriodFlow.value)
        assertNull(bridge.networkStatusMessage.value)
        assertFalse(bridge.isDisconnected.value)
    }

    @Test
    fun `onNetworkRestored with changed IP immediately disconnects with NETWORK_LOST`() {
        val bridge = createBridge()
        val info = ConnectivityMonitor.NetworkInfo(
            isConnected = true,
            ipAddresses = setOf("192.168.1.50"),
            networkId = "100",
            networkType = 1,
        )
        bridge.captureNetworkState(info)

        val lostNetwork = mock(Network::class.java)
        bridge.onNetworkLost(lostNetwork, setOf("192.168.1.50"))
        assertTrue(bridge.inGracePeriodFlow.value)

        val changedIpInfo = ConnectivityMonitor.NetworkInfo(
            isConnected = true,
            ipAddresses = setOf("172.20.10.2"),
            networkId = "102",
            networkType = 0, // Cellular
        )
        bridge.onNetworkRestored(changedIpInfo)

        assertFalse(bridge.inGracePeriodFlow.value)
        assertTrue(bridge.isDisconnected.value)
        assertEquals(DisconnectReason.NETWORK_LOST, bridge.disconnectReason.value)
        verify(terminalService, times(1)).onBridgeDisconnected(bridge, DisconnectReason.NETWORK_LOST)
    }
}
