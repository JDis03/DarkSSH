package com.darkssh.client.service

/**
 * Credential store with both in-memory cache and persistent encrypted storage.
 *
 * - In-memory cache: Fast access for current session (cleared on app close)
 * - Encrypted storage: Persists passwords when user chooses "Remember password"
 *
 * Flow:
 * 1. putPassword() saves to memory cache (always) + encrypted storage (if remember=true)
 * 2. getPassword() checks memory cache first, then encrypted storage
 * 3. Allows SFTP to reuse credentials from active SSH session
 */
object CredentialStore {
    private val passwords = mutableMapOf<Long, String>()
    private val keys = mutableMapOf<Long, ByteArray>()

    /**
     * Save password with optional persistence.
     * @param hostId Host database ID
     * @param password The password
     * @param remember If true, password persists across app restarts
     */
    fun putPassword(
        hostId: Long,
        password: String,
        remember: Boolean = false,
    ) {
        // Always save to memory cache
        passwords[hostId] = password

        // Save to encrypted storage if remember flag is set
        if (remember) {
            EncryptedCredentialStore.putPassword(hostId, password, remember = true)
        } else {
            // Just save to memory, don't persist
            EncryptedCredentialStore.putPassword(hostId, password, remember = false)
        }
    }

    /**
     * Get password from memory cache or encrypted storage.
     * Checks memory first (fast), then encrypted storage (persistent).
     */
    fun getPassword(hostId: Long): String? {
        // Try memory cache first
        passwords[hostId]?.let { return it }

        // Fall back to encrypted storage
        return EncryptedCredentialStore.getPassword(hostId)
    }

    /**
     * Remove credentials from both memory and encrypted storage.
     */
    fun remove(hostId: Long) {
        passwords.remove(hostId)
        keys.remove(hostId)
        EncryptedCredentialStore.remove(hostId)
    }

    /**
     * Clear all credentials from memory (does NOT clear encrypted storage).
     * Use this when logging out or clearing session.
     */
    fun clear() {
        passwords.clear()
        keys.clear()
    }

    /**
     * Clear everything including encrypted storage.
     * Use this for "Forget all passwords" feature.
     */
    fun clearAll() {
        passwords.clear()
        keys.clear()
        EncryptedCredentialStore.clear()
    }
}
