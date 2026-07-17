package com.darkssh.client.ui.screens.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkssh.client.data.entity.Pubkey
import com.darkssh.client.data.repository.PubkeyRepository
import com.darkssh.client.service.TerminalService
import com.darkssh.client.util.PubkeyUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PubkeyListViewModel
    @Inject
    constructor(
        application: Application,
        private val pubkeyRepository: PubkeyRepository,
        private val clipboardManager: ClipboardManager,
    ) : AndroidViewModel(application) {
        private val _pubkeys = MutableStateFlow<List<Pubkey>>(emptyList())
        val pubkeys: StateFlow<List<Pubkey>> = _pubkeys

        private val _loadedKeys = MutableStateFlow<Set<String>>(emptySet())
        val loadedKeys: StateFlow<Set<String>> = _loadedKeys

        private val _passwordPrompt = MutableStateFlow<Pubkey?>(null)
        val passwordPrompt: StateFlow<Pubkey?> = _passwordPrompt

        /** One-shot message for the snackbar (e.g. "Public key copied", or an error). */
        private val _message = MutableStateFlow<String?>(null)
        val message: StateFlow<String?> = _message

        fun clearMessage() {
            _message.value = null
        }

        private var terminalService: TerminalService? = null

        init {
            viewModelScope.launch {
                pubkeyRepository.getAllPubkeys().collect { keys ->
                    _pubkeys.value = keys
                }
            }
        }

        fun setTerminalService(service: TerminalService?) {
            terminalService = service
            loadStartupKeys()
        }

        private fun loadStartupKeys() {
            val service = terminalService ?: return
            viewModelScope.launch {
                val allKeys = _pubkeys.value
                for (pubkey in allKeys) {
                    if (pubkey.startup && !pubkey.encrypted) {
                        tryUnlockKey(pubkey, null)
                    }
                }
            }
        }

        fun toggleKeyLoaded(pubkey: Pubkey) {
            val nickname = pubkey.nickname
            if (_loadedKeys.value.contains(nickname)) {
                terminalService?.loadedKeypairs?.remove(nickname)
                _loadedKeys.value = _loadedKeys.value - nickname
            } else {
                if (pubkey.encrypted) {
                    _passwordPrompt.value = pubkey
                } else {
                    tryUnlockKey(pubkey, null)
                }
            }
        }

        fun unlockKeyWithPassword(password: String) {
            val pubkey = _passwordPrompt.value ?: return
            _passwordPrompt.value = null
            tryUnlockKey(pubkey, password)
        }

        fun cancelPasswordPrompt() {
            _passwordPrompt.value = null
        }

        private fun tryUnlockKey(
            pubkey: Pubkey,
            password: String?,
        ) {
            val keyPair =
                PubkeyUtils.convertToKeyPair(pubkey, password) ?: run {
                    Timber.w("Failed to unlock key: ${pubkey.nickname}")
                    return
                }

            terminalService?.loadedKeypairs?.put(pubkey.nickname, keyPair)
            _loadedKeys.value = _loadedKeys.value + pubkey.nickname
        }

        fun deleteKey(pubkey: Pubkey) {
            viewModelScope.launch {
                terminalService?.loadedKeypairs?.remove(pubkey.nickname)
                _loadedKeys.value = _loadedKeys.value - pubkey.nickname
                pubkeyRepository.deletePubkey(pubkey)
            }
        }

        /**
         * Copies the OpenSSH `authorized_keys`-format public key line (e.g.
         * "ssh-ed25519 AAAA... nickname") to the clipboard, so the user can paste it
         * into a server's ~/.ssh/authorized_keys to enable key-based login.
         * Mirrors ConnectBot's PubkeyListViewModel.copyPublicKey().
         */
        fun copyPublicKey(pubkey: Pubkey) {
            viewModelScope.launch {
                try {
                    if (pubkey.type == PubkeyUtils.KeyType.IMPORTED) {
                        _message.value = "Cannot export public key from an imported key"
                        return@launch
                    }

                    val authorizedKeysLine =
                        withContext(Dispatchers.Default) {
                            val publicKey = PubkeyUtils.decodePublic(pubkey.publicKey, pubkey.type)
                            com.trilead.ssh2.crypto.PublicKeyUtils.toAuthorizedKeysFormat(publicKey, pubkey.nickname)
                        }

                    val clip = ClipData.newPlainText("Public Key", authorizedKeysLine)
                    clipboardManager.setPrimaryClip(clip)
                    _message.value = "Public key copied to clipboard"
                } catch (e: Exception) {
                    Timber.e(e, "Failed to copy public key")
                    _message.value = "Failed to copy public key: ${e.message}"
                }
            }
        }
    }
