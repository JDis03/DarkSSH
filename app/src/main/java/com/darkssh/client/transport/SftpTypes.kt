/*
 * DarkSSH SFTP - Shared value types (SftpEntry, SftpAuthState)
 * Used by both sshj (legacy) and cbssh (new) SFTP clients, plus
 * SftpViewModel, SftpScreen and SftpClipboard.
 *
 * Kept in the transport package (not inside a specific client's file) so
 * callers can import them without depending on a specific implementation,
 * and so the sshj legacy client can eventually be deleted without breaking
 * the rest of the app.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport

/**
 * A single file or directory entry returned by [ISftpClient.ls] / [ISftpClient.stat].
 *
 * Pure value type — no behavior beyond derived fields consumed by the UI
 * (sorting, icons, formatting).
 *
 * @param name File/directory name (no path components).
 * @param path Full remote path.
 * @param isDirectory Whether this entry is a directory.
 * @param isSymlink Whether this entry is a symbolic link.
 * @param size File size in bytes (0 for directories).
 * @param permissions POSIX permission string (e.g. "rwxr-xr-x"), or null if unavailable.
 * @param modifiedTime Last modified timestamp (millis since epoch), or null if unavailable.
 */
data class SftpEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long,
    val permissions: String?,
    val modifiedTime: Long?,
)

/**
 * Authentication state for an SFTP session, surfaced by [SftpViewModel] and
 * rendered by [SftpScreen] (password prompt, connecting spinner, error banner).
 */
sealed class SftpAuthState {
    data object Idle : SftpAuthState()

    data object Connecting : SftpAuthState()

    data class NeedsPassword(
        val hostname: String,
        val username: String,
    ) : SftpAuthState()

    data object Authenticating : SftpAuthState()

    data object Authenticated : SftpAuthState()

    data class Failed(
        val message: String,
    ) : SftpAuthState()
}
