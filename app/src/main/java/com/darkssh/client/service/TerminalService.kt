package com.darkssh.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.darkssh.client.R
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.HostRepository
import com.darkssh.client.data.repository.KnownHostRepository
import com.darkssh.client.transport.SftpClient
import com.darkssh.client.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.security.KeyPair
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class TerminalService : Service() {
    companion object {
        const val CHANNEL_ID = "darkssh_connections"
        const val CHANNEL_NAME = "SSH Connections"
        const val NOTIFICATION_ID = 1
        const val ACTION_CONNECT = "com.darkssh.client.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.darkssh.client.ACTION_DISCONNECT"
        const val EXTRA_HOST_ID = "host_id"
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main)

    private val _bridges = MutableStateFlow<List<TerminalBridge>>(emptyList())
    val bridges: StateFlow<List<TerminalBridge>> = _bridges

    /**
     * Set of hostIds that currently have at least one active SSH connection.
     * Derived reactively from bridges + their isConnected states.
     * Used by HostListScreen to show online/offline indicators without
     * coupling the ViewModel to TerminalService.
     */
    val connectedHostIds: StateFlow<Set<Long>> = _bridges
        .flatMapLatest { bridgeList ->
            if (bridgeList.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(emptySet())
            } else {
                // Combine all bridge isConnected flows into a single Set<Long>
                combine(bridgeList.map { bridge ->
                    bridge.isConnected.map { connected ->
                        bridge.host.id to connected
                    }
                }) { pairs ->
                    pairs.filter { it.second }.map { it.first }.toSet()
                }
            }
        }
        .stateIn(serviceScope, SharingStarted.WhileSubscribed(), emptySet())

    private val _activeBridge = MutableStateFlow<TerminalBridge?>(null)
    val activeBridge: StateFlow<TerminalBridge?> = _activeBridge

    private val sftpClients = ConcurrentHashMap<Long, SftpClient>()

    val loadedKeypairs: ConcurrentHashMap<String, KeyPair> = ConcurrentHashMap()

    @Inject lateinit var hostRepository: HostRepository

    @Inject lateinit var knownHostRepository: KnownHostRepository

    @Inject lateinit var tabRepository: com.darkssh.client.data.repository.TabRepository

    @Inject lateinit var clipboardManager: ClipboardManager

    private val binder = TerminalBinder()

    inner class TerminalBinder : Binder() {
        fun getService(): TerminalService = this@TerminalService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Timber.d("TerminalService created")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val hostId = intent.getLongExtra(EXTRA_HOST_ID, -1L)
                if (hostId > 0) {
                    serviceScope.launch {
                        openConnection(hostId)
                    }
                }
            }

            ACTION_DISCONNECT -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()

        // Close all bridges first
        _bridges.value.forEach { it.close() }
        _bridges.value = emptyList()

        // Disconnect SFTP clients with timeout
        // Use runBlocking with timeout to prevent ANR but still cleanup properly
        sftpClients.forEach { (hostId, client) ->
            try {
                runBlocking {
                    withTimeoutOrNull(1500L) {
                        client.disconnect()
                    } ?: Timber.w("SFTP disconnect timeout for host $hostId")
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to disconnect SFTP client for host $hostId")
            }
        }
        sftpClients.clear()

        // Cancel service scope
        serviceJob.cancel()

        Timber.d("TerminalService destroyed")
    }

    suspend fun openConnection(
        hostId: Long,
        tabId: String? = null,
    ): TerminalBridge? {
        val host = hostRepository.getHostById(hostId) ?: return null
        return openConnection(host, tabId)
    }

    fun openConnection(
        host: Host,
        tabId: String? = null,
    ): TerminalBridge {
        Timber.d("TerminalService: openConnection called for ${host.nickname} (hostId=${host.id}, tabId=$tabId)")
        val bridge = TerminalBridge(host, this, knownHostRepository, tabRepository, clipboardManager, tabId)
        _bridges.value = _bridges.value + bridge

        // Auto-set as active bridge if this is the first bridge or no active bridge
        if (_activeBridge.value == null || _bridges.value.size == 1) {
            Timber.d("TerminalService: Auto-setting bridge as active (first bridge or no active bridge)")
            _activeBridge.value = bridge
        }

        val notification = createConnectionNotification(host)
        startForeground(NOTIFICATION_ID, notification)

        Timber.d("TerminalService: Starting connection for bridge with tabId=${bridge.tabId}")
        bridge.startConnection()
        return bridge
    }

    fun setActiveBridge(bridge: TerminalBridge?) {
        val previousBridge = _activeBridge.value
        if (previousBridge != bridge) {
            Timber.d("TerminalService: Active bridge changed: ${previousBridge?.host?.nickname} (${previousBridge?.tabId}) -> ${bridge?.host?.nickname} (${bridge?.tabId})")
        }
        _activeBridge.value = bridge
    }

    fun onBridgeDisconnected(
        bridge: TerminalBridge,
        reason: DisconnectReason,
    ) {
        // Close bridge FIRST to prevent race conditions
        bridge.close(reason)

        // Then remove from list
        val currentBridges = _bridges.value.toMutableList()
        currentBridges.remove(bridge)
        _bridges.value = currentBridges

        // Clear active bridge if it was the one disconnected
        if (_activeBridge.value == bridge) {
            _activeBridge.value = currentBridges.firstOrNull()
        }

        if (currentBridges.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            val notification = createConnectionNotification(currentBridges.first().host)
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Active SSH terminal sessions"
            }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createConnectionNotification(host: Host): Notification {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle("DarkSSH")
            .setContentText("Connected to ${host.nickname.ifBlank { host.hostname }}")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
