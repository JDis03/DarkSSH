package com.darkssh.client.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.darkssh.client.service.TerminalBridge
import org.connectbot.terminal.TerminalEmulator

/**
 * Ultra-simple terminal component for debugging crashes
 */
@Composable
fun SimpleTerminal(
    terminalEmulator: TerminalEmulator,
    terminalBridge: TerminalBridge?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    
    val terminalView = remember(terminalEmulator) {
        createSimpleTerminalView(context, terminalEmulator, terminalBridge)
    }

    if (terminalView != null) {
        AndroidView(
            factory = { terminalView },
            modifier = modifier.fillMaxSize(),
        )
    } else {
        // Fallback when view creation fails
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Terminal Error",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@SuppressLint("ViewConstructor")
private class SimpleTerminalView(
    context: Context,
    private val terminalEmulator: TerminalEmulator,
    private val terminalBridge: TerminalBridge?,
) : View(context) {
    
    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 40f
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }
    
    private val backgroundPaint = Paint().apply {
        color = Color.BLACK
    }

    init {
        try {
            isFocusable = true
            isFocusableInTouchMode = true
            keepScreenOn = true
        } catch (e: Exception) {
            android.util.Log.e("SimpleTerminal", "Init failed", e)
        }
    }

    override fun onDraw(canvas: Canvas) {
        try {
            // Clear background
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
            
            // Simple terminal display
            textPaint.color = Color.GREEN
            canvas.drawText("DarkSSH Terminal (Safe Mode)", 20f, 80f, textPaint)
            
            val connectionStatus = if (terminalBridge?.isConnected?.value == true) {
                "Connected ✓"
            } else {
                "Not Connected"
            }
            
            textPaint.color = if (terminalBridge?.isConnected?.value == true) Color.GREEN else Color.RED
            canvas.drawText("Status: $connectionStatus", 20f, 140f, textPaint)
            
            textPaint.color = Color.WHITE
            canvas.drawText("Terminal ready for input", 20f, 200f, textPaint)
            canvas.drawText("Basic mode - no crashes!", 20f, 260f, textPaint)
            
        } catch (e: Exception) {
            android.util.Log.e("SimpleTerminal", "Draw failed", e)
        }
    }
}

private fun createSimpleTerminalView(
    context: Context,
    terminalEmulator: TerminalEmulator,
    terminalBridge: TerminalBridge?,
): SimpleTerminalView? {
    return try {
        android.util.Log.d("SimpleTerminal", "Creating simple terminal view")
        SimpleTerminalView(context, terminalEmulator, terminalBridge)
    } catch (e: Exception) {
        android.util.Log.e("SimpleTerminal", "Failed to create simple terminal", e)
        null
    }
}