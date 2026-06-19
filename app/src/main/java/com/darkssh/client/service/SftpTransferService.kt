package com.darkssh.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.darkssh.client.R
import com.darkssh.client.data.entity.Host
import com.darkssh.client.transport.SftpClient
import com.darkssh.client.transport.TransferProgress
import com.darkssh.client.util.DebugLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

data class TransferTask(
    val id: Int,
    val hostId: Long,
    val type: TransferType,
    val localPath: String,
    val remotePath: String,
    val totalBytes: Long,
    val status: TransferStatus,
    val progress: TransferProgress? = null,
    val error: String? = null,
)

enum class TransferType {
    UPLOAD,
    DOWNLOAD,
}

enum class TransferStatus {
    QUEUED,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@AndroidEntryPoint
class SftpTransferService : Service() {
    @Inject
    lateinit var notificationManager: NotificationManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val transferJobs = ConcurrentHashMap<Int, Job>()
    private val transferIdCounter = AtomicInteger(0)

    private val _transfers = MutableStateFlow<Map<Int, TransferTask>>(emptyMap())
    val transfers: StateFlow<Map<Int, TransferTask>> = _transfers.asStateFlow()

    // Static to access from ViewModel
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "sftp_transfers"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START_UPLOAD = "com.darkssh.client.ACTION_START_UPLOAD"
        const val ACTION_START_DOWNLOAD = "com.darkssh.client.ACTION_START_DOWNLOAD"
        const val ACTION_CANCEL_TRANSFER = "com.darkssh.client.ACTION_CANCEL_TRANSFER"

        const val EXTRA_TRANSFER_ID = "transfer_id"
        const val EXTRA_HOST_ID = "host_id"
        const val EXTRA_LOCAL_PATH = "local_path"
        const val EXTRA_REMOTE_PATH = "remote_path"
        const val EXTRA_TOTAL_BYTES = "total_bytes"

        // Global reference to current service instance (for observing transfers from UI)
        private var instance: SftpTransferService? = null

        /**
         * Observable transfer state for UI (like File Manager+)
         * Returns active transfers with progress
         */
        val activeTransfers: StateFlow<Map<Int, TransferTask>>
            get() = instance?.transfers ?: MutableStateFlow(emptyMap())

        fun startUpload(
            context: Context,
            hostId: Long,
            localPath: String,
            remotePath: String,
            totalBytes: Long,
        ) {
            val intent =
                Intent(context, SftpTransferService::class.java).apply {
                    action = ACTION_START_UPLOAD
                    putExtra(EXTRA_HOST_ID, hostId)
                    putExtra(EXTRA_LOCAL_PATH, localPath)
                    putExtra(EXTRA_REMOTE_PATH, remotePath)
                    putExtra(EXTRA_TOTAL_BYTES, totalBytes)
                }
            context.startForegroundService(intent)
        }

        fun cancelTransfer(
            context: Context,
            transferId: Int,
        ) {
            val intent =
                Intent(context, SftpTransferService::class.java).apply {
                    action = ACTION_CANCEL_TRANSFER
                    putExtra(EXTRA_TRANSFER_ID, transferId)
                }
            context.startForegroundService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        DebugLogger.i("SftpTransferService", "Service created")
        Timber.d("SftpTransferService created")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        intent?.let { handleIntent(it) }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        Timber.d("SftpTransferService destroyed")
    }

