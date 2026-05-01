package com.darkssh.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.darkssh.client.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TerminalService : Service() {
    companion object {
        const val CHANNEL_ID = "darkssh_connections"
        const val CHANNEL_NAME = "SSH Connections"
        const val NOTIFICATION_ID = 1
        const val ACTION_CONNECT = "com.darkssh.client.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.darkssh.client.ACTION_DISCONNECT"
        const val EXTRA_HOST_ID = "host_id"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val hostId = intent.getLongExtra(EXTRA_HOST_ID, -1L)
                startForeground(NOTIFICATION_ID, createNotification(hostId))
            }
            ACTION_DISCONNECT -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Active SSH terminal sessions"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(hostId: Long): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DarkSSH")
            .setContentText("SSH connection active")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
}