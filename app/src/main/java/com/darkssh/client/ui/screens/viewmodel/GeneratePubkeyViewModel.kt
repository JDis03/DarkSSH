package com.darkssh.client.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkssh.client.data.entity.Pubkey
import com.darkssh.client.data.repository.PubkeyRepository
import com.darkssh.client.util.PubkeyUtils
import com.trilead.ssh2.crypto.keys.Ed25519Provider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.security.KeyPairGenerator
import java.security.SecureRandom
import javax.inject.Inject

@HiltViewModel
class GeneratePubkeyViewModel
    @Inject
    constructor(
        private val pubkeyRepository: PubkeyRepository,
    ) : ViewModel() {
        private val _isGenerating = MutableStateFlow(false)
        val isGenerating: StateFlow<Boolean> = _isGenerating

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error

        private val _generated = MutableStateFlow(false)
        val generated: StateFlow<Boolean> = _generated

        fun generateKey(
            nickname: String,
            keyType: String,
            bits: Int,
            password: String?,
            confirmPassword: String?,
            unlockAtStartup: Boolean,
            confirmUse: Boolean,
        ) {
            if (nickname.isBlank()) {
                _error.value = "Nickname is required"
                return
            }

            if (!password.isNullOrBlank() && password != confirmPassword) {
                _error.value = "Passwords do not match"
                return
            }

            if (password.isNullOrBlank() && unlockAtStartup) {
                _error.value = "Encrypted keys cannot auto-unlock at startup"
                return
            }

            _isGenerating.value = true
            _error.value = null

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    if (keyType == PubkeyUtils.KeyType.ED25519) {
                        Ed25519Provider.insertIfNeeded()
                    }

                    val keyPairGen = KeyPairGenerator.getInstance(keyType)
                    val random = SecureRandom()
                    keyPairGen.initialize(bits, random)
                    val keyPair = keyPairGen.generateKeyPair()

                    val encrypted = !password.isNullOrBlank()
                    val privateKeyData =
                        PubkeyUtils.getEncodedPrivate(
                            keyPair.private,
                            if (encrypted) password else null,
                        )
                    val publicKeyData = keyPair.public.encoded

                    val pubkey =
                        Pubkey(
                            nickname = nickname,
                            type = keyType,
                            privateKey = privateKeyData,
                            publicKey = publicKeyData,
                            encrypted = encrypted,
                            startup = unlockAtStartup,
                            confirmation = confirmUse,
                            setupDate = System.currentTimeMillis(),
                            storageType = "EXPORTABLE",
                        )

                    pubkeyRepository.insertPubkey(pubkey)
                    _generated.value = true
                } catch (e: Exception) {
                    _error.value = "Key generation failed: ${e.message}"
                } finally {
                    _isGenerating.value = false
                }
            }
        }
    }
