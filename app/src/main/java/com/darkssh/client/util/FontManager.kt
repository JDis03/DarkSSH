package com.darkssh.client.util

import android.content.Context
import android.graphics.Typeface
import timber.log.Timber

/**
 * Manages custom terminal fonts.
 * Loads Typeface from app assets/fonts/ directory.
 *
 * FiraCode.ttf is patched: calt ligatures are baked into liga so
 * Android renders programming ligatures by default.
 */
object FontManager {

    enum class FontPreset(
        val displayName: String,
        val assetPath: String?,
    ) {
        SYSTEM("System Monospace", null),
        FIRA_CODE("Fira Code (ligatures)", "fonts/FiraCode.ttf"),
    }

    private val cache = mutableMapOf<String, Typeface>()

    fun getTypeface(context: Context, preset: FontPreset): Typeface {
        if (preset.assetPath == null) return Typeface.MONOSPACE

        cache[preset.assetPath]?.let { return it }

        return try {
            Typeface.createFromAsset(context.assets, preset.assetPath).also {
                cache[preset.assetPath] = it
                Timber.d("FontManager: loaded ${preset.displayName}")
            }
        } catch (e: Exception) {
            Timber.w(e, "FontManager: failed to load ${preset.displayName}")
            Typeface.MONOSPACE
        }
    }

    fun fromName(name: String): FontPreset =
        FontPreset.values().firstOrNull { it.name == name } ?: FontPreset.SYSTEM

    fun clearCache() = cache.clear()
}