    private fun handleIntent(intent: Intent) {
        DebugLogger.d("SftpTransferService", "handleIntent: action=${intent.action}")
        Timber.d("handleIntent: action=${intent.action}")
        when (intent.action) {
            ACTION_START_UPLOAD -> {
                val hostId = intent.getLongExtra(EXTRA_HOST_ID, -1)
                val localPath =
                    intent.getStringExtra(EXTRA_LOCAL_PATH) ?: run {
                        DebugLogger.e("SftpTransferService", "Missing EXTRA_LOCAL_PATH")
                        Timber.e("Missing EXTRA_LOCAL_PATH")
                        return
                    }
                val remotePath =
                    intent.getStringExtra(EXTRA_REMOTE_PATH) ?: run {
                        DebugLogger.e("SftpTransferService", "Missing EXTRA_REMOTE_PATH")
                        Timber.e("Missing EXTRA_REMOTE_PATH")
                        return
                    }
                val totalBytes = intent.getLongExtra(EXTRA_TOTAL_BYTES, 0)
                DebugLogger.i(
                    "SftpTransferService",
                    "Starting upload: hostId=$hostId, file=${localPath.substringAfterLast('/')}, size=${totalBytes / 1024}KB",
                )
                Timber.d("Starting upload: hostId=$hostId, local=$localPath, remote=$remotePath, bytes=$totalBytes")
                startUploadInternal(hostId, localPath, remotePath, totalBytes)
            }

            ACTION_CANCEL_TRANSFER -> {
                val transferId = intent.getIntExtra(EXTRA_TRANSFER_ID, -1)
                DebugLogger.i("SftpTransferService", "Canceling transfer: $transferId")
                cancelTransferInternal(transferId)
            }

            else -> {
                DebugLogger.w("SftpTransferService", "Unknown action: ${intent.action}")
                Timber.w("Unknown action: ${intent.action}")
            }
        }
    }

    private fun startUploadInternal(
        hostId: Long,
        localPath: String,
        remotePath: String,
        totalBytes: Long,
    ) {
        val transferId = transferIdCounter.incrementAndGet()
        DebugLogger.d("SftpTransferService", "Transfer #$transferId queued")
        Timber.d("startUploadInternal called")
        val task =
            TransferTask(
                id = transferId,
                hostId = hostId,
                type = TransferType.UPLOAD,
                localPath = localPath,
                remotePath = remotePath,
                totalBytes = totalBytes,
                status = TransferStatus.QUEUED,
            )

        Timber.d("Transfer task created: id=$transferId")
        updateTransfer(task)

        val job =
            serviceScope.launch {
                try {
                    updateTransfer(task.copy(status = TransferStatus.TRANSFERRING))
                    updateNotification() // Show notification immediately

                    // Get SFTP client from existing connection
                    DebugLogger.d("SftpTransferService", "Getting SFTP client for hostId=$hostId")
                    Timber.d("Getting SFTP client for hostId=$hostId")
                    val sftpClient = getActiveSftpClient(hostId)
                    if (sftpClient == null) {
                        DebugLogger.e("SftpTransferService", "No active SFTP connection for hostId=$hostId")
                        Timber.e("No active SFTP connection for hostId=$hostId")
                        updateTransfer(
                            task.copy(
                                status = TransferStatus.FAILED,
                                error = "No active SFTP connection for this host",
                            ),
                        )
                        return@launch
                    }
                    DebugLogger.i("SftpTransferService", "SFTP client found, uploading...")
                    Timber.d("SFTP client found, starting upload")

                    val localFile = File(localPath)

                    // Use optimized upload (window 32MB + packet 256KB)
                    Timber.d("Uploading ${localFile.name} (${totalBytes / 1024 / 1024}MB)")
                    val result =
                        sftpClient.uploadFile(localFile, remotePath) { progress ->
                            DebugLogger.d("SftpTransferService", "Upload progress callback: ${progress.percentage}%")
                            updateTransfer(
                                task.copy(
                                    status = TransferStatus.TRANSFERRING,
                                    progress = progress,
                                ),
                            )
                            updateNotification()
                        }

                    result.fold(
                        onSuccess = {
                            updateTransfer(task.copy(status = TransferStatus.COMPLETED))
                            DebugLogger.i("SftpTransferService", "Transfer #$transferId completed ✓")
                            Timber.d("Transfer $transferId completed")
                        },
                        onFailure = { error ->
                            updateTransfer(
                                task.copy(
                                    status = TransferStatus.FAILED,
                                    error = error.message,
                                ),
                            )
                            DebugLogger.e("SftpTransferService", "Transfer #$transferId failed: ${error.message}")
                            Timber.e(error, "Transfer $transferId failed")
                        },
                    )
                } catch (e: Exception) {
                    updateTransfer(
                        task.copy(
                            status = TransferStatus.FAILED,
                            error = e.message,
                        ),
                    )
                    Timber.e(e, "Transfer $transferId exception")
                } finally {
                    // Clean up temporary file if it was created from content URI
                    val localFile = File(localPath)
                    if (localFile.name.startsWith("upload_") && localFile.extension == "tmp") {
                        try {
                            if (localFile.delete()) {
                                DebugLogger.d("SftpTransferService", "Cleaned up temp file: ${localFile.name}")
                            }
                        } catch (e: Exception) {
                            DebugLogger.w("SftpTransferService", "Failed to delete temp file: ${e.message}")
                        }
                    }

                    transferJobs.remove(transferId)
                    checkStopService()
                }
            }

        transferJobs[transferId] = job
    }

