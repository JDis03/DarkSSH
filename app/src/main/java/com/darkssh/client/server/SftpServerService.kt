package com.darkssh.client.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Foreground service that runs SFTP and Health Check servers.
 * 
 * Features:
 * - SFTP server on port 2222 (port 22 requires root)
 * - Health check HTTP server on port 8222
 * - Foreground service notification
 * - Auto-restart on app launch
 */
class SftpServerService : Service() {
    
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private lateinit var sftpManager: SftpServerManager
    private lateinit var healthCheckServer: HealthCheckServer
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sftp_server_channel"
        private const val CHANNEL_NAME = "SFTP Server"
        
        private const val DEFAULT_SFTP_PORT = 2222  // Port 22 requires root on Android
        private const val DEFAULT_HEALTH_PORT = 8222  // Avoid collision with SFTP
        private const val DEFAULT_USERNAME = "root"
        private const val DEFAULT_PASSWORD = "darkssh"

        @Volatile
        var isServiceRunning = false
            private set
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): SftpServerService = this@SftpServerService
    }
    
    override fun onCreate() {
        super.onCreate()
        Timber.d("SftpServerService created")
        
        sftpManager = SftpServerManager(applicationContext)
        healthCheckServer = HealthCheckServer(sftpManager)
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("SftpServerService starting...")
        
        isServiceRunning = true
        
        // Start foreground
        val notification = buildNotification("Starting servers...")
        startForeground(NOTIFICATION_ID, notification)
        
        // Start servers in background
        serviceScope.launch {
            try {
                // Start SFTP server
                sftpManager.start(
                    port = DEFAULT_SFTP_PORT,
                    username = DEFAULT_USERNAME,
                    password = DEFAULT_PASSWORD,
                    rootPath = "/sdcard/"
                )
                
                // Start health check server
                healthCheckServer.start(DEFAULT_HEALTH_PORT)
                
                // Update notification
                updateNotification("Servers running")
                
                Timber.i("✓ All servers started successfully")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to start servers")
                updateNotification("Failed to start - ${e.message}")
                isServiceRunning = false
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        Timber.i("SftpServerService stopping...")
        
        isServiceRunning = false
        
        serviceScope.launch {
            try {
                sftpManager.stop()
                healthCheckServer.stop()
                Timber.i("✓ All servers stopped")
            } catch (e: Exception) {
                Timber.w(e, "Error stopping servers")
            } finally {
                serviceScope.cancel()
            }
        }
        
        super.onDestroy()
    }
    
    /**
     * Creates notification channel for Android O+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for SFTP server status"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Builds notification with given status message.
     */
    private fun buildNotification(status: String): Notification {
        // Intent to launch main activity (you'll need to update this)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DarkSSH Server")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_sys_upload) // Use proper icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    /**
     * Updates foreground notification.
     */
    private fun updateNotification(status: String) {
        val notification = buildNotification(status)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Returns true if SFTP server is running.
     */
    fun isSftpRunning(): Boolean = sftpManager.isRunning()
    
    /**
     * Returns true if health check server is running.
     */
    fun isHealthCheckRunning(): Boolean = healthCheckServer.isRunning()
}
