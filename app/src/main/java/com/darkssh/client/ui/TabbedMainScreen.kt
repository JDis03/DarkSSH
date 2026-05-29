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
import androidx.compose.runtime.getValue
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
    // Ensure initialPage is valid (within bounds)
    val initialPage = if (tabs.isNotEmpty()) currentTabIndex.coerceIn(0, tabs.size - 1) else 0
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { tabs.size })

    // Sync pager with current tab index when tabs change
    LaunchedEffect(currentTabIndex, tabs.size) {
        if (tabs.isNotEmpty()) {
            // Coerce to valid range first
            val targetPage = currentTabIndex.coerceIn(0, tabs.size - 1)
            if (pagerState.currentPage != targetPage && targetPage < tabs.size) {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    // Sync tab manager when user swipes
    LaunchedEffect(pagerState.currentPage) {
        if (tabs.isNotEmpty() && pagerState.currentPage < tabs.size) {
            tabManager.switchTab(pagerState.currentPage)
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
                    onAddTab = { onBack() }, // Go to Hosts tab to add new tab
                    onCloseTab = { tabId ->
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
                    val isCurrentPage = pagerState.currentPage == page

                    when (tab.type) {
                        TabType.SSH_TERMINAL -> {
                            ConsoleScreen(
                                hostId = tab.hostId,
                                onBack = { /* Tabs don't have back */ },
                                terminalService = terminalService,
                                inTab = true,
                                tabId = tab.id,
                                isActive = isCurrentPage,
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
