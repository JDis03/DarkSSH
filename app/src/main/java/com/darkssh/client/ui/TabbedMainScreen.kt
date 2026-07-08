package com.darkssh.client.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.rememberCoroutineScope
import com.darkssh.client.data.entity.Tab
import com.darkssh.client.data.entity.TabType
import com.darkssh.client.service.TerminalService
import com.darkssh.client.ui.components.TabBar
import com.darkssh.client.ui.screens.ConsoleScreen
import com.darkssh.client.ui.screens.SftpScreen
import com.darkssh.client.ui.viewmodel.TabManager
import com.darkssh.client.ui.screens.viewmodel.SftpViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabbedMainScreen(
    terminalService: TerminalService? = null,
    tabManager: TabManager = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val tabs by tabManager.tabs.collectAsState()
    val currentTabIndex by tabManager.currentTabIndex.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Safe index: clamp to valid range
    val safeTabIndex = if (tabs.isNotEmpty()) currentTabIndex.coerceIn(0, tabs.size - 1) else 0

    val pagerState = rememberPagerState(
        initialPage = safeTabIndex,
        pageCount = { tabs.size },
    )

    // SOURCE OF TRUTH: TabManager.currentTabIndex drives the pager.
    // One-directional: TabManager → pager. No feedback loop.
    LaunchedEffect(safeTabIndex) {
        if (tabs.isNotEmpty() && pagerState.currentPage != safeTabIndex) {
            Timber.d("TabbedMainScreen: TabManager→pager: $safeTabIndex")
            pagerState.scrollToPage(safeTabIndex)
        }
    }

    // USER SWIPE: pager → TabManager (only on settled, not during animation)
    val currentTabIndexUpdated by rememberUpdatedState(currentTabIndex)
    
    // Update active bridge when tab changes (Termius pattern: centralized control)
    // This is the SINGLE source of truth for which bridge is active
    LaunchedEffect(currentTabIndex, tabs.size) {
        val currentTab = tabs.getOrNull(currentTabIndex)
        Timber.d("TabbedMainScreen: Tab changed to index $currentTabIndex (${currentTab?.type})")
        
        if (currentTab != null) {
            when (currentTab.type) {
                TabType.SSH_TERMINAL -> {
                    // Find the bridge for this SSH tab
                    val bridges = terminalService?.bridges?.value ?: emptyList()
                    val bridge = bridges.firstOrNull { it.tabId == currentTab.id }
                    if (bridge != null) {
                        Timber.d("TabbedMainScreen: Setting active bridge for SSH tab ${currentTab.id}")
                        terminalService?.setActiveBridge(bridge)
                    } else {
                        Timber.d("TabbedMainScreen: No bridge found for SSH tab ${currentTab.id}")
                    }
                }
                TabType.SFTP_BROWSER -> {
                    // SFTP doesn't need active bridge for notifications
                    Timber.d("TabbedMainScreen: Clearing active bridge for SFTP tab")
                    terminalService?.setActiveBridge(null)
                }
            }
        } else {
            Timber.d("TabbedMainScreen: No current tab, clearing active bridge")
            terminalService?.setActiveBridge(null)
        }
    }

    // Reactive: when a new bridge is added, check if it should be active for the current tab
    // This handles the case where the bridge is created AFTER the tab change effect runs
    val bridges by terminalService?.bridges?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    LaunchedEffect(bridges.size, currentTabIndex) {
        val currentTab = tabs.getOrNull(currentTabIndex)
        if (currentTab?.type == TabType.SSH_TERMINAL) {
            val bridge = bridges.firstOrNull { it.tabId == currentTab.id }
            if (bridge != null && terminalService?.activeBridge?.value != bridge) {
                Timber.d("TabbedMainScreen: New bridge detected for current tab ${currentTab.id}, updating active bridge")
                terminalService?.setActiveBridge(bridge)
            }
        }
    }

    // USER SWIPE: notify TabManager when swipe gesture completes
    LaunchedEffect(pagerState.settledPage) {
        if (tabs.isNotEmpty() && pagerState.settledPage != currentTabIndexUpdated) {
            Timber.d("TabbedMainScreen: User swiped to page ${pagerState.settledPage}")
            tabManager.switchTab(pagerState.settledPage)
        }
    }

    // Handle back button: go back to Hosts tab
    BackHandler(enabled = tabs.isNotEmpty()) {
        onBack()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            // Tab bar
            if (tabs.isNotEmpty()) {
                TabBar(
                    tabs = tabs,
                    pagerState = pagerState,
                    terminalService = terminalService,
                    onAddTab = { onBack() },
                    onCloseTab = { tabId ->
                        closeTab(tabId, tabs, terminalService, coroutineScope, tabManager)
                    },
                    onCloseOthers = { keepId ->
                        // Close all tabs EXCEPT keepId — full cleanup per tab
                        tabs.filter { it.id != keepId }.forEach { tab ->
                            closeTab(tab.id, tabs, terminalService, coroutineScope, tabManager)
                        }
                    },
                    onCloseAll = {
                        // Close every tab — full cleanup per tab
                        tabs.toList().forEach { tab ->
                            closeTab(tab.id, tabs, terminalService, coroutineScope, tabManager)
                        }
                    },
                )
            }

            // Horizontal pager for tab content
            if (tabs.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.Tab,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "No open sessions",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { onBack() }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("  New connection")
                        }
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val tab = tabs.getOrNull(page) ?: return@HorizontalPager
                    val isVisible = pagerState.currentPage == page

                    when (tab.type) {
                        TabType.SSH_TERMINAL -> {
                            ConsoleScreen(
                                hostId = tab.hostId,
                                onBack = { 
                                    // Close tab when user clicks "Close" in disconnect overlay
                                    tabManager.closeTab(tab.id)
                                },
                                terminalService = terminalService,
                                inTab = true,
                                tabId = tab.id,
                                isActive = isVisible,
                            )
                        }
                        TabType.SFTP_BROWSER -> {
                            SftpScreen(
                                hostId = tab.hostId,
                                onBack = { 
                                    // Close tab when user clicks back
                                    tabManager.closeTab(tab.id)
                                },
                                inTab = true,
                                isActive = isVisible,
                                terminalService = terminalService,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full tab close: disconnects SSH bridge / SFTP client, then removes from DB.
 * Single source of truth — used by close, close-others, close-all.
 */
private fun closeTab(
    tabId: String,
    tabs: List<Tab>,
    terminalService: TerminalService?,
    scope: CoroutineScope,
    tabManager: com.darkssh.client.ui.viewmodel.TabManager,
) {
    val tab = tabs.find { it.id == tabId } ?: return

    // SSH: disconnect bridge
    if (tab.type == TabType.SSH_TERMINAL) {
        val bridge = terminalService?.bridges?.value?.find { it.tabId == tabId }
        if (bridge != null) {
            terminalService.onBridgeDisconnected(
                bridge,
                com.darkssh.client.service.DisconnectReason.USER_REQUESTED,
            )
        }
    }

    // SFTP: disconnect client
    if (tab.type == TabType.SFTP_BROWSER) {
        com.darkssh.client.ui.screens.viewmodel.SftpViewModel.activeClients[tab.hostId]?.let { client ->
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { client.disconnect() }
                    Timber.d("closeTab: SFTP disconnected for tab $tabId (host ${tab.hostId})")
                } catch (e: Exception) {
                    Timber.w(e, "closeTab: SFTP disconnect failed for tab $tabId")
                }
            }
            com.darkssh.client.ui.screens.viewmodel.SftpViewModel.activeClients.remove(tab.hostId)
        }
    }

    // DB: remove tab
    tabManager.closeTab(tabId)
}
