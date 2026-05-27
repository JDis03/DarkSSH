package com.darkssh.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.darkssh.client.util.FileLoggingTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogsScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    var logContent by remember { mutableStateOf("Loading logs...") }
    var isRefreshing by remember { mutableStateOf(false)}
    var currentLogFile by remember { mutableStateOf<File?>(null) }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Function to read log file
    suspend fun readLogs(): String = withContext(Dispatchers.IO) {
        try {
            val logDir = File(context.cacheDir, "logs")
            val logFiles = logDir.listFiles { file ->
                file.name.startsWith("darkssh_") && file.name.endsWith(".log")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
            
            if (logFiles.isEmpty()) {
                currentLogFile = null
                return@withContext "No logs found.\n\nLogs will appear here once you perform actions like uploading files."
            }
            
            // Read the latest log file
            val latestLog = logFiles.first()
            currentLogFile = latestLog
            val content = latestLog.readText()
            
            // Show last 10KB to avoid memory issues
            if (content.length > 10000) {
                "... (showing last 10KB)\n\n" + content.takeLast(10000)
            } else {
                content
            }
        } catch (e: Exception) {
            currentLogFile = null
            "Error reading logs: ${e.message}"
        }
    }
    
    // Copy to clipboard
    fun copyToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("DarkSSH Logs", logContent)
        clipboard.setPrimaryClip(clip)
        snackbarMessage = "Logs copied to clipboard"
        showSnackbar = true
    }
    
    // Share log file
    fun shareLogFile() {
        currentLogFile?.let { file ->
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "DarkSSH Debug Logs")
                    putExtra(Intent.EXTRA_TEXT, "DarkSSH debug logs attached")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share logs via"))
            } catch (e: Exception) {
                snackbarMessage = "Error sharing: ${e.message}"
                showSnackbar = true
            }
        } ?: run {
            snackbarMessage = "No log file to share"
            showSnackbar = true
        }
    }
    
    // Auto-refresh logs every 2 seconds
    LaunchedEffect(Unit) {
        while (true) {
            logContent = readLogs()
            // Auto-scroll to bottom
            scrollState.animateScrollTo(scrollState.maxValue)
            delay(2000) // Refresh every 2 seconds
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Logs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { copyToClipboard() }) {
                        Icon(Icons.Default.ContentCopy, "Copy to clipboard")
                    }
                    IconButton(onClick = { shareLogFile() }) {
                        Icon(Icons.Default.Share, "Share log file")
                    }
                    IconButton(
                        onClick = {
                            isRefreshing = true
                            // Clear logs using FileLoggingTree if accessible
                            val logDir = File(context.cacheDir, "logs")
                            logDir.listFiles()?.forEach { it.delete() }
                            logContent = "Logs cleared."
                            currentLogFile = null
                            isRefreshing = false
                        }
                    ) {
                        Icon(Icons.Default.Clear, "Clear logs")
                    }
                    IconButton(
                        onClick = {
                            isRefreshing = true
                        }
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            if (logContent.isEmpty() || logContent == "Loading logs...") {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                Text(
                    text = logContent,
                    color = Color(0xFFCCCCCC),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(8.dp)
                )
            }
        }
    }
    
    // Trigger refresh when flag is set
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            logContent = readLogs()
            isRefreshing = false
        }
    }
    
    // Show snackbar
    LaunchedEffect(showSnackbar) {
        if (showSnackbar) {
            snackbarHostState.showSnackbar(snackbarMessage)
            showSnackbar = false
        }
    }
}
