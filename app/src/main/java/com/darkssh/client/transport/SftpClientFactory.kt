/*
 * DarkSSH SFTP Client - Factory
 * Copyright 2026 DarkSSH
 *
 * Factory that returns either the legacy sshj-based SftpClient or the new
 * cbssh-based SftpClient2 based on the user's preference flag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.darkssh.client.transport

import android.content.Context
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.KnownHostRepository
import com.darkssh.client.transport.cbssh.SftpClient2
import com.darkssh.client.util.AppPreferences
import com.darkssh.client.util.DebugLogger
import timber.log.Timber

/**
 * Factory for creating SFTP client implementations.
 *
 * Returns either:
 * - [SftpClient] (sshj-based legacy implementation)
 * - [SftpClient2] (cbssh-based new implementation)
 *
 * Selection is based on the `useCbsshSftp` user preference. Defaults to the
 * legacy implementation for safety during the migration period.
 */
object SftpClientFactory {
    /**
     * Create an SFTP client for the given host using the user's preferred implementation.
     *
     * @param host The SSH host to connect to.
     * @param context Android context for accessing SharedPreferences.
     * @param knownHostRepository Per-host known-hosts trust store, forwarded to
     *   [SftpClient2] for real host key verification. Optional so existing callers
     *   that only care about the sshj-vs-cbssh backend selection (e.g. tests that
     *   never call connect()) keep compiling unchanged; a caller that actually
     *   connects via the cbssh backend should always supply it.
     * @param onUnknownHostKey Forwarded to [SftpClient2]; see its documentation.
     * @return [ISftpClient] implementation based on the useCbsshSftp preference.
     */
    fun create(
        host: Host,
        context: Context,
        knownHostRepository: KnownHostRepository? = null,
        onUnknownHostKey: (suspend (algorithm: String, fingerprints: String) -> Boolean)? = null,
    ): ISftpClient {
        val useCbssh = AppPreferences.getUseCbsshSftp(context)
        val implName = if (useCbssh) "cbssh (SftpClient2)" else "sshj (SftpClient legacy)"
        Timber.i("SftpClientFactory: using $implName for ${host.hostname}")
        DebugLogger.i("SftpClientFactory", "Backend: $implName → ${host.hostname}:${host.port}")
        return if (useCbssh) {
            SftpClient2(host, knownHostRepository, onUnknownHostKey)
        } else {
            SftpClient(host)
        }
    }
}
