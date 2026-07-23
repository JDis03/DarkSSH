package com.darkssh.client.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.darkssh.client.util.AppPreferences
import com.darkssh.client.util.FontManager

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ktlint:standard:function-naming")
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onServerSettings: () -> Unit = {},
    onDebugLogs: () -> Unit = {},
    onSshKeys: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedFont by remember {
        mutableStateOf(AppPreferences.getTerminalFont(context))
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            // SSH Keys Section
            ListItem(
                headlineContent = { Text("SSH Keys") },
                supportingContent = { Text("Generate and manage keys for key-based login") },
                leadingContent = {
                    Icon(Icons.Default.Key, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable { onSshKeys() },
            )

            HorizontalDivider()

            // Server Settings Section
            ListItem(
                headlineContent = { Text("Server Settings") },
                supportingContent = { Text("Configure SFTP server") },
                leadingContent = {
                    Icon(Icons.Default.Storage, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable { onServerSettings() },
            )

            HorizontalDivider()

            // Debug Logs Section
            ListItem(
                headlineContent = { Text("Debug Logs") },
                supportingContent = { Text("View transfer logs and debug info") },
                leadingContent = {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable { onDebugLogs() },
            )

            HorizontalDivider()

            // Terminal Font Section
            Text(
                "Terminal Font",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                FontManager.FontPreset.values().forEach { preset ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedFont == preset,
                                    onClick = {
                                        selectedFont = preset
                                        AppPreferences.setTerminalFont(context, preset)
                                        FontManager.clearCache()
                                    },
                                ).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedFont == preset,
                            onClick = null,
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                preset.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (preset == FontManager.FontPreset.FIRA_CODE) {
                                Text(
                                    "Includes programming ligatures: != === -> => ...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    "Font change takes effect on next terminal connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

        }
    }
}
