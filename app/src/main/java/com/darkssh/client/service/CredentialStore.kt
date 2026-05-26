package com.darkssh.client.service

/**
 * In-memory credential store shared between SSH terminal and SFTP.
 * Allows SFTP to reuse the same credentials as the active terminal session.
 */
object CredentialStore {
    private val passwords = mutableMapOf<Long, String>()
    private val keys = mutableMapOf<Long, ByteArray>()

    fun putPassword(hostId: Long, password: String) {
        passwords[hostId] = password
    }

    fun getPassword(hostId: Long): String? = passwords[hostId]

    fun remove(hostId: Long) {
        passwords.remove(hostId)
        keys.remove(hostId)
    }

    fun clear() {
        passwords.clear()
        keys.clear()
    }
}
