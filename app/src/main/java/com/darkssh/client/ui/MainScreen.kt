package com.darkssh.client.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.darkssh.client.data.entity.TabType
import com.darkssh.client.service.TerminalService
import com.darkssh.client.ui.screens.HostListScreen
import com.darkssh.client.ui.screens.SettingsScreen
import com.darkssh.client.ui.screens.ServerSettingsScreen
import com.darkssh.client.ui.screens.DebugLogsScreen
import com.darkssh.client.ui.viewmodel.TabManager

@Composable
fun MainScreen(
    terminalService: TerminalService? = null,
    tabManager: TabManager = hiltViewModel(),
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showServerSettings by remember { mutableStateOf(false) }
    var showDebugLogs by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Computer, contentDescription = null) },
                    label = { Text("Hosts") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Tab, contentDescription = null) },
                    label = { Text("Tabs") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                )
            }
        },
    ) { paddingValues ->
        when (selectedTab) {
            0 -> {
                // Tab 1: Hosts
                HostListScreen(
                    modifier = Modifier.padding(paddingValues),
                    onHostClick = { host ->
                        tabManager.createOrSwitchToTab(TabType.SSH_TERMINAL, host.id, host.nickname)
                        selectedTab = 1 // Switch to Tabs view
                    },
                    onAddHostClick = { /* TODO: Navigate to host editor */ },
                    onEditHostClick = { /* TODO: Navigate to host editor */ },
                    onSftpClick = { host ->
                        tabManager.createOrSwitchToTab(TabType.SFTP_BROWSER, host.id, "SFTP: ${host.nickname}")
                        selectedTab = 1 // Switch to Tabs view
                    },
                )
            }
            1 -> {
                // Tab 2: Tabs (Vivaldi-style tabbed interface)
                TabbedMainScreen(
                    terminalService = terminalService,
                    tabManager = tabManager,
                )
            }
            2 -> {
                // Tab 3: Settings
                when {
                    showServerSettings -> {
                        ServerSettingsScreen(
                            onNavigateBack = { showServerSettings = false },
                        )
                    }
                    showDebugLogs -> {
                        DebugLogsScreen(
                            onNavigateBack = { showDebugLogs = false },
                        )
                    }
                    else -> {
                        SettingsScreen(
                            onBack = { selectedTab = 0 }, // Go back to Hosts
                            onServerSettings = { showServerSettings = true },
                            onDebugLogs = { showDebugLogs = true },
                        )
                    }
                }
            }
        }
    }
}
