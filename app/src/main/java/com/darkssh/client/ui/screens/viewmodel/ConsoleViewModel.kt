package com.darkssh.client.ui.screens.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.HostRepository
import com.darkssh.client.service.TerminalBridge
import com.darkssh.client.service.TerminalService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ConsoleViewModel
    @Inject
    constructor(
        application: Application,
        private val hostRepository: HostRepository,
    ) : AndroidViewModel(application) {
        private val _bridge = MutableStateFlow<TerminalBridge?>(null)
        val bridge: StateFlow<TerminalBridge?> = _bridge

        private val _host = MutableStateFlow<Host?>(null)
        val host: StateFlow<Host?> = _host

        private val _isDisconnected = MutableStateFlow(false)
        val isDisconnected: StateFlow<Boolean> = _isDisconnected

        private val _disconnectMessage = MutableStateFlow<String?>(null)
        val disconnectMessage: StateFlow<String?> = _disconnectMessage

        private val _disconnectReason = MutableStateFlow<com.darkssh.client.service.DisconnectReason?>(null)
        val disconnectReason: StateFlow<com.darkssh.client.service.DisconnectReason?> = _disconnectReason

        private var terminalService: TerminalService? = null
        private var connectionJob: Job? = null
        private var observeJobs = mutableListOf<Job>()
        private var currentTabId: String? = null
        private var currentHostId: Long = -1L

        fun loadHost(hostId: Long) {
            viewModelScope.launch {
                val h = hostRepository.getHostById(hostId)
                _host.value = h
            }
        }

        fun setTerminalService(service: TerminalService?) {
            terminalService = service
        }

        fun connect(
            hostId: Long,
            tabId: String? = null,
        ) {
            connectionJob?.cancel()
            observeJobs.forEach { it.cancel() }
            observeJobs.clear()
            currentTabId = tabId
            currentHostId = hostId

            viewModelScope.launch {
                val h = hostRepository.getHostById(hostId) ?: return@launch
                _host.value = h

                val service = terminalService ?: return@launch

                // If we have a tabId, look for bridge with matching tabId
                // Otherwise, look for any bridge with matching hostId (legacy behavior)
                val existingBridge =
                    if (tabId != null) {
                        service.bridges.value.find { it.tabId == tabId }
                    } else {
                        service.bridges.value.find { it.host.id == hostId && !it.isDisconnected.value }
                    }

                if (existingBridge != null && !existingBridge.isDisconnected.value) {
                    _bridge.value = existingBridge
                    _isDisconnected.value = false
                    _disconnectMessage.value = null
                    observeBridge(existingBridge)
                } else {
                    val b = service.openConnection(h, tabId)
                    _bridge.value = b
                    _isDisconnected.value = false
                    _disconnectMessage.value = null
                    observeBridge(b)
                }
            }
        }

        private fun observeBridge(b: TerminalBridge) {
            observeJobs.add(
                viewModelScope.launch {
                    b.isDisconnected.collect { disconnected ->
                        if (disconnected) {
                            _isDisconnected.value = true
                        }
                    }
                },
            )
            observeJobs.add(
                viewModelScope.launch {
                    b.disconnectMessage.collect { msg ->
                        _disconnectMessage.value = msg
                    }
                },
            )
            observeJobs.add(
                viewModelScope.launch {
                    b.disconnectReason.collect { reason ->
                        _disconnectReason.value = reason
                    }
                },
            )
            // Detect silent disconnects: wait until connected, then watch for isConnected→false
            // without isDisconnected being set (e.g. network timeout with no TCP RST)
            observeJobs.add(
                viewModelScope.launch {
                    var wasConnected = false
                    b.isConnected.collect { connected ->
                        if (connected) {
                            wasConnected = true
                        } else if (wasConnected && !b.isDisconnected.value) {
                            // Was connected, now not connected, but no disconnect event fired
                            Timber.w("Silent disconnect detected for ${b.host.nickname}")
                            _isDisconnected.value = true
                            if (_disconnectMessage.value == null) {
                                _disconnectMessage.value = "Connection lost"
                            }
                        }
                    }
                },
            )
        }

        fun disconnect() {
            _bridge.value?.let { bridge ->
                terminalService?.onBridgeDisconnected(bridge, com.darkssh.client.service.DisconnectReason.USER_REQUESTED)
                bridge.close()
            }
            _bridge.value = null
            _isDisconnected.value = true
        }

        fun detachFromBridge() {
            _bridge.value = null
        }

        fun reconnect() {
            val h = _host.value ?: return
            Timber.d("ConsoleViewModel: Reconnecting to ${h.nickname} (hostId=${h.id}, tabId=$currentTabId)")
            observeJobs.forEach { it.cancel() }
            observeJobs.clear()
            _bridge.value = null
            _isDisconnected.value = false
            _disconnectMessage.value = null
            _disconnectReason.value = null

            viewModelScope.launch {
                val service = terminalService ?: return@launch
                // Pass tabId to associate the new bridge with the existing tab
                Timber.d("ConsoleViewModel: Calling openConnection with tabId=$currentTabId")
                val b = service.openConnection(h, currentTabId)
                Timber.d("ConsoleViewModel: openConnection returned bridge, observing...")
                _bridge.value = b
                observeBridge(b)
            }
        }

        override fun onCleared() {
            super.onCleared()
            connectionJob?.cancel()
            observeJobs.forEach { it.cancel() }
            observeJobs.clear()
        }
    }
