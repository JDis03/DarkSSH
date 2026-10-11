package com.darkssh.client.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostKeyPolicyTest {
    @Test
    fun `evaluate implements TOFU with strict pinning`() {
        assertEquals(HostKeyPolicy.Decision.UNKNOWN_HOST, HostKeyPolicy.evaluate(emptyList(), "A"))
        assertEquals(HostKeyPolicy.Decision.TRUSTED, HostKeyPolicy.evaluate(listOf("A", "B"), "B"))
        assertEquals(HostKeyPolicy.Decision.CHANGED, HostKeyPolicy.evaluate(listOf("A"), "C"))
    }

    @Test
    fun `key type is read from the SSH wire blob`() {
        val type = "ssh-ed25519".toByteArray()
        val blob = byteArrayOf(0, 0, 0, type.size.toByte()) + type + ByteArray(36)
        assertEquals("ssh-ed25519", HostKeyPolicy.keyTypeFromBlob(blob))
        assertNull(HostKeyPolicy.keyTypeFromBlob(byteArrayOf(0, 0)))
        assertNull(HostKeyPolicy.keyTypeFromBlob(byteArrayOf(0, 0, 0, 50, 1, 2)))
    }

    @Test
    fun `known key types are negotiated first`() {
        val available = listOf("ssh-ed25519", "ecdsa-sha2-nistp256", "rsa-sha2-256", "rsa-sha2-512")
        assertEquals(
            listOf("ecdsa-sha2-nistp256", "ssh-ed25519", "rsa-sha2-256", "rsa-sha2-512"),
            HostKeyPolicy.preferredAlgorithms(listOf("ecdsa-sha2-nistp256"), available),
        )
        // RSA key blobs map to the rsa-sha2 signature algorithms (strongest first).
        assertEquals(
            listOf("rsa-sha2-512", "rsa-sha2-256", "ssh-ed25519", "ecdsa-sha2-nistp256"),
            HostKeyPolicy.preferredAlgorithms(listOf("ssh-rsa"), available),
        )
        assertEquals(available, HostKeyPolicy.preferredAlgorithms(emptyList(), available))
    }
}
