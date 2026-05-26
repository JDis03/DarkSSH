package com.darkssh.client.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.darkssh.client.ui.screens.viewmodel.ServerSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ServerSettingsViewModel = hiltViewModel()
) {
    val serverState by viewModel.serverState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Server Status Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "SFTP Server",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (serverState.isRunning) "Running" else "Stopped",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (serverState.isRunning) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            if (serverState.isRunning) {
                                Text(
                                    text = "Ready for connections",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        FilledTonalButton(
                            onClick = { viewModel.toggleServer() }
                        ) {
                            Icon(
                                imageVector = if (serverState.isRunning) Icons.Default.PowerOff else Icons.Default.Power,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (serverState.isRunning) "Stop" else "Start")
                        }
                    }
                }
            }
            
            // Connection Info Card
            if (serverState.isRunning) {
                val context = LocalContext.current
                val ip = serverState.deviceIp
                val port = serverState.sftpPort
                val user = serverState.username
                val pass = "darkssh"
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Connection Info", style = MaterialTheme.typography.titleMedium)
                        InfoRow("IP", ip)
                        InfoRow("Port", port.toString())
                        InfoRow("User", user)
                        InfoRow("Password", pass)
                        InfoRow("Root folder", "/sdcard/ (Downloads, Movies, DCIM...)")

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        // --- Command blocks with copy ---
                        CommandBlock("sftp connect", "sftp -P $port $user@$ip", context)
                        CommandBlock("scp download", "scp -P $port \"$user@$ip:/sdcard/Download/file.mp4\" .", context)
                        CommandBlock("Browse files", "echo 'ls /sdcard/Download/; ls /sdcard/Movies/; ls /sdcard/DCIM/' | sftp -P $port $user@$ip", context)

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("AI Prompt (copy this for ChatGPT/Claude)", style = MaterialTheme.typography.labelMedium)
                        
                        val aiPrompt = buildString {
                            append("Connect by SFTP: host=$ip, port=$port, user=$user, password=$pass. ")
                            append("Root is /sdcard/. ")
                            append("Browse /sdcard/Download/, /sdcard/Movies/, /sdcard/DCIM/ to find files. ")
                            append("Use 'get <filepath>' to download.")
                        }
                        CopyableSurface(aiPrompt, "Copy AI prompt", context)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun CommandBlock(label: String, command: String, context: Context) {
    Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    CopyableSurface(command, "Copy $label", context)
}

@Composable
private fun CopyableSurface(text: String, copyLabel: String, context: Context) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val clip = ClipData.newPlainText("cmd", text)
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ContentCopy, copyLabel, modifier = Modifier.size(16.dp))
            }
        }
    }
}
