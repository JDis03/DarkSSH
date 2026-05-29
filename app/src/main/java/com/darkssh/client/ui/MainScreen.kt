package com.darkssh.client.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.BackHandler
import com.darkssh.client.data.entity.TabType
import com.darkssh.client.service.TerminalService
import com.darkssh.client.ui.screens.HostListScreen
import com.darkssh.client.ui.screens.HostEditorScreen
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
    var showExitDialog by remember { mutableStateOf(false) }
    var showHostEditor by remember { mutableStateOf(false) }
    var editingHostId by remember { mutableStateOf<Long?>(null) }
    
    // Get context for finishing activity
    val context = LocalContext.current
    
    // Get tabs state to conditionally hide bottom bar
    val tabs by tabManager.tabs.collectAsState()
    
    // Handle back gesture
    BackHandler {
        when (selectedTab) {
            1 -> {
                // In Tabs: go to Hosts
                selectedTab = 0
            }
            2 -> {
                // In Settings: go to Hosts
                selectedTab = 0
            }
            0 -> {
                // In Hosts: ask for confirmation to exit
                showExitDialog = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            // Only show bottom bar when in Hosts or Settings, not when tabs are open
            if (selectedTab != 1 || tabs.isEmpty()) {
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
            }
        },
    ) { paddingValues ->
        when (selectedTab) {
            0 -> {
                // Tab 1: Hosts
                if (showHostEditor) {
                    HostEditorScreen(
                        onSave = {
                            showHostEditor = false
                            editingHostId = null
                        },
                        onCancel = {
                            showHostEditor = false
                            editingHostId = null
                        },
                    )
                } else {
                    HostListScreen(
                        modifier = Modifier.padding(paddingValues),
                        onHostClick = { host ->
                            tabManager.createTabAndSwitch(TabType.SSH_TERMINAL, host.id, host.nickname)
                            selectedTab = 1 // Switch to Tabs view
                        },
                        onAddHostClick = {
                            editingHostId = null
                            showHostEditor = true
                        },
                        onEditHostClick = { host ->
                            editingHostId = host.id
                            showHostEditor = true
                        },
                        onSftpClick = { host ->
                            tabManager.createTabAndSwitch(TabType.SFTP_BROWSER, host.id, "SFTP: ${host.nickname}")
                            selectedTab = 1 // Switch to Tabs view
                        },
                    )
                }
            }
            1 -> {
                // Tab 2: Tabs (Vivaldi-style tabbed interface)
                TabbedMainScreen(
                    terminalService = terminalService,
                    tabManager = tabManager,
                    onBack = { selectedTab = 0 }, // Back to Hosts
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
    
    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit DarkSSH?") },
            text = { Text("Are you sure you want to exit the application?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Exit the app
                        (context as? ComponentActivity)?.finishAffinity()
                    }
                ) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
