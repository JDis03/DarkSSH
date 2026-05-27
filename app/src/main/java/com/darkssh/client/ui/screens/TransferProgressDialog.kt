package com.darkssh.client.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.darkssh.client.transport.TransferProgress
import kotlin.math.roundToInt

/**
 * Transfer progress dialog exactly like File Manager+
 * Shows download/upload progress with detailed info
 */
@Composable
fun TransferProgressDialog(
    progress: TransferProgress,
    isUpload: Boolean = false,
    onHide: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    // Log when dialog renders
    timber.log.Timber.d("TransferProgressDialog: Rendering with progress=${progress.percentage}%")
    
    Dialog(onDismissRequest = onHide) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Title with percentage (like File Manager+)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUpload) "Uploading" else "Downloading",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${progress.percentage}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // File name
                Text(
                    text = progress.filePath.substringAfterLast('/'),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Transfer info: transferred/total and speed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${formatFileSize(progress.transferred)} / ${formatFileSize(progress.total)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = progress.speedFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Progress bar (green/teal like File Manager+)
                LinearProgressIndicator(
                    progress = { progress.percentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Progress count and ETA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Progress : 0 / 1",  // TODO: Support multiple files
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = calculateETA(progress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onHide) {
                        Text("Hide")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/**
 * Calculate estimated time remaining
 */
private fun calculateETA(progress: TransferProgress): String {
    if (progress.speed <= 0) return "--:--"
    
    val remaining = progress.total - progress.transferred
    val secondsRemaining = (remaining.toDouble() / progress.speed).roundToInt()
    
    val hours = secondsRemaining / 3600
    val minutes = (secondsRemaining % 3600) / 60
    val seconds = secondsRemaining % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

/**
 * Format file size (copied from SftpScreen for consistency)
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
