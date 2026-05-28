package com.darkssh.client.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.darkssh.client.data.entity.TabType
import com.darkssh.client.service.TerminalService
import com.darkssh.client.ui.components.TabBar
import com.darkssh.client.ui.screens.ConsoleScreen
import com.darkssh.client.ui.screens.HostListScreen
import com.darkssh.client.ui.screens.SftpScreen
import com.darkssh.client.ui.viewmodel.TabManager
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    var showHostPicker by remember { mutableStateOf(false) }
    var showAddTabDialog by remember { mutableStateOf(false) }

    // Sync pager with tab manager
    LaunchedEffect(pagerState.currentPage) {
        if (tabs.isNotEmpty()) {
            tabManager.switchTab(pagerState.currentPage)
        }
    }

    // Sync tab manager with pager (for programmatic tab changes)
    LaunchedEffect(currentTabIndex) {
        if (tabs.isNotEmpty() && pagerState.currentPage != currentTabIndex && currentTabIndex < tabs.size) {
            pagerState.animateScrollToPage(currentTabIndex)
        }
    }

    // Handle back button: always go to HostList first before exiting app
    BackHandler(enabled = tabs.isNotEmpty() && !showHostPicker) {
        showHostPicker = true
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
                    onAddTab = { showAddTabDialog = true },
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

                    when (tab.type) {
                        TabType.SSH_TERMINAL -> {
                            ConsoleScreen(
                                hostId = tab.hostId,
                                onBack = { /* Tabs don't have back */ },
                                terminalService = terminalService,
                                inTab = true,
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

        // Add tab dialog
        if (showAddTabDialog) {
            AlertDialog(
                onDismissRequest = { showAddTabDialog = false },
                title = { Text("New Tab") },
                text = {
                    Column {
                        Button(
                            onClick = {
                                showAddTabDialog = false
                                showHostPicker = true
                            },
                        ) {
                            Text("SSH Terminal")
                        }
                        Button(
                            onClick = {
                                showAddTabDialog = false
                                showHostPicker = true
                            },
                        ) {
                            Text("SFTP Browser")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddTabDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        // Host picker (simplified - just shows host list)
        if (showHostPicker) {
            // Handle back when host picker is shown: close it if there are tabs open
            BackHandler(enabled = tabs.isNotEmpty()) {
                showHostPicker = false
            }
            
            Surface(modifier = Modifier.fillMaxSize()) {
                HostListScreen(
                    onHostClick = { host ->
                        tabManager.createOrSwitchToTab(TabType.SSH_TERMINAL, host.id, host.nickname)
                        showHostPicker = false
                    },
                    onAddHostClick = { /* TODO */ },
                    onEditHostClick = { /* TODO */ },
                    onSftpClick = { host ->
                        tabManager.createOrSwitchToTab(TabType.SFTP_BROWSER, host.id, "SFTP: ${host.nickname}")
                        showHostPicker = false
                    },
                )
            }
        }
    }
}
