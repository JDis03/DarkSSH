/*
 * DarkSSH SFTP - TransferProgress data class
 * Progress information passed from transfer engines to UI.
 *
 * Used by both sshj (legacy) and cbssh (new) SFTP clients, plus
 * SftpTransferService. Kept in the transport package so callers can
 * import it without depending on a specific client implementation.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport

/**
 * Progress update emitted during an SFTP file transfer.
 *
 * Emitted by [ISftpClient] download/upload callbacks and rendered by the
 * transfer progress dialog. Pure value type — no behavior beyond derived
 * fields (percentage, speed, formatting).
 *
 * @param transferred Bytes successfully transferred so far.
 * @param total Total expected bytes (0 for unknown / empty files).
 * @param filePath Local path of the file being transferred, used for display.
 * @param startTime Timestamp when the transfer began (millis since epoch).
 * @param currentTime Timestamp of this progress emission (millis since epoch).
 */
data class TransferProgress(
    val transferred: Long,
    val total: Long,
    val filePath: String,
    val startTime: Long = System.currentTimeMillis(),
    val currentTime: Long = System.currentTimeMillis(),
) {
    /** Progress as integer percentage 0-100+. May exceed 100 if transferred > total. */
    val percentage: Int get() = if (total > 0) (transferred * 100 / total).toInt() else 0

    /** Seconds elapsed between [startTime] and [currentTime]. May be negative if time goes backwards. */
    val elapsedSeconds: Double get() = (currentTime - startTime) / 1000.0

    /** Current transfer speed in bytes per second. Returns 0 if elapsed time is zero or negative. */
    val speed: Long get() = if (elapsedSeconds > 0) (transferred / elapsedSeconds).toLong() else 0L

    /** Human-readable speed: "X B/s", "X.X KB/s", or "X.X MB/s". */
    val speedFormatted: String get() = formatSpeed(speed)

    private fun formatSpeed(bytesPerSecond: Long): String =
        when {
            bytesPerSecond >= 1_048_576 -> "%.1f MB/s".format(bytesPerSecond / 1_048_576.0)
            bytesPerSecond >= 1024 -> "%.1f KB/s".format(bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }
}
