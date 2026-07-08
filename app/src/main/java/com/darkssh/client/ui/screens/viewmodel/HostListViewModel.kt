package com.darkssh.client.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.HostRepository
import com.darkssh.client.data.repository.TabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HostListViewModel
    @Inject
    constructor(
        private val hostRepository: HostRepository,
        private val tabRepository: TabRepository,
    ) : ViewModel() {
        val hosts: StateFlow<List<Host>> =
            hostRepository
                .getAllHosts()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
        fun cloneHost(host: Host) {
            viewModelScope.launch {
                // Generate a unique nickname: "Copy of X", "Copy of X (2)", etc.
                val existingNicknames = hosts.value.map { it.nickname }.toSet()
                val baseName = "Copy of ${host.nickname.ifBlank { host.hostname }}"
                val uniqueNickname = if (baseName !in existingNicknames) {
                    baseName
                } else {
                    // Find first available suffix (2..Int.MAX) — never throws
                    generateSequence(2) { it + 1 }
                        .map { "$baseName ($it)" }
                        .first { it !in existingNicknames }
                }

                val clone = host.copy(
                    id = 0L,           // new row
                    nickname = uniqueNickname,
                    lastConnected = null,
                )
                hostRepository.insertHost(clone)
                Timber.d("Cloned host '${host.nickname}' → '$uniqueNickname'")
            }
        }

        fun deleteHost(host: Host) {
            viewModelScope.launch {
                // Close all tabs associated with this host
                val tabs = tabRepository.getAllTabs().first()
                val tabsToClose = tabs.filter { it.hostId == host.id }
                
                Timber.d("HostListViewModel: Deleting host ${host.nickname} (id=${host.id}), closing ${tabsToClose.size} tabs")
                
                tabsToClose.forEach { tab ->
                    Timber.d("HostListViewModel: Closing tab ${tab.id} (type=${tab.type})")
                    tabRepository.deleteTab(tab)
                }
                
                // Delete the host from database
                hostRepository.deleteHost(host)
            }
        }
    }
