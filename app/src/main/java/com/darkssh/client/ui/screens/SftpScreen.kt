package com.darkssh.client.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.darkssh.client.transport.SftpAuthState
import com.darkssh.client.transport.SftpEntry
import kotlinx.coroutines.launch
import com.darkssh.client.transport.TransferProgress
import com.darkssh.client.ui.screens.viewmodel.SftpViewModel
import com.darkssh.client.ui.screens.viewmodel.SortBy
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("ktlint:standard:function-naming")
@Composable
fun SftpScreen(
    hostId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    inTab: Boolean = false,
    viewModel: SftpViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    
    // Debug: Log when transferProgress changes
    androidx.compose.runtime.LaunchedEffect(uiState.transferProgress) {
        timber.log.Timber.d("SftpScreen: transferProgress changed to ${uiState.transferProgress?.percentage}%")
    }
    var showMkdirDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<SftpEntry?>(null) }
    var showRenameDialog by remember { mutableStateOf<SftpEntry?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            try {
                val originalName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else "unknown"
                } ?: "unknown"
                val tempFile = File.createTempFile("upload_", ".tmp", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                viewModel.uploadFile(tempFile, originalName)
            } catch (e: Exception) {
                Timber.e(e, "Failed to pick file for upload")
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(hostId)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCurrentDirectory()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showMkdirDialog) {
        MkdirDialog(
            onDismiss = { showMkdirDialog = false },
            onConfirm = { name ->
                viewModel.createDirectory(name)
                showMkdirDialog = false
            },
        )
    }

    showRenameDialog?.let { entry ->
        RenameDialog(
            currentName = entry.name,
            onDismiss = { showRenameDialog = null },
            onConfirm = { newName ->
                viewModel.renameEntry(entry, newName)
                showRenameDialog = null
            },
        )
    }

    uiState.renameConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRenameConflict() },
            title = { Text("Replace?") },
            text = {
                Text(
                    "A ${if (conflict.entry.isDirectory) "folder" else "file"} named '${conflict.newName}' already exists. Replace it?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmRenameOverwrite() },
                ) {
                    Text("Replace", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRenameConflict() }) {
                    Text("Cancel")
                }
            },
        )
    }

    uiState.downloadConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDownloadConflict() },
            title = { Text("Replace file?") },
            text = {
                Text("A file named '${conflict.fileName}' already exists in Downloads. Replace it?")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDownloadOverwrite() },
                ) {
                    Text("Replace", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDownloadConflict() }) {
                    Text("Cancel")
                }
            },
        )
    }

    showDeleteDialog?.let { entry ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete ${entry.name}?") },
            text = {
                Text(
                    if (entry.isDirectory) "Remove directory '${entry.name}'?"
                    else "Delete file '${entry.name}'?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEntry(entry)
                        showDeleteDialog = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    when (val authState = uiState.authState) {
        is SftpAuthState.NeedsPassword -> {
            SftpPasswordScreen(
                hostname = authState.hostname,
                username = authState.username,
                onConnect = { password -> viewModel.connectWithPassword(password) },
                onBack = onBack,
            )
            return
        }
        is SftpAuthState.Connecting -> {
            val h = uiState.host
            SftpConnectingScreen(hostname = h?.hostname ?: "...")
            return
        }
        is SftpAuthState.Failed -> {
            SftpErrorScreen(
                message = authState.message,
                onRetry = { viewModel.dismissAuthError() },
                onBack = onBack,
            )
            return
        }
        is SftpAuthState.Authenticated -> {
            // Continue to file browser below
        }
        else -> {}
    }

    // Actions Composable - to reuse in both TopAppBar modes
    @Composable
    fun SftpActions() {
                    IconButton(onClick = { viewModel.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                    }
                    IconButton(onClick = { viewModel.listDirectory("/") }) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                    IconButton(onClick = { showMkdirDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                    }
                    IconButton(
                        onClick = {
                            pickFileLauncher.launch(arrayOf("*/*"))
                        },
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = "Upload")
                    }
                    // Paste button (only visible when clipboard has data)
                    if (viewModel.hasClipboardData()) {
                        IconButton(onClick = { viewModel.pasteFiles() }) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = "Paste (${viewModel.getClipboardFileCount()} files)",
                                tint = when (viewModel.getClipboardOperation()) {
                                    com.darkssh.client.ui.screens.viewmodel.SftpClipboard.Operation.CUT -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            SortBy.entries.forEach { sortBy ->
                                val label = when (sortBy) {
                                    SortBy.NAME -> "Name"
                                    SortBy.SIZE -> "Size"
                                    SortBy.DATE -> "Date"
                                    SortBy.TYPE -> "Type"
                                }
                                val arrow = if (uiState.sortBy == sortBy) {
                                    if (uiState.sortAscending) " ▲" else " ▼"
                                } else ""
                                DropdownMenuItem(
                                    text = { Text("$label$arrow") },
                                    onClick = {
                                        viewModel.changeSortStyle(sortBy)
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Show hidden files") },
                                onClick = {
                                    viewModel.toggleShowHiddenFiles()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Checkbox(
                                        checked = uiState.showHiddenFiles,
                                        onCheckedChange = null,
                                    )
                                },
                            )
                            if (uiState.selectedEntries.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Delete selected") },
                                    onClick = { showMenu = false },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                    },
                                )
                            }
                        }
                    }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.imeAnimationTarget,
        topBar = {
            // Show TopAppBar with different content based on inTab
            if (!inTab) {
                // Full TopAppBar with title and navigation
                TopAppBar(
                    title = {
                        Text(
                            uiState.currentPath,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = { SftpActions() },
                )
            } else {
                // Compact TopAppBar with only actions (for tab mode)
                TopAppBar(
                    title = { }, // Empty title, TabBar handles this
                    actions = { SftpActions() },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(paddingValues)
                .padding(paddingValues)
                .windowInsetsPadding(WindowInsets.imeAnimationTarget),
        ) {
            BreadcrumbBar(
                currentPath = uiState.currentPath,
                onNavigate = { path -> viewModel.listDirectory(path) },
            )

            // Show transfer progress dialog (like File Manager+)
            uiState.transferProgress?.let { progress ->
                TransferProgressDialog(
                    progress = progress,
                    isUpload = true,  // TODO: Detect if upload or download
                    onHide = { 
                        viewModel.hideTransferDialog()
                    },
                    onCancel = { 
                        viewModel.cancelTransfer()
                    }
                )
            }

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refreshCurrentDirectory() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.entries, key = { it.path }) { entry ->
                        SftpEntryRow(
                            entry = entry,
                            onClick = {
                                if (entry.isDirectory || entry.isSymlink) {
                                    viewModel.navigateTo(entry.path)
                                } else {
                                    viewModel.requestDownload(entry.path, entry.name)
                                }
                            },
                            onRename = { showRenameDialog = entry },
                            onDelete = { showDeleteDialog = entry },
                            onDownload = {
                                viewModel.requestDownload(entry.path, entry.name)
                            },
                            onCopy = {
                                viewModel.copyFiles(listOf(entry))
                                scope.launch {
                                    snackbarHostState.showSnackbar("Copied ${entry.name}")
                                }
                            },
                            onCut = {
                                viewModel.cutFiles(listOf(entry))
                                scope.launch {
                                    snackbarHostState.showSnackbar("Cut ${entry.name}")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun BreadcrumbBar(
    currentPath: String,
    onNavigate: (String) -> Unit,
) {
    val parts = buildList {
        add("/")
        if (currentPath != "/") {
            val trimmed = currentPath.trimStart('/').trimEnd('/')
            if (trimmed.isNotEmpty()) {
                val segments = trimmed.split("/")
                var accumulated = ""
                for (seg in segments) {
                    accumulated = "$accumulated/$seg"
                    add(accumulated)
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        parts.forEachIndexed { index, path ->
            if (index > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val label = if (index == 0) "root" else path.substringAfterLast("/")
            Text(
                text = label,
                style = if (index == parts.lastIndex)
                    MaterialTheme.typography.labelMedium
                else
                    MaterialTheme.typography.labelSmall,
                color = if (index == parts.lastIndex)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onNavigate(path) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ktlint:standard:function-naming")
@Composable
private fun SftpPasswordScreen(
    hostname: String,
    username: String,
    onConnect: (String) -> Unit,
    onBack: () -> Unit,
) {
    var password by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SFTP - $hostname") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.size(24.dp))

            Text(
                "Connect to SFTP",
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.size(8.dp))

            Text(
                "$username@$hostname",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.size(24.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.size(16.dp))

            androidx.compose.material3.Button(
                onClick = { if (password.isNotBlank()) onConnect(password) },
                enabled = password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Connect")
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun SftpConnectingScreen(hostname: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.5f))
            Spacer(modifier = Modifier.size(16.dp))
            Text("Connecting to $hostname...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ktlint:standard:function-naming")
@Composable
private fun SftpErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    inTab: Boolean = false,
) {
    Scaffold(
        topBar = {
            if (!inTab) {
                TopAppBar(
                    title = { Text("SFTP Connection Failed") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.size(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("Go Back") }
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("ktlint:standard:function-naming")
@Composable
private fun SftpEntryRow(
    entry: SftpEntry,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onCopy: () -> Unit = {},
    onCut: () -> Unit = {},
) {
    var showContext by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showContext = true },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = if (entry.isDirectory) "Directory" else "File",
            tint = if (entry.isDirectory) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.size > 0) {
                    Text(
                        text = formatFileSize(entry.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                entry.modifiedTime?.let { mtime ->
                    Text(
                        text = formatDate(mtime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!entry.isDirectory) {
            IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Box {
            IconButton(onClick = { showContext = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
            DropdownMenu(
                expanded = showContext,
                onDismissRequest = { showContext = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = {
                        showContext = false
                        onCopy()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Cut") },
                    onClick = {
                        showContext = false
                        onCut()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ContentCut, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        showContext = false
                        onRename()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        showContext = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    },
                )
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TransferProgressBar(progress: TransferProgress) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Transferring: ${progress.filePath.substringAfterLast('/')}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = progress.speedFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LinearProgressIndicator(
            progress = { progress.percentage / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${formatFileSize(progress.transferred)} / ${formatFileSize(progress.total)} (${progress.percentage}%)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun MkdirDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Folder name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun formatFileSize(size: Long): String {
    return when {
        size >= 1_073_741_824 -> "%.1f GB".format(size / 1_073_741_824.0)
        size >= 1_048_576 -> "%.1f MB".format(size / 1_048_576.0)
        size >= 1024 -> "%.1f KB".format(size / 1024.0)
        else -> "$size B"
    }
}

private fun formatDate(timestamp: Long): String {
    return try {
        val date = Date(timestamp * 1000)
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
    } catch (_: Exception) {
        ""
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("New name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && name != currentName) onConfirm(name) },
                enabled = name.isNotBlank() && name != currentName,
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
