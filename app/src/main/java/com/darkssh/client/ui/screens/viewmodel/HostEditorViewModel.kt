package com.darkssh.client.ui.screens.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.HostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HostEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val hostRepository: HostRepository,
) : ViewModel() {
    private val hostId: Long = savedStateHandle["hostId"] ?: -1L

    private val _host = MutableStateFlow<Host?>(null)
    val host: StateFlow<Host?> = _host

    init {
        if (hostId != -1L) {
            viewModelScope.launch {
                _host.value = hostRepository.getHostById(hostId)
            }
        }
    }

    fun saveHost(
        nickname: String,
        hostname: String,
        username: String,
        port: Int,
        compression: Boolean,
        stayConnected: Boolean,
    ) {
        viewModelScope.launch {
            val existingHost = _host.value
            if (existingHost != null) {
                hostRepository.updateHost(
                    existingHost.copy(
                        nickname = nickname,
                        hostname = hostname,
                        username = username,
                        port = port,
                        compression = compression,
                        stayConnected = stayConnected,
                    ),
                )
            } else {
                hostRepository.insertHost(
                    Host(
                        nickname = nickname,
                        hostname = hostname,
                        username = username,
                        port = port,
                        compression = compression,
                        stayConnected = stayConnected,
                    ),
                )
            }
        }
    }
}