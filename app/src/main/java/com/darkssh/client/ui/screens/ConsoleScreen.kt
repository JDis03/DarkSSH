package com.darkssh.client.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.darkssh.client.util.AppPreferences
import com.darkssh.client.util.FontManager
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.darkssh.client.service.PromptRequest
import com.darkssh.client.service.PromptResponse
import com.darkssh.client.service.TerminalBridge
import com.darkssh.client.service.TerminalService
import com.darkssh.client.ui.components.Terminal
import com.darkssh.client.ui.screens.viewmodel.ConsoleViewModel
import timber.log.Timber

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConsoleScreen(
    hostId: Long,
    onBack: () -> Unit,
    terminalService: TerminalService? = null,
    modifier: Modifier = Modifier,
    inTab: Boolean = false,
    tabId: String? = null,
    isActive: Boolean = true,
    viewModel: ConsoleViewModel = hiltViewModel(key = tabId ?: "console_$hostId"),
) {
    val bridge by viewModel.bridge.collectAsState()
    val host by viewModel.host.collectAsState()
    val isDisconnected by viewModel.isDisconnected.collectAsState()
    val disconnectMessage by viewModel.disconnectMessage.collectAsState()
    val disconnectReason by viewModel.disconnectReason.collectAsState()
    val isConnected by bridge?.isConnected?.collectAsState() ?: remember { mutableStateOf(false) }
    val currentPrompt by bridge?.promptRequest?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val fontSize by bridge?.fontSize?.collectAsState() ?: remember { mutableStateOf(14f) }

    var showMenu by remember { mutableStateOf(false) }
    var promptInput by remember { mutableStateOf("") }
    var showSoftwareKeyboard by remember { mutableStateOf(true) }
    var focusTrigger by remember { mutableStateOf(0) }
    var forceShowKeyboardTrigger by remember { mutableStateOf(0) }
    var prevIsActive by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val terminalTypeface = remember {
        val preset = AppPreferences.getTerminalFont(context)
        FontManager.getTypeface(context, preset)
    }
    val density = LocalDensity.current
    val imeHeight = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val imeVisible = imeHeight > 0.dp

    // Termius pattern: force keyboard on when THIS terminal becomes active.
    // BUT respect a TUI's mouse-reporting mode (htop, ranger, darkred, etc): if the
    // session in this tab has enabled mouse tracking, don't force the keyboard back
    // open just because the user swiped/switched to this tab — that was popping the
    // keyboard on top of mouse-driven TUIs on every tab switch. If we can't tell yet
    // (bridge/emulator not ready), fall back to the previous always-show behavior.
    LaunchedEffect(isActive) {
        if (isActive && !prevIsActive) {
            val mouseTrackingActive = bridge?.terminalEmulator?.isMouseTrackingActive() == true
            if (!mouseTrackingActive) {
                showSoftwareKeyboard = true
            }
        }
        prevIsActive = isActive
    }

    LaunchedEffect(imeVisible, isActive) {
        // Only sync with IME visibility when this terminal is NOT active
        if (!isActive && !imeVisible) {
            showSoftwareKeyboard = false
        }
    }

    DisposableEffect(hostId, tabId) {
        viewModel.setTerminalService(terminalService)
        viewModel.connect(hostId, tabId)
        onDispose {
            viewModel.detachFromBridge()
        }
    }

    // Force focus when this terminal becomes active (Termius pattern)
    DisposableEffect(isActive) {
        if (isActive) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                focusTrigger++
            }, 100)
        }
        onDispose { }
    }

    // NOTE: Active bridge management is now handled by TabbedMainScreen (Termius pattern)
    // This ensures single source of truth and prevents race conditions during tab switches
    // ConsoleScreen just renders the terminal, it doesn't control which bridge is active


    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.imeAnimationTarget,
        topBar = {
            if (!inTab) {
                TopAppBar(
                    title = { Text(host?.nickname?.ifBlank { host?.hostname } ?: "Terminal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Toggle Keyboard") },
                            onClick = {
                                showMenu = false
                                showSoftwareKeyboard = !showSoftwareKeyboard
                                // Explicit user action turning it back on must bypass Android's
                                // "ignore SHOW_IMPLICIT right after an explicit dismiss" behavior.
                                if (showSoftwareKeyboard) forceShowKeyboardTrigger++
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Keyboard, contentDescription = null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("A+") },
                            onClick = {
                                showMenu = false
                                val b = bridge
                                if (b != null) {
                                    Timber.d("ConsoleScreen: A+ clicked, bridge is valid")
                                    b.increaseFontSize()
                                } else {
                                    Timber.w("ConsoleScreen: A+ clicked but bridge is null!")
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("A-") },
                            onClick = {
                                showMenu = false
                                val b = bridge
                                if (b != null) {
                                    Timber.d("ConsoleScreen: A- clicked, bridge is valid")
                                    b.decreaseFontSize()
                                } else {
                                    Timber.w("ConsoleScreen: A- clicked but bridge is null!")
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Disconnect") },
                            onClick = {
                                showMenu = false
                                viewModel.disconnect()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Reconnect") },
                            onClick = {
                                showMenu = false
                                viewModel.reconnect()
                            },
                        )
                    }
                },
            )
            }
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding(),
                )
                .windowInsetsPadding(WindowInsets.imeAnimationTarget),
        ) {
            val currentBridge = bridge
            if (currentBridge != null && isConnected && currentBridge.darkTerminalSession != null) {
                Terminal(
                    terminalSession = currentBridge.darkTerminalSession!!,
                    terminalBridge = currentBridge,
                    modifier = Modifier.fillMaxSize(),
                    showSoftKeyboard = showSoftwareKeyboard,
                    focusTrigger = focusTrigger,
                    forceShowKeyboardTrigger = forceShowKeyboardTrigger,
                    fontSize = fontSize,
                    typeface = terminalTypeface,
                    isActive = isActive, // Control focus based on tab visibility
                    focusTrigger = focusTrigger,
                )
            } else if (isDisconnected && disconnectReason != com.darkssh.client.service.DisconnectReason.USER_REQUESTED) {
                // Only show reconnect overlay if disconnect was NOT user-requested
                DisconnectedOverlay(
                    message = disconnectMessage ?: "Disconnected",
                    onReconnect = { viewModel.reconnect() },
                    onClose = onBack,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (!imeVisible) {
                ConsoleKeyBar(
                    bridge = currentBridge,
                    onShowKeyboard = {
                        showSoftwareKeyboard = true
                        forceShowKeyboardTrigger++
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            currentPrompt?.let { prompt ->
                when (prompt) {
                    is PromptRequest.StringPrompt -> {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("Authentication Required") },
                            text = {
                                OutlinedTextField(
                                    value = promptInput,
                                    onValueChange = { promptInput = it },
                                    label = { Text(prompt.prompt) },
                                    visualTransformation =
                                        if (prompt.echo) {
                                            androidx.compose.ui.text.input.VisualTransformation.None
                                        } else {
                                            PasswordVisualTransformation()
                                        },
                                    singleLine = true,
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    bridge?.respondToPrompt(PromptResponse.StringResponse(promptInput))
                                    promptInput = ""
                                }) {
                                    Text("OK")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    bridge?.respondToPrompt(PromptResponse.StringResponse(null))
                                    promptInput = ""
                                }) {
                                    Text("Cancel")
                                }
                            },
                        )
                    }
                    is PromptRequest.BooleanPrompt -> {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("Confirm") },
                            text = { Text(prompt.prompt) },
                            confirmButton = {
                                TextButton(onClick = {
                                    bridge?.respondToPrompt(PromptResponse.BooleanResponse(true))
                                }) {
                                    Text("Yes")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    bridge?.respondToPrompt(PromptResponse.BooleanResponse(false))
                                }) {
                                    Text("No")
                                }
                            },
                        )
                    }
                    is PromptRequest.HostKeyPrompt -> {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("Host Key Verification") },
                            text = {
                                Column {
                                    Text("The authenticity of host '${prompt.hostname}:${prompt.port}' can't be established.")
                                    Text(prompt.fingerprints)
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    bridge?.respondToPrompt(PromptResponse.BooleanResponse(true))
                                }) {
                                    Text("Accept")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    bridge?.respondToPrompt(PromptResponse.BooleanResponse(false))
                                }) {
                                    Text("Reject")
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
private fun ConsoleKeyBar(
    bridge: TerminalBridge?,
    onShowKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { bridge?.write("\u001b[A".toByteArray()) }) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up")
            }
            IconButton(onClick = { bridge?.write("\u001b[D".toByteArray()) }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Left")
            }
            IconButton(onClick = { bridge?.write("\u001b[B".toByteArray()) }) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down")
            }
            IconButton(onClick = { bridge?.write("\u001b[C".toByteArray()) }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Right")
            }
            TextButton(onClick = { bridge?.write("\t".toByteArray()) }) {
                Text("Tab", style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onShowKeyboard) {
                Icon(Icons.Default.Keyboard, contentDescription = "Show Keyboard")
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DisconnectedOverlay(
    message: String,
    onReconnect: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Disconnected",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.padding(top = 16.dp)) {
                Button(onClick = onReconnect, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Reconnect")
                }
                Button(onClick = onClose) {
                    Text("Close")
                }
            }
        }
    }
}

