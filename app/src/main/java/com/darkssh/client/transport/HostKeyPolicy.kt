package com.darkssh.client.transport

/**
 * Host key trust policy shared by the terminal (trilead) and SFTP (cbssh) clients.
 *
 * Mirrors OpenSSH semantics:
 * - A host with no stored keys is unknown: ask the user (trust on first use).
 * - A presented key is trusted only if it exactly matches a stored key for that host.
 * - A host that already has stored keys but presents a key that matches none of them
 *   is treated as CHANGED and rejected without prompting. This includes a *different
 *   key type*: an attacker cannot bypass the warning by offering e.g. an RSA key when
 *   an Ed25519 key is pinned (algorithm downgrade), which previously showed the
 *   harmless-looking "new host" prompt and then auto-sent the saved password.
 *
 * To keep legitimate multi-key servers working, clients prefer the already-known key
 * types during negotiation ([preferredAlgorithms]), exactly as OpenSSH orders
 * HostKeyAlgorithms by known_hosts entries.
 */
object HostKeyPolicy {
    enum class Decision { TRUSTED, UNKNOWN_HOST, CHANGED }

    private const val KEY_TYPE_RSA = "ssh-rsa"
    private val rsaSignatureAlgorithms = listOf("rsa-sha2-512", "rsa-sha2-256", KEY_TYPE_RSA)

    /**
     * @param knownKeys Base64 key blobs already stored for this host (any algorithm).
     * @param presentedKey Base64 blob of the key the server presented.
     */
    fun evaluate(
        knownKeys: Collection<String>,
        presentedKey: String,
    ): Decision =
        when {
            knownKeys.isEmpty() -> Decision.UNKNOWN_HOST
            presentedKey in knownKeys -> Decision.TRUSTED
            else -> Decision.CHANGED
        }

    /**
     * Reads the key type (first SSH `string`) from a public key blob, e.g.
     * "ssh-ed25519", "ecdsa-sha2-nistp256" or "ssh-rsa". Returns null if malformed.
     */
    fun keyTypeFromBlob(blob: ByteArray): String? {
        if (blob.size < 4) return null
        val length =
            ((blob[0].toInt() and 0xff) shl 24) or
                ((blob[1].toInt() and 0xff) shl 16) or
                ((blob[2].toInt() and 0xff) shl 8) or
                (blob[3].toInt() and 0xff)
        if (length <= 0 || length > 64 || 4 + length > blob.size) return null
        val type = String(blob, 4, length, Charsets.US_ASCII)
        return type.takeIf { t -> t.all { it.isLetterOrDigit() || it == '-' || it == '@' || it == '.' } }
    }

    /**
     * Orders [available] host key signature algorithms so the ones matching already
     * known key types come first. Unknown types keep their original relative order.
     */
    fun preferredAlgorithms(
        knownKeyTypes: Collection<String>,
        available: List<String>,
    ): List<String> {
        val preferred =
            knownKeyTypes
                .flatMap { type -> if (type == KEY_TYPE_RSA) rsaSignatureAlgorithms else listOf(type) }
                .filter { it in available }
                .distinct()
        return preferred + available.filterNot { it in preferred }
    }
}
