package com.darkssh.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkssh.client.data.entity.Tab
import com.darkssh.client.data.entity.TabType
import com.darkssh.client.data.repository.TabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TabManager
    @Inject
    constructor(
        private val tabRepository: TabRepository,
    ) : ViewModel() {
        private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
        val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

        private val _currentTabIndex = MutableStateFlow(0)
        val currentTabIndex: StateFlow<Int> = _currentTabIndex.asStateFlow()

        init {
            loadTabs()
        }

        private fun loadTabs() {
            viewModelScope.launch {
                tabRepository.getAllTabs().collect { tabs ->
                    _tabs.value = tabs
                    // Ensure currentTabIndex is valid
                    if (_currentTabIndex.value >= tabs.size) {
                        _currentTabIndex.value = (tabs.size - 1).coerceAtLeast(0)
                    }
                }
            }
        }

        fun createTab(
            type: TabType,
            hostId: Long,
            title: String = "",
        ) {
            viewModelScope.launch {
                val position = _tabs.value.size
                val tab =
                    Tab(
                        type = type,
                        hostId = hostId,
                        position = position,
                        title = title,
                    )
                tabRepository.insertTab(tab)
                // Switch to newly created tab
                _currentTabIndex.value = position
            }
        }

        fun closeTab(tabId: String) {
            viewModelScope.launch {
                val tab = tabRepository.getTabById(tabId) ?: return@launch
                tabRepository.deleteTab(tab)
                // Reorder remaining tabs
                val remainingTabs = _tabs.value.filter { it.id != tabId }
                remainingTabs.forEachIndexed { index, t ->
                    if (t.position != index) {
                        tabRepository.updateTabPosition(t.id, index)
                    }
                }
            }
        }

        fun switchTab(index: Int) {
            if (index >= 0 && index < _tabs.value.size) {
                _currentTabIndex.value = index
            }
        }

        fun reorderTabs(
            fromIndex: Int,
            toIndex: Int,
        ) {
            viewModelScope.launch {
                val mutableTabs = _tabs.value.toMutableList()
                val tab = mutableTabs.removeAt(fromIndex)
                mutableTabs.add(toIndex, tab)

                // Update positions in database
                mutableTabs.forEachIndexed { index, t ->
                    tabRepository.updateTabPosition(t.id, index)
                }
            }
        }

        fun getCurrentTab(): Tab? {
            val index = _currentTabIndex.value
            return _tabs.value.getOrNull(index)
        }
    }
