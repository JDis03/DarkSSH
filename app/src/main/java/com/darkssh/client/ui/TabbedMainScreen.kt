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
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.rememberCoroutineScope
import com.darkssh.client.data.entity.TabType
import com.darkssh.client.service.TerminalService
import com.darkssh.client.ui.components.TabBar
import com.darkssh.client.ui.screens.ConsoleScreen
import com.darkssh.client.ui.screens.SftpScreen
import com.darkssh.client.ui.viewmodel.TabManager
import com.darkssh.client.ui.screens.viewmodel.SftpViewModel
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
    
    // Ensure currentTabIndex is always valid
    val safeTabIndex = if (tabs.isNotEmpty()) currentTabIndex.coerceIn(0, tabs.size - 1) else 0
    
    val pagerState = rememberPagerState(
        initialPage = safeTabIndex,
        pageCount = { tabs.size }
    )
    
    // Create a state for current visible page that triggers recomposition
    val currentVisiblePage by remember { 
        derivedStateOf { pagerState.currentPage.coerceIn(0, tabs.size - 1).takeIf { tabs.isNotEmpty() } ?: 0 } 
    }

    // Track if we're currently syncing to avoid loops
    var isSyncing by remember { mutableStateOf(false) }
    
    // Sync pager with ViewModel (source of truth: TabManager)
    LaunchedEffect(safeTabIndex, tabs.size) {
        if (tabs.isNotEmpty() && pagerState.currentPage != safeTabIndex && !isSyncing) {
            Timber.d("TabbedMainScreen: Syncing pager to TabManager: currentPage=${pagerState.currentPage} -> safeTabIndex=$safeTabIndex")
            isSyncing = true
            // Use scrollToPage (instant) to avoid race condition during recomposition
            pagerState.scrollToPage(safeTabIndex)
            isSyncing = false
        }
    }

    // Sync ViewModel when user manually swipes (but not when we're syncing programmatically)
    LaunchedEffect(pagerState.settledPage) {
        // Only sync when user gesture completes (settledPage changes) and we're not syncing
        if (tabs.isNotEmpty() && pagerState.settledPage != currentTabIndex && !isSyncing) {
            Timber.d("TabbedMainScreen: User swiped to page ${pagerState.settledPage}, syncing TabManager")
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
                    onAddTab = { onBack() }, // Go to Hosts tab to add new tab
                    onCloseTab = { tabId ->
                        val tab = tabs.find { it.id == tabId }
                        
                        // Close TerminalBridge if SSH tab
                        if (tab?.type == TabType.SSH_TERMINAL) {
                            val bridgeToClose = terminalService?.bridges?.value?.find { it.tabId == tabId }
                            if (bridgeToClose != null) {
                                terminalService.onBridgeDisconnected(
                                    bridgeToClose,
                                    com.darkssh.client.service.DisconnectReason.USER_REQUESTED
                                )
                            }
                        }
                        
                        // Close SftpClient if SFTP tab
                        if (tab?.type == TabType.SFTP_BROWSER) {
                            val hostId = tab.hostId
                            SftpViewModel.activeClients[hostId]?.let { client ->
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            client.disconnect()
                                        }
                                        Timber.d("TabbedMainScreen: disconnected SFTP client for tab $tabId (host $hostId)")
                                    } catch (e: Exception) {
                                        Timber.w(e, "TabbedMainScreen: failed to disconnect SFTP on tab close")
                                    }
                                }
                                SftpViewModel.activeClients.remove(hostId)
                            }
                        }
                        
                        // Close the tab in database
                        tabManager.closeTab(tabId)
                    },
                )
            }

            // Horizontal pager for tab content
            if (tabs.isEmpty()) {
                // Show empty state when no tabs
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            Icons.Default.Tab,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "No tabs open",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Go to Hosts tab to open a connection",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val tab = tabs.getOrNull(page) ?: return@HorizontalPager
                    // Use the derived state to ensure recomposition when current page changes
                    val isVisible = currentVisiblePage == page

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
                            )
                        }
                    }
                }
            }
        }
    }
}
