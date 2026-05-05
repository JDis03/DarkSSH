package com.darkssh.client.ui.screens.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.HostRepository
import com.darkssh.client.transport.SftpAuthState
import com.darkssh.client.transport.SftpClient
import com.darkssh.client.transport.SftpClientSSHJ
import com.darkssh.client.transport.SftpEntry
import com.darkssh.client.transport.TransferProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.OutputStream
import javax.inject.Inject

data class SftpUiState(
    val currentPath: String = "/",
    val entries: List<SftpEntry> = emptyList(),
    val allEntries: List<SftpEntry> = emptyList(),
    val showHiddenFiles: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val pathStack: List<String> = emptyList(),
    val selectedEntries: Set<String> = emptySet(),
    val transfers: List<TransferInfo> = emptyList(),
    val transferProgress: TransferProgress? = null,
    val authState: SftpAuthState = SftpAuthState.Idle,
    val host: Host? = null,
)

data class TransferInfo(
    val id: Long,
    val fileName: String,
    val remotePath: String,
    val localPath: String,
    val isDownload: Boolean,
    val isComplete: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SftpViewModel @Inject constructor(
    application: Application,
    private val hostRepository: HostRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SftpUiState())
    val uiState: StateFlow<SftpUiState> = _uiState.asStateFlow()

    private var sftpClient: SftpClient? = null
    private var sftpClientSSHJ: SftpClientSSHJ? = null
    private var transferIdCounter = 0L

    // Use SSHJ for much faster transfers
    private val useSSHJ = true

    private var pendingDownload: PendingDownload? = null

    data class PendingDownload(
        val remotePath: String,
        val fileName: String,
    )

    fun initialize(hostId: Long) {
        if (_uiState.value.host != null) return

        viewModelScope.launch {
            val h = hostRepository.getHostById(hostId) ?: run {
                _uiState.value = _uiState.value.copy(error = "Host not found")
                return@launch
            }
            _uiState.value = _uiState.value.copy(host = h)
            _uiState.value = _uiState.value.copy(authState = SftpAuthState.NeedsPassword(h.hostname, h.username))
        }
    }

    fun connectWithPassword(password: String) {
        val h = _uiState.value.host ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(authState = SftpAuthState.Connecting, error = null)

            if (useSSHJ) {
                val client = SftpClientSSHJ(h)
                val result = client.connectWithPassword(password)

                if (result.isSuccess) {
                    sftpClientSSHJ = client
                    _uiState.value = _uiState.value.copy(authState = SftpAuthState.Authenticated)
                    val pwd = client.pwd()
                    _uiState.value = _uiState.value.copy(currentPath = pwd)
                    listDirectory(pwd)
                } else {
                    _uiState.value = _uiState.value.copy(
                        authState = SftpAuthState.Failed(result.exceptionOrNull()?.message ?: "Authentication failed"),
                    )
                }
            } else {
                val client = SftpClient(h)
                val result = client.connectWithPassword(password)

                if (result.isSuccess) {
                    sftpClient = client
                    _uiState.value = _uiState.value.copy(authState = SftpAuthState.Authenticated)
                    val pwd = client.pwd()
                    _uiState.value = _uiState.value.copy(currentPath = pwd)
                    listDirectory(pwd)
                } else {
                    _uiState.value = _uiState.value.copy(
                        authState = SftpAuthState.Failed(result.exceptionOrNull()?.message ?: "Authentication failed"),
                    )
                }
            }
        }
    }

    fun dismissAuthError() {
        val h = _uiState.value.host ?: return
        _uiState.value = _uiState.value.copy(authState = SftpAuthState.NeedsPassword(h.hostname, h.username))
    }

    private fun filterEntries(entries: List<SftpEntry>, showHidden: Boolean): List<SftpEntry> {
        return if (showHidden) entries else entries.filterNot { it.name.startsWith(".") }
    }

    fun toggleShowHiddenFiles() {
        val current = _uiState.value
        val showHidden = !current.showHiddenFiles
        _uiState.value = current.copy(
            showHiddenFiles = showHidden,
            entries = filterEntries(current.allEntries, showHidden),
        )
    }

    fun listDirectory(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = if (useSSHJ) {
                sftpClientSSHJ?.ls(path) ?: run {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "SFTP not connected")
                    return@launch
                }
            } else {
                sftpClient?.ls(path) ?: run {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "SFTP not connected")
                    return@launch
                }
            }

            result.onSuccess { entries ->
                val showHidden = _uiState.value.showHiddenFiles
                _uiState.value = _uiState.value.copy(
                    allEntries = entries,
                    entries = filterEntries(entries, showHidden),
                    isLoading = false,
                    currentPath = path,
                    selectedEntries = emptySet(),
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message,
                )
            }
        }
    }

    fun navigateTo(path: String) {
        val current = _uiState.value.currentPath
        _uiState.value = _uiState.value.copy(
            pathStack = _uiState.value.pathStack + current,
        )
        listDirectory(path)
    }

    fun navigateUp() {
        val current = _uiState.value.currentPath
        if (current == "/") return

        val parent = current.substringBeforeLast('/', "/").ifEmpty { "/" }
        _uiState.value = _uiState.value.copy(
            pathStack = _uiState.value.pathStack + current,
        )
        listDirectory(parent)
    }

    fun toggleSelection(path: String) {
        val current = _uiState.value.selectedEntries
        _uiState.value = _uiState.value.copy(
            selectedEntries = if (path in current) current - path else current + path,
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedEntries = emptySet())
    }

    fun requestDownload(remotePath: String, fileName: String) {
        pendingDownload = PendingDownload(remotePath, fileName)
    }

    fun getAndClearPendingDownload(): PendingDownload? {
        val pd = pendingDownload
        pendingDownload = null
        return pd
    }

    fun executeDownload(uri: android.net.Uri) {
        val pd = getAndClearPendingDownload() ?: return

        var counter = 0
        viewModelScope.launch(Dispatchers.IO) {
            val id = ++transferIdCounter
            val transfer = TransferInfo(
                id = id,
                fileName = pd.fileName,
                remotePath = pd.remotePath,
                localPath = uri.toString(),
                isDownload = true,
            )
            addTransfer(transfer)

            val app = getApplication<Application>()
            val outputStream: OutputStream? = try {
                app.contentResolver.openOutputStream(uri, "wt")
            } catch (e: Exception) {
                Timber.e(e, "Failed to open output stream for URI: $uri")
                withContext(Dispatchers.Main) {
                    updateTransfer(id, isComplete = true, error = "Cannot open destination: ${e.message}")
                    _uiState.value = _uiState.value.copy(transferProgress = null)
                }
                return@launch
            }
            if (outputStream == null) {
                withContext(Dispatchers.Main) {
                    updateTransfer(id, isComplete = true, error = "Cannot open destination")
                    _uiState.value = _uiState.value.copy(transferProgress = null)
                }
                return@launch
            }

            val result = if (useSSHJ) {
                sftpClientSSHJ?.downloadToStream(pd.remotePath, outputStream) { progress ->
                    if (counter++ % 5 == 0) {
                        _uiState.value = _uiState.value.copy(transferProgress = progress)
                    }
                }
            } else {
                sftpClient?.downloadToStream(pd.remotePath, outputStream) { progress ->
                    if (counter++ % 5 == 0) {
                        _uiState.value = _uiState.value.copy(transferProgress = progress)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                updateTransfer(id, isComplete = true, error = result?.exceptionOrNull()?.message)
                _uiState.value = _uiState.value.copy(transferProgress = null)
                if (result?.isSuccess == true) {
                    _uiState.value = _uiState.value.copy(message = "Downloaded: ${pd.fileName}")
                } else {
                    _uiState.value = _uiState.value.copy(error = result?.exceptionOrNull()?.message ?: "Download failed")
                }
            }
        }
    }

    fun uploadFile(localFile: File) {
        val basePath = _uiState.value.currentPath.trimEnd('/')
        val remotePath = "$basePath/${localFile.name}"

        var counter = 0
        viewModelScope.launch(Dispatchers.IO) {
            val id = ++transferIdCounter
            val transfer = TransferInfo(
                id = id,
                fileName = localFile.name,
                remotePath = remotePath,
                localPath = localFile.absolutePath,
                isDownload = false,
            )
            addTransfer(transfer)

            val result = if (useSSHJ) {
                sftpClientSSHJ?.uploadFile(localFile, remotePath) { progress ->
                    if (counter++ % 10 == 0) {
                        _uiState.value = _uiState.value.copy(transferProgress = progress)
                    }
                }
            } else {
                sftpClient?.uploadFile(localFile, remotePath) { progress ->
                    if (counter++ % 10 == 0) {
                        _uiState.value = _uiState.value.copy(transferProgress = progress)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                updateTransfer(id, isComplete = true, error = result?.exceptionOrNull()?.message)
                _uiState.value = _uiState.value.copy(transferProgress = null)
                listDirectory(_uiState.value.currentPath)
            }

            if (localFile.name.startsWith("upload_") && localFile.extension == "tmp") {
                localFile.delete()
            }
        }
    }

    fun createDirectory(name: String) {
        val basePath = _uiState.value.currentPath.trimEnd('/')
        val path = "$basePath/$name"
        viewModelScope.launch {
            val result = if (useSSHJ) {
                sftpClientSSHJ?.mkdir(path)
            } else {
                sftpClient?.mkdir(path)
            }
            if (result?.isSuccess == true) {
                listDirectory(_uiState.value.currentPath)
            } else {
                _uiState.value = _uiState.value.copy(error = result?.exceptionOrNull()?.message)
            }
        }
    }

    fun deleteEntry(entry: SftpEntry) {
        viewModelScope.launch {
            val result = if (useSSHJ) {
                if (entry.isDirectory) {
                    sftpClientSSHJ?.rmdir(entry.path)
                } else {
                    sftpClientSSHJ?.rm(entry.path)
                }
            } else {
                if (entry.isDirectory) {
                    sftpClient?.rmdir(entry.path)
                } else {
                    sftpClient?.rm(entry.path)
                }
            }
            if (result?.isSuccess == true) {
                listDirectory(_uiState.value.currentPath)
            } else {
                _uiState.value = _uiState.value.copy(error = result?.exceptionOrNull()?.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun addTransfer(transfer: TransferInfo) {
        _uiState.value = _uiState.value.copy(
            transfers = _uiState.value.transfers + transfer,
        )
    }

    private fun updateTransfer(id: Long, isComplete: Boolean, error: String? = null) {
        _uiState.value = _uiState.value.copy(
            transfers = _uiState.value.transfers.map {
                if (it.id == id) it.copy(isComplete = isComplete, error = error) else it
            },
        )
    }

    override fun onCleared() {
        super.onCleared()
        runBlocking {
            sftpClient?.disconnect()
            sftpClientSSHJ?.disconnect()
        }
    }
}