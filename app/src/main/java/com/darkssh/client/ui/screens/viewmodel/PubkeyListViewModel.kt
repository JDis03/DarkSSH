package com.darkssh.client.ui.screens.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkssh.client.data.entity.Pubkey
import com.darkssh.client.data.repository.PubkeyRepository
import com.darkssh.client.service.TerminalService
import com.darkssh.client.util.PubkeyUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PubkeyListViewModel
    @Inject
    constructor(
        application: Application,
        private val pubkeyRepository: PubkeyRepository,
    ) : AndroidViewModel(application) {
        private val _pubkeys = MutableStateFlow<List<Pubkey>>(emptyList())
        val pubkeys: StateFlow<List<Pubkey>> = _pubkeys

        private val _loadedKeys = MutableStateFlow<Set<String>>(emptySet())
        val loadedKeys: StateFlow<Set<String>> = _loadedKeys

        private val _passwordPrompt = MutableStateFlow<Pubkey?>(null)
        val passwordPrompt: StateFlow<Pubkey?> = _passwordPrompt

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
    }
