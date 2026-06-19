package com.darkssh.client.util

import android.content.Context
import androidx.core.content.edit

/**
 * Simple SharedPreferences wrapper for app-level settings.
 */
object AppPreferences {
    private const val PREFS_NAME = "darkssh_prefs"
    private const val KEY_TERMINAL_FONT = "terminal_font"

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
}
