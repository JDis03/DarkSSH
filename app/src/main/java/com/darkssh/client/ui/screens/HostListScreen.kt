package com.darkssh.client.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.darkssh.client.data.entity.Host
import com.darkssh.client.ui.screens.viewmodel.HostListViewModel
import kotlinx.coroutines.delay

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(
    onHostClick: (Host) -> Unit,
    onAddHostClick: () -> Unit,
    onEditHostClick: (Host) -> Unit,
    onDeleteHostClick: (Host) -> Unit = {},
    onSftpClick: (Host) -> Unit = {},
    onCloneClick: (Host) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HostListViewModel = hiltViewModel(),
) {
    val hosts by viewModel.hosts.collectAsState()
    var expandedHostId by remember { mutableStateOf<Long?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("DarkSSH") },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHostClick) {
                Icon(Icons.Default.Add, contentDescription = "Add host")
            }
        },
    ) { paddingValues ->
        if (hosts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Computer,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "No hosts configured",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Tap + to add a new SSH connection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
            ) {
                items(
                    count = hosts.size,
                    key = { index -> hosts[index].id },
                ) { index ->
                    val host = hosts[index]
                    HostCard(
                        host = host,
                        onHostClick = { onHostClick(host) },
                        onEditClick = { onEditHostClick(host) },
                        onDeleteClick = { onDeleteHostClick(host) },
                        onSftpClick = { onSftpClick(host) },
                        onCloneClick = { onCloneClick(host) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostCard(
    host: Host,
    onHostClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSftpClick: () -> Unit,
    onCloneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var show by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swipe right to edit
                    onEditClick()
                    false // Don't dismiss
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swipe left to delete
                    show = false
                    true
                }
                else -> false
            }
        }
    )

    // Trigger delete after animation
    LaunchedEffect(show) {
        if (!show) {
            delay(300) // Wait for animation
            onDeleteClick()
        }
    }

    AnimatedVisibility(
        visible = show,
        exit = shrinkVertically(
            animationSpec = tween(300),
            shrinkTowards = Alignment.Top
        ) + fadeOut()
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                // Background colors for swipe actions
                val alignment: Alignment
                val icon: androidx.compose.ui.graphics.vector.ImageVector
                val color: Color
                val iconTint: Color
                
                when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        // Swipe right → Edit (blue)
                        alignment = Alignment.CenterStart
                        icon = Icons.Default.Edit
                        color = MaterialTheme.colorScheme.primaryContainer
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        // Swipe left → Delete (red)
                        alignment = Alignment.CenterEnd
                        icon = Icons.Default.Delete
                        color = MaterialTheme.colorScheme.errorContainer
                        iconTint = MaterialTheme.colorScheme.onErrorContainer
                    }
                    else -> {
                        alignment = Alignment.Center
                        icon = Icons.Default.Computer
                        color = Color.Transparent
                        iconTint = Color.Transparent
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color)
                        .padding(horizontal = 20.dp),
                    contentAlignment = alignment
                ) {
                    if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = iconTint,
                        )
                    }
                }
            },
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true,
        ) {
            Card(
                onClick = onHostClick,
                modifier = modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 4.dp,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Icon + Info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Default.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = host.nickname.ifBlank { host.hostname },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${host.username}@${host.hostname}:${host.port}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    
                    // Menu button
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("SFTP File Manager") },
                                onClick = {
                                    showMenu = false
                                    onSftpClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clone Host") },
                                onClick = {
                                    showMenu = false
                                    onCloneClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = null,
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}