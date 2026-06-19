package com.darkssh.client.service

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.model.OsType
import com.darkssh.client.data.repository.KnownHostRepository
import com.darkssh.client.data.repository.TabRepository
import com.darkssh.client.terminal.DarkTerminalSession
import com.darkssh.client.terminal.emulator.TerminalEmulator
import com.darkssh.client.terminal.emulator.TerminalSessionClient
import com.darkssh.client.transport.AbsTransport
import com.darkssh.client.transport.SSH
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class TerminalBridge(
    val host: Host,
    private val terminalService: TerminalService,
    private val knownHostRepository: KnownHostRepository,
    private val tabRepository: TabRepository,
    private val clipboardManager: ClipboardManager,
    val tabId: String? = null,
) : TerminalSessionClient {
    private val _fontSize = MutableStateFlow(14f)
    val fontSize: StateFlow<Float> = _fontSize

    private var lastFontResizeTime = 0L

    fun increaseFontSize() {
        val now = System.currentTimeMillis()
        if (now - lastFontResizeTime < 80) return
        lastFontResizeTime = now
        _fontSize.value = (_fontSize.value + 2f).coerceAtMost(36f)
        com.darkssh.client.util.DebugLogger.UI.volumeZoom("UP", _fontSize.value)
    }

    fun decreaseFontSize() {
        val now = System.currentTimeMillis()
        if (now - lastFontResizeTime < 80) return
        lastFontResizeTime = now
        _fontSize.value = (_fontSize.value - 2f).coerceAtLeast(8f)
        com.darkssh.client.util.DebugLogger.UI.volumeZoom("DOWN", _fontSize.value)
    }

    private val bridgeJob = SupervisorJob()
    private val bridgeScope = CoroutineScope(bridgeJob + Dispatchers.Main)

    private sealed class TransportOperation {
        data class WriteData(val data: ByteArray) : TransportOperation()
        data class SetDimensions(val cols: Int, val rows: Int, val width: Int, val height: Int) : TransportOperation()
        data object Flush : TransportOperation()
    }
    private val transportOperations = Channel<TransportOperation>(Channel.UNLIMITED)

    @Volatile
    var transport: AbsTransport? = null
        private set

    @Volatile
    var darkTerminalSession: DarkTerminalSession? = null
        private set

    val terminalEmulator: TerminalEmulator?
        get() = darkTerminalSession?.emulator

    @Volatile
    var columns: Int = 80
        private set
    @Volatile
    var rows: Int = 24
        private set

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _isDisconnected = MutableStateFlow(false)
    val isDisconnected: StateFlow<Boolean> = _isDisconnected
    
    private val _disconnectReason = MutableStateFlow<DisconnectReason?>(null)
    val disconnectReason: StateFlow<DisconnectReason?> = _disconnectReason
    
    private val _osType = MutableStateFlow(OsType.UNKNOWN)
    val osType: StateFlow<OsType> = _osType

    /** Called by Terminal composable to receive screen update notifications. */
    var onScreenUpdate: (() -> Unit)? = null

    private val _promptRequest = MutableStateFlow<PromptRequest?>(null)
    val promptRequest: StateFlow<PromptRequest?> = _promptRequest

    private val _disconnectMessage = MutableStateFlow<String?>(null)
    val disconnectMessage: StateFlow<String?> = _disconnectMessage

    private var relay: Relay? = null
    private var pendingPrompt: CompletableDeferred<PromptResponse>? = null

    fun createTerminalEmulator() {
        try {
            Timber.d("Creating DarkTerminalSession")

            darkTerminalSession = DarkTerminalSession(5000, this)
            darkTerminalSession?.setKeyboardListener { data ->
                try {
                    write(data)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to handle keyboard input")
                }
            }

            Timber.d("DarkTerminalSession created successfully (emulator will be created by TerminalView)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to create DarkTerminalSession")
            throw e
        }
    }

    init {
        // Load previously detected OS from Tab immediately (before UI renders)
        tabId?.let { id ->
            bridgeScope.launch(Dispatchers.IO) {
                val tab = tabRepository.getTabById(id)
                tab?.let {
                    if (it.osType != OsType.UNKNOWN) {
                        _osType.value = it.osType
                        Timber.d("Loaded cached OS type: ${it.osType.displayName}")
                    }
                }
            }
        }
    }
    
    fun startConnection() {
        bridgeScope.launch(Dispatchers.Main) {
            try {
                createTerminalEmulator()
                Timber.d("DarkTerminalSession created successfully")

                bridgeScope.launch(Dispatchers.IO) {
                    try {
                        val ssh = SSH(host, this@TerminalBridge, terminalService, knownHostRepository)
                        transport = ssh
                        ssh.connect()
                    } catch (e: CancellationException) {
                        Timber.d("Connection cancelled for ${host.hostname}")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to connect to ${host.hostname}")
                        dispatchDisconnect(e.message ?: "Connection failed")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to create DarkTerminalSession")
                dispatchDisconnect("Failed to initialize terminal")
            }
        }
    }

    fun onConnected() {
        android.util.Log.d("TerminalBridge", "🟢 onConnected() called, transport=${transport?.javaClass?.simpleName}")
        _isConnected.value = true
        try {
            val t = transport
            if (t != null) {
                android.util.Log.d("TerminalBridge", "🟢 Creating relay...")
                relay = Relay(t, this)
                relay?.start()
                android.util.Log.d("TerminalBridge", "🟢 Relay started")
                startTransportProcessor()
                
                // Detect OS in background (don't block connection)
                if (t is SSH) {
                    bridgeScope.launch(Dispatchers.IO) {
                        val connection = t.getConnection()
                        if (connection != null) {
                            val detectedOs = OsDetector.detectOs(connection)
                            _osType.value = detectedOs
                            Timber.d("Detected OS: ${detectedOs.displayName}")
                            
                            // Persist detected OS to database for future connections
                            tabId?.let { id ->
                                try {
                                    val tab = tabRepository.getTabById(id)
                                    tab?.let {
                                        if (it.osType != detectedOs) {
                                            tabRepository.updateTab(it.copy(osType = detectedOs))
                                            Timber.d("Saved OS type to database: ${detectedOs.displayName}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to save OS type to database")
                                }
                            }
                        }
                    }
                }
            } else {
                android.util.Log.e("TerminalBridge", "❌ Transport is null!")
            }
        } catch (e: Exception) {
            android.util.Log.e("TerminalBridge", "❌ Error: ${e.message}", e)
        }
    }

    private fun startTransportProcessor() {
        bridgeScope.launch(Dispatchers.IO) {
            for (operation in transportOperations) {
                try {
                    when (operation) {
                        is TransportOperation.WriteData -> {
                            val bytes = operation.data
                            val t = transport ?: continue
                            t.write(bytes)
                            t.flush()
                        }
                        is TransportOperation.SetDimensions -> {
                            transport?.setDimensions(operation.cols, operation.rows, operation.width, operation.height)
                        }
                        is TransportOperation.Flush -> {
                            transport?.flush()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TerminalBridge", "❌ Transport op error: ${e.message}", e)
                }
            }
        }
    }

    fun onRelayData(data: ByteArray) {
        try {
            darkTerminalSession?.append(data)
        } catch (e: Exception) {
            Timber.e(e, "💥 Failed to process ${data.size} bytes")
        }
    }

    fun dispatchDisconnect(message: String? = null, reason: DisconnectReason = DisconnectReason.UNKNOWN) {
        _disconnectMessage.value = message
        _disconnectReason.value = reason
        _isConnected.value = false
        _isDisconnected.value = true
        
        // Stop relay
        relay?.stop()
        relay = null
        
        // Cancel pending prompts
        cancelPendingPrompt()
        
        Timber.w("Disconnected from ${host.nickname}: $message (reason: $reason)")
        terminalService.onBridgeDisconnected(this, reason)
    }

    fun close(reason: DisconnectReason = DisconnectReason.USER_REQUESTED) {
        _disconnectReason.value = reason
        
        // Cancel all coroutines first
        bridgeScope.cancel()
        bridgeJob.cancel()
        
        // Stop relay
        relay?.stop()
        relay = null
        
        // Close channels and transport
        transportOperations.close()
        transport?.close()
        transport = null
        
        // Clean terminal session
        darkTerminalSession?.finish()
        darkTerminalSession = null
        
        // Cancel pending prompts
        cancelPendingPrompt()
        
        // Update state
        _isConnected.value = false
        _isDisconnected.value = true
    }

    fun setDimensions(cols: Int, newRows: Int, width: Int = 0, height: Int = 0) {
        columns = cols
        rows = newRows
        transportOperations.trySend(TransportOperation.SetDimensions(cols, newRows, width, height))
        darkTerminalSession?.updateSize(cols, newRows, 0, 0)
    }

    fun write(data: ByteArray) {
        transportOperations.trySend(TransportOperation.WriteData(data))
    }

    suspend fun promptForPassword(): String? {
        val deferred = CompletableDeferred<PromptResponse>()
        pendingPrompt = deferred
        _promptRequest.value = PromptRequest.StringPrompt("Password:", false)
        val result = withTimeoutOrNull(60_000L) { deferred.await() }
        pendingPrompt = null
        _promptRequest.value = null
        return (result as? PromptResponse.StringResponse)?.value
    }

    fun promptForPasswordBlocking(): String? {
        return try {
            // Use Main.immediate to avoid blocking Main dispatcher unnecessarily
            runBlocking(Dispatchers.Main.immediate) {
                promptForPassword()
            }
        } catch (e: CancellationException) {
            Timber.d("Password prompt cancelled")
            null
        }
    }

    suspend fun promptForInput(prompt: String, echo: Boolean): String? {
        val deferred = CompletableDeferred<PromptResponse>()
        pendingPrompt = deferred
        _promptRequest.value = PromptRequest.StringPrompt(prompt, echo)
        val result = withTimeoutOrNull(60_000L) { deferred.await() }
        pendingPrompt = null
        _promptRequest.value = null
        return (result as? PromptResponse.StringResponse)?.value
    }

    fun promptForInputBlocking(prompt: String, echo: Boolean): String? {
        return try {
            // Use Main.immediate to avoid blocking Main dispatcher unnecessarily
            runBlocking(Dispatchers.Main.immediate) {
                promptForInput(prompt, echo)
            }
        } catch (e: CancellationException) {
            Timber.d("Input prompt cancelled")
            null
        }
    }

    suspend fun promptForHostKeyVerification(hostname: String, port: Int, fingerprints: String): Boolean {
        val deferred = CompletableDeferred<PromptResponse>()
        pendingPrompt = deferred
        _promptRequest.value = PromptRequest.HostKeyPrompt(hostname, port, fingerprints)
        val result = withTimeoutOrNull(120_000L) { deferred.await() }
        pendingPrompt = null
        _promptRequest.value = null
        return (result as? PromptResponse.BooleanResponse)?.value ?: false
    }

    fun promptForHostKeyVerificationBlocking(hostname: String, port: Int, fingerprints: String): Boolean {
        return try {
            // Use Main.immediate to avoid blocking Main dispatcher unnecessarily
            runBlocking(Dispatchers.Main.immediate) {
                promptForHostKeyVerification(hostname, port, fingerprints)
            }
        } catch (e: CancellationException) {
            Timber.d("Host key verification prompt cancelled")
            false
        }
    }

    fun respondToPrompt(response: PromptResponse) {
        pendingPrompt?.complete(response)
    }

    private fun cancelPendingPrompt() {
        pendingPrompt?.cancel()
        pendingPrompt = null
        _promptRequest.value = null
    }

    // TerminalSessionClient implementation

    override fun onTextChanged(session: com.darkssh.client.terminal.emulator.TerminalSession) {
        onScreenUpdate?.invoke()
    }

    override fun onTitleChanged(session: com.darkssh.client.terminal.emulator.TerminalSession) {
        Timber.d("Terminal title changed")
    }

    override fun onSessionFinished(session: com.darkssh.client.terminal.emulator.TerminalSession) {
        dispatchDisconnect("Session finished")
    }

    override fun onCopyTextToClipboard(
        session: com.darkssh.client.terminal.emulator.TerminalSession,
        text: String,
    ) {
        val preview = if (text.length > 50) text.take(50) + "..." else text
        Timber.d("[OSC52] Copied to clipboard: $preview")
        
        val clip = ClipData.newPlainText("terminal", text)
        clipboardManager.setPrimaryClip(clip)
    }

    override fun onPasteTextFromClipboard(session: com.darkssh.client.terminal.emulator.TerminalSession?) {
        Timber.d("Paste from clipboard requested")
    }

    override fun onBell(session: com.darkssh.client.terminal.emulator.TerminalSession) {
        Timber.d("Bell!")
    }

    override fun onColorsChanged(session: com.darkssh.client.terminal.emulator.TerminalSession) {
        Timber.d("Colors changed")
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        // Cursor visibility changed
    }
    
    override fun onTerminalSizeChanged(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        // Update PTY dimensions when terminal size changes (e.g., font zoom)
        // NOTE: Don't call setDimensions() here - it would cause infinite loop
        // The emulator already resized, just notify SSH transport
        Timber.d("Terminal size changed: ${columns}x${rows} (${cellWidthPixels}x${cellHeightPixels}px)")
        this.columns = columns
        this.rows = rows
        // Only send to SSH transport, don't update emulator (already done)
        transportOperations.trySend(TransportOperation.SetDimensions(columns, rows, cellWidthPixels, cellHeightPixels))
    }

    override fun setTerminalShellPid(
        session: com.darkssh.client.terminal.emulator.TerminalSession,
        pid: Int,
    ) {
        // No shell PID for SSH sessions
    }

    override fun getTerminalCursorStyle(): Int? {
        return null // Use default cursor style
    }

    override fun logError(tag: String, message: String) = Timber.e("$tag: $message")
    override fun logWarn(tag: String, message: String) = Timber.w("$tag: $message")
    override fun logInfo(tag: String, message: String) = Timber.i("$tag: $message")
    override fun logDebug(tag: String, message: String) = Timber.d("$tag: $message")
    override fun logVerbose(tag: String, message: String) = Timber.v("$tag: $message")
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Timber.e(e, "$tag: $message")
    override fun logStackTrace(tag: String, e: Exception) = Timber.e(e, tag)
}
