package com.darkssh.client.util

import android.content.Context
import androidx.core.content.edit

/**
 * Simple SharedPreferences wrapper for app-level settings.
 */
object AppPreferences {
    private const val PREFS_NAME = "darkssh_prefs"
    private const val KEY_TERMINAL_FONT = "terminal_font"
    private const val KEY_SFTP_SORT_BY = "sftp_sort_by"
    private const val KEY_SFTP_SORT_ASCENDING = "sftp_sort_ascending"
    private const val KEY_SFTP_SHOW_HIDDEN = "sftp_show_hidden"
    private const val KEY_USE_CBSSH_SFTP = "use_cbssh_sftp"

    fun getTerminalFont(context: Context): FontManager.FontPreset {
        val name =
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TERMINAL_FONT, FontManager.FontPreset.SYSTEM.name)
        return FontManager.fromName(name ?: FontManager.FontPreset.SYSTEM.name)
    }

    fun setTerminalFont(
        context: Context,
        preset: FontManager.FontPreset,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_TERMINAL_FONT, preset.name)
        }
    }

    // SFTP preferences
    fun getSftpSortBy(context: Context): String =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SFTP_SORT_BY, "NAME") ?: "NAME"

    fun setSftpSortBy(
        context: Context,
        sortBy: String,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_SFTP_SORT_BY, sortBy)
        }
    }

    fun getSftpSortAscending(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SFTP_SORT_ASCENDING, true)

    fun setSftpSortAscending(
        context: Context,
        ascending: Boolean,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_SFTP_SORT_ASCENDING, ascending)
        }
    }

    fun getSftpShowHidden(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SFTP_SHOW_HIDDEN, false)

    fun setSftpShowHidden(
        context: Context,
        showHidden: Boolean,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_SFTP_SHOW_HIDDEN, showHidden)
        }
    }

    /**
     * Whether to use the new cbssh-based SFTP client instead of the legacy sshj one.
     *
     * Defaults to false (legacy sshj) for safety. Users can opt-in via Settings.
     * Once the cbssh path is stable, this will be flipped to true by default and eventually removed.
     */
    fun getUseCbsshSftp(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_CBSSH_SFTP, false)

    fun setUseCbsshSftp(
        context: Context,
        enabled: Boolean,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_USE_CBSSH_SFTP, enabled)
        }
    }
}
