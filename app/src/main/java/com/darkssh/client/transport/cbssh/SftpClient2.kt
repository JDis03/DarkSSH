/*
 * DarkSSH SFTP Client - cbssh Migration
 * Copyright 2026 DarkSSH
 *
 * Drop-in replacement for SftpClient using cbssh instead of sshj.
 * Maintains the same public API to allow easy switching via feature flag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.darkssh.client.transport.cbssh

import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.KnownHostRepository
import com.darkssh.client.transport.ISftpClient
import com.darkssh.client.transport.SftpEntry
import com.darkssh.client.transport.TransferProgress
import com.darkssh.client.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.connectbot.sshlib.AuthResult
import org.connectbot.sshlib.ConnectResult
import org.connectbot.sshlib.HostKeyVerifier
import org.connectbot.sshlib.PublicKey
import org.connectbot.sshlib.SftpClient
import org.connectbot.sshlib.SftpDirectoryEntry
import org.connectbot.sshlib.SftpResult
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.SshSession
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.KeyPair

/**
 * Drop-in replacement for SftpClient using cbssh (ConnectBot SSH Kotlin library).
 * Implements the same public API as the sshj-based SftpClient.
 *
 * Usage:
 * ```kotlin
 * val client = SftpClient2(host, knownHostRepository, onUnknownHostKey)
 * client.connectWithPassword(password)
 * client.ls(path)  // Returns Result<List<SftpEntry>>
 * client.downloadFile(remotePath, localFile) { progress -> ... }
 * client.disconnect()
 * ```
 *
 * @param knownHostRepository Per-host known-hosts trust store used for real host key
 *   verification (see [DarkSshHostKeyVerifier]). When `null` (e.g. a caller that has
 *   not been wired up yet), the connection fails closed — every host key is rejected
 *   rather than silently trusted, since blindly trusting is the exact gap this class
 *   used to have.
 * @param onUnknownHostKey Called with (algorithm, formatted fingerprints) the first
 *   time a host's key is seen. Must return `true` to trust and persist it, `false` to
 *   reject the connection. Ignored if [knownHostRepository] is `null`.
 */
