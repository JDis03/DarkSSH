/*
 * DarkSSH SFTP Client - Common Interface
 * Copyright 2026 DarkSSH
 *
 * Common interface implemented by both:
 * - SftpClient (sshj-based legacy implementation)
 * - SftpClient2 (cbssh-based new implementation)
 *
 * Allows the SFTP feature flag (useCbsshSftp) to swap implementations
 * transparently at the ViewModel/Worker level.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.darkssh.client.transport

import java.io.File
import java.io.OutputStream
import java.security.KeyPair

/**
 * Common interface for SFTP client implementations.
 *
 * Both [SftpClient] (sshj-based) and [SftpClient2] (cbssh-based)
 * implement this interface to enable runtime feature-flag selection
 * via [SftpClientFactory].
 */
interface ISftpClient {
    /** Whether the client is currently connected to the SFTP server. */
    val isConnected: Boolean

    /** Connect using a password. */
    suspend fun connectWithPassword(password: String): Result<Unit>

    /** Connect using an SSH key pair. */
    suspend fun connectWithKey(keyPair: KeyPair): Result<Unit>

    /** Disconnect from the SFTP server. */
    suspend fun disconnect()

    /**
     * Mark as disconnected without performing server-side cleanup.
     * Used when the connection is known to be dead.
     */
    fun setDisconnected()

    /** Get the current working directory on the remote server. */
    suspend fun pwd(): String

    /** List files in a directory. */
    suspend fun ls(path: String): Result<List<SftpEntry>>

    /**
     * Download a remote file to an OutputStream.
     *
     * @param onProgress Optional callback invoked with [TransferProgress] updates.
     */
    suspend fun downloadToStream(
        remotePath: String,
        outputStream: OutputStream,
        onProgress: ((TransferProgress) -> Unit)?,
    ): Result<Unit>

    /**
     * Download a remote file to a local file.
     *
     * @param onProgress Optional callback invoked with [TransferProgress] updates.
     */
    suspend fun downloadFile(
        remotePath: String,
        localFile: File,
        onProgress: ((TransferProgress) -> Unit)?,
    ): Result<Unit>

    /**
     * Upload a local file to the remote server.
     *
     * @param onProgress Optional callback invoked with [TransferProgress] updates.
     */
    suspend fun uploadFile(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)?,
    ): Result<Unit>

    /** Create a remote directory. */
    suspend fun mkdir(path: String): Result<Unit>

    /** Remove a remote file. */
    suspend fun rm(path: String): Result<Unit>

    /** Remove a remote directory. */
    suspend fun rmdir(path: String): Result<Unit>

    /** Rename a remote file or directory. */
    suspend fun rename(
        oldPath: String,
        newPath: String,
    ): Result<Unit>

    /** Check whether a remote path exists. */
    suspend fun exists(path: String): Boolean

    /** Stat a remote path and return its metadata. */
    suspend fun stat(path: String): Result<SftpEntry?>

    /**
     * Copy a file or directory via SSH (much faster than SFTP streaming).
     *
     * @param isDirectory Whether the source is a directory (recursive copy required).
     */
    suspend fun copyFileViaSsh(
        sourcePath: String,
        destPath: String,
        isDirectory: Boolean,
    ): Result<Unit>

    /** Move a file from sourcePath to destPath. */
    suspend fun moveFile(
        sourcePath: String,
        destPath: String,
    ): Result<Unit>
}
