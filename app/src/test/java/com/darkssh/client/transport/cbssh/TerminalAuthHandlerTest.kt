package com.darkssh.client.transport.cbssh

import android.app.Application
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.entity.Pubkey
import com.darkssh.client.data.repository.PubkeyRepository
import com.darkssh.client.service.CredentialStore
import com.darkssh.client.service.TerminalBridge
import com.darkssh.client.service.TerminalService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.connectbot.sshlib.AuthPublicKey
import org.connectbot.sshlib.KeyboardInteractiveCallback.Prompt
import org.connectbot.sshlib.SshSigning
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class TerminalAuthHandlerTest {
    private val host = Host(id = 72001L, hostname = "example.com", username = "user")
    private lateinit var bridge: TerminalBridge
    private lateinit var service: TerminalService
    private lateinit var repository: PubkeyRepository
    private lateinit var loadedKeypairs: ConcurrentHashMap<String, KeyPair>

    @Before
    fun setUp() {
        CredentialStore.clearAll()
        bridge = mock(TerminalBridge::class.java)
        service = mock(TerminalService::class.java)
        repository = mock(PubkeyRepository::class.java)
        loadedKeypairs = ConcurrentHashMap()
        whenever(service.pubkeyRepository).thenReturn(repository)
        whenever(service.loadedKeypairs).thenReturn(loadedKeypairs)
    }

    @After
    fun tearDown() {
        CredentialStore.clearAll()
    }

    @Test
    fun `remembered password is offered once through KBI and removed when rejected`() = runBlocking {
        CredentialStore.putPassword(host.id, "remembered", remember = true)
        whenever(bridge.promptForInput("Password:", false)).thenReturn("replacement")
        val handler = handler()

        assertEquals(listOf("remembered"), handler.answer(Prompt("Password:", false)))
        verify(bridge, never()).promptForInput(any(), any())

        handler.onAttemptFinished(success = false)
        assertNull(CredentialStore.getPassword(host.id))

        // Even if another connection populates the shared cache, this handler must not
        // retry remembered credentials after its first offer.
        CredentialStore.putPassword(host.id, "another-cached-password")
        assertEquals(listOf("replacement"), handler.answer(Prompt("Password:", false)))
        verify(bridge).promptForInput("Password:", false)
        handler.onAttemptFinished(success = true)
        assertEquals("replacement", CredentialStore.getPassword(host.id))
    }

    @Test
    fun `falling back to password auth rejects remembered KBI password instead of reusing it`() = runBlocking {
        CredentialStore.putPassword(host.id, "rejected")
        whenever(bridge.promptForPassword()).thenReturn("typed-password")
        val handler = handler()

        assertEquals(listOf("rejected"), handler.answer(Prompt("Password:", false)))
        assertEquals("typed-password", handler.onPasswordNeeded())
        assertNull(CredentialStore.getPassword(host.id))
        verify(bridge).promptForPassword()
        handler.onAttemptFinished(success = true)
        assertEquals("typed-password", CredentialStore.getPassword(host.id))
    }

    @Test
    fun `typed KBI password is stored only after authentication succeeds`() = runBlocking {
        whenever(bridge.promptForInput("Password:", false)).thenReturn("typed-password")
        val handler = handler()

        assertEquals(listOf("typed-password"), handler.answer(Prompt("Password:", false)))
        assertNull(CredentialStore.getPassword(host.id))
        handler.onAttemptFinished(success = true)
        assertEquals("typed-password", CredentialStore.getPassword(host.id))
        assertFalse(handler.prompting.value)
    }

    @Test
    fun `typed KBI password is not stored after authentication fails`() = runBlocking {
        whenever(bridge.promptForInput("Password:", false)).thenReturn("wrong-password")
        val handler = handler()

        assertEquals(listOf("wrong-password"), handler.answer(Prompt("Password:", false)))
        handler.onAttemptFinished(success = false)
        assertNull(CredentialStore.getPassword(host.id))
        // A later successful phase must not resurrect the rejected candidate.
        handler.onAttemptFinished(success = true)
        assertNull(CredentialStore.getPassword(host.id))
    }

    @Test
    fun `OTP prompts are neither substituted nor saved`() = runBlocking {
        assertChallengeIsNotPassword(Prompt("Verification code:", false))
        assertChallengeIsNotPassword(Prompt("OTP:", false))
    }

    @Test
    fun `password change prompts are neither substituted nor saved`() = runBlocking {
        assertChallengeIsNotPassword(Prompt("New password:", false))
        assertChallengeIsNotPassword(Prompt("Retype password:", false))
        assertChallengeIsNotPassword(Prompt("Password again:", false))
    }

    @Test
    fun `visible password prompt is neither substituted nor saved`() = runBlocking {
        assertChallengeIsNotPassword(Prompt("Password:", true))
    }

    @Test
    fun `multiple KBI prompts are answered individually without substituting or saving passwords`() = runBlocking {
        val prompts = arrayOf(Prompt("Password:", false), Prompt("Verification code:", false))
        whenever(bridge.promptForInput("Password:", false)).thenReturn("typed-password")
        whenever(bridge.promptForInput("Verification code:", false)).thenReturn("123456")
        CredentialStore.putPassword(host.id, "remembered")
        val handler = handler()

        assertEquals(listOf("typed-password", "123456"), handler.answer(*prompts))
        handler.onAttemptFinished(success = true)
        assertEquals("remembered", CredentialStore.getPassword(host.id))

        CredentialStore.remove(host.id)
        val freshHandler = handler()
        assertEquals(listOf("typed-password", "123456"), freshHandler.answer(*prompts))
        freshHandler.onAttemptFinished(success = true)
        assertNull(CredentialStore.getPassword(host.id))
    }

    @Test
    fun `null KBI password response throws cancellation and clears prompting state`() = runBlocking {
        whenever(bridge.promptForInput("Password:", false)).thenReturn(null)
        val handler = handler()

        expectCancellation { handler.answer(Prompt("Password:", false)) }

        assertTrue(handler.cancelled)
        assertFalse(handler.prompting.value)
        assertNull(handler.answer(Prompt("Password:", false)))
        assertNull(handler.onPasswordNeeded())
        verify(bridge).promptForInput("Password:", false)
        verify(bridge, never()).promptForPassword()
        handler.onAttemptFinished(success = true)
        assertNull(CredentialStore.getPassword(host.id))
    }

    @Test
    fun `null OTP response cancels before later prompts are requested`() = runBlocking {
        whenever(bridge.promptForInput("OTP:", false)).thenReturn(null)
        val handler = handler()

        expectCancellation {
            handler.answer(Prompt("OTP:", false), Prompt("Second challenge:", false))
        }

        assertTrue(handler.cancelled)
        assertFalse(handler.prompting.value)
        verify(bridge, never()).promptForInput("Second challenge:", false)
        assertNull(CredentialStore.getPassword(host.id))
    }

    @Test
    fun `null password auth response throws cancellation`() = runBlocking {
        whenever(bridge.promptForPassword()).thenReturn(null)
        val handler = handler()

        expectCancellation { handler.onPasswordNeeded() }

        assertTrue(handler.cancelled)
        assertFalse(handler.prompting.value)
        assertNull(CredentialStore.getPassword(host.id))
    }

    @Test
    fun `assigned cached encrypted key is the only offered key and does not require unlocking`() = runBlocking {
        val assigned = rsaKeyPair()
        val other = rsaKeyPair()
        loadedKeypairs["assigned"] = assigned
        loadedKeypairs["other"] = other
        // No private key material: this can only succeed by using the unlocked cache.
        whenever(repository.getPubkeyById(42L)).thenReturn(
            Pubkey(id = 42L, nickname = "assigned", type = "RSA", encrypted = true),
        )
        val handler = handler(host.copy(pubkeyId = 42L))

        val offered = handler.onPublicKeysNeeded()

        assertEquals(1, offered.size)
        assertArrayEquals(SshSigning.encodePublicKey(assigned).publicKeyBlob, offered.single().publicKeyBlob)
        assertEquals(offered, handler.onPublicKeysNeeded())
        verify(repository).getPubkeyById(42L)
        verify(bridge, never()).promptForInput(any(), any())
        assertNull(handler.onSignatureRequest(SshSigning.encodePublicKey(other), byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `nonexistent assigned key never falls back to other loaded keys`(): Unit = runBlocking {
        val other = rsaKeyPair()
        loadedKeypairs["other"] = other
        whenever(repository.getPubkeyById(404L)).thenReturn(null)
        val handler = handler(host.copy(pubkeyId = 404L))

        assertTrue(handler.onPublicKeysNeeded().isEmpty())
        assertTrue(handler.onPublicKeysNeeded().isEmpty())
        assertNull(handler.onSignatureRequest(SshSigning.encodePublicKey(other), byteArrayOf(1)))
        verify(repository).getPubkeyById(404L)
        verify(bridge, never()).promptForInput(any(), any())
    }

    @Test
    fun `RSA signature uses rsa-sha2-512 from callback rather than ssh-rsa from offered key`() = runBlocking {
        val pair = rsaKeyPair()
        loadedKeypairs["rsa"] = pair
        val handler = handler()
        val offered = handler.onPublicKeysNeeded().single()
        assertEquals("ssh-rsa", offered.algorithmName)
        val requested = AuthPublicKey("rsa-sha2-512", offered.publicKeyBlob)
        val data = "SSH authentication session data".toByteArray(Charsets.UTF_8)

        val signature = requireNotNull(handler.onSignatureRequest(requested, data))

        // Compare the SSH wrapper too, not just the raw RSA bytes. PKCS#1 v1.5
        // signatures are deterministic, so the signing helper is an exact oracle.
        assertArrayEquals(SshSigning.signWithKeyPair("rsa-sha2-512", pair, data), signature)
        DataInputStream(ByteArrayInputStream(signature)).use { input ->
            val algorithm = ByteArray(input.readInt()).also { input.readFully(it) }
            val rawSignature = ByteArray(input.readInt()).also { input.readFully(it) }
            assertEquals("rsa-sha2-512", String(algorithm, Charsets.US_ASCII))
            assertEquals(0, input.available())
            val verifier = Signature.getInstance("SHA512withRSA")
            verifier.initVerify(pair.public)
            verifier.update(data)
            assertTrue("Signature must verify with SHA-512, not SHA-1", verifier.verify(rawSignature))
        }
    }

    private fun handler(target: Host = host) = TerminalAuthHandler(target, bridge, service)

    private suspend fun TerminalAuthHandler.answer(vararg prompts: Prompt): List<String>? =
        onKeyboardInteractivePrompt("Authentication", "", prompts.toList())

    private suspend fun assertChallengeIsNotPassword(prompt: Prompt) {
        whenever(bridge.promptForInput(prompt.text, prompt.echo)).thenReturn("typed-challenge")
        CredentialStore.putPassword(host.id, "remembered")
        val withRememberedPassword = handler()
        assertEquals(listOf("typed-challenge"), withRememberedPassword.answer(prompt))
        withRememberedPassword.onAttemptFinished(success = true)
        assertEquals("remembered", CredentialStore.getPassword(host.id))

        CredentialStore.remove(host.id)
        val withoutRememberedPassword = handler()
        assertEquals(listOf("typed-challenge"), withoutRememberedPassword.answer(prompt))
        withoutRememberedPassword.onAttemptFinished(success = true)
        assertNull(CredentialStore.getPassword(host.id))
    }

    private suspend fun expectCancellation(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected CancellationException when the authentication prompt is cancelled")
        } catch (_: CancellationException) {
            // Cancellation must propagate instead of being treated as an auth rejection.
        }
    }

    private fun rsaKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
}
