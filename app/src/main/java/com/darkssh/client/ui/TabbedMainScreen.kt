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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.darkssh.client.data.entity.TabType
import com.darkssh.client.service.TerminalService
import com.darkssh.client.ui.components.TabBar
import com.darkssh.client.ui.screens.ConsoleScreen
import com.darkssh.client.ui.screens.SftpScreen
import com.darkssh.client.ui.viewmodel.TabManager

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabbedMainScreen(
    terminalService: TerminalService? = null,
    tabManager: TabManager = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val tabs by tabManager.tabs.collectAsState()
    val currentTabIndex by tabManager.currentTabIndex.collectAsState()
    
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

    // Sync pager with ViewModel (source of truth: TabManager)
    LaunchedEffect(safeTabIndex, tabs.size) {
        if (tabs.isNotEmpty() && pagerState.currentPage != safeTabIndex) {
            // Use scrollToPage (instant) to avoid race condition during recomposition
            pagerState.scrollToPage(safeTabIndex)
        }
    }

    // Sync ViewModel when user manually swipes
    LaunchedEffect(pagerState.settledPage) {
        // Only sync when user gesture completes (settledPage changes)
        if (tabs.isNotEmpty() && pagerState.settledPage != currentTabIndex) {
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
                        // Close the bridge associated with this tab
                        val bridgeToClose = terminalService?.bridges?.value?.find { it.tabId == tabId }
                        if (bridgeToClose != null) {
                            terminalService.onBridgeDisconnected(
                                bridgeToClose,
                                com.darkssh.client.service.DisconnectReason.USER_REQUESTED
                            )
                        }
                        // Close the tab
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
                                onBack = { /* Tabs don't have back */ },
                                terminalService = terminalService,
                                inTab = true,
                                tabId = tab.id,
                                isActive = isVisible,
                            )
                        }
                        TabType.SFTP_BROWSER -> {
                            SftpScreen(
                                hostId = tab.hostId,
                                onBack = { /* Tabs don't have back */ },
                                inTab = true,
                            )
                        }
                    }
                }
            }
        }
    }
}
