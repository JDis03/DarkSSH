package com.darkssh.client.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class SshFingerprintTest {
    @Test
    fun `build uses standard OpenSSH Base64 format without URL-safe substitution or padding`() {
        // Find input bytes whose SHA-256 digest contains standard base64 symbols (+ and /)
        // For example, byte pattern that hashes to known bytes
        var key = ByteArray(32) { it.toByte() }
        var digest = MessageDigest.getInstance("SHA-256").digest(key)
        var i = 0
        // Search deterministically for a key whose SHA-256 digest produces '+' and '/' in standard base64
        while (true) {
            val b64 = java.util.Base64.getEncoder().withoutPadding().encodeToString(digest)
            if (b64.contains('+') && b64.contains('/')) {
                break
            }
            i++
            key = byteArrayOf(i.toByte(), (i shr 8).toByte()) + ByteArray(30)
            digest = MessageDigest.getInstance("SHA-256").digest(key)
        }

        val output = SshFingerprint.build("ssh-ed25519", key)
        val lines = output.lines()
        assertEquals(3, lines.size)
        assertEquals("ssh-ed25519", lines[0])

        val sha256Line = lines[1]
        assertTrue(sha256Line.startsWith("SHA256:"))
        val sha256Value = sha256Line.removePrefix("SHA256:")

        // Standard OpenSSH SHA256 fingerprints are exactly 43 characters (no padding '=')
        assertEquals(43, sha256Value.length)
        assertFalse("Must not have trailing padding '='", sha256Value.endsWith("="))
        assertTrue("Must preserve '+' from standard Base64", sha256Value.contains('+'))
        assertTrue("Must preserve '/' from standard Base64", sha256Value.contains('/'))
        assertFalse("Must not use URL_SAFE '-'", sha256Value.contains('-'))
        assertFalse("Must not use URL_SAFE '_'", sha256Value.contains('_'))

        val md5Line = lines[2]
        assertTrue(md5Line.startsWith("MD5:"))
        val md5Value = md5Line.removePrefix("MD5:")
        assertEquals(16, md5Value.split(":").size)
    }
}
