/*
 * DarkSSH SFTP Client - cbssh Migration
 * Copyright 2026 DarkSSH
 *
 * Transfer progress data class for cbssh-based transfers.
 * Same structure as the existing one used by sshj.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.darkssh.client.transport.cbssh

/**
 * Data class representing transfer progress (download/upload/copy).
 * Used as callback parameter for cbssh-based transfer operations.
 *
 * @param bytesTransferred Bytes transferred so far
 * @param totalBytes Total bytes to transfer
 * @param filename Name of the file being transferred
 * @param startTime Unix timestamp when transfer started
 * @param currentTime Unix timestamp of this progress update
 */
data class TransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val filename: String,
    val startTime: Long,
    val currentTime: Long,
) {
    /** Progress as percentage (0.0 to 1.0) */
    val percentage: Float
        get() = if (totalBytes > 0) bytesTransferred.toFloat() / totalBytes else 0f

    /** Transfer speed in bytes per second */
    val speedBytesPerSecond: Long
        get() {
            val elapsedMs = currentTime - startTime
            if (elapsedMs <= 0) return 0L
            return (bytesTransferred * 1000L) / elapsedMs
        }

    /** Formatted speed string (e.g., "1.5 MB/s") */
    val speedFormatted: String
        get() = formatSpeed(speedBytesPerSecond)

    private fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0) return "0 B/s"
        return when {
            bytesPerSecond >= 1024L * 1024L -> "${bytesPerSecond / (1024L * 1024L)} MB/s"
            bytesPerSecond >= 1024L -> "${bytesPerSecond / 1024L} KB/s"
            else -> "$bytesPerSecond B/s"
        }
    }
}