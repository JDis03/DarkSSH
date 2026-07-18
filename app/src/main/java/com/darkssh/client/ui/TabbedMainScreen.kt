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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.darkssh.client.data.entity.Tab
import com.darkssh.client.data.entity.TabType
import com.darkssh.client.service.DisconnectReason
import com.darkssh.client.service.TerminalService
import com.darkssh.client.ui.components.TabBar
import com.darkssh.client.ui.screens.ConsoleScreen
import com.darkssh.client.ui.screens.SftpScreen
import com.darkssh.client.ui.screens.viewmodel.SftpViewModel
import com.darkssh.client.ui.viewmodel.TabManager
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
    val bridges by terminalService?.bridges?.collectAsState() ?: remember { 
        androidx.compose.runtime.mutableStateOf(emptyList()) 
    }
    val coroutineScope = rememberCoroutineScope()
    
    // Safe index: use derivedStateOf for efficient recomposition
    val safeTabIndex by remember(tabs, currentTabIndex) {
        derivedStateOf {
            if (tabs.isNotEmpty()) currentTabIndex.coerceIn(0, tabs.size - 1) else 0
        }
    }

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
    LaunchedEffect(pagerState.settledPage) {
        if (tabs.isNotEmpty() && pagerState.settledPage != currentTabIndexUpdated) {
            Timber.d("TabbedMainScreen: User swiped to page ${pagerState.settledPage}")
            tabManager.switchTab(pagerState.settledPage)
        }
    }

    // CONSOLIDATED BRIDGE MANAGEMENT: Single effect handles all bridge activation scenarios
    // Triggers on: tab index change, tabs list change, bridges list change
    LaunchedEffect(currentTabIndex, tabs, bridges) {
        val currentTab = tabs.getOrNull(currentTabIndex)
        
        when {
            currentTab == null -> {
                Timber.d("TabbedMainScreen: No current tab, clearing active bridge")
                terminalService?.setActiveBridge(null)
            }
            currentTab.type == TabType.SSH_TERMINAL -> {
                val bridge = bridges.firstOrNull { it.tabId == currentTab.id }
                val currentActive = terminalService?.activeBridge?.value
                if (bridge != null && currentActive != bridge) {
                    Timber.d("TabbedMainScreen: Setting active bridge for SSH tab ${currentTab.id}")
                    terminalService?.setActiveBridge(bridge)
                } else if (bridge == null && currentActive != null) {
                    // Tab exists but bridge not yet created (connecting) - don't clear
                    Timber.d("TabbedMainScreen: SSH tab ${currentTab.id} waiting for bridge")
                }
            }
            currentTab.type == TabType.SFTP_BROWSER -> {
                Timber.d("TabbedMainScreen: Clearing active bridge for SFTP tab")
                terminalService?.setActiveBridge(null)
            }
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
 * 
 * Note: Cleanup is performed synchronously for SSH (bridge disconnect is fast)
 * and asynchronously for SFTP (network I/O). Tab is removed from DB after cleanup
 * to ensure proper resource release.
 */
private fun closeTab(
    tabId: String,
    tabs: List<Tab>,
    terminalService: TerminalService?,
    scope: CoroutineScope,
    tabManager: TabManager,
) {
    val tab = tabs.find { it.id == tabId } ?: return

    scope.launch {
        try {
            when (tab.type) {
                TabType.SSH_TERMINAL -> {
                    // SSH: disconnect bridge (synchronous, fast)
                    val bridge = terminalService?.bridges?.value?.find { it.tabId == tabId }
                    if (bridge != null) {
                        terminalService.onBridgeDisconnected(bridge, DisconnectReason.USER_REQUESTED)
                        Timber.d("closeTab: SSH bridge disconnected for tab $tabId")
                    }
                }
                TabType.SFTP_BROWSER -> {
                    // SFTP: disconnect client (async, may involve network I/O)
                    // TODO: Change key from hostId to tabId when refactor-001 is implemented
                    SftpViewModel.activeClients[tab.hostId]?.let { client ->
                        try {
                            withContext(Dispatchers.IO) { client.disconnect() }
                            Timber.d("closeTab: SFTP disconnected for tab $tabId (host ${tab.hostId})")
                        } catch (e: Exception) {
                            Timber.w(e, "closeTab: SFTP disconnect failed for tab $tabId")
                        }
                        SftpViewModel.activeClients.remove(tab.hostId)
                    }
                }
            }
        } finally {
            // Always remove tab from DB, even if cleanup failed
            tabManager.closeTab(tabId)
        }
    }
}