    private fun cancelTransferInternal(transferId: Int) {
        transferJobs[transferId]?.cancel()
        transferJobs.remove(transferId)
        _transfers.value[transferId]?.let { task ->
            updateTransfer(task.copy(status = TransferStatus.CANCELLED))
        }
        checkStopService()
    }

    private fun updateTransfer(task: TransferTask) {
        _transfers.value =
            _transfers.value.toMutableMap().apply {
                put(task.id, task)
            }
        DebugLogger.d(
            "SftpTransferService",
            "Transfer updated: id=${task.id}, status=${task.status}, progress=${task.progress?.percentage}%",
        )
        Timber.d("Transfer updated: id=${task.id}, status=${task.status}, progress=${task.progress?.percentage}%")

        // Update notification when transfer status/progress changes
        if (task.status == TransferStatus.TRANSFERRING) {
            updateNotification()
        }
    }

    private fun checkStopService() {
        val activeTransfers =
            _transfers.value.values.any {
                it.status == TransferStatus.QUEUED || it.status == TransferStatus.TRANSFERRING
            }
        if (!activeTransfers) {
            Timber.d("No active transfers, stopping service")
            stopSelf()
        }
    }

    private fun getActiveSftpClient(hostId: Long): SftpClient? {
        // Access the static map from SftpViewModel
        return com.darkssh.client.ui.screens.viewmodel.SftpViewModel.activeClients[hostId]
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "SFTP Transfers",
                NotificationManager.IMPORTANCE_HIGH, // HIGH like File Manager+ to show progress updates
            ).apply {
                description = "Background file transfers via SFTP"
                setShowBadge(true) // Show badge with transfer count
                setSound(null, null) // No sound
                enableVibration(false) // No vibration
                enableLights(true) // Enable LED like File Manager+
            }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification =
        NotificationCompat
            .Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SFTP Transfers")
            .setContentText("Ready to transfer files")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

    private fun updateNotification() {
        val activeTransfers =
            _transfers.value.values.filter {
                it.status == TransferStatus.TRANSFERRING
            }

        if (activeTransfers.isEmpty()) return

        val firstTransfer = activeTransfers.first()
        val content =
            when (firstTransfer.type) {
                TransferType.UPLOAD -> {
                    val fileName = File(firstTransfer.localPath).name
                    val progress = firstTransfer.progress
                    if (progress != null) {
                        "$fileName • ${progress.percentage}% • ${progress.speedFormatted}"
                    } else {
                        "Uploading $fileName..."
                    }
                }

                TransferType.DOWNLOAD -> {
                    val fileName = File(firstTransfer.remotePath).name
                    val progress = firstTransfer.progress
                    if (progress != null) {
                        "$fileName • ${progress.percentage}% • ${progress.speedFormatted}"
                    } else {
                        "Downloading $fileName..."
                    }
                }
            }

        val notification =
            NotificationCompat
                .Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("SFTP Transfers (${activeTransfers.size})")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .setShowWhen(false) // Don't show timestamp
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // DEFAULT shows progress updates
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setProgress(100, firstTransfer.progress?.percentage ?: 0, false)
                .setOnlyAlertOnce(true)
                .setSilent(true) // No sound on updates
                .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            val percentage = firstTransfer.progress?.percentage ?: 0
            DebugLogger.d("SftpTransferService", "Notification updated: $percentage% - $content")
        } catch (e: Exception) {
            DebugLogger.e("SftpTransferService", "Failed to update notification", e)
        }
    }
}
