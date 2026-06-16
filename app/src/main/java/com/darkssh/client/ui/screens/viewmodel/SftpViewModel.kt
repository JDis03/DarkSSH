package com.darkssh.client.ui.screens.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.net.toFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.darkssh.client.R
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.HostRepository
import com.darkssh.client.service.CredentialStore
import com.darkssh.client.transport.SftpAuthState
import com.darkssh.client.transport.SftpClient
import com.darkssh.client.transport.SftpEntry
import com.darkssh.client.transport.TransferProgress
import com.darkssh.client.ui.MainActivity
import com.darkssh.client.worker.UploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.OutputStream
import javax.inject.Inject

sealed class DownloadTarget {
    abstract val fileName: String
    abstract val displayPath: String

    data class MediaStore(override val fileName: String) : DownloadTarget() {
        override val displayPath: String get() = "Downloads/$fileName"
    }

    data class FileTarget(override val fileName: String, val file: java.io.File) : DownloadTarget() {
        override val displayPath: String get() = file.absolutePath
    }
}

enum class SortBy { NAME, SIZE, DATE, TYPE }

    data class SftpUiState(
        val currentPath: String = "/",
        val entries: List<SftpEntry> = emptyList(),
        val allEntries: List<SftpEntry> = emptyList(),
        val showHiddenFiles: Boolean = false,
        val sortBy: SortBy = SortBy.NAME,
        val sortAscending: Boolean = true,
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        val pathStack: List<String> = emptyList(),
        val selectedEntries: Set<String> = emptySet(),
        val transfers: List<TransferInfo> = emptyList(),
        val transferProgress: TransferProgress? = null,
        val authState: SftpAuthState = SftpAuthState.Idle,
        val host: Host? = null,
        val renameConflict: RenameConflict? = null,
        val downloadConflict: DownloadConflict? = null,
    )

    data class RenameConflict(
        val entry: SftpEntry,
        val newName: String,
        val targetPath: String,
    )

    data class DownloadConflict(
        val remotePath: String,
        val fileName: String,
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

    companion object {
        // Made internal for access from SftpTransferService and UploadWorker
        internal val activeClients = ConcurrentHashMap<Long, SftpClient>()
        
        // Global transfer state (survives ViewModel recreation)
        private val activeTransfers = ConcurrentHashMap<String, TransferProgress>()
        
        // Active WorkManager upload jobs (for observing progress)
        private val activeWorkIds = ConcurrentHashMap<Long, UUID>()
        
        fun setActiveTransfer(key: String, progress: TransferProgress?) {
            if (progress != null) {
                activeTransfers[key] = progress
            } else {
                activeTransfers.remove(key)
            }
        }
        
        fun getActiveTransfer(key: String): TransferProgress? = activeTransfers[key]
        
        fun hasActiveTransfers(): Boolean = activeTransfers.isNotEmpty()
        
        fun setActiveWorkId(hostId: Long, workId: UUID?) {
            if (workId != null) {
                activeWorkIds[hostId] = workId
            } else {
                activeWorkIds.remove(hostId)
            }
        }
        
        fun getActiveWorkId(hostId: Long): UUID? = activeWorkIds[hostId]
    }

    private val _uiState = MutableStateFlow(SftpUiState())
    val uiState: StateFlow<SftpUiState> = _uiState.asStateFlow()

    private var sftpClient: SftpClient?
        get() = _uiState.value.host?.let { activeClients[it.id] }
        set(value) {
            val hostId = _uiState.value.host?.id ?: return
            if (value != null) activeClients[hostId] = value
            else activeClients.remove(hostId)
        }
    private var transferIdCounter = 0L
    private var downloadNotificationId = 1000

    private var pendingRename: PendingRename? = null
    private var currentTransferJob: kotlinx.coroutines.Job? = null
    private var transferDialogHidden = false



    data class PendingRename(
        val entry: SftpEntry,
        val newName: String,
    )

    fun initialize(hostId: Long) {
        val existingHost = _uiState.value.host
        if (existingHost != null) {
            // Already initialized, check if we have an active client
            val client = activeClients[hostId]
            if (client != null && client.isConnected) {
                _uiState.value = _uiState.value.copy(authState = SftpAuthState.Authenticated)
                
                // Restore active WorkManager upload if exists
                val workId = getActiveWorkId(hostId)
                if (workId != null) {
                    val workManager = WorkManager.getInstance(getApplication())
                    viewModelScope.launch {
                        val workInfo = workManager.getWorkInfoById(workId).get()
                        if (workInfo != null && !workInfo.state.isFinished) {
                            // Work is still running, resume observing it
                            val fileName = "file" // We don't have fileName stored, will update from progress
                            observeUploadWork(workManager, workId, fileName, hostId)
                            Timber.d("Resumed observing upload work: $workId")
                        } else {
                            // Work is finished or not found, clear it
                            setActiveWorkId(hostId, null)
                        }
                    }
                }
                
                // Don't reload if we already have entries (user can pull-to-refresh if needed)
                if (_uiState.value.entries.isNotEmpty()) return
                
                listDirectory(_uiState.value.currentPath)
                return
            }
        }

        viewModelScope.launch {
            val h = hostRepository.getHostById(hostId) ?: run {
                _uiState.value = _uiState.value.copy(error = "Host not found")
                return@launch
            }
            _uiState.value = _uiState.value.copy(host = h)
            
            // Restore active transfer if exists and not hidden
            val transferKey = "upload_$hostId"
            val activeTransfer = getActiveTransfer(transferKey)
            if (activeTransfer != null && !transferDialogHidden) {
                _uiState.value = _uiState.value.copy(transferProgress = activeTransfer)
            }

            // Check if there's an existing connection from previous session
            val existing = activeClients[hostId]
            if (existing != null && existing.isConnected) {
                sftpClient = existing
                _uiState.value = _uiState.value.copy(authState = SftpAuthState.Authenticated)
                val pwd = existing.pwd()
                _uiState.value = _uiState.value.copy(currentPath = pwd)
                listDirectory(pwd)
                return@launch
            }

            val storedPassword = CredentialStore.getPassword(hostId)
            if (storedPassword != null) {
                connectWithPassword(storedPassword)
            } else {
                _uiState.value = _uiState.value.copy(authState = SftpAuthState.NeedsPassword(h.hostname, h.username))
            }
        }
    }

    fun connectWithPassword(password: String) {
        val hostId = _uiState.value.host?.id ?: return
        CredentialStore.putPassword(hostId, password)
        val h = _uiState.value.host ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(authState = SftpAuthState.Connecting, error = null)

            val client = SftpClient(h)
            val result = client.connectWithPassword(password)

            if (result.isSuccess) {
                activeClients[hostId] = client
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

    fun dismissAuthError() {
        val h = _uiState.value.host ?: return
        _uiState.value = _uiState.value.copy(authState = SftpAuthState.NeedsPassword(h.hostname, h.username))
    }

    private fun filterEntries(entries: List<SftpEntry>, showHidden: Boolean): List<SftpEntry> {
        val filtered = if (showHidden) entries else entries.filterNot { it.name.startsWith(".") }
        val s = _uiState.value.sortBy
        val asc = _uiState.value.sortAscending
        val comparator: Comparator<SftpEntry> = when (s) {
            SortBy.NAME -> compareBy<SftpEntry> { it.name.lowercase() }
            SortBy.SIZE -> compareBy<SftpEntry> { if (it.isDirectory) Long.MIN_VALUE else it.size }
            SortBy.DATE -> compareBy<SftpEntry> { it.modifiedTime ?: Long.MIN_VALUE }
            SortBy.TYPE -> compareBy<SftpEntry> { if (it.isDirectory) 0 else 1 }.thenBy { it.name.lowercase() }
        }
        return if (asc) filtered.sortedWith(comparator) else filtered.sortedWith(comparator.reversed())
    }

    fun changeSortStyle(sortBy: SortBy) {
        val current = _uiState.value
        val ascending = if (current.sortBy == sortBy) !current.sortAscending else true
        _uiState.value = current.copy(sortBy = sortBy, sortAscending = ascending)
        _uiState.value = _uiState.value.copy(
            entries = filterEntries(current.allEntries, current.showHiddenFiles),
        )
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
            _uiState.value = _uiState.value.copy(isLoading = true, isRefreshing = false, error = null)

            if (tryReconnectIfNeeded().not()) return@launch

            val result = sftpClient?.ls(path) ?: run {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "SFTP not connected")
                return@launch
            }

            result.onSuccess { entries ->
                val showHidden = _uiState.value.showHiddenFiles
                _uiState.value = _uiState.value.copy(
                    allEntries = entries,
                    entries = filterEntries(entries, showHidden),
                    isLoading = false,
                    isRefreshing = false,
                    currentPath = path,
                    selectedEntries = emptySet(),
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.message,
                )
            }
        }
    }

    private suspend fun tryReconnectIfNeeded(): Boolean {
        val hostId = _uiState.value.host?.id ?: return false
        val existing = activeClients[hostId]
        if (existing != null && existing.isConnected) return true

        val h = _uiState.value.host ?: return false
        val password = CredentialStore.getPassword(h.id) ?: return false

        return try {
            val c = SftpClient(h)
            if (c.connectWithPassword(password).isSuccess) {
                activeClients[hostId] = c
                true
            } else false
        } catch (_: Exception) { false }
    }

    fun refreshCurrentDirectory() {
        val path = _uiState.value.currentPath
        if (path.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            listDirectory(path)
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
        val target = resolveDownloadTarget(fileName)

        if (targetExists(target)) {
            _uiState.value = _uiState.value.copy(
                downloadConflict = DownloadConflict(remotePath, fileName),
            )
        } else {
            startDownload(remotePath, target)
        }
    }

    fun confirmDownloadOverwrite() {
        val conflict = _uiState.value.downloadConflict ?: return
        _uiState.value = _uiState.value.copy(downloadConflict = null)
        val target = resolveDownloadTarget(conflict.fileName)
        startDownload(conflict.remotePath, target)
    }

    fun dismissDownloadConflict() {
        _uiState.value = _uiState.value.copy(downloadConflict = null)
    }

    private fun resolveDownloadTarget(fileName: String): DownloadTarget {
        val app = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            DownloadTarget.MediaStore(fileName)
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val subdir = File(dir, "DarkSSH")
            if (!subdir.exists()) subdir.mkdirs()
            DownloadTarget.FileTarget(fileName, File(subdir, fileName))
        }
    }

    private fun targetExists(target: DownloadTarget): Boolean {
        val app = getApplication<Application>()
        return if (target is DownloadTarget.MediaStore) {
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(target.fileName)
            var exists = false
            app.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                exists = cursor.count > 0
            }
            exists
        } else {
            (target as DownloadTarget.FileTarget).file.exists()
        }
    }

    private fun startDownload(remotePath: String, target: DownloadTarget) {
        val app = getApplication<Application>()
        val notifId = downloadNotificationId++
        createDownloadNotificationChannel(app)
        showDownloadNotification(app, notifId, target.fileName, 0L, 0L)

        var counter = 0
        viewModelScope.launch(Dispatchers.IO) {
            val id = ++transferIdCounter
            val transfer = TransferInfo(
                id = id,
                fileName = target.fileName,
                remotePath = remotePath,
                localPath = target.displayPath,
                isDownload = true,
            )
            addTransfer(transfer)

            val result = when (target) {
                is DownloadTarget.MediaStore -> downloadViaMediaStore(remotePath, target, app, notifId) { progress ->
                    if (counter++ % 5 == 0) {
                        _uiState.value = _uiState.value.copy(transferProgress = progress)
                        showDownloadNotification(app, notifId, target.fileName, progress.transferred, progress.total)
                    }
                }
                is DownloadTarget.FileTarget -> {
                    sftpClient?.downloadFile(remotePath, target.file) { progress ->
                        if (counter++ % 5 == 0) {
                            _uiState.value = _uiState.value.copy(transferProgress = progress)
                            showDownloadNotification(app, notifId, target.fileName, progress.transferred, progress.total)
                        }
                    } ?: Result.failure(Exception("SFTP not connected"))
                }
            }

            withContext(Dispatchers.Main) {
                updateTransfer(id, isComplete = true, error = result.exceptionOrNull()?.message)
                _uiState.value = _uiState.value.copy(transferProgress = null)
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(message = "Downloaded: ${target.fileName}")
                    showDownloadCompleteNotification(app, notifId, target.fileName)
                } else {
                    _uiState.value = _uiState.value.copy(error = result.exceptionOrNull()?.message ?: "Download failed")
                    showDownloadFailedNotification(app, notifId, target.fileName)
                }
            }
        }
    }

    private suspend fun downloadViaMediaStore(
        remotePath: String,
        target: DownloadTarget.MediaStore,
        app: Application,
        notifId: Int,
        onProgress: ((TransferProgress) -> Unit)?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

            // Find existing entry with same name
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(target.fileName)
            var existingUri: android.net.Uri? = null
            app.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    existingUri = android.content.ContentUris.withAppendedId(collection, id)
                }
            }

            val uri = existingUri ?: run {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, target.fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                app.contentResolver.insert(collection, values)
                    ?: return@withContext Result.failure(Exception("Failed to create download entry"))
            }

            if (existingUri != null) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                app.contentResolver.update(uri, values, null, null)
            }

            val outputStream = app.contentResolver.openOutputStream(uri, "wt")
                ?: run {
                    if (existingUri == null) {
                        app.contentResolver.delete(uri, null, null)
                    }
                    return@withContext Result.failure(Exception("Cannot open output stream"))
                }

            try {
                outputStream.use { stream ->
                    val result = sftpClient?.downloadToStream(remotePath, stream, onProgress)
                    if (result?.isFailure == true) {
                        if (existingUri == null) {
                            app.contentResolver.delete(uri, null, null)
                        }
                        return@withContext result
                    }
                }
            } catch (e: Exception) {
                if (existingUri == null) {
                    app.contentResolver.delete(uri, null, null)
                }
                return@withContext Result.failure(e)
            }

            val values = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            app.contentResolver.update(uri, values, null, null)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to download via MediaStore: $remotePath")
            Result.failure(e)
        }
    }

    private fun createDownloadNotificationChannel(app: Application) {
        val channel = NotificationChannel(
            "sftp_downloads",
            "SFTP Downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "SFTP file download progress"
        }
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createUploadNotificationChannel(app: Application) {
        val channel = NotificationChannel(
            "sftp_uploads",
            "SFTP Uploads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "SFTP file upload progress"
        }
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun showDownloadNotification(app: Application, notifId: Int, fileName: String, transferred: Long, total: Long) {
        val manager = app.getSystemService(NotificationManager::class.java)
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(app, "sftp_downloads")
            .setContentTitle("Downloading: $fileName")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        if (total > 0) {
            builder.setContentText("${formatBytes(transferred)} / ${formatBytes(total)}")
            val percent = (transferred * 100 / total).toInt()
            builder.setProgress(100, percent, false)
        } else {
            builder.setContentText("Starting...")
            builder.setProgress(100, 0, true)
        }

        manager.notify(notifId, builder.build())
    }

    private fun showDownloadCompleteNotification(app: Application, notifId: Int, fileName: String) {
        val manager = app.getSystemService(NotificationManager::class.java)
        val builder = NotificationCompat.Builder(app, "sftp_downloads")
            .setContentTitle("Download complete")
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(false)
            .setAutoCancel(true)

        manager.notify(notifId, builder.build())
    }

    private fun showDownloadFailedNotification(app: Application, notifId: Int, fileName: String) {
        val manager = app.getSystemService(NotificationManager::class.java)
        val builder = NotificationCompat.Builder(app, "sftp_downloads")
            .setContentTitle("Download failed")
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(false)
            .setAutoCancel(true)

        manager.notify(notifId, builder.build())
    }

    private fun showUploadNotification(app: Application, notifId: Int, fileName: String, transferred: Long, total: Long) {
        val manager = app.getSystemService(NotificationManager::class.java)
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(app, "sftp_uploads")
            .setContentTitle("Uploading: $fileName")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        if (total > 0) {
            builder.setContentText("${formatBytes(transferred)} / ${formatBytes(total)}")
            val percent = (transferred * 100 / total).toInt()
            builder.setProgress(100, percent, false)
        } else {
            builder.setContentText("Starting...")
            builder.setProgress(100, 0, true)
        }

        manager.notify(notifId, builder.build())
    }

    private fun showUploadCompleteNotification(app: Application, notifId: Int, fileName: String) {
        val manager = app.getSystemService(NotificationManager::class.java)
        val builder = NotificationCompat.Builder(app, "sftp_uploads")
            .setContentTitle("Upload complete")
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(false)
            .setAutoCancel(true)

        manager.notify(notifId, builder.build())
    }

    private fun showUploadFailedNotification(app: Application, notifId: Int, fileName: String) {
        val manager = app.getSystemService(NotificationManager::class.java)
        val builder = NotificationCompat.Builder(app, "sftp_uploads")
            .setContentTitle("Upload failed")
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(false)
            .setAutoCancel(true)

        manager.notify(notifId, builder.build())
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    fun uploadFile(localFile: File, originalName: String = localFile.name) {
        try {
            Timber.d("uploadFile called: file=$originalName, exists=${localFile.exists()}, size=${localFile.length()}")
            
            val basePath = _uiState.value.currentPath.trimEnd('/')
            val remotePath = "$basePath/$originalName"
            val app = getApplication<Application>()
            val hostId = _uiState.value.host?.id ?: run {
                Timber.e("No host ID available")
                _uiState.value = _uiState.value.copy(error = "No host selected")
                return
            }

            Timber.d("Upload params: hostId=$hostId, remotePath=$remotePath")

            // Check if we have an active SFTP client
            val client = activeClients[hostId]
            val useWorkManager = try {
                val isConnected = client != null && client.isConnected
                Timber.d("Client check: exists=${client != null}, connected=$isConnected")
                isConnected
            } catch (e: Exception) {
                Timber.w(e, "Error checking client connection, using direct upload")
                false
            }
        
        if (useWorkManager) {
            // Use WorkManager for background upload (survives app closure)
            Timber.d("Enqueuing upload to WorkManager: $originalName")

            val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(
                    workDataOf(
                        UploadWorker.KEY_HOST_ID to hostId,
                        UploadWorker.KEY_LOCAL_PATH to localFile.absolutePath,
                        UploadWorker.KEY_REMOTE_PATH to remotePath,
                        UploadWorker.KEY_FILE_NAME to originalName
                    )
                )
                .build()

            val workManager = WorkManager.getInstance(app)
            workManager.enqueue(uploadWorkRequest)
            setActiveWorkId(hostId, uploadWorkRequest.id)
            observeUploadWork(workManager, uploadWorkRequest.id, originalName, hostId)
            Timber.d("Upload work enqueued with ID: ${uploadWorkRequest.id}")
        } else {
            // Fallback to direct upload (legacy method)
            Timber.d("Using direct upload (no active client for WorkManager): $originalName")
            uploadFileDirect(localFile, originalName, remotePath, hostId)
        }
        } catch (e: Exception) {
            Timber.e(e, "Exception in uploadFile")
            _uiState.value = _uiState.value.copy(error = "Upload error: ${e.message}")
        }
    }
    
    private fun uploadFileDirect(localFile: File, originalName: String, remotePath: String, hostId: Long) {
        val app = getApplication<Application>()
        createUploadNotificationChannel(app)
        val notifId = 3000 + originalName.hashCode() % 1000
        transferDialogHidden = false
        val transferKey = "upload_$hostId"
        
        currentTransferJob = viewModelScope.launch {
            try {
                val initialProgress = TransferProgress(0, localFile.length(), originalName)
                setActiveTransfer(transferKey, initialProgress)
                if (!transferDialogHidden) {
                    _uiState.value = _uiState.value.copy(transferProgress = initialProgress)
                }
                showUploadNotification(app, notifId, originalName, 0, localFile.length())
                
                var counter = 0
                val result = sftpClient?.uploadFile(localFile, remotePath) { progress ->
                    setActiveTransfer(transferKey, progress)
                    if (counter++ % 5 == 0) {
                        if (!transferDialogHidden) {
                            _uiState.value = _uiState.value.copy(transferProgress = progress)
                        }
                        showUploadNotification(app, notifId, originalName, progress.transferred, progress.total)
                    }
                } ?: Result.failure(Exception("SFTP not connected"))
                
                result.fold(
                    onSuccess = {
                        currentTransferJob = null
                        transferDialogHidden = false
                        setActiveTransfer(transferKey, null)
                        _uiState.value = _uiState.value.copy(
                            transferProgress = null,
                            message = "Upload complete: $originalName"
                        )
                        showUploadCompleteNotification(app, notifId, originalName)
                        if (localFile.name.startsWith("upload_") && localFile.extension == "tmp") {
                            localFile.delete()
                        }
                        listDirectory(_uiState.value.currentPath)
                    },
                    onFailure = { error ->
                        currentTransferJob = null
                        transferDialogHidden = false
                        setActiveTransfer(transferKey, null)
                        _uiState.value = _uiState.value.copy(
                            transferProgress = null,
                            error = "Upload failed: ${error.message}"
                        )
                        showUploadFailedNotification(app, notifId, originalName)
                        Timber.e(error, "Upload failed")
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                currentTransferJob = null
                transferDialogHidden = false
                setActiveTransfer(transferKey, null)
                _uiState.value = _uiState.value.copy(transferProgress = null)
                Timber.d("Upload cancelled: $originalName")
                val notificationManager = app.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(notifId)
                throw e
            } catch (e: Exception) {
                currentTransferJob = null
                transferDialogHidden = false
                setActiveTransfer(transferKey, null)
                _uiState.value = _uiState.value.copy(
                    transferProgress = null,
                    error = "Upload error: ${e.message}"
                )
                showUploadFailedNotification(app, notifId, originalName)
                Timber.e(e, "Upload exception")
            }
        }
    }

    private fun observeUploadWork(workManager: WorkManager, workId: UUID, fileName: String, hostId: Long) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        val transferred = workInfo.progress.getLong(UploadWorker.PROGRESS_CURRENT, 0L)
                        val total = workInfo.progress.getLong(UploadWorker.PROGRESS_TOTAL, 0L)
                        val startTime = workInfo.progress.getLong(UploadWorker.PROGRESS_START_TIME, System.currentTimeMillis())

                        if (total > 0) {
                            val progress = TransferProgress(
                                transferred = transferred,
                                total = total,
                                filePath = fileName,
                                startTime = startTime,
                                currentTime = System.currentTimeMillis()
                            )
                            if (!transferDialogHidden) {
                                _uiState.value = _uiState.value.copy(transferProgress = progress)
                            }
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        setActiveWorkId(hostId, null)
                        _uiState.value = _uiState.value.copy(
                            transferProgress = null,
                            message = "Upload complete: $fileName"
                        )
                        listDirectory(_uiState.value.currentPath)
                        Timber.d("Upload work succeeded: $fileName")
                    }
                    WorkInfo.State.FAILED -> {
                        setActiveWorkId(hostId, null)
                        val errorMessage = workInfo.outputData.getString("error") ?: "Unknown error"
                        _uiState.value = _uiState.value.copy(
                            transferProgress = null,
                            error = "Upload failed: $errorMessage"
                        )
                        Timber.e("Upload work failed: $fileName - $errorMessage")
                    }
                    WorkInfo.State.CANCELLED -> {
                        setActiveWorkId(hostId, null)
                        _uiState.value = _uiState.value.copy(transferProgress = null)
                        Timber.d("Upload work cancelled: $fileName")
                    }
                    else -> {
                        // ENQUEUED or BLOCKED - show initial state
                        if (workInfo?.state == WorkInfo.State.ENQUEUED) {
                            val initialProgress = TransferProgress(0, 100, fileName)
                            if (!transferDialogHidden) {
                                _uiState.value = _uiState.value.copy(transferProgress = initialProgress)
                            }
                        }
                    }
                }
            }
        }
    }

    fun createDirectory(name: String) {
        val basePath = _uiState.value.currentPath.trimEnd('/')
        val path = "$basePath/$name"
        viewModelScope.launch {
            val result = sftpClient?.mkdir(path)
            if (result?.isSuccess == true) {
                listDirectory(_uiState.value.currentPath)
            } else {
                _uiState.value = _uiState.value.copy(error = result?.exceptionOrNull()?.message)
            }
        }
    }

    fun deleteEntry(entry: SftpEntry) {
        viewModelScope.launch {
            val result = if (entry.isDirectory) {
                sftpClient?.rmdir(entry.path)
            } else {
                sftpClient?.rm(entry.path)
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

    fun dismissRenameConflict() {
        _uiState.value = _uiState.value.copy(renameConflict = null)
    }

    fun renameEntry(entry: SftpEntry, newName: String) {
        if (newName == entry.name) return
        val basePath = _uiState.value.currentPath.trimEnd('/')
        val newPath = "$basePath/$newName"

        viewModelScope.launch(Dispatchers.IO) {
            val targetExists = sftpClient?.exists(newPath) ?: false

            if (targetExists) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        renameConflict = RenameConflict(entry, newName, newPath),
                    )
                }
            } else {
                doRename(entry, newPath)
            }
        }
    }

    fun confirmRenameOverwrite() {
        val conflict = _uiState.value.renameConflict ?: return
        _uiState.value = _uiState.value.copy(renameConflict = null)

        viewModelScope.launch(Dispatchers.IO) {
            if (conflict.entry.isDirectory) {
                val rmdirResult = sftpClient?.rmdir(conflict.targetPath)
                if (rmdirResult?.isSuccess != true) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            error = rmdirResult?.exceptionOrNull()?.message ?: "Cannot remove existing directory",
                        )
                    }
                    return@launch
                }
            } else {
                val rmResult = sftpClient?.rm(conflict.targetPath)
                if (rmResult?.isSuccess != true) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            error = rmResult?.exceptionOrNull()?.message ?: "Cannot remove existing file",
                        )
                    }
                    return@launch
                }
            }
            doRename(conflict.entry, conflict.targetPath)
        }
    }

    private suspend fun doRename(entry: SftpEntry, newPath: String) {
        val result = sftpClient?.rename(entry.path, newPath)

        withContext(Dispatchers.Main) {
            if (result?.isSuccess == true) {
                _uiState.value = _uiState.value.copy(message = "Renamed to ${newPath.substringAfterLast('/')}")
                listDirectory(_uiState.value.currentPath)
            } else {
                _uiState.value = _uiState.value.copy(error = result?.exceptionOrNull()?.message ?: "Rename failed")
            }
        }
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

    // Copy/Cut/Paste operations (like File Manager+)
    
    fun copyFiles(files: List<SftpEntry>) {
        val hostId = _uiState.value.host?.id ?: return
        val currentPath = _uiState.value.currentPath
        SftpClipboard.copy(files, hostId, currentPath)
        Timber.d("Copied ${files.size} files to clipboard")
    }

    fun cutFiles(files: List<SftpEntry>) {
        val hostId = _uiState.value.host?.id ?: return
        val currentPath = _uiState.value.currentPath
        SftpClipboard.cut(files, hostId, currentPath)
        Timber.d("Cut ${files.size} files to clipboard")
    }

    fun pasteFiles() {
        val clipboardData = SftpClipboard.paste() ?: return
        val hostId = _uiState.value.host?.id ?: return
        val targetPath = _uiState.value.currentPath.trimEnd('/')

        if (clipboardData.hostId != hostId) {
            _uiState.value = _uiState.value.copy(
                error = "Cannot paste files from different host"
            )
            return
        }

        val operationName = when (clipboardData.operation) {
            SftpClipboard.Operation.COPY -> "Copying"
            SftpClipboard.Operation.CUT -> "Moving"
        }
        
        Timber.d("$operationName ${clipboardData.files.size} files to $targetPath")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = activeClients[hostId]
                if (client == null || !client.isConnected) {
                    _uiState.value = _uiState.value.copy(
                        error = "Not connected to SFTP server"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                var successCount = 0
                var failCount = 0
                
                for (entry in clipboardData.files) {
                    val sourcePath = "${clipboardData.sourcePath.trimEnd('/')}/${entry.name}"
                    val destPath = "$targetPath/${entry.name}"

                    try {
                        when (clipboardData.operation) {
                            SftpClipboard.Operation.COPY -> {
                                // Copy file/directory via SSH (much faster than SFTP streaming)
                                val result = client.copyFileViaSsh(sourcePath, destPath, entry.isDirectory)
                                if (result.isSuccess) {
                                    Timber.d("Copied: $sourcePath -> $destPath")
                                    successCount++
                                } else {
                                    Timber.e("Failed to copy: ${entry.name}")
                                    failCount++
                                }
                            }
                            SftpClipboard.Operation.CUT -> {
                                // Move (rename) file/directory
                                val result = client.moveFile(sourcePath, destPath)
                                if (result.isSuccess) {
                                    Timber.d("Moved: $sourcePath -> $destPath")
                                    successCount++
                                } else {
                                    failCount++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to paste ${entry.name}")
                        failCount++
                    }
                }

                // Clear clipboard after paste (only if all succeeded)
                if (failCount == 0) {
                    SftpClipboard.clear()
                }

                // Show result message
                val message = when {
                    failCount == 0 -> "$operationName completed: $successCount file(s)"
                    successCount == 0 -> "Failed to paste files"
                    else -> "$operationName completed: $successCount succeeded, $failCount failed"
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = if (failCount > 0) message else null
                )

                // Refresh current directory
                listDirectory(_uiState.value.currentPath)
            } catch (e: Exception) {
                Timber.e(e, "Paste operation failed")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to paste files"
                )
            }
        }
    }

    fun hasClipboardData(): Boolean = SftpClipboard.hasData()

    fun getClipboardOperation(): SftpClipboard.Operation? = SftpClipboard.getOperation()

    fun getClipboardFileCount(): Int = SftpClipboard.getFileCount()

    fun hideTransferDialog() {
        // Hide dialog but keep transfer running in background
        transferDialogHidden = true
        _uiState.update { it.copy(transferProgress = null) }
        Timber.d("Transfer dialog hidden, transfer continues in background")
    }
    
    fun cancelTransfer() {
        val hostId = _uiState.value.host?.id ?: return
        
        // Cancel WorkManager upload if exists
        val workId = getActiveWorkId(hostId)
        if (workId != null) {
            val workManager = WorkManager.getInstance(getApplication())
            workManager.cancelWorkById(workId)
            setActiveWorkId(hostId, null)
            Timber.d("Cancelled WorkManager upload: $workId")
        }
        
        // Cancel legacy coroutine job if exists (for backwards compatibility)
        currentTransferJob?.cancel()
        currentTransferJob = null
        transferDialogHidden = false
        
        // Clear from global state
        val transferKey = "upload_$hostId"
        setActiveTransfer(transferKey, null)
        
        _uiState.update { it.copy(transferProgress = null) }
        Timber.d("Transfer cancelled")
    }
    
    fun showTransferDialog() {
        // Show the transfer dialog again if hidden
        transferDialogHidden = false
    }

    override fun onCleared() {
        super.onCleared()
        currentTransferJob?.cancel()
        
        // Clean up this ViewModel's resources from static maps
        val hostId = _uiState.value.host?.id
        if (hostId != null) {
            val hasActiveWork = getActiveWorkId(hostId) != null
            val hasActiveTransfer = getActiveTransfer("upload_$hostId") != null || 
                                   getActiveTransfer("download_$hostId") != null
            
            // Only disconnect if no active transfers for this host
            if (!hasActiveWork && !hasActiveTransfer) {
                activeClients[hostId]?.let { client ->
                    viewModelScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                client.disconnect()
                            }
                            Timber.d("SftpViewModel: disconnected SFTP client for host $hostId")
                        } catch (e: Exception) {
                            Timber.w(e, "SftpViewModel: failed to disconnect SFTP client on clear")
                        }
                    }
                }
                activeClients.remove(hostId)
                Timber.d("SftpViewModel cleared - cleaned up resources for host $hostId")
            } else {
                Timber.d("SftpViewModel cleared - keeping connection alive (active transfers exist)")
            }
        }
    }
}