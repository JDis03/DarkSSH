package com.darkssh.client.util

import android.webkit.MimeTypeMap
import java.util.Locale

internal object SftpOpenFileUtils {
    private const val DEFAULT_FILE_NAME = "remote-file"
    private const val DEFAULT_MIME_TYPE = "application/octet-stream"
    private const val OPEN_CACHE_MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L

    private val textMimeTypes =
        mapOf(
            "md" to "text/markdown",
            "markdown" to "text/markdown",
            "txt" to "text/plain",
            "log" to "text/plain",
            "conf" to "text/plain",
            "ini" to "text/plain",
            "sh" to "text/plain",
            "kt" to "text/plain",
            "java" to "text/plain",
            "py" to "text/plain",
            "js" to "text/plain",
            "ts" to "text/plain",
            "css" to "text/plain",
            "sql" to "text/plain",
            "toml" to "text/plain",
            "yaml" to "text/plain",
            "yml" to "text/plain",
            "json" to "application/json",
            "xml" to "application/xml",
            "csv" to "text/csv",
        )

    fun safeCacheFileName(fileName: String): String {
        val leafName = fileName.substringAfterLast('/').substringAfterLast('\\')
        val sanitized =
            leafName.map { character ->
                if (character == '\u0000' || character.isISOControl()) '_' else character
            }.joinToString("")

        return sanitized.takeUnless { it.isBlank() || it == "." || it == ".." } ?: DEFAULT_FILE_NAME
    }

    fun mimeTypeForFileName(
        fileName: String,
        platformMimeType: (String) -> String? = { extension ->
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        },
    ): String {
        val leafName = safeCacheFileName(fileName)
        val lastDot = leafName.lastIndexOf('.')
        val extension =
            if (lastDot > 0 && lastDot < leafName.lastIndex) {
                leafName.substring(lastDot + 1).lowercase(Locale.ROOT)
            } else {
                ""
            }

        return textMimeTypes[extension]
            ?: extension.takeIf { it.isNotEmpty() }?.let(platformMimeType)
            ?: DEFAULT_MIME_TYPE
    }

    fun pruneExpiredCacheEntries(
        cacheDirectory: java.io.File,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val expirationTime = nowMillis - OPEN_CACHE_MAX_AGE_MILLIS
        cacheDirectory.listFiles()
            ?.filter { entry -> entry.lastModified() < expirationTime }
            ?.forEach { entry -> entry.deleteRecursively() }
    }
}
