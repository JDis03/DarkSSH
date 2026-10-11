package com.darkssh.client.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SftpOpenFileUtilsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `safe cache name strips remote path components`() {
        assertEquals("notes.md", SftpOpenFileUtils.safeCacheFileName("../../docs/notes.md"))
        assertEquals("notes.md", SftpOpenFileUtils.safeCacheFileName("C:\\docs\\notes.md"))
    }

    @Test
    fun `safe cache name replaces invalid leaf names`() {
        assertEquals("remote-file", SftpOpenFileUtils.safeCacheFileName(".."))
        assertEquals("remote-file", SftpOpenFileUtils.safeCacheFileName("   "))
        assertEquals("note_.md", SftpOpenFileUtils.safeCacheFileName("note\u0000.md"))
    }

    @Test
    fun `markdown uses markdown MIME type`() {
        assertEquals(
            "text/markdown",
            SftpOpenFileUtils.mimeTypeForFileName("README.MD") { null },
        )
    }

    @Test
    fun `platform resolver handles standard binary extension`() {
        assertEquals(
            "image/png",
            SftpOpenFileUtils.mimeTypeForFileName("preview.png") { extension ->
                if (extension == "png") "image/png" else null
            },
        )
    }

    @Test
    fun `unknown or missing extension uses binary fallback`() {
        assertEquals(
            "application/octet-stream",
            SftpOpenFileUtils.mimeTypeForFileName("archive.custom") { null },
        )
        assertEquals(
            "application/octet-stream",
            SftpOpenFileUtils.mimeTypeForFileName("README") { null },
        )
    }

    @Test
    fun `cache pruning removes only entries older than one day`() {
        val cacheDirectory = temporaryFolder.newFolder("sftp-open")
        val expiredEntry = java.io.File(cacheDirectory, "expired").apply { mkdirs() }
        val freshEntry = java.io.File(cacheDirectory, "fresh").apply { mkdirs() }
        java.io.File(expiredEntry, "notes.md").writeText("old")
        java.io.File(freshEntry, "notes.md").writeText("new")

        val now = 2L * 24L * 60L * 60L * 1000L
        expiredEntry.setLastModified(0L)
        freshEntry.setLastModified(now)

        SftpOpenFileUtils.pruneExpiredCacheEntries(cacheDirectory, now)

        assertFalse(expiredEntry.exists())
        assertTrue(freshEntry.exists())
    }
}
