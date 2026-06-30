package com.darkssh.client.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.darkssh.client.R
import com.darkssh.client.ui.MainActivity
import com.darkssh.client.ui.screens.viewmodel.SftpViewModel
import timber.log.Timber
import java.io.File

/**
 * WorkManager Worker for background file uploads via SFTP.
 * Survives app lifecycle and continues uploading even when app is closed.
 */
class UploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_HOST_ID = "host_id"
        const val KEY_LOCAL_PATH = "local_path"
        const val KEY_REMOTE_PATH = "remote_path"
        const val KEY_FILE_NAME = "file_name"

        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_TOTAL = "progress_total"
        const val PROGRESS_PERCENTAGE = "progress_percentage"
        const val PROGRESS_START_TIME = "progress_start_time"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sftp_upload_channel"
    }

    override suspend fun doWork(): Result {
        val hostId = inputData.getLong(KEY_HOST_ID, -1L)
        val localPath = inputData.getString(KEY_LOCAL_PATH) ?: return Result.failure()
        val remotePath = inputData.getString(KEY_REMOTE_PATH) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: File(localPath).name

        if (hostId == -1L) {
            Timber.e("Invalid host ID for upload")
            return Result.failure()
        }

        Timber.d("Starting background upload: $fileName")

        // Show foreground notification
        setForeground(createForegroundInfo(fileName, 0, 100))

        val localFile = File(localPath)
        if (!localFile.exists()) {
            Timber.e("Local file does not exist: $localPath")
            return Result.failure()
        }

        // Get SFTP client from global cache
        val sftpClient = SftpViewModel.activeClients[hostId]
        if (sftpClient == null || !sftpClient.isConnected) {
            Timber.e("SFTP client not connected for host $hostId")
            return Result.failure(
                workDataOf("error" to "Connection lost. Please reconnect and try again."),
            )
        }

        return try {
            val result =
                sftpClient.uploadFile(localFile, remotePath) { progress ->
                    // Update progress notification and work data (include startTime for speed calculation)
                    setProgressAsync(
                        workDataOf(
                            PROGRESS_CURRENT to progress.transferred,
                            PROGRESS_TOTAL to progress.total,
                            PROGRESS_PERCENTAGE to progress.percentage,
                            PROGRESS_START_TIME to progress.startTime,
                        ),
                    )
                    setForegroundAsync(createForegroundInfo(fileName, progress.transferred, progress.total))
                }

            result.fold(
                onSuccess = {
                    Timber.d("Upload completed: $fileName")
                    showCompletionNotification(fileName, true)

                    // Clean up temp file if needed
                    if (localFile.name.startsWith("upload_") && localFile.extension == "tmp") {
                        localFile.delete()
                        Timber.d("Cleaned up temp file: ${localFile.name}")
                    }

                    Result.success()
                },
                onFailure = { error ->
                    Timber.e(error, "Upload failed: $fileName")
                    showCompletionNotification(fileName, false, error.message)
                    Result.failure(workDataOf("error" to (error.message ?: "Unknown error")))
                },
            )
        } catch (e: Exception) {
            Timber.e(e, "Upload exception: $fileName")
            showCompletionNotification(fileName, false, e.message)
            Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "file"
        return createForegroundInfo(fileName, 0, 100)
    }

    private fun createForegroundInfo(
        fileName: String,
        transferred: Long,
        total: Long,
    ): ForegroundInfo {
        createNotificationChannel()

        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("Uploading: $fileName")
                .setContentText(formatProgress(transferred, total))
                .setSmallIcon(R.drawable.ic_notification)
                .setProgress(100, if (total > 0) (transferred * 100 / total).toInt() else 0, total == 0L)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "SFTP Uploads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Background file upload progress"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun showCompletionNotification(
        fileName: String,
        success: Boolean,
        errorMessage: String? = null,
    ) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification =
            NotificationCompat
                .Builder(applicationContext, CHANNEL_ID)
                .setContentTitle(if (success) "Upload complete" else "Upload failed")
                .setContentText(if (success) fileName else "$fileName: ${errorMessage ?: "Unknown error"}")
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setOngoing(false)
                .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun formatProgress(
        transferred: Long,
        total: Long,
    ): String {
        if (total == 0L) return "Starting..."
        val transferredMB = transferred / 1_048_576.0
        val totalMB = total / 1_048_576.0
        val percentage = (transferred * 100 / total).toInt()
        return String.format("%.1f / %.1f MB (%d%%)", transferredMB, totalMB, percentage)
    }
}
