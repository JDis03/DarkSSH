package com.darkssh.client.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks network availability and IP addresses for connection resilience.
 * Inspired by ConnectBot's ConnectivityMonitor.
 */
class ConnectivityMonitor(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnectivityLost(network: Network, lostIpAddresses: Set<String>)
        fun onConnectivityRestored(networkInfo: NetworkInfo)
    }

    data class NetworkInfo(
        val isConnected: Boolean,
        val ipAddresses: Set<String>,
        val networkId: String,
        val networkType: Int,
    )

    private val connectivityManager: ConnectivityManager = context.getSystemService(
        Context.CONNECTIVITY_SERVICE,
    ) as ConnectivityManager

    @Volatile
    private var currentNetworkInfo: NetworkInfo? = null

    private val allNetworks = ConcurrentHashMap<Network, NetworkInfo>()

    @Volatile
    private var defaultNetwork: Network? = null

    @Volatile
    private var lastNotifiedNetworkId: String? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Timber.i("ConnectivityMonitor: Network available: $network")
            updateNetworkInfo(network)
        }

        override fun onLost(network: Network) {
            Timber.i("ConnectivityMonitor: Network lost: $network")
            val lostInfo = allNetworks.remove(network)
            if (defaultNetwork == network) {
                defaultNetwork = null
                currentNetworkInfo = null
                lastNotifiedNetworkId = null
            }
            listener.onConnectivityLost(network, lostInfo?.ipAddresses ?: emptySet())
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            Timber.d("ConnectivityMonitor: Link properties changed for network: $network")
            updateNetworkInfo(network, linkProperties)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            Timber.d("ConnectivityMonitor: Network capabilities changed: $network")
            updateNetworkInfo(network)
        }
    }

    private val defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Timber.i("ConnectivityMonitor: Default network available: $network")
            defaultNetwork = network
            updateNetworkInfo(network)
        }

        override fun onLost(network: Network) {
            Timber.i("ConnectivityMonitor: Default network lost: $network")
            if (defaultNetwork == network) {
                defaultNetwork = null
                currentNetworkInfo = null
                lastNotifiedNetworkId = null
            }
        }
    }

    fun init() {
        try {
            defaultNetwork = connectivityManager.activeNetwork
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(request, networkCallback)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                connectivityManager.registerDefaultNetworkCallback(defaultNetworkCallback)
            }

            @Suppress("DEPRECATION")
            for (network in connectivityManager.allNetworks) {
                updateNetworkInfo(network)
            }
            Timber.i("ConnectivityMonitor initialized (activeNetwork=$defaultNetwork)")
        } catch (e: Exception) {
            Timber.e(e, "ConnectivityMonitor: Failed to register network callbacks")
        }
    }

    fun cleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                connectivityManager.unregisterNetworkCallback(defaultNetworkCallback)
            }
            Timber.i("ConnectivityMonitor cleaned up")
        } catch (e: Exception) {
            Timber.w(e, "ConnectivityMonitor: Failed to unregister network callback")
        }
    }

    fun getCurrentNetworkInfo(): NetworkInfo? = currentNetworkInfo

    private fun updateNetworkInfo(network: Network, linkProperties: LinkProperties? = null) {
        val props = linkProperties ?: connectivityManager.getLinkProperties(network)
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        if (props == null || capabilities == null) {
            return
        }

        val ipAddresses = extractIpAddresses(props)
        val networkId = network.toString()
        val networkType = getNetworkType(capabilities)

        val newNetworkInfo = NetworkInfo(
            isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            ipAddresses = ipAddresses,
            networkId = networkId,
            networkType = networkType,
        )

        allNetworks[network] = newNetworkInfo

        if (network == defaultNetwork || defaultNetwork == null) {
            val prevInfo = currentNetworkInfo
            val wasConnectedWithIp = prevInfo?.isConnected == true && prevInfo.ipAddresses.isNotEmpty()
            currentNetworkInfo = newNetworkInfo

            if (newNetworkInfo.isConnected && newNetworkInfo.ipAddresses.isNotEmpty()) {
                val networkChanged = lastNotifiedNetworkId != null && lastNotifiedNetworkId != networkId
                if (!wasConnectedWithIp || networkChanged) {
                    lastNotifiedNetworkId = networkId
                    listener.onConnectivityRestored(newNetworkInfo)
                }
            }
        }
    }

    private fun extractIpAddresses(linkProperties: LinkProperties): Set<String> =
        linkProperties.linkAddresses
            .mapNotNull { it.address?.hostAddress }
            .toSet()

    private fun getNetworkType(capabilities: NetworkCapabilities): Int = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
            NetworkCapabilities.TRANSPORT_WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
            NetworkCapabilities.TRANSPORT_CELLULAR
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
            NetworkCapabilities.TRANSPORT_ETHERNET
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ->
            NetworkCapabilities.TRANSPORT_VPN
        else -> -1
    }
}
