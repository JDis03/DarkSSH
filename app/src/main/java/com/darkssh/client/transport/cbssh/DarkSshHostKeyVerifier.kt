/*
 * DarkSSH SFTP Client - Host Key Verification (cbssh)
 * Copyright 2026 DarkSSH
 *
 * cbssh HostKeyVerifier backed by KnownHostRepository — the SFTP equivalent
 * of SSH.kt's inner HostKeyVerifier class (used by the terminal). Same TOFU
 * semantics, same KnownHostRepository schema, same fingerprint format:
 * unknown key -> ask onUnknownKey and persist on accept; known mismatch ->
 * reject without asking.
 *
 * Unlike SSH.kt's verifier, this one needs no runBlocking bridge — cbssh's
 * HostKeyVerifier.verify() is already a suspend function.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.darkssh.client.transport.cbssh

import android.util.Base64
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.entity.KnownHost
import com.darkssh.client.data.repository.KnownHostRepository
import com.darkssh.client.util.SshFingerprint
import org.connectbot.sshlib.HostKeyVerifier
import org.connectbot.sshlib.PublicKey
import timber.log.Timber

/**
 * Verifies an SFTP server's host key against [KnownHostRepository], the same
 * per-host trust store the terminal (`SSH.kt`) uses.
 *
 * @param host The host being connected to (identifies the trust store row).
 * @param knownHostRepository Persistent per-host known-hosts store.
 * @param onUnknownKey Called with (algorithm, formatted fingerprints) when no
 *   stored key exists yet for this host+algorithm. Must return `true` to
 *   trust (and persist) the key, `false` to reject the connection.
 */
class DarkSshHostKeyVerifier(
    private val host: Host,
    private val knownHostRepository: KnownHostRepository,
    private val onUnknownKey: suspend (algorithm: String, fingerprints: String) -> Boolean,
) : HostKeyVerifier {
    companion object {
        private const val TAG = "DarkSshHostKeyVerifier"
    }

    override suspend fun verify(key: PublicKey): Boolean {
        val keyData = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
        val existing = knownHostRepository.getByHostIdAndAlgo(host.id, key.type)

        if (existing.isEmpty()) {
            Timber.d("$TAG: no known host key for host=${host.id} algo=${key.type}, prompting")
            val fingerprints = SshFingerprint.build(key.type, key.encoded)
            val accepted = onUnknownKey(key.type, fingerprints)
            if (accepted) {
                knownHostRepository.insert(
                    KnownHost(
                        hostId = host.id,
                        hostname = host.hostname,
                        port = host.port,
                        hostKeyAlgo = key.type,
                        hostKey = keyData,
                    ),
                )
                Timber.d("$TAG: host key accepted and saved for host=${host.id}")
            } else {
                Timber.w("$TAG: host key rejected by user for host=${host.id}")
            }
            return accepted
        }

        val matches = existing.any { it.hostKey == keyData }
        Timber.d("$TAG: found ${existing.size} known host key(s) for host=${host.id}, match=$matches")
        return matches
    }
}
