package com.darkssh.client.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.HostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HostListViewModel
    @Inject
    constructor(
        private val hostRepository: HostRepository,
    ) : ViewModel() {
        val hosts: StateFlow<List<Host>> =
            hostRepository
                .getAllHosts()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }
