package com.darkssh.client.ui.screens.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkssh.client.server.SftpServerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ServerState(
    val isRunning: Boolean = false,
    val sftpPort: Int = 2222,
    val healthPort: Int = 8222,
    val deviceIp: String = "0.0.0.0",
    val username: String = "root",
    val uptime: Long = 0,
)

@HiltViewModel
class ServerSettingsViewModel
    @Inject
    constructor(
        private val application: Application,
    ) : AndroidViewModel(application) {
        private val _serverState = MutableStateFlow(ServerState())
        val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

        init {
            updateDeviceIp()
            _serverState.value =
                _serverState.value.copy(
                    isRunning = SftpServerService.isServiceRunning,
                )
        }

        fun toggleServer() {
            viewModelScope.launch {
                if (_serverState.value.isRunning) {
                    stopServer()
                } else {
                    startServer()
                }
            }
        }

        private fun startServer() {
            try {
                Timber.i("Starting SFTP server from UI...")
                val intent = Intent(application, SftpServerService::class.java)
                application.startForegroundService(intent)

                _serverState.value = _serverState.value.copy(isRunning = true)
                Timber.i("✓ SFTP server start requested")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start SFTP server")
                _serverState.value = _serverState.value.copy(isRunning = false)
            }
        }

        private fun stopServer() {
            try {
                Timber.i("Stopping SFTP server from UI...")
                val intent = Intent(application, SftpServerService::class.java)
                application.stopService(intent)

                _serverState.value = _serverState.value.copy(isRunning = false)
                Timber.i("✓ SFTP server stop requested")
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop SFTP server")
            }
        }

        private fun updateDeviceIp() {
            try {
                // Simple IP detection - get WiFi IP
                val wifiManager =
                    application.getSystemService(android.content.Context.WIFI_SERVICE)
                        as? android.net.wifi.WifiManager

                wifiManager?.connectionInfo?.let { info ->
                    val ipInt = info.ipAddress
                    val ip =
                        String.format(
                            "%d.%d.%d.%d",
                            ipInt and 0xff,
                            ipInt shr 8 and 0xff,
                            ipInt shr 16 and 0xff,
                            ipInt shr 24 and 0xff,
                        )
                    _serverState.value = _serverState.value.copy(deviceIp = ip)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to get device IP")
            }
        }
    }
