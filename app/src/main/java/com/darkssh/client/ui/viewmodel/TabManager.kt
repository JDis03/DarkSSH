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
                    val previousSize = _tabs.value.size
                    _tabs.value = tabs

                    // If a new tab was added and we should switch to it
                    if (shouldSwitchToNewTab && tabs.size > previousSize) {
                        _currentTabIndex.value = tabs.size - 1
                        shouldSwitchToNewTab = false
                    } else {
                        // Ensure currentTabIndex is valid
                        if (_currentTabIndex.value >= tabs.size) {
                            _currentTabIndex.value = (tabs.size - 1).coerceAtLeast(0)
                        }
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

                // Try to reuse osType from previous tab of same host (avoid icon flicker)
                val previousTab = _tabs.value.find { it.hostId == hostId && it.type == type }
                val cachedOsType = previousTab?.osType ?: com.darkssh.client.data.model.OsType.UNKNOWN

                val tab =
                    Tab(
                        type = type,
                        hostId = hostId,
                        position = position,
                        title = title,
                        osType = cachedOsType,
                    )
                com.darkssh.client.util.DebugLogger.Tab
                    .created(tab.id, hostId, type.name)
                tabRepository.insertTab(tab)
                // Note: _currentTabIndex will be updated when _tabs Flow emits the new list
                // in the switchToNewTab check below
            }
        }

        private var shouldSwitchToNewTab = false

        fun createTabAndSwitch(
            type: TabType,
            hostId: Long,
            title: String = "",
        ) {
            shouldSwitchToNewTab = true
            createTab(type, hostId, title)
        }

        /**
         * Creates a tab or switches to an existing one with the same host and type.
         * If a tab already exists for this host+type combination, switches to it.
         * Otherwise, creates a new tab and switches to it.
         */
        fun createOrSwitchToTab(
            type: TabType,
            hostId: Long,
            title: String = "",
        ) {
            val existingTabIndex =
                _tabs.value.indexOfFirst {
                    it.type == type && it.hostId == hostId
                }

            if (existingTabIndex >= 0) {
                // Tab already exists, switch to it
                switchTab(existingTabIndex)
            } else {
                // Create new tab and switch to it
                shouldSwitchToNewTab = true
                createTab(type, hostId, title)
            }
        }

        fun closeTab(tabId: String) {
            viewModelScope.launch {
                val tab = tabRepository.getTabById(tabId) ?: return@launch
                val closedTabIndex = _tabs.value.indexOfFirst { it.id == tabId }

                com.darkssh.client.util.DebugLogger.Tab
                    .closed(tabId)
                tabRepository.deleteTab(tab)

                // Reorder remaining tabs
                val remainingTabs = _tabs.value.filter { it.id != tabId }
                remainingTabs.forEachIndexed { index, t ->
                    if (t.position != index) {
                        tabRepository.updateTabPosition(t.id, index)
                    }
                }

                // Adjust currentTabIndex if needed
                if (closedTabIndex >= 0 && remainingTabs.isNotEmpty()) {
                    // If closed tab was the current one, switch to the previous tab or the first one
                    if (_currentTabIndex.value >= closedTabIndex) {
                        _currentTabIndex.value = (_currentTabIndex.value - 1).coerceAtLeast(0)
                    }
                    // Ensure currentTabIndex is still valid
                    _currentTabIndex.value = _currentTabIndex.value.coerceIn(0, remainingTabs.size - 1)
                } else if (remainingTabs.isEmpty()) {
                    // No tabs left, reset index
                    _currentTabIndex.value = 0
                }
            }
        }

        fun switchTab(index: Int) {
            if (index >= 0 && index < _tabs.value.size) {
                val oldIndex = _currentTabIndex.value
                _currentTabIndex.value = index
                if (oldIndex != index) {
                    com.darkssh.client.util.DebugLogger.Tab
                        .switched(oldIndex, index)
                }
            }
        }

        fun closeOtherTabs(keepTabId: String) {
            val toClose = _tabs.value.filter { it.id != keepTabId }
            toClose.forEach { tab -> closeTab(tab.id) }
        }

        fun closeAllTabs() {
            val toClose = _tabs.value.toList()
            toClose.forEach { tab -> closeTab(tab.id) }
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
