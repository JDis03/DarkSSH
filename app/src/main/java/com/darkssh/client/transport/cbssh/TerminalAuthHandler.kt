package com.darkssh.client.transport.cbssh

import com.darkssh.client.data.entity.Host
import com.darkssh.client.service.CredentialStore
import com.darkssh.client.service.TerminalBridge
import com.darkssh.client.service.TerminalService
import com.darkssh.client.transport.KeyboardInteractivePassword
import com.darkssh.client.util.PubkeyUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import org.connectbot.sshlib.AuthHandler
import org.connectbot.sshlib.AuthPublicKey
import org.connectbot.sshlib.KeyboardInteractiveCallback
import org.connectbot.sshlib.SshSigning
import timber.log.Timber
import java.security.KeyPair

/** UI/credential adapter; cbssh owns method discovery, public-key probes and signing order. */
internal class TerminalAuthHandler(
    private val host: Host,
    private val bridge: TerminalBridge,
    private val service: TerminalService,
) : AuthHandler {
    private var keys: List<Pair<AuthPublicKey, KeyPair>>? = null
    private var savedPasswordOffered = false
    private var candidatePassword: String? = null
    private var candidateWasSaved = false
    val prompting = MutableStateFlow(false)
    var cancelled = false
        private set
    var passwordRequests = 0
        private set

    override suspend fun onAuthMethodsAvailable(methods: Set<String>) {
        Timber.d("SSH: Server authentication methods: ${methods.joinToString()}")
    }

    override suspend fun onPublicKeysNeeded(): List<AuthPublicKey> {
        if (keys == null) {
            val assignedId = host.pubkeyId
            val pairs = if (assignedId != null) {
                listOfNotNull(loadAssignedKey(assignedId))
            } else {
                service.loadedKeypairs.values.toList()
            }
            keys = pairs.mapNotNull { pair ->
                try {
                    SshSigning.encodePublicKey(pair) to pair
                } catch (e: Exception) {
                    Timber.w(e, "SSH: Unable to encode authentication key")
                    null
                }
            }
        }
        Timber.d("SSH: Offering ${keys.orEmpty().size} authentication key(s)")
        return keys.orEmpty().map { it.first }
    }

    private suspend fun loadAssignedKey(id: Long): KeyPair? {
        val pubkey = service.pubkeyRepository.getPubkeyById(id) ?: return null
        service.loadedKeypairs[pubkey.nickname]?.let { return it }
        val password = if (pubkey.encrypted) {
            prompt { bridge.promptForInput("Password for key \"${pubkey.nickname}\":", false) }
                ?: return null
        } else {
            null
        }
        return PubkeyUtils.convertToKeyPair(pubkey, password)?.also {
            service.loadedKeypairs[pubkey.nickname] = it
        }
    }

    override suspend fun onSignatureRequest(key: AuthPublicKey, dataToSign: ByteArray): ByteArray? {
        val pair = keys.orEmpty().firstOrNull { it.first.publicKeyBlob.contentEquals(key.publicKeyBlob) }?.second
            ?: return null
        return try {
            // cbssh may upgrade an ssh-rsa blob to rsa-sha2-512/256. Sign with
            // the negotiated algorithm supplied by the callback, never SHA-1 by default.
            SshSigning.signWithKeyPair(key.algorithmName, pair, dataToSign)
        } catch (e: Exception) {
            Timber.w(e, "SSH: Unable to sign authentication request")
            null
        }
    }

    override suspend fun onKeyboardInteractivePrompt(
        name: String,
        instruction: String,
        prompts: List<KeyboardInteractiveCallback.Prompt>,
    ): List<String>? {
        Timber.d("SSH: Keyboard-interactive requested ${prompts.size} response(s)")
        if (cancelled) return null
        val first = prompts.firstOrNull()
        if (KeyboardInteractivePassword.isPasswordPrompt(prompts.size, first?.text, first?.echo ?: false)) {
            return listOf(password { bridge.promptForInput(first!!.text, false) } ?: return null)
        }
        // Never substitute or save OTPs, visible challenges or password-change prompts.
        val responses = mutableListOf<String>()
        for (prompt in prompts) {
            val response = prompt { bridge.promptForInput(prompt.text, prompt.echo) } ?: run {
                cancelled = true
                throw CancellationException("Authentication prompt cancelled")
            }
            responses += response
        }
        return responses
    }

    override suspend fun onPasswordNeeded(): String? {
        passwordRequests++
        Timber.d("SSH: Password authentication requested")
        // Reaching password after keyboard-interactive means that phase failed.
        rejectCandidate()
        if (cancelled) return null
        return password { bridge.promptForPassword() }
    }

    private suspend fun password(ask: suspend () -> String?): String? {
        // A second password challenge in the same KBI exchange must not leave
        // the first (rejected) saved credential behind when the candidate changes.
        rejectCandidate()
        val saved = if (!savedPasswordOffered) CredentialStore.getPassword(host.id) else null
        savedPasswordOffered = true
        val value = saved ?: prompt(ask) ?: run {
            cancelled = true
            throw CancellationException("Authentication prompt cancelled")
        }
        candidatePassword = value
        candidateWasSaved = saved != null
        return value
    }

    private suspend fun <T> prompt(ask: suspend () -> T): T {
        prompting.value = true
        return try {
            ask()
        } finally {
            prompting.value = false
        }
    }

    fun onAttemptFinished(success: Boolean) {
        if (success) {
            candidatePassword?.let { CredentialStore.putPassword(host.id, it, remember = host.stayConnected) }
            candidatePassword = null
            candidateWasSaved = false
        } else {
            rejectCandidate()
        }
    }

    private fun rejectCandidate() {
        if (candidateWasSaved) CredentialStore.remove(host.id)
        candidatePassword = null
        candidateWasSaved = false
    }
}
