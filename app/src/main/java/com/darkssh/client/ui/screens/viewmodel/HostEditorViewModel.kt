package com.darkssh.client.ui.screens.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.entity.Pubkey
import com.darkssh.client.data.repository.HostRepository
import com.darkssh.client.data.repository.PubkeyRepository
import com.darkssh.client.util.TextSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HostEditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val hostRepository: HostRepository,
        private val pubkeyRepository: PubkeyRepository,
    ) : ViewModel() {
        private val hostId: Long = savedStateHandle.get<String>("hostId")?.toLongOrNull() ?: -1L

        private val _host = MutableStateFlow<Host?>(null)
        val host: StateFlow<Host?> = _host

        private val _pubkeys = MutableStateFlow<List<Pubkey>>(emptyList())
        val pubkeys: StateFlow<List<Pubkey>> = _pubkeys

        /**
         * Emitted after a successful save: hostId, new nickname.
         * The UI layer (which has access to TabManager) reacts to this
         * and refreshes open tabs for the renamed host.
         */
        private val _savedHost = MutableSharedFlow<Pair<Long, String>>(replay = 0)
        val savedHost: SharedFlow<Pair<Long, String>> = _savedHost.asSharedFlow()

        init {
            viewModelScope.launch {
                // Load available pubkeys
                pubkeyRepository.getAllPubkeys().collect { keys ->
                    _pubkeys.value = keys
                }
            }

            if (hostId != -1L) {
                viewModelScope.launch {
                    _host.value = hostRepository.getHostById(hostId)
                }
            }
        }

        fun saveHost(
            nickname: String,
            hostname: String,
            username: String,
            port: Int,
            compression: Boolean,
            stayConnected: Boolean,
            pubkeyId: Long?,
        ) {
            // Strip whitespace/invisible Unicode junk that mobile keyboard
            // autocomplete/autocorrect or paste can silently insert. Left uncleaned,
            // these break exact-match parsers downstream - e.g. a hostname that is a
            // valid IPv4 literal plus one invisible character stops being recognized
            // as an IP by java.net.InetAddress, which then tries (and fails) to
            // resolve it via DNS instead of connecting directly. hostname gets the
            // strict whitelist pass (sanitizeStrict) since it can never legitimately
            // contain anything but the network-address charset; nickname/username
            // keep the lighter pass that preserves genuine embedded spaces. See
            // bug-012 (TextSanitizer.kt has the full rationale + why a hand-picked
            // blacklist of "known" invisible characters wasn't enough).
            val cleanNickname = TextSanitizer.sanitize(nickname)
            val cleanHostname = TextSanitizer.sanitizeStrict(hostname)
            val cleanUsername = TextSanitizer.sanitize(username)
            viewModelScope.launch {
                val existingHost = _host.value
                if (existingHost != null) {
                    val previousNickname = existingHost.nickname
                    hostRepository.updateHost(
                        existingHost.copy(
                            nickname = cleanNickname,
                            hostname = cleanHostname,
                            username = cleanUsername,
                            port = port,
                            compression = compression,
                            stayConnected = stayConnected,
                            pubkeyId = pubkeyId,
                        ),
                    )
                    // Emit saved event so the UI layer can refresh tabs for this host
                    // when the nickname changed. Avoids injecting one @HiltViewModel
                    // into another (Hilt prohibits that).
                    _savedHost.emit(existingHost.id to cleanNickname)
                } else {
                    hostRepository.insertHost(
                        Host(
                            nickname = cleanNickname,
                            hostname = cleanHostname,
                            username = cleanUsername,
                            port = port,
                            compression = compression,
                            stayConnected = stayConnected,
                            pubkeyId = pubkeyId,
                        ),
                    )
                }
            }
        }

    fun loadHost(hostId: Long) {
        viewModelScope.launch {
            if (hostId > 0) {
                _host.value = hostRepository.getHostById(hostId)
            } else {
                // Clear host when adding new (hostId = -1)
                _host.value = null
            }
        }
    }
    }
