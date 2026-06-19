package com.darkssh.client.service

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

/**
 * Persistent encrypted credential store using EncryptedSharedPreferences.
 * Credentials are encrypted with Android Keystore and survive app restarts.
 */
object EncryptedCredentialStore {
    private const val PREFS_NAME = "darkssh_encrypted_credentials"
    private const val KEY_PREFIX_PASSWORD = "pwd_"
    private const val KEY_PREFIX_REMEMBER = "remember_"

    private var sharedPrefs: SharedPreferences? = null

    /**
     * Initialize the encrypted store. Must be called before any other method.
     * Call from Application.onCreate() or before first use.
     */
    fun init(context: Context) {
        if (sharedPrefs != null) return

        try {
            val masterKey =
                MasterKey
                    .Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

            sharedPrefs =
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize EncryptedCredentialStore")
        }
    }

    /**
     * Save password for a host with optional remember flag.
     * @param hostId Host database ID
     * @param password The password to save
     * @param remember If true, password persists across app restarts
     */
    fun putPassword(
        hostId: Long,
        password: String,
        remember: Boolean = false,
    ) {
        try {
            sharedPrefs?.edit()?.apply {
                putString(KEY_PREFIX_PASSWORD + hostId, password)
                putBoolean(KEY_PREFIX_REMEMBER + hostId, remember)
                apply()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to save password for host $hostId")
        }
    }

    /**
     * Get saved password for a host.
     * @param hostId Host database ID
     * @return Saved password or null if not found
     */
    fun getPassword(hostId: Long): String? =
        try {
            sharedPrefs?.getString(KEY_PREFIX_PASSWORD + hostId, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get password for host $hostId")
            null
        }

    /**
     * Check if password should be remembered (persisted).
     * @param hostId Host database ID
     * @return true if password should persist across app restarts
     */
    fun shouldRemember(hostId: Long): Boolean =
        try {
            sharedPrefs?.getBoolean(KEY_PREFIX_REMEMBER + hostId, false) ?: false
        } catch (e: Exception) {
            Timber.e(e, "Failed to check remember flag for host $hostId")
            false
        }

    /**
     * Remove saved credentials for a host.
     * @param hostId Host database ID
     */
    fun remove(hostId: Long) {
        try {
            sharedPrefs?.edit()?.apply {
                remove(KEY_PREFIX_PASSWORD + hostId)
                remove(KEY_PREFIX_REMEMBER + hostId)
                apply()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove credentials for host $hostId")
        }
    }

    /**
     * Clear all saved credentials.
     */
    fun clear() {
        try {
            sharedPrefs?.edit()?.clear()?.apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear credentials")
        }
    }

    /**
     * Get all host IDs with saved passwords.
     * @return List of host IDs
     */
    fun getAllHostIds(): List<Long> =
        try {
            sharedPrefs
                ?.all
                ?.keys
                ?.filter { it.startsWith(KEY_PREFIX_PASSWORD) }
                ?.mapNotNull { it.removePrefix(KEY_PREFIX_PASSWORD).toLongOrNull() }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get all host IDs")
            emptyList()
        }
}
