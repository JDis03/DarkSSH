package com.darkssh.client.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.darkssh.client.ui.nav.DarkSSHNavHost
import com.darkssh.client.ui.theme.DarkSSHTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DarkSSHTheme {
                DarkSSHNavHost()
            }
        }
    }
}