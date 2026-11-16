package com.darkssh.client.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectivityMonitorTest {

    @Mock
    lateinit var context: Context

    @Mock
    lateinit var connectivityManager: ConnectivityManager

    @Mock
    lateinit var listener: ConnectivityMonitor.Listener

    private lateinit var connectivityMonitor: ConnectivityMonitor

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        `when`(connectivityManager.allNetworks).thenReturn(emptyArray())

        connectivityMonitor = ConnectivityMonitor(context, listener)
    }

    @Test
    fun `init registers callbacks with ConnectivityManager`() {
        connectivityMonitor.init()

        verify(connectivityManager).registerNetworkCallback(any(), any<ConnectivityManager.NetworkCallback>())
        verify(connectivityManager).registerDefaultNetworkCallback(any<ConnectivityManager.NetworkCallback>())
    }

    @Test
    fun `cleanup unregisters callbacks with ConnectivityManager`() {
        connectivityMonitor.init()
        connectivityMonitor.cleanup()

        verify(connectivityManager, times(2)).unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
    }

    @Test
    fun `onLost notifies listener with cached IP addresses`() {
        val network = mock(Network::class.java)
        val ipAddresses = setOf("192.168.1.100", "fe80::1")

        // Populate allNetworks via reflection
        val allNetworksField = connectivityMonitor.javaClass.getDeclaredField("allNetworks")
        allNetworksField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val allNetworks = allNetworksField.get(connectivityMonitor) as ConcurrentHashMap<Network, ConnectivityMonitor.NetworkInfo>

        val networkInfo = ConnectivityMonitor.NetworkInfo(
            isConnected = true,
            ipAddresses = ipAddresses,
            networkId = "net1",
            networkType = 1,
        )
        allNetworks[network] = networkInfo

        // Trigger onLost via reflection on private networkCallback
        val callbackField = connectivityMonitor.javaClass.getDeclaredField("networkCallback")
        callbackField.isAccessible = true
        val callback = callbackField.get(connectivityMonitor) as ConnectivityManager.NetworkCallback

        callback.onLost(network)

        // Verify listener was notified with the exact IPs
        verify(listener).onConnectivityLost(network, ipAddresses)
    }

    @Test
    fun `onLost on defaultNetwork resets currentNetworkInfo`() {
        val network = mock(Network::class.java)

        val defaultNetworkField = connectivityMonitor.javaClass.getDeclaredField("defaultNetwork")
        defaultNetworkField.isAccessible = true
        defaultNetworkField.set(connectivityMonitor, network)

        val currentNetworkInfoField = connectivityMonitor.javaClass.getDeclaredField("currentNetworkInfo")
        currentNetworkInfoField.isAccessible = true
        currentNetworkInfoField.set(connectivityMonitor, ConnectivityMonitor.NetworkInfo(true, setOf("1.2.3.4"), "1", 1))

        val callbackField = connectivityMonitor.javaClass.getDeclaredField("defaultNetworkCallback")
        callbackField.isAccessible = true
        val callback = callbackField.get(connectivityMonitor) as ConnectivityManager.NetworkCallback

        callback.onLost(network)

        assertNull(connectivityMonitor.getCurrentNetworkInfo())
    }

    private fun anyNetworkInfo(): ConnectivityMonitor.NetworkInfo {
        any<ConnectivityMonitor.NetworkInfo>()
        return ConnectivityMonitor.NetworkInfo(false, emptySet(), "", -1)
    }

    @Test
    fun `onAvailable without IPs does not notify restored until LinkProperties arrive with IPs`() {
        val network = mock(Network::class.java)
        val caps = mock(NetworkCapabilities::class.java)
        `when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true)
        `when`(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true)

        val emptyProps = mock(LinkProperties::class.java)
        `when`(emptyProps.linkAddresses).thenReturn(emptyList())

        `when`(connectivityManager.getNetworkCapabilities(network)).thenReturn(caps)
        `when`(connectivityManager.getLinkProperties(network)).thenReturn(emptyProps)

        val callbackField = connectivityMonitor.javaClass.getDeclaredField("defaultNetworkCallback")
        callbackField.isAccessible = true
        val defaultCallback = callbackField.get(connectivityMonitor) as ConnectivityManager.NetworkCallback

        // First onAvailable arrives before DHCP assigns IP -> must NOT call onConnectivityRestored
        defaultCallback.onAvailable(network)
        verify(listener, never()).onConnectivityRestored(anyNetworkInfo())

        // Then onLinkPropertiesChanged arrives with real IP -> MUST call onConnectivityRestored
        val linkAddr = mock(LinkAddress::class.java)
        `when`(linkAddr.address).thenReturn(InetAddress.getByName("192.168.1.50"))
        val realProps = mock(LinkProperties::class.java)
        `when`(realProps.linkAddresses).thenReturn(listOf(linkAddr))

        val netCallbackField = connectivityMonitor.javaClass.getDeclaredField("networkCallback")
        netCallbackField.isAccessible = true
        val netCallback = netCallbackField.get(connectivityMonitor) as ConnectivityManager.NetworkCallback

        netCallback.onLinkPropertiesChanged(network, realProps)
        verify(listener, times(1)).onConnectivityRestored(
            ConnectivityMonitor.NetworkInfo(
                isConnected = true,
                ipAddresses = setOf("192.168.1.50"),
                networkId = network.toString(),
                networkType = NetworkCapabilities.TRANSPORT_WIFI,
            ),
        )
    }

    @Test
    fun `defaultNetwork switching directly to new network notifies onConnectivityRestored`() {
        val net1 = mock(Network::class.java)
        val net2 = mock(Network::class.java)

        val caps = mock(NetworkCapabilities::class.java)
        `when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true)
        `when`(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true)

        val addr1 = mock(LinkAddress::class.java)
        `when`(addr1.address).thenReturn(InetAddress.getByName("192.168.1.50"))
        val props1 = mock(LinkProperties::class.java)
        `when`(props1.linkAddresses).thenReturn(listOf(addr1))

        val addr2 = mock(LinkAddress::class.java)
        `when`(addr2.address).thenReturn(InetAddress.getByName("10.0.0.2"))
        val props2 = mock(LinkProperties::class.java)
        `when`(props2.linkAddresses).thenReturn(listOf(addr2))

        `when`(connectivityManager.getNetworkCapabilities(net1)).thenReturn(caps)
        `when`(connectivityManager.getNetworkCapabilities(net2)).thenReturn(caps)
        `when`(connectivityManager.getLinkProperties(net1)).thenReturn(props1)
        `when`(connectivityManager.getLinkProperties(net2)).thenReturn(props2)

        val callbackField = connectivityMonitor.javaClass.getDeclaredField("defaultNetworkCallback")
        callbackField.isAccessible = true
        val defaultCallback = callbackField.get(connectivityMonitor) as ConnectivityManager.NetworkCallback

        defaultCallback.onAvailable(net1)
        defaultCallback.onAvailable(net2)

        verify(listener, times(2)).onConnectivityRestored(anyNetworkInfo())
    }
}
