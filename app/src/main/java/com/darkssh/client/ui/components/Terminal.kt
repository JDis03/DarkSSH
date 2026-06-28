package com.darkssh.client.ui.components

import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.darkssh.client.service.TerminalBridge
import com.darkssh.client.terminal.DarkTerminalSession
import com.darkssh.client.terminal.emulator.TerminalSession
import com.darkssh.client.terminal.view.TerminalView
import com.darkssh.client.terminal.view.TerminalViewClient
import timber.log.Timber
import kotlin.math.abs

@Composable
fun Terminal(
    terminalSession: DarkTerminalSession,
    modifier: Modifier = Modifier,
    fontSize: Float = 20f,
    typeface: Typeface = Typeface.MONOSPACE,
    terminalBridge: TerminalBridge? = null,
    showSoftKeyboard: Boolean = true,
    isActive: Boolean = true, // Whether this terminal is the active/visible tab
    focusTrigger: Int = 0, // External trigger to force focus transfer
    forceShowKeyboardTrigger: Int = 0, // Increment to force-show the keyboard (explicit user action)
) {
    val context = LocalContext.current
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    var prevFontSize by remember { mutableFloatStateOf(0f) }
    var prevTypeface by remember { mutableStateOf<Typeface?>(null) }

    val terminalViewRef = remember { mutableStateOf<TerminalView?>(null) }

    // NestedScrollConnection to resolve gesture conflict between terminal scroll and HorizontalPager swipe.
    // Problem: When scrolling vertically in the terminal history, the finger naturally has a small
    // horizontal component. HorizontalPager intercepts this and triggers tab switching mid-scroll.
    // Solution: When the gesture is predominantly vertical (Y > X * threshold), consume it entirely
    // so HorizontalPager never sees it. Horizontal swipes still work for intentional tab changes.
    val terminalNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Threshold: if vertical component is > 1.2x horizontal, it's a vertical scroll
                // (lower ratio = more aggressive blocking of pager swipe during scroll)
                val dominated = abs(available.y) > abs(available.x) * 1.2f
                return if (dominated && abs(available.y) > 0.5f) {
                    // Consume the horizontal component to prevent pager from swiping
                    // Let vertical pass through (terminal's GestureRecognizer handles it)
                    Offset(available.x, 0f)
                } else {
                    Offset.Zero
                }
            }
        }
    }

    // Explicit "Show Keyboard" button press: SHOW_FORCED, not SHOW_IMPLICIT.
    // Android silently ignores SHOW_IMPLICIT (used below and in the update{} block) if the
    // user just explicitly dismissed the keyboard (back button, swipe-down, etc) — that's an
    // intentional anti-annoyance behavior. But when the user deliberately taps our own
    // "Show Keyboard" affordance (ConsoleKeyBar), that IS an explicit request and must always
    // work, so it needs SHOW_FORCED. Skip the initial trigger=0 (no button press yet).
    LaunchedEffect(forceShowKeyboardTrigger) {
        if (forceShowKeyboardTrigger > 0) {
            val view = terminalViewRef.value
            if (view != null) {
                view.requestFocus()
                imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
                Timber.d("Terminal: Force-showed keyboard (explicit request, trigger=$forceShowKeyboardTrigger)")
            }
        }
    }


    // Explicit focus transfer when this terminal becomes active OR focus trigger changes
    LaunchedEffect(isActive, showSoftKeyboard, focusTrigger) {
        val view = terminalViewRef.value
        if (view != null) {
            if (isActive && showSoftKeyboard) {
                view.post {
                    view.rootView?.findFocus()?.clearFocus()
                    view.requestFocus()
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                    Timber.d("Terminal: Focus transferred to active tab")
                }
            } else if (!isActive) {
                view.post {
                    if (view.hasFocus()) {
                        view.clearFocus()
                        imm.hideSoftInputFromWindow(view.windowToken, 0)
                        Timber.d("Terminal: Focus cleared from inactive tab")
                    }
                }
            }
        }
    }

    // Wrap AndroidView in Box with nestedScroll to intercept gestures before HorizontalPager
    Box(modifier = modifier.nestedScroll(terminalNestedScroll)) {
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
                        // Mirrors Termux's own TermuxTerminalViewClient#onSingleTapUp(): only pop
                        // the soft keyboard when the remote app hasn't enabled mouse reporting
                        // (DECSET 1000/1002/1003 etc). When mouse tracking IS active, the tap is
                        // already forwarded as a mouse click to the TUI (see TerminalView's
                        // GestureAndScaleRecognizer#onUp) — popping the keyboard on top of that is
                        // just an annoyance that eats half the screen while using a mouse-driven
                        // TUI (htop, ranger, darkred, etc), not something the user asked for.
                        val mouseTrackingActive = terminalView.mEmulator?.isMouseTrackingActive() == true
                        if (!mouseTrackingActive && !e.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
                            imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
                        }
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
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            // CRITICAL: Control focusability and focus IMMEDIATELY to prevent inactive tabs from receiving input
            // This must happen in update block, not DisposableEffect, for immediate effect
            view.isFocusable = isActive
            view.isFocusableInTouchMode = isActive
            
            if (isActive && showSoftKeyboard) {
                // Only request focus and show keyboard if this tab is active
                if (!view.hasFocus()) {
                    view.requestFocus()
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                    Timber.d("Terminal: Activated - requesting focus and showing keyboard")
                }
            } else {
                // Clear focus and hide keyboard when tab is not active
                if (view.hasFocus()) {
                    view.clearFocus()
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                    if (!isActive) {
                        Timber.d("Terminal: Deactivated - clearing focus and hiding keyboard")
                    }
                }
            }
            
            // Only update when font actually changes
            if (fontSize != prevFontSize) {
                Timber.d("Terminal: setTextSize ${fontSize.toInt()}")
                view.setTextSize(fontSize.toInt())
                prevFontSize = fontSize
            }
            if (typeface != prevTypeface) {
                view.setTypeface(typeface)
                prevTypeface = typeface
            }
        },
    )
    } // End Box wrapper
}
