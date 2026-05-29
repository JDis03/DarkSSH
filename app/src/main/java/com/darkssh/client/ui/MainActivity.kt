package com.darkssh.client.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.darkssh.client.service.TerminalService
import com.darkssh.client.ui.nav.DarkSSHNavHost
import com.darkssh.client.ui.theme.DarkSSHTheme
import com.darkssh.client.util.DebugLogger
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var terminalService by mutableStateOf<TerminalService?>(null)
    private var isBound = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            DebugLogger.i("MainActivity", "Notification permission granted")
        } else {
            DebugLogger.w("MainActivity", "Notification permission denied")
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TerminalService.TerminalBinder
            terminalService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            terminalService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+ (like File Manager+)
        requestNotificationPermission()

        val serviceIntent = Intent(this, TerminalService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            DarkSSHTheme {
                MainScreen(
                    terminalService = terminalService,
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    DebugLogger.i("MainActivity", "Notification permission already granted")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show rationale if needed (for now just request)
                    DebugLogger.i("MainActivity", "Showing notification permission rationale")
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Request permission
                    DebugLogger.i("MainActivity", "Requesting notification permission")
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            DebugLogger.i("MainActivity", "Notification permission not required (Android < 13)")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        terminalService = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // Ctrl+Shift+V = Paste from clipboard
            if (event.isCtrlPressed && event.isShiftPressed && event.keyCode == KeyEvent.KEYCODE_V) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString() ?: ""
                    if (text.isNotEmpty()) {
                        terminalService?.bridges?.value?.forEach { bridge ->
                            if (bridge.isConnected.value) {
                                bridge.write(text.encodeToByteArray())
                            }
                        }
                    }
                }
                return true
            }

            // Volume Up = Increase font size (only when terminal is active)
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                val bridge = terminalService?.activeBridge?.value
                if (bridge != null && bridge.isConnected.value) {
                    bridge.increaseFontSize()
                    return true
                }
            }

            // Volume Down = Decrease font size (only when terminal is active)
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                val bridge = terminalService?.activeBridge?.value
                if (bridge != null && bridge.isConnected.value) {
                    bridge.decreaseFontSize()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}