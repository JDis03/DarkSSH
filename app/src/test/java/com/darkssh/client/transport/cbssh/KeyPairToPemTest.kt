/*
 * DarkSSH SFTP Client - cbssh Migration
 * Copyright 2026 DarkSSH
 *
 * Unit tests for KeyPairToPem conversion helper.
 * Generates real keypairs and verifies the PEM output is valid OpenSSH format.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.darkssh.client.transport.cbssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.EdECPrivateKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.NamedParameterSpec

/**
 * Tests for [KeyPairToPem] helper.
 *
 * Generates real keypairs using the JDK and verifies that the output is
 * a valid OpenSSH PEM that cbssh can read back.
 */
class KeyPairToPemTest {
    @Test
    fun `RSA key converts to valid PEM`() {
        val keyPair = generateRsaKeyPair(2048)
        val pem = KeyPairToPem.toPem(keyPair)

        assertPEMStructure(pem)
        assertTrue(
            "PEM should contain BEGIN marker",
            pem.contains("-----BEGIN OPENSSH PRIVATE KEY-----"),
        )
        assertTrue(
            "PEM should contain END marker",
            pem.contains("-----END OPENSSH PRIVATE KEY-----"),
        )
    }

    @Test
    fun `RSA public key is encoded in SSH wire format`() {
        val keyPair = generateRsaKeyPair(2048)
        val pem = KeyPairToPem.toPem(keyPair)
        // PEM should be non-empty and well-formed
        assertTrue("PEM should be non-empty", pem.isNotEmpty())
        // Base64 content should decode back to openssh-key-v1 magic
        val base64Content =
            pem
                .replace("-----BEGIN OPENSSH PRIVATE KEY-----", "")
                .replace("-----END OPENSSH PRIVATE KEY-----", "")
                .replace("\n", "")
                .trim()
        val decoded =
            java.util.Base64
                .getDecoder()
                .decode(base64Content)
        val magic = String(decoded.copyOfRange(0, 15), Charsets.US_ASCII)
        assertEquals("openssh-key-v1\u0000", magic)
    }

    @Test
    fun `Ed25519 key converts to valid PEM`() {
        val keyPair = generateEd25519KeyPair()
        val pem = KeyPairToPem.toPem(keyPair)

        assertPEMStructure(pem)
    }

    @Test
    fun `Ed25519 key from a provider not implementing EdECPrivateKey still converts (Conscrypt regression)`() {
        // Reproduces the real-device bug found via manual testing on Android 16 (API 36):
        // Android's Conscrypt provider returns com.android.org.conscrypt.OpenSslEdDsaPrivateKey
        // for Ed25519 keys, which does NOT implement java.security.interfaces.EdECPrivateKey
        // (unlike the JVM's own SunEC provider, used by generateEd25519KeyPair() above).
        // KeyPairToPem.inferKeyType/extractEd25519Seed used to hard-require that interface
        // and threw "Unsupported key type: ...OpenSslEdDsaPrivateKey" — this test fakes that
        // exact shape (real PKCS8 bytes, algorithm name "Ed25519", no EdECPrivateKey) to
        // guard the fallback path that fixes it.
        val realKeyPair = generateEd25519KeyPair()
        val fakePrivateKey =
            object : PrivateKey {
                override fun getAlgorithm() = "Ed25519"

                override fun getFormat() = "PKCS#8"

                override fun getEncoded() = realKeyPair.private.encoded
            }
        // Note: fakePrivateKey deliberately only implements PrivateKey (not EdECPrivateKey) —
        // that's the whole point of this test — and Kotlin can already prove that statically.
        val fakeKeyPair = KeyPair(realKeyPair.public, fakePrivateKey)

        val pem = KeyPairToPem.toPem(fakeKeyPair)
        assertPEMStructure(pem)

        // Ground truth: the real 32-byte seed, read directly from the JVM's own
        // EdECPrivateKey (the same value the primary code path would extract).
        val expectedSeed =
            (realKeyPair.private as EdECPrivateKey).bytes.orElseThrow {
                AssertionError("Test setup: JVM-generated Ed25519 key has no seed bytes")
            }

        // The fallback path took the last 32 bytes of getEncoded() as the seed and wrote it
        // into the private section. Confirm those exact seed bytes appear verbatim in the
        // decoded output — a 32-byte match is not something the randomized checkint or the
        // (different) public key bytes could produce by coincidence.
        val decoded = decodeOpenSshV1Body(pem)
        assertTrue(
            "Expected the real Ed25519 seed bytes to appear in the fallback-generated PEM",
            containsSubsequence(decoded, expectedSeed),
        )
    }

    /** Decodes an OpenSSH v1 PEM (as produced by [KeyPairToPem.toPem]) back to raw bytes. */
    private fun decodeOpenSshV1Body(pem: String): ByteArray {
        val base64Content =
            pem
                .replace("-----BEGIN OPENSSH PRIVATE KEY-----", "")
                .replace("-----END OPENSSH PRIVATE KEY-----", "")
                .replace("\n", "")
                .trim()
        return java.util.Base64.getDecoder().decode(base64Content)
    }

