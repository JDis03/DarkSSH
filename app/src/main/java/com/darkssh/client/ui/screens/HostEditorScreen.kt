package com.darkssh.client.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.darkssh.client.ui.screens.viewmodel.HostEditorViewModel

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HostEditorScreen(
    hostId: Long = -1L,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onHostSaved: (hostId: Long, newNickname: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: HostEditorViewModel = hiltViewModel(),
) {
    // Load host if editing, or clear if adding new (needed when not using NavHost navigation)
    LaunchedEffect(hostId) {
        viewModel.loadHost(hostId)
    }
    val host by viewModel.host.collectAsState()
    val pubkeys by viewModel.pubkeys.collectAsState()

    // When host is saved (edit only), notify parent so open tabs reflect the
    // new nickname without restarting the connection.
    LaunchedEffect(viewModel) {
        viewModel.savedHost.collect { (savedHostId, newNickname) ->
            onHostSaved(savedHostId, newNickname)
        }
    }
    
    // Use hostId as key to reset state when switching between add/edit
    var nickname by rememberSaveable(hostId) { mutableStateOf("") }
    var hostname by rememberSaveable(hostId) { mutableStateOf("") }
    var username by rememberSaveable(hostId) { mutableStateOf("") }
    var port by rememberSaveable(hostId) { mutableIntStateOf(22) }
    var compression by rememberSaveable(hostId) { mutableStateOf(false) }
    var stayConnected by rememberSaveable(hostId) { mutableStateOf(false) }
    var selectedPubkeyId by rememberSaveable(hostId) { mutableLongStateOf(-1L) }
    var showPubkeyDropdown by rememberSaveable(hostId) { mutableStateOf(false) }

    // Load host data when available
    LaunchedEffect(host) {
        host?.let { h ->
            nickname = h.nickname
            hostname = h.hostname
            username = h.username
            port = h.port
            compression = h.compression
            stayConnected = h.stayConnected
            selectedPubkeyId = h.pubkeyId ?: -1L
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.imeAnimationTarget,
        topBar = {
            TopAppBar(
                title = { Text(if (hostId > 0) "Edit Host" else "Add Host") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveHost(
                                nickname = nickname,
                                hostname = hostname,
                                username = username,
                                port = port,
                                compression = compression,
                                stayConnected = stayConnected,
                                pubkeyId = if (selectedPubkeyId == -1L) null else selectedPubkeyId,
                            )
                            onSave()
                        },
                        enabled = nickname.isNotBlank() && hostname.isNotBlank() && username.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .consumeWindowInsets(paddingValues)
                    .padding(paddingValues)
                    .windowInsetsPadding(WindowInsets.imeAnimationTarget)
                    .padding(16.dp),
            verticalArrangement =
                androidx.compose.foundation.layout.Arrangement
                    .spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("Nickname") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = hostname,
                onValueChange = { hostname = it },
                label = { Text("Hostname") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = username.isBlank(),
                supportingText = if (username.isBlank()) {{ Text("Username is required") }} else null,
            )

            OutlinedTextField(
                value = port.toString(),
                onValueChange = { port = it.toIntOrNull() ?: 22 },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Compression", modifier = Modifier.weight(1f))
                Switch(checked = compression, onCheckedChange = { compression = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Stay Connected", modifier = Modifier.weight(1f))
                Switch(checked = stayConnected, onCheckedChange = { stayConnected = it })
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pubkey selection
            Text(
                "SSH Key",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            // NOTE: A plain OutlinedTextField(readOnly=true) + Modifier.clickable does NOT
            // reliably open on tap — the text field's own internal pointer input handling
            // (for cursor/focus) tends to consume the touch before the outer clickable sees
            // it. ExposedDropdownMenuBox is the Material3-blessed pattern for this exact
            // "tap a field to pick from a list" use case and doesn't have that problem.
            ExposedDropdownMenuBox(
                expanded = showPubkeyDropdown,
                onExpandedChange = { showPubkeyDropdown = it },
            ) {
                OutlinedTextField(
                    value = if (selectedPubkeyId == -1L) {
                        "No key (password auth)"
                    } else {
                        pubkeys.find { it.id == selectedPubkeyId }?.nickname ?: "Unknown key"
                    },
                    onValueChange = { },
                    label = { Text("SSH Key") },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        // PrimaryNotEditable (not the generic/deprecated menuAnchor()) is
                        // what Material3 documents for readOnly=true fields specifically —
                        // it opens the menu WITH focus, which the generic overload doesn't
                        // guarantee, and that mismatch is a known cause of "tapping an item
                        // does nothing" for readOnly ExposedDropdownMenuBox fields.
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPubkeyDropdown)
                    },
                    supportingText = if (pubkeys.isEmpty()) {
                        { Text("No SSH keys yet — generate one in SSH Keys") }
                    } else {
                        null
                    },
                )

                // Only "No key (password auth)" + real, selectable keys go in the menu.
                // A disabled placeholder row here (when pubkeys is empty) reads as a
                // second, dead "option" and is confusing — the empty-state hint above
                // (supportingText) covers that instead.
                ExposedDropdownMenu(
                    expanded = showPubkeyDropdown,
                    onDismissRequest = { showPubkeyDropdown = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("No key (password auth)") },
                        onClick = {
                            selectedPubkeyId = -1L
                            showPubkeyDropdown = false
                        },
                    )
                    pubkeys.forEach { pubkey ->
                        DropdownMenuItem(
                            text = { Text(pubkey.nickname) },
                            onClick = {
                                selectedPubkeyId = pubkey.id
                                showPubkeyDropdown = false
                            },
                        )
                    }
                }
            }
        }
    }
}
