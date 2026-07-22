/*
 * DarkSSH SFTP - DarkSshHostKeyVerifier tests
 *
 * Verifies the TOFU (trust-on-first-use) semantics against a mocked
 * KnownHostRepository, mirroring the behavior contract asserted for the
 * terminal's SSH.kt HostKeyVerifier: known-match accepts silently,
 * known-mismatch rejects without prompting, unknown key prompts and persists
 * on accept, unknown key does not persist on reject.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport.cbssh

import android.util.Base64
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.entity.KnownHost
import com.darkssh.client.data.repository.KnownHostRepository
import kotlinx.coroutines.runBlocking
import org.connectbot.sshlib.PublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33]) // Robolectric provides a real android.util.Base64 implementation
class DarkSshHostKeyVerifierTest {

    private lateinit var repo: KnownHostRepository
    private val host = Host(id = 1L, hostname = "example.com", port = 22, username = "user")
    private val keyBytes = byteArrayOf(1, 2, 3, 4, 5)
    private val publicKey = PublicKey(type = "ssh-ed25519", encoded = keyBytes)
    private val keyDataBase64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP)

    @Before
    fun setup() {
        repo = mock(KnownHostRepository::class.java)
    }

    @Test
    fun `known matching key is accepted without prompting`() {
        runBlocking {
            whenever(repo.getByHostIdAndAlgo(1L, "ssh-ed25519")).thenReturn(
                listOf(KnownHost(hostId = 1L, hostname = "example.com", port = 22, hostKeyAlgo = "ssh-ed25519", hostKey = keyDataBase64)),
            )
            var promptCalled = false
            val verifier = DarkSshHostKeyVerifier(host, repo) { _, _ -> promptCalled = true; true }

            val result = verifier.verify(publicKey)

            assertTrue("Expected known matching key to be accepted", result)
            assertFalse("Should not prompt for an already-known key", promptCalled)
            verify(repo, never()).insert(any())
        }
    }

    @Test
    fun `known mismatched key is rejected without prompting`() {
        runBlocking {
            val differentKeyBase64 = Base64.encodeToString(byteArrayOf(9, 9, 9), Base64.NO_WRAP)
            whenever(repo.getByHostIdAndAlgo(1L, "ssh-ed25519")).thenReturn(
                listOf(KnownHost(hostId = 1L, hostname = "example.com", port = 22, hostKeyAlgo = "ssh-ed25519", hostKey = differentKeyBase64)),
            )
            var promptCalled = false
            val verifier = DarkSshHostKeyVerifier(host, repo) { _, _ -> promptCalled = true; true }

            val result = verifier.verify(publicKey)

            assertFalse("Expected mismatched key to be rejected (fails closed)", result)
            assertFalse("Should not prompt on a mismatch — reject silently", promptCalled)
            verify(repo, never()).insert(any())
        }
    }

    @Test
    fun `unknown key prompts and persists on accept`() {
        runBlocking {
            whenever(repo.getByHostIdAndAlgo(1L, "ssh-ed25519")).thenReturn(emptyList())
            var promptedAlgo: String? = null
            var promptedFingerprints: String? = null
            val verifier =
                DarkSshHostKeyVerifier(host, repo) { algo, fingerprints ->
                    promptedAlgo = algo
                    promptedFingerprints = fingerprints
                    true
                }

            val result = verifier.verify(publicKey)

            assertTrue("Expected accept when the callback returns true", result)
            assertEquals("ssh-ed25519", promptedAlgo)
            val fingerprints = requireNotNull(promptedFingerprints)
            assertTrue(
                "Fingerprints should include the algorithm and both hash formats",
                fingerprints.contains("ssh-ed25519") &&
                    fingerprints.contains("SHA256:") &&
                    fingerprints.contains("MD5:"),
            )
            verify(repo).insert(
                eq(KnownHost(hostId = 1L, hostname = "example.com", port = 22, hostKeyAlgo = "ssh-ed25519", hostKey = keyDataBase64)),
            )
        }
    }

    @Test
    fun `unknown key does not persist on reject`() {
        runBlocking {
            whenever(repo.getByHostIdAndAlgo(1L, "ssh-ed25519")).thenReturn(emptyList())
            val verifier = DarkSshHostKeyVerifier(host, repo) { _, _ -> false }

            val result = verifier.verify(publicKey)

            assertFalse("Expected rejection when the callback returns false", result)
            verify(repo, never()).insert(any())
        }
    }
}