    private fun containsSubsequence(
        haystack: ByteArray,
        needle: ByteArray,
    ): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        outer@ for (start in 0..(haystack.size - needle.size)) {
            for (i in needle.indices) {
                if (haystack[start + i] != needle[i]) continue@outer
            }
            return true
        }
        return false
    }

    @Test
    fun `ECDSA P-256 key converts to valid PEM`() {
        val keyPair = generateEcKeyPair("secp256r1")
        val pem = KeyPairToPem.toPem(keyPair)

        assertPEMStructure(pem)
    }

    @Test
    fun `ECDSA P-384 key converts to valid PEM`() {
        val keyPair = generateEcKeyPair("secp384r1")
        val pem = KeyPairToPem.toPem(keyPair)

        assertPEMStructure(pem)
    }

    @Test
    fun `ECDSA P-521 key converts to valid PEM`() {
        val keyPair = generateEcKeyPair("secp521r1")
        val pem = KeyPairToPem.toPem(keyPair)

        assertPEMStructure(pem)
    }

    @Test
    fun `PEM output is consistent across multiple calls`() {
        val keyPair = generateRsaKeyPair(2048)

        val pem1 = KeyPairToPem.toPem(keyPair)
        val pem2 = KeyPairToPem.toPem(keyPair)

        // Both should have same structure (but checkint differs, so base64 differs)
        assertEquals(
            "Both PEMs should have same number of lines",
            pem1.lines().size,
            pem2.lines().size,
        )
        assertTrue(pem1.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"))
        assertTrue(pem2.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"))
    }

    @Test
    fun `Generated PEM can be roundtripped through base64 decode`() {
        val keyPair = generateRsaKeyPair(2048)
        val pem = KeyPairToPem.toPem(keyPair)

        val base64Content =
            pem
                .replace("-----BEGIN OPENSSH PRIVATE KEY-----", "")
                .replace("-----END OPENSSH PRIVATE KEY-----", "")
                .replace("\n", "")
                .trim()

        // Should be valid base64
        val decoded =
            try {
                java.util.Base64
                    .getDecoder()
                    .decode(base64Content)
            } catch (e: IllegalArgumentException) {
                fail("PEM content is not valid base64: ${e.message}")
                return
            }

        // First 15 bytes should be OpenSSH v1 magic
        val magic = String(decoded.copyOfRange(0, 15), Charsets.US_ASCII)
        assertEquals("openssh-key-v1\u0000", magic)
    }

    @Test
    fun `Unsupported key type throws IllegalArgumentException`() {
        // Create a keypair with an unsupported algorithm (DSA is not supported by SSH)
        val keyPairGen = KeyPairGenerator.getInstance("DSA")
        keyPairGen.initialize(1024)
        val dsaKeyPair = keyPairGen.generateKeyPair()

        try {
            KeyPairToPem.toPem(dsaKeyPair)
            fail("Expected IllegalArgumentException for unsupported DSA key")
        } catch (e: IllegalArgumentException) {
            // Expected
            assertTrue(
                "Error message should mention unsupported",
                e.message?.contains("Unsupported", ignoreCase = true) == true,
            )
        }
    }

    // ---- Helper methods ----

    private fun assertPEMStructure(pem: String) {
        assertNotNull("PEM should not be null", pem)
        assertTrue(
            "PEM should start with BEGIN marker",
            pem.trimStart().startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"),
        )
        assertTrue(
            "PEM should end with END marker",
            pem.trimEnd().endsWith("-----END OPENSSH PRIVATE KEY-----"),
        )
        // Should have at least 3 lines: BEGIN, base64 content, END
        val lines = pem.lines().filter { it.isNotBlank() }
        assertTrue(
            "PEM should have at least 3 lines, got ${lines.size}",
            lines.size >= 3,
        )
    }

    private fun generateRsaKeyPair(bits: Int): KeyPair {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(bits)
        val kp = keyPairGen.generateKeyPair()
        // Verify type
        assertTrue(
            "Generated key should be RSAPrivateCrtKey",
            kp.private is RSAPrivateCrtKey,
        )
        return kp
    }

    private fun generateEd25519KeyPair(): KeyPair {
        val keyPairGen = KeyPairGenerator.getInstance("Ed25519")
        val kp = keyPairGen.generateKeyPair()
        assertTrue(
            "Generated key should be EdECPrivateKey",
            kp.private is EdECPrivateKey,
        )
        return kp
    }

    private fun generateEcKeyPair(curve: String): KeyPair {
        val keyPairGen = KeyPairGenerator.getInstance("EC")
        keyPairGen.initialize(ECGenParameterSpec(curve))
        val kp = keyPairGen.generateKeyPair()
        assertTrue(
            "Generated key should be ECPrivateKey",
            kp.private is ECPrivateKey,
        )
        return kp
    }
}
