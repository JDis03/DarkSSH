package com.darkssh.client.ui.screens.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.HostRepository
import com.darkssh.client.data.repository.KnownHostRepository
import com.darkssh.client.service.DisconnectReason
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
        private val knownHostRepository: KnownHostRepository,
    ) : AndroidViewModel(application) {
        private val _bridge = MutableStateFlow<TerminalBridge?>(null)
        val bridge: StateFlow<TerminalBridge?> = _bridge

        private val _host = MutableStateFlow<Host?>(null)
        val host: StateFlow<Host?> = _host

        private val _isDisconnected = MutableStateFlow(false)
        val isDisconnected: StateFlow<Boolean> = _isDisconnected

        private val _disconnectMessage = MutableStateFlow<String?>(null)
        val disconnectMessage: StateFlow<String?> = _disconnectMessage

        private var terminalService: TerminalService? = null
        private var connectionJob: Job? = null

        fun loadHost(hostId: Long) {
            viewModelScope.launch {
                val h = hostRepository.getHostById(hostId)
                _host.value = h
            }
        }

        fun setTerminalService(service: TerminalService?) {
            terminalService = service
        }

        fun connect(hostId: Long) {
            connectionJob?.cancel()
            viewModelScope.launch {
                val h = hostRepository.getHostById(hostId) ?: return@launch
                _host.value = h

                val service = terminalService ?: return@launch
                val b = service.openConnection(h)
                _bridge.value = b
                _isDisconnected.value = false
                _disconnectMessage.value = null

                launch {
                    b.isDisconnected.collect { disconnected ->
                        if (disconnected) {
                            _isDisconnected.value = true
                        }
                    }
                }

                launch {
                    b.disconnectMessage.collect { msg ->
                        _disconnectMessage.value = msg
                    }
                }
            }
        }

        fun disconnect() {
            _bridge.value?.close()
            _bridge.value = null
            _isDisconnected.value = true
        }

        fun reconnect() {
            val oldBridge = _bridge.value
            _bridge.value = null
            oldBridge?.close()

            _isDisconnected.value = false
            _disconnectMessage.value = null

            val h = _host.value ?: return
            viewModelScope.launch {
                val service = terminalService ?: return@launch
                val b = service.openConnection(h)
                _bridge.value = b

                launch {
                    b.isDisconnected.collect { disconnected ->
                        if (disconnected) {
                            _isDisconnected.value = true
                        }
                    }
                }

                launch {
                    b.disconnectMessage.collect { msg ->
                        _disconnectMessage.value = msg
                    }
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            connectionJob?.cancel()
            _bridge.value?.close()
        }
    }