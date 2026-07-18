package com.darkssh.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkssh.client.data.entity.Tab
import com.darkssh.client.data.entity.TabType
import com.darkssh.client.data.model.OsType
import com.darkssh.client.data.repository.TabRepository
import com.darkssh.client.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

        // Mutex to protect tab operations from race conditions
        private val tabMutex = Mutex()

        // Thread-safe flag for switching to new tab (protected by tabMutex)
        private var pendingSwitchToNewTab = false

        init {
            loadTabs()
        }

        private fun loadTabs() {
            viewModelScope.launch {
                tabRepository.getAllTabs().collect { tabs ->
                    tabMutex.withLock {
                        val previousSize = _tabs.value.size
                        _tabs.value = tabs

                        // If a new tab was added and we should switch to it
                        if (pendingSwitchToNewTab && tabs.size > previousSize) {
                            _currentTabIndex.value = tabs.size - 1
                            pendingSwitchToNewTab = false
                        } else {
                            // Ensure currentTabIndex is valid
                            if (_currentTabIndex.value >= tabs.size) {
                                _currentTabIndex.value = (tabs.size - 1).coerceAtLeast(0)
                            }
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
                tabMutex.withLock {
                    val currentTabs = _tabs.value
                    val position = currentTabs.size

                    // Try to reuse osType from previous tab of same host (avoid icon flicker)
                    val previousTab = currentTabs.find { it.hostId == hostId && it.type == type }
                    val cachedOsType = previousTab?.osType ?: OsType.UNKNOWN

                    val tab = Tab(
                        type = type,
                        hostId = hostId,
                        position = position,
                        title = title,
                        osType = cachedOsType,
                    )
                    DebugLogger.Tab.created(tab.id, hostId, type.name)
                    tabRepository.insertTab(tab)
                }
            }
        }

        fun createTabAndSwitch(
            type: TabType,
            hostId: Long,
            title: String = "",
        ) {
            viewModelScope.launch {
                tabMutex.withLock {
                    pendingSwitchToNewTab = true
                }
                createTab(type, hostId, title)
            }
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
            viewModelScope.launch {
                tabMutex.withLock {
                    val currentTabs = _tabs.value
                    val existingTabIndex = currentTabs.indexOfFirst {
                        it.type == type && it.hostId == hostId
                    }

                    if (existingTabIndex >= 0) {
                        // Tab already exists, switch to it
                        _currentTabIndex.value = existingTabIndex
                        DebugLogger.Tab.switched(_currentTabIndex.value, existingTabIndex)
                    } else {
                        // Create new tab and switch to it
                        pendingSwitchToNewTab = true
                    }
                }
                // If we need to create, do it outside the lock to avoid deadlock
                if (pendingSwitchToNewTab) {
                    createTab(type, hostId, title)
                }
            }
        }

        /**
         * Closes a tab and cleans up its position in the list.
         * Uses a snapshot of the current tabs to avoid race conditions with Flow updates.
         */
        fun closeTab(tabId: String) {
            viewModelScope.launch {
                tabMutex.withLock {
                    val tab = tabRepository.getTabById(tabId) ?: return@withLock
                    
                    // Take a snapshot of current state BEFORE any modifications
                    val currentTabs = _tabs.value.toList()
                    val closedTabIndex = currentTabs.indexOfFirst { it.id == tabId }

                    DebugLogger.Tab.closed(tabId)
                    tabRepository.deleteTab(tab)

                    // Calculate remaining tabs from our snapshot
                    val remainingTabs = currentTabs.filter { it.id != tabId }
                    
                    // Reorder remaining tabs based on snapshot
                    remainingTabs.forEachIndexed { index, t ->
                        if (t.position != index) {
                            tabRepository.updateTabPosition(t.id, index)
                        }
                    }

                    // Adjust currentTabIndex if needed
                    if (closedTabIndex >= 0 && remainingTabs.isNotEmpty()) {
                        // If closed tab was at or before current, shift index back
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
        }

        fun switchTab(index: Int) {
            viewModelScope.launch {
                tabMutex.withLock {
                    val currentTabs = _tabs.value
                    if (index >= 0 && index < currentTabs.size) {
                        val oldIndex = _currentTabIndex.value
                        _currentTabIndex.value = index
                        if (oldIndex != index) {
                            DebugLogger.Tab.switched(oldIndex, index)
                        }
                    }
                }
            }
        }

        fun reorderTabs(fromIndex: Int, toIndex: Int) {
            viewModelScope.launch {
                tabMutex.withLock {
                    val currentTabs = _tabs.value.toMutableList()
                    if (fromIndex !in currentTabs.indices || toIndex !in currentTabs.indices) return@withLock
                    
                    val tab = currentTabs.removeAt(fromIndex)
                    currentTabs.add(toIndex, tab)

                    // Update positions in database
                    currentTabs.forEachIndexed { index, t ->
                        tabRepository.updateTabPosition(t.id, index)
                    }

                    // Adjust currentTabIndex if it was affected by the reorder
                    val currentIndex = _currentTabIndex.value
                    when {
                        currentIndex == fromIndex -> _currentTabIndex.value = toIndex
                        fromIndex < currentIndex && toIndex >= currentIndex -> _currentTabIndex.value--
                        fromIndex > currentIndex && toIndex <= currentIndex -> _currentTabIndex.value++
                    }
                }
            }
        }

        /**
         * Updates the title of every open tab for a given host.
         * Called when the host is renamed so open tabs reflect the new nickname live.
         * No-op if no tabs exist for the host (or title is unchanged).
         */
        fun updateTabsForHost(hostId: Long, newTitle: String) {
            viewModelScope.launch {
                tabMutex.withLock {
                    val tabsForHost = _tabs.value.filter { it.hostId == hostId && it.title != newTitle }
                    if (tabsForHost.isEmpty()) return@withLock
                    tabsForHost.forEach { tab ->
                        tabRepository.updateTab(tab.copy(title = newTitle))
                    }
                    DebugLogger.Tab.updated(tabsForHost.size, hostId)
                }
            }
        }

        fun getCurrentTab(): Tab? {
            val index = _currentTabIndex.value
            return _tabs.value.getOrNull(index)
        }
    }
