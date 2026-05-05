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
    showSoftKeyboard: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }

    ConnectBotTerminal(
        terminalEmulator = terminalEmulator,
        modifier = modifier,
        typeface = Typeface.MONOSPACE,
        initialFontSize = fontSize.sp,
        keyboardEnabled = true,
        showSoftKeyboard = showSoftKeyboard,
        focusRequester = focusRequester,
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(showSoftKeyboard) {
        if (showSoftKeyboard) {
            focusRequester.requestFocus()
        }
    }
}
