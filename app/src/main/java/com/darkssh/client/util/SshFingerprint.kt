/*
 * DarkSSH - Shared SSH host key fingerprint formatting
 *
 * Used by both the terminal's host key verifier (SSH.kt, sshlib) and the
 * SFTP host key verifier (DarkSshHostKeyVerifier, cbssh) so both surfaces
 * render an identical fingerprint string for the same key, instead of two
 * copies of the same formatting logic drifting apart over time.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.darkssh.client.util

import android.util.Base64
import java.security.MessageDigest

/**
 * Formats a host key's MD5 and SHA256 fingerprints for display in a
 * host-key-verification prompt.
 */
object SshFingerprint {
    /**
     * @param algo Host key algorithm name (e.g. "ssh-ed25519").
     * @param key Raw host key bytes (wire format).
     * @return Multi-line string: algorithm, SHA256 fingerprint, MD5 fingerprint.
     */
    fun build(
        algo: String,
        key: ByteArray,
    ): String {
        val md5 =
            MessageDigest
                .getInstance("MD5")
                .digest(key)
                .joinToString(":") { "%02x".format(it) }
        val sha256 =
            Base64.encodeToString(
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(key),
                Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
            )
        return "$algo\nSHA256:$sha256\nMD5:$md5"
    }
}
