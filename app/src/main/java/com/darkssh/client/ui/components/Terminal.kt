package com.darkssh.client.ui.components

import android.graphics.Typeface
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.sp
import com.darkssh.client.service.TerminalBridge
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.Terminal as ConnectBotTerminal

@Composable
fun Terminal(
    terminalEmulator: TerminalEmulator,
    modifier: Modifier = Modifier,
    fontSize: Float = 20f,
    terminalBridge: TerminalBridge? = null,
    safeMode: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }

    DisposableEffect(terminalEmulator) {
        Log.d("Terminal", "Terminal Composable created")
        onDispose {
            Log.d("Terminal", "Terminal Composable disposed")
        }
    }

    ConnectBotTerminal(
        terminalEmulator = terminalEmulator,
        modifier = modifier,
        typeface = Typeface.MONOSPACE,
        initialFontSize = fontSize.sp,
        keyboardEnabled = true,
        showSoftKeyboard = true,
        focusRequester = focusRequester,
        onTerminalTap = {
            Log.d("Terminal", "Terminal tapped")
        }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        Log.d("Terminal", "Focus requested for ConnectBot Terminal")
    }
}