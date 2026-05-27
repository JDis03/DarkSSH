package com.darkssh.client.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
) {
    val tabs by tabManager.tabs.collectAsState()
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    var showHostPicker by remember { mutableStateOf(false) }
    var showAddTabDialog by remember { mutableStateOf(false) }

    // Sync pager with tab manager
    LaunchedEffect(pagerState.currentPage) {
        tabManager.switchTab(pagerState.currentPage)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column {
            // Tab bar at top
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
                // Show host list when no tabs
                HostListScreen(
                    onHostClick = { host ->
                        tabManager.createTab(TabType.SSH_TERMINAL, host.id, host.nickname)
                    },
                    onAddHostClick = { /* TODO: Navigate to host editor */ },
                    onEditHostClick = { /* TODO: Navigate to host editor */ },
                    onSftpClick = { host ->
                        tabManager.createTab(TabType.SFTP_BROWSER, host.id, "SFTP: ${host.nickname}")
                    },
                )
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
                            )
                        }
                        TabType.SFTP_BROWSER -> {
                            SftpScreen(
                                hostId = tab.hostId,
                                onBack = { /* Tabs don't have back */ },
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
            Surface(modifier = Modifier.fillMaxSize()) {
                HostListScreen(
                    onHostClick = { host ->
                        tabManager.createTab(TabType.SSH_TERMINAL, host.id, host.nickname)
                        showHostPicker = false
                    },
                    onAddHostClick = { /* TODO */ },
                    onEditHostClick = { /* TODO */ },
                    onSftpClick = { host ->
                        tabManager.createTab(TabType.SFTP_BROWSER, host.id, "SFTP: ${host.nickname}")
                        showHostPicker = false
                    },
                )
            }
        }
    }
}