class SftpClient2(
    private val host: Host,
    private val knownHostRepository: KnownHostRepository? = null,
    private val onUnknownHostKey: (suspend (algorithm: String, fingerprints: String) -> Boolean)? = null,
) : ISftpClient {
    private var sshClient: SshClient? = null
    private var sftpClient: SftpClient? = null
    private var transfer: CbsshTransfer? = null
    private var transferEngine: TransferEngine? = null
    private var keepAliveJob: kotlinx.coroutines.Job? = null

    companion object {
        /**
         * Feature flag: use new TransferEngine instead of CbsshTransfer.
         * TransferEngine has adaptive pipeline, retry, timeouts, resume support.
         * Set to true once testing confirms it's stable.
         */
        var useTransferEngine: Boolean = true
    }

    override val isConnected: Boolean
        get() = sftpClient != null && sshClient?.isAuthenticated == true

    /**
     * Build the host key verifier for this connection.
     *
     * Backed by [DarkSshHostKeyVerifier] (real TOFU verification against
     * [knownHostRepository], matching the terminal's trust model) when a repository
     * was supplied. Fails closed — rejects every key — if it wasn't, instead of
     * falling back to the old "always trust" behavior.
     */
    private fun buildHostKeyVerifier(): HostKeyVerifier {
        val repo = knownHostRepository
        return if (repo != null) {
            DarkSshHostKeyVerifier(host, repo) { algo, fingerprints ->
                onUnknownHostKey?.invoke(algo, fingerprints) ?: false
            }
        } else {
            object : HostKeyVerifier {
                override suspend fun verify(key: PublicKey): Boolean {
                    Timber.w(
                        "SftpClient2: no KnownHostRepository configured for ${host.hostname} — " +
                            "rejecting host key (fail closed, was: always-trust)",
                    )
                    return false
                }
            }
        }
    }

    /**
     * Logs the algorithms actually negotiated for this connection (KEX, host key, cipher,
     * MAC) via [DebugLogger], so real-device logs show what was used rather than only what
     * cbssh *supports* (see `Algorithms.kt`'s full candidate lists). Also flags whether the
     * negotiated KEX is post-quantum (`mlkem768x25519-sha256`).
     *
     * Best-effort: [org.connectbot.sshlib.SshClient.connectionInfo] is nullable — swallow
     * and skip logging rather than fail an otherwise-successful connection over a
     * diagnostics gap.
     */
    private fun logNegotiatedAlgorithms(client: SshClient) {
        val info = client.connectionInfo ?: return
        val pq = if (info.isPostQuantumSecure) " [post-quantum]" else ""
        DebugLogger.i(
            "SshNegotiation",
            "kex=${info.kexAlgorithm}$pq hostkey=${info.serverHostKeyAlgorithm} " +
                "cipher(c2s/s2c)=${info.encryptionAlgorithmC2S}/${info.encryptionAlgorithmS2C} " +
                "mac(c2s/s2c)=${info.macAlgorithmC2S ?: "(aead)"}/${info.macAlgorithmS2C ?: "(aead)"}",
        )
    }

    /**
     * Mark connection as dead after an unrecoverable IoError.
     * Sets isConnected to false so the ViewModel reconnects.
     */
    private fun markDead(cause: Throwable) {
        Timber.w("cbssh SFTP connection died: ${cause.message} — marking disconnected")
        DebugLogger.w("SftpClient2", "❌ Conexión muerta: ${cause.message}")
        keepAliveJob?.cancel()
        keepAliveJob = null
        sftpClient = null
        transfer = null
        transferEngine = null
        // Don't null sshClient — let isAuthenticated reflect the real state
    }

    /**
     * Connect to SFTP server using password authentication.
     */
    override suspend fun connectWithPassword(password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val verifier = buildHostKeyVerifier()

                val client =
                    SshClient(
                        config =
                            org.connectbot.sshlib.SshClientConfig {
                                host = this@SftpClient2.host.hostname
                                port = if (this@SftpClient2.host.port <= 0) 22 else this@SftpClient2.host.port
                                this.hostKeyVerifier = verifier
                                // Keep alive cada 15s — evita que VPN/NAT/firewalls
                                // maten la conexión durante idle (envía SSH_MSG_IGNORE).
                                keepAliveIntervalMs = 15_000L
                            },
                    )

                when (val connectResult = client.connect()) {
                    is ConnectResult.Success -> { /* continue */ }

                    is ConnectResult.TransportError -> {
                        return@withContext Result.failure(connectResult.cause)
                    }

                    is ConnectResult.HostKeyRejected -> {
                        return@withContext Result.failure(
                            IOException("Host key rejected"),
                        )
                    }

                    is ConnectResult.AlgorithmMismatch -> {
                        return@withContext Result.failure(
                            IOException("Algorithm mismatch: ${connectResult.message}"),
                        )
                    }

                    is ConnectResult.ProtocolError -> {
                        return@withContext Result.failure(
                            IOException("Protocol error: ${connectResult.message}"),
                        )
                    }
                }

                when (val authResult = client.authenticatePassword(host.username, password)) {
                    is AuthResult.Success -> { /* continue */ }

                    is AuthResult.Error -> {
                        client.disconnect()
                        return@withContext Result.failure(
                            IOException("Auth failed: ${authResult.message}"),
                        )
                    }

                    else -> {
                        client.disconnect()
                        return@withContext Result.failure(
                            IOException("Auth failed: $authResult"),
                        )
                    }
                }

                sshClient = client

                when (val sftpResult = client.openSftp()) {
                    is SftpResult.Success -> {
                        sftpClient = sftpResult.value
                        transfer = CbsshTransfer(sftpResult.value)
                        transferEngine = TransferEngine(sftpResult.value)
                    }

                    else -> {
                        client.disconnect()
                        return@withContext Result.failure(sftpResult.toException())
                    }
                }

                val engineInfo = if (useTransferEngine) "TransferEngine" else "CbsshTransfer"
                Timber.d("cbssh SFTP session opened for ${host.hostname} (using $engineInfo)")
                DebugLogger.i("SftpClient2", "✅ Conectado (password) a ${host.hostname}:${host.port} como ${host.username} [$engineInfo]")
                logNegotiatedAlgorithms(client)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "cbssh SFTP connect+auth failed")
                DebugLogger.e("SftpClient2", "❌ Error conectando a ${host.hostname}: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Connect to SFTP server using SSH key pair.
     *
     * Supports RSA, ECDSA (P-256/P-384/P-521), and Ed25519 keys.
     * Encrypted keys (passphrase) are not supported yet.
     */
    override suspend fun connectWithKey(keyPair: KeyPair): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val verifier = buildHostKeyVerifier()

                val client =
                    SshClient(
                        config =
                            org.connectbot.sshlib.SshClientConfig {
                                host = this@SftpClient2.host.hostname
                                port = if (this@SftpClient2.host.port <= 0) 22 else this@SftpClient2.host.port
                                this.hostKeyVerifier = verifier
                                // Keep alive cada 15s — evita que VPN/NAT/firewalls
                                // maten la conexión durante idle (envía SSH_MSG_IGNORE).
                                keepAliveIntervalMs = 15_000L
                            },
                    )

                when (val connectResult = client.connect()) {
                    is ConnectResult.Success -> { /* continue */ }

                    is ConnectResult.TransportError -> {
                        return@withContext Result.failure(connectResult.cause)
                    }

                    is ConnectResult.HostKeyRejected -> {
                        return@withContext Result.failure(
                            IOException("Host key rejected"),
                        )
                    }

                    is ConnectResult.AlgorithmMismatch -> {
                        return@withContext Result.failure(
                            IOException("Algorithm mismatch: ${connectResult.message}"),
                        )
                    }

                    is ConnectResult.ProtocolError -> {
                        return@withContext Result.failure(
                            IOException("Protocol error: ${connectResult.message}"),
                        )
                    }
                }

                // Convert KeyPair to OpenSSH PEM format
                val pemData = KeyPairToPem.toPem(keyPair)

                // Authenticate using PEM-encoded key (no passphrase support yet)
                when (
                    val authResult =
                        client.authenticatePublicKey(
                            username = host.username,
                            privateKeyData = pemData,
                            passphrase = null,
                        )
                ) {
                    is AuthResult.Success -> { /* continue */ }

                    is AuthResult.Error -> {
                        client.disconnect()
                        return@withContext Result.failure(
                            IOException("Auth failed: ${authResult.message}"),
                        )
                    }

                    else -> {
                        client.disconnect()
                        return@withContext Result.failure(
                            IOException("Auth failed: $authResult"),
                        )
                    }
                }

                sshClient = client

                when (val sftpResult = client.openSftp()) {
                    is SftpResult.Success -> {
                        sftpClient = sftpResult.value
                        transfer = CbsshTransfer(sftpResult.value)
                        transferEngine = TransferEngine(sftpResult.value)
                    }

                    else -> {
                        client.disconnect()
                        return@withContext Result.failure(sftpResult.toException())
                    }
                }

                val engineInfo = if (useTransferEngine) "TransferEngine" else "CbsshTransfer"
                Timber.d("cbssh SFTP session opened (pubkey auth) for ${host.hostname} (using $engineInfo)")
                DebugLogger.i("SftpClient2", "✅ Conectado (pubkey) a ${host.hostname}:${host.port} como ${host.username} [$engineInfo]")
                logNegotiatedAlgorithms(client)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "cbssh SFTP pubkey auth failed")
                DebugLogger.e("SftpClient2", "❌ Error conectando (pubkey) a ${host.hostname}: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Disconnect from SFTP server.
     */
    override suspend fun disconnect() =
        withContext(Dispatchers.IO) {
            try {
                sftpClient?.close()
                sftpClient = null
                sshClient?.disconnect()
                sshClient = null
                transfer = null
                transferEngine = null
                Timber.d("cbssh SFTP session closed for ${host.hostname}")
            } catch (e: Exception) {
                Timber.e(e, "Error closing cbssh SFTP session")
            }
        }

    /**
     * Mark session as disconnected without graceful close.
     */
    override fun setDisconnected() {
        sftpClient = null
        sshClient = null
        transfer = null
        transferEngine = null
        Timber.w("cbssh SFTP marked as disconnected for ${host.hostname}")
    }

    /**
     * Get current working directory.
     */
    override suspend fun pwd(): String =
        withContext(Dispatchers.IO) {
            try {
                val client = sftpClient ?: return@withContext "/"
                when (val result = client.realpath(".")) {
                    is SftpResult.Success -> result.value
                    else -> "/"
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get working directory")
                "/"
            }
        }

    /**
     * List directory contents.
     */
    override suspend fun ls(path: String): Result<List<SftpEntry>> =
        withContext(Dispatchers.IO) {
            val client = sftpClient ?: return@withContext Result.failure(Exception("SFTP not connected"))
            DebugLogger.d("SftpClient2", "ls: $path")
            when (val result = client.listdir(path)) {
                is SftpResult.Success -> {
                    val entries =
                        result.value
                            .mapNotNull { entry ->
                                if (entry.filename == "." || entry.filename == "..") {
                                    null
                                } else {
                                    convertEntry(entry, path)
                                }
                            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    DebugLogger.d("SftpClient2", "ls: ${entries.size} entradas en $path")
                    Result.success(entries)
                }

                is SftpResult.ServerError -> {
                    Result.failure(
                        IOException("SFTP server error: ${result.message}"),
                    )
                }

                is SftpResult.ProtocolError -> {
                    Result.failure(
                        IOException("SFTP protocol error: ${result.message}"),
                    )
                }

                is SftpResult.IoError -> {
                    markDead(result.cause)
                    Result.failure(result.cause)
                }
            }
        }

    /**
     * Convert SftpDirectoryEntry to SftpEntry.
     */
    private fun convertEntry(
        entry: SftpDirectoryEntry,
        parentPath: String,
    ): SftpEntry {
        val fullPath =
            if (parentPath.endsWith("/")) {
                "$parentPath${entry.filename}"
            } else {
                "$parentPath/${entry.filename}"
            }
        val attrs = entry.attrs
        return SftpEntry(
            name = entry.filename,
            path = fullPath,
            isDirectory = isDirectoryFromPermissions(attrs.permissions),
            isSymlink = false, // cbssh doesn't expose this in listdir
            size = attrs.size ?: 0L,
            permissions = attrs.permissions?.toString(),
            modifiedTime = (attrs.mtime?.toLong() ?: 0L) * 1000L,
        )
    }

    /**
     * Download a file to a stream with progress tracking.
     */
    override suspend fun downloadToStream(
        remotePath: String,
        outputStream: OutputStream,
        onProgress: ((TransferProgress) -> Unit)?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            DebugLogger.d("SftpClient2", "downloadToStream: $remotePath [engine=$useTransferEngine]")

            if (useTransferEngine) {
                val engine =
                    transferEngine ?: return@withContext Result.failure(
                        Exception("SFTP not connected"),
                    )
                mapResult(engine.downloadToStreamCompat(remotePath, outputStream, onProgress))
            } else {
                val transfer =
                    transfer ?: return@withContext Result.failure(
                        Exception("SFTP not connected"),
                    )
                mapResult(transfer.downloadToStream(remotePath, outputStream, onProgress))
            }
        }

    /**
     * Download a file to a local file with progress tracking.
     */
    override suspend fun downloadFile(
        remotePath: String,
        localFile: File,
        onProgress: ((TransferProgress) -> Unit)?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            DebugLogger.d("SftpClient2", "downloadFile: $remotePath → ${localFile.name} [engine=$useTransferEngine]")

            if (useTransferEngine) {
                val engine =
                    transferEngine ?: return@withContext Result.failure(
                        Exception("SFTP not connected"),
                    )
                mapResult(engine.downloadCompat(remotePath, localFile, onProgress))
            } else {
                val transfer =
                    transfer ?: return@withContext Result.failure(
                        Exception("SFTP not connected"),
                    )
                mapResult(transfer.download(remotePath, localFile, onProgress))
            }
        }

    /**
     * Upload a file with progress tracking.
     * Tries regular SFTP upload first, falls back to SCP if available.
     */
    override suspend fun uploadFile(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)?,
    ): Result<Unit> {
        DebugLogger.d(
            "SftpClient2",
            "uploadFile: ${localFile.name} (${localFile.length()} bytes) → $remotePath [engine=$useTransferEngine]",
        )

        val sftpResult =
            if (useTransferEngine) {
                val engine = transferEngine ?: return Result.failure(Exception("SFTP not connected"))
                engine.uploadCompat(localFile, remotePath, onProgress)
            } else {
                val transfer = transfer ?: return Result.failure(Exception("SFTP not connected"))
                transfer.upload(localFile, remotePath, onProgress)
            }

        if (sftpResult is SftpResult.Success) {
            DebugLogger.i("SftpClient2", "✅ Upload OK (SFTP): ${localFile.name}")
            return Result.success(Unit)
        }

        // Check if file exists (might have succeeded despite error)
        if (exists(remotePath)) {
            Timber.w("Upload reported error but file exists, considering success")
            DebugLogger.w("SftpClient2", "⚠️ Upload SFTP reportó error pero el archivo existe en $remotePath")
            return Result.success(Unit)
        }

        // Fall back to SCP via SSH session
        Timber.w("SFTP upload failed, falling back to SCP")
        DebugLogger.w("SftpClient2", "⚠️ SFTP upload falló, intentando SCP fallback")
        return uploadViaScp(localFile, remotePath, onProgress)
    }

    /**
     * Upload using SCP protocol via SSH exec channel (fallback for large files).
     */
    private suspend fun uploadViaScp(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val ssh = sshClient ?: return@withContext Result.failure(Exception("SSH not connected"))
                val session =
                    ssh.openSession()
                        ?: return@withContext Result.failure(Exception("Failed to open session"))

                session.use { s ->
                    if (!s.requestExec("scp -t $remotePath")) {
                        return@withContext Result.failure(Exception("Failed to exec scp"))
                    }

                    val totalBytes = localFile.length()
                    val startTime = System.currentTimeMillis()

                    // Send SCP header: C0644 <size> <filename>\n
                    val header = "C0644 $totalBytes ${localFile.name}\n"
                    s.write(header.toByteArray())

                    // Wait for initial ACK (0x00)
                    val ack = s.read()
                    if (ack == null || ack.isEmpty() || ack[0] != 0x00.toByte()) {
                        return@withContext Result.failure(Exception("No ACK after SCP header"))
                    }

                    // Send file data
                    var bytesWritten = 0L
                    val buffer = ByteArray(32 * 1024)
                    var lastReportedBytes = 0L

                    localFile.inputStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break

                            s.write(buffer.copyOf(read))
                            bytesWritten += read

                            // Throttled progress (every 256KB)
                            if (bytesWritten - lastReportedBytes >= 256L * 1024 || bytesWritten >= totalBytes) {
                                onProgress?.invoke(
                                    TransferProgress(
                                        transferred = bytesWritten,
                                        total = totalBytes,
                                        filePath = localFile.name,
                                        startTime = startTime,
                                        currentTime = System.currentTimeMillis(),
                                    ),
                                )
                                lastReportedBytes = bytesWritten
                            }
                        }
                    }

                    // Send terminator byte (0x00)
                    s.write(byteArrayOf(0x00))

                    // Wait for final ACK
                    val finalAck = s.read()
                    if (finalAck == null || finalAck.isEmpty() || finalAck[0] != 0x00.toByte()) {
                        return@withContext Result.failure(Exception("No final ACK from SCP"))
                    }

                    s.sendEof()
                    Timber.d("SCP upload completed: ${localFile.name}")
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Timber.e(e, "SCP upload failed: ${localFile.name}")
                Result.failure(e)
            }
        }

    /**
     * Upload a file in parallel chunks.
     * Currently delegates to regular upload (parallel chunks have known issues).
     */
    suspend fun uploadFileParallel(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
        chunkSize: Long = 10L * 1024 * 1024,
        parallelChunks: Int = 4,
    ): Result<Unit> {
        Timber.d("Using cbssh SFTP upload (parallel chunks deferred): ${localFile.name}")
        return uploadFile(localFile, remotePath, onProgress)
    }

    /**
     * Create a directory.
     */
    override suspend fun mkdir(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val client = sftpClient ?: return@withContext Result.failure(Exception("SFTP not connected"))
            DebugLogger.d("SftpClient2", "mkdir: $path")
            mapResult(client.mkdir(path))
        }

    /**
     * Delete a file.
     */
    override suspend fun rm(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val client = sftpClient ?: return@withContext Result.failure(Exception("SFTP not connected"))
            DebugLogger.d("SftpClient2", "rm: $path")
            mapResult(client.remove(path))
        }

    /**
     * Remove an empty directory.
     */
    override suspend fun rmdir(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val client = sftpClient ?: return@withContext Result.failure(Exception("SFTP not connected"))
            DebugLogger.d("SftpClient2", "rmdir: $path")
            mapResult(client.rmdir(path))
        }

    /**
     * Rename or move a file/directory.
     */
    override suspend fun rename(
        oldPath: String,
        newPath: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val client = sftpClient ?: return@withContext Result.failure(Exception("SFTP not connected"))
            DebugLogger.d("SftpClient2", "rename: $oldPath → $newPath")
            mapResult(client.rename(oldPath, newPath))
        }

    /**
     * Check if a path exists.
     */
    override suspend fun exists(path: String): Boolean =
        withContext(Dispatchers.IO) {
            val client = sftpClient ?: return@withContext false
            when (client.stat(path)) {
                is SftpResult.Success -> true
                else -> false
            }
        }

    /**
     * Get file/directory metadata.
     */
    override suspend fun stat(path: String): Result<SftpEntry?> =
        withContext(Dispatchers.IO) {
            val client = sftpClient ?: return@withContext Result.failure(Exception("SFTP not connected"))
            when (val result = client.stat(path)) {
                is SftpResult.Success -> {
                    val attrs = result.value
                    val name = path.substringAfterLast('/')
                    Result.success(
                        SftpEntry(
                            name = name,
                            path = path,
                            isDirectory = isDirectoryFromPermissions(attrs.permissions),
                            isSymlink = false,
                            size = attrs.size ?: 0L,
                            permissions = attrs.permissions?.toString(),
                            modifiedTime = (attrs.mtime?.toLong() ?: 0L) * 1000L,
                        ),
                    )
                }

                is SftpResult.ServerError -> {
                    // File not found is not an error here
                    if (result.statusCode.name == "NO_SUCH_FILE") {
                        Result.success(null)
                    } else {
                        Result.failure(IOException("SFTP server error: ${result.message}"))
                    }
                }

                is SftpResult.ProtocolError -> {
                    Result.failure(
                        IOException("SFTP protocol error: ${result.message}"),
                    )
                }

                is SftpResult.IoError -> {
                    Result.failure(
                        result.cause,
                    )
                }
            }
        }

    /**
     * Execute a command on the remote server via SSH.
     */
    private suspend fun executeCommand(command: String): Result<Pair<String, Int>> =
        withContext(Dispatchers.IO) {
            try {
                val ssh = sshClient ?: return@withContext Result.failure(Exception("SSH not connected"))
                val session =
                    ssh.openSession()
                        ?: return@withContext Result.failure(Exception("Failed to open session"))

                session.use { s ->
                    if (!s.requestExec(command)) {
                        return@withContext Result.failure(Exception("Failed to exec command"))
                    }

                    // Collect output
                    val outputBuilder = StringBuilder()
                    while (true) {
                        val data = s.read() ?: break
                        outputBuilder.append(String(data))
                    }

                    s.sendEof()

                    Result.success(Pair(outputBuilder.toString(), 0))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to execute command: $command")
                Result.failure(e)
            }
        }

    /**
     * Server-side file copy.
     */
    override suspend fun copyFileViaSsh(
        sourcePath: String,
        destPath: String,
        isDirectory: Boolean,
        overwrite: Boolean,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val ssh = sshClient ?: return@withContext Result.failure(Exception("SSH not connected"))
                val session =
                    ssh.openSession()
                        ?: return@withContext Result.failure(Exception("Failed to open session"))

                session.use { s ->
                    val flags =
                        buildString {
                            if (isDirectory) append("-r ")
                            if (overwrite) append("-f ")
                        }.trim()
                    val command = "cp $flags '$sourcePath' '$destPath'"
                    DebugLogger.d("SftpClient2", "copyFileViaSsh: $command")
                    if (!s.requestExec(command)) {
                        return@withContext Result.failure(Exception("Failed to exec cp command"))
                    }
                    // Drain stdout to EOF to ensure command completes
                    while (true) {
                        val data = s.read() ?: break
                    }
                    s.sendEof()
                    DebugLogger.i("SftpClient2", "✅ copy OK: $sourcePath → $destPath")
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy via SSH: $sourcePath -> $destPath")
                DebugLogger.e("SftpClient2", "❌ copy falló: $sourcePath → $destPath: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Move a file (rename + delete source if on same device).
     */
    override suspend fun moveFile(
        sourcePath: String,
        destPath: String,
    ): Result<Unit> {
        DebugLogger.d("SftpClient2", "moveFile: $sourcePath → $destPath")
        // Try simple rename first
        val renameResult = rename(sourcePath, destPath)
        if (renameResult.isSuccess) {
            return renameResult
        }
        // Fallback: copy via SFTP + delete
        val t = transfer ?: return Result.failure(Exception("SFTP not connected"))
        val copyResult = mapResult(t.copy(sourcePath, destPath))
        if (copyResult.isFailure) {
            return copyResult
        }
        return rm(sourcePath)
    }

    /**
     * Recursively delete a directory via SSH rm -rf.
     */
    override suspend fun deleteDirectoryViaSsh(remotePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val ssh = sshClient ?: return@withContext Result.failure(Exception("SSH not connected"))
                val session =
                    ssh.openSession()
                        ?: return@withContext Result.failure(Exception("Failed to open session"))

                session.use { s ->
                    val command = "rm -rf '$remotePath'"
                    DebugLogger.d("SftpClient2", "deleteDirectoryViaSsh: $command")
                    if (!s.requestExec(command)) {
                        return@withContext Result.failure(Exception("Failed to exec rm command"))
                    }
                    // Drain stdout to EOF
                    while (true) {
                        s.read() ?: break
                    }
                    s.sendEof()
                    DebugLogger.i("SftpClient2", "✅ delete OK: $remotePath")
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete directory via SSH: $remotePath")
                DebugLogger.e("SftpClient2", "❌ delete falló: $remotePath: ${e.message}")
                Result.failure(e)
            }
        }
}

/**
 * Map SftpResult<T> to Result<Unit> preserving error info.
 *
 * `internal` (rather than `private`) so unit tests in this module can
 * exercise the mapping directly without exercising real SFTP I/O. Not part
 * of the public API.
 */
internal fun <T> mapResult(result: SftpResult<T>): Result<Unit> =
    when (result) {
        is SftpResult.Success -> {
            Result.success(Unit)
        }

        is SftpResult.ServerError -> {
            Result.failure(
                IOException("SFTP server error: ${result.message}"),
            )
        }

        is SftpResult.ProtocolError -> {
            Result.failure(
                IOException("SFTP protocol error: ${result.message}"),
            )
        }

        is SftpResult.IoError -> {
            Result.failure(
                result.cause as? Exception ?: IOException(result.cause.message ?: "SFTP I/O error"),
            )
        }
    }

/**
 * Convert SftpResult to Exception.
 */
private fun SftpResult<*>.toException(): Exception =
    when (this) {
        is SftpResult.Success<*> -> IOException("Operation succeeded but returned value (unexpected)")
        is SftpResult.ServerError -> IOException("SFTP server error: $message")
        is SftpResult.ProtocolError -> IOException("SFTP protocol error: $message")
        is SftpResult.IoError -> cause as? Exception ?: IOException(cause.message ?: "SFTP I/O error")
    }

/**
 * Check if the given permissions indicate a directory.
 * S_IFMT (0xF000) mask with S_IFDIR (0x4000).
 */
private fun isDirectoryFromPermissions(permissions: Int?): Boolean = permissions != null && (permissions and 0xF000) == 0x4000
