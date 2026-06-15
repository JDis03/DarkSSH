package com.darkssh.client.ui.components

import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.darkssh.client.service.TerminalBridge
import com.darkssh.client.terminal.DarkTerminalSession
import com.darkssh.client.terminal.emulator.TerminalSession
import com.darkssh.client.terminal.view.TerminalView
import com.darkssh.client.terminal.view.TerminalViewClient
import timber.log.Timber

@Composable
fun Terminal(
    terminalSession: DarkTerminalSession,
    modifier: Modifier = Modifier,
    fontSize: Float = 20f,
    typeface: Typeface = Typeface.MONOSPACE,
    terminalBridge: TerminalBridge? = null,
    showSoftKeyboard: Boolean = true,
) {
    val context = LocalContext.current
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    var prevFontSize by remember { mutableFloatStateOf(0f) }
    var prevTypeface by remember { mutableStateOf<Typeface?>(null) }

    val terminalViewRef = remember { mutableStateOf<TerminalView?>(null) }

    // Handle keyboard visibility changes without triggering recomposition loop
    DisposableEffect(showSoftKeyboard) {
        val view = terminalViewRef.value
        if (view != null) {
            if (showSoftKeyboard) {
                view.requestFocus()
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            } else {
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
        onDispose {}
    }

    AndroidView(
        factory = { ctx ->
            TerminalView(ctx, null).apply {
                val terminalView = this
                isFocusable = true
                isFocusableInTouchMode = true
                terminalViewRef.value = this

                setTerminalViewClient(object : TerminalViewClient {
                    override fun onScale(scale: Float): Float = scale

                    override fun onSingleTapUp(e: MotionEvent) {
                        terminalView.requestFocus()
                        imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
                    }

                    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
                    override fun shouldEnforceCharBasedInput(): Boolean = true
                    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
                    override fun isTerminalViewSelected(): Boolean = true
                    override fun copyModeChanged(copyMode: Boolean) {}
                    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession?): Boolean = false
                    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
                    override fun onLongPress(event: MotionEvent): Boolean = false
                    override fun readControlKey(): Boolean = false
                    override fun readAltKey(): Boolean = false
                    override fun readShiftKey(): Boolean = false
                    override fun readFnKey(): Boolean = false
                    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
                    override fun onEmulatorSet() {}

                    override fun logError(tag: String, message: String) = Timber.e("$tag: $message")
                    override fun logWarn(tag: String, message: String) = Timber.w("$tag: $message")
                    override fun logInfo(tag: String, message: String) = Timber.i("$tag: $message")
                    override fun logDebug(tag: String, message: String) = Timber.d("$tag: $message")
                    override fun logVerbose(tag: String, message: String) = Timber.v("$tag: $message")
                    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Timber.e(e, "$tag: $message")
                    override fun logStackTrace(tag: String, e: Exception) = Timber.e(e, tag)
                })

                // Renderer MUST exist before onSizeChanged fires
                // Use actual font size (like Termius) - recalculates cols/rows automatically
                setTextSize(fontSize.toInt())
                setTypeface(typeface)
                prevFontSize = fontSize
                prevTypeface = typeface

                attachSession(terminalSession)

                terminalBridge?.onScreenUpdate = {
                    onScreenUpdated()
                }
            }
        },
        modifier = modifier,
        update = { view ->
            // Only update when font actually changes
            if (fontSize != prevFontSize) {
                view.setTextSize(fontSize.toInt())
                prevFontSize = fontSize
            }
            if (typeface != prevTypeface) {
                view.setTypeface(typeface)
                prevTypeface = typeface
            }
        },
    )
}
