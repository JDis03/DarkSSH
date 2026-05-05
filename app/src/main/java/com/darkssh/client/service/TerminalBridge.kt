package com.darkssh.client.service

import android.os.Looper
import androidx.compose.ui.graphics.Color
import com.darkssh.client.data.entity.Host
import com.darkssh.client.data.repository.KnownHostRepository
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
import org.connectbot.terminal.ProgressState
import org.connectbot.terminal.TerminalDimensions
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import timber.log.Timber

class TerminalBridge(
    val host: Host,
    private val terminalService: TerminalService,
    private val knownHostRepository: KnownHostRepository,
) {
    // Local echo control
    private var localEchoEnabled = false
    private var lastInputTime = 0L
    private var lastServerResponse = 0L
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
    var terminalEmulator: TerminalEmulator? = null
        private set
    
    // Accumulate terminal output for rendering
    private val terminalOutput = StringBuilder()
    val terminalOutputFlow = MutableStateFlow("")
    
    fun appendTerminalOutput(text: String) {
        // FILTRO PRELIMINAR para evitar acumular códigos problemáticos
        val cleanText = text
            .replace(Regex("\\u001B\\[[0-9;]*[mGKHfJABCDsuhl]"), "") // Códigos ANSI básicos
            .replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"), "") // Caracteres de control (excepto \t, \n, \r)
        
        terminalOutput.append(cleanText)
        
        // Mantener solo las últimas 20 líneas para estabilidad
        val lines = terminalOutput.toString().split('\n')
        if (lines.size > 30) {
            val recentLines = lines.takeLast(20).joinToString("\n")
            terminalOutput.clear()
            terminalOutput.append(recentLines)
        }
        
        terminalOutputFlow.value = terminalOutput.toString()
    }
    
    fun clearTerminalOutput() {
        terminalOutput.clear()
        terminalOutputFlow.value = ""
    }

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

    private val _promptRequest = MutableStateFlow<PromptRequest?>(null)
    val promptRequest: StateFlow<PromptRequest?> = _promptRequest

    private val _disconnectMessage = MutableStateFlow<String?>(null)
    val disconnectMessage: StateFlow<String?> = _disconnectMessage

    private var relay: Relay? = null
    private var pendingPrompt: CompletableDeferred<PromptResponse>? = null

    @Suppress("ktlint:standard:argument-list-wrapping")
    fun createTerminalEmulator() {
        try {
            Timber.d("Creating TerminalEmulator with size ${columns}x${rows}")
            
            val onKeyboardInput: (ByteArray) -> Unit = { data -> 
                Timber.d("⌨️ onKeyboardInput callback triggered! ${data.size} bytes: ${data.joinToString(" ") { "%02x".format(it) }}")
                try {
                    write(data)
                    lastInputTime = System.currentTimeMillis()
                    
                    // LOCAL ECHO: Solo para caracteres imprimibles cuando esté habilitado
                    if (localEchoEnabled) {
                        val dataString = String(data, Charsets.UTF_8)
                        if (dataString.isPrintable()) {
                            terminalEmulator?.writeInput(data)
                            Timber.d("🔄 Local echo: '$dataString'")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to handle keyboard input")
                }
            }
            val onBell: () -> Unit = { Timber.d("Bell!") }
            val onResize: (TerminalDimensions) -> Unit = { dims ->
                try {
                    columns = dims.columns
                    rows = dims.rows
                    transportOperations.trySend(
                        TransportOperation.SetDimensions(dims.columns, dims.rows, 0, 0),
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to handle resize")
                }
            }
            val onTitleChanged: (String) -> Unit = { _ -> }
            val onProgressChanged: (ProgressState, Int) -> Unit = { state, progress -> 
                Timber.d("Terminal progress: state=$state, progress=$progress")
            }

            terminalEmulator = TerminalEmulatorFactory.create(
                initialRows = maxOf(1, rows),     // Ensure positive rows
                initialCols = maxOf(1, columns),  // Ensure positive columns
                defaultForeground = Color.White,
                defaultBackground = Color.Black,
                onKeyboardInput = onKeyboardInput,
                onBell = onBell,
                onResize = onResize,
                onClipboardCopy = { text ->
                    // OSC 52 clipboard support
                    Timber.d("Clipboard copy: ${text.length} chars")
                },
                onProgressChange = onProgressChanged,
            )
            
            // Observe terminal snapshot for UI rendering
            // Note: snapshot is internal, so we need to use reflection or modify termlib
            // For now, we'll rely on the relay data flow
            Timber.d("TerminalEmulator created successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to create TerminalEmulator")
            throw e
        }
    }

    fun startConnection() {
        // Create TerminalEmulator on Main thread (required for Looper.getMainLooper())
        bridgeScope.launch(Dispatchers.Main) {
            try {
                createTerminalEmulator()
                Timber.d("TerminalEmulator created successfully")
                
                // Now start connection on IO thread
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
                Timber.e(e, "Failed to create TerminalEmulator")
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
                            android.util.Log.d("TerminalBridge", "📤 Processor: writing ${bytes.size} bytes: '${String(bytes, Charsets.UTF_8)}' hex=${bytes.joinToString(" ") { "%02x".format(it) }}")
                            val t = transport ?: continue
                            android.util.Log.d("TerminalBridge", "📤 Transport: connected=${t.isConnected()}, sessionOpen=${t.isSessionOpen()}")
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
            val dataStr = String(data, Charsets.UTF_8)
            Timber.d("🔄 Relay data received: ${data.size} bytes: '${dataStr.replace("\n", "\\n").replace("\r", "\\r")}'")
            
            lastServerResponse = System.currentTimeMillis()
            
            // Auto-disable local echo if server is responding quickly
            if (lastServerResponse - lastInputTime < 100) {
                if (localEchoEnabled) {
                    localEchoEnabled = false
                    Timber.d("🔇 Local echo disabled - server is responding")
                }
            }
            
            // Feed data to BOTH terminal emulator AND keep raw output for fallback rendering
            terminalEmulator?.let { emulator ->
                emulator.writeInput(data)
                Timber.d("✅ Data written to terminal emulator")
            } ?: run {
                Timber.w("❌ TerminalEmulator is null, dropping ${data.size} bytes")
            }
            
            // Solo acumular output si no hay emulador (fallback extremo)
            if (terminalEmulator == null) {
                appendTerminalOutput(dataStr)
            }
            // Con emulador funcionando, NO necesitamos acumular texto crudo
        } catch (e: Exception) {
            Timber.e(e, "💥 Failed to process ${data.size} bytes")
        }
    }

    fun dispatchDisconnect(message: String? = null) {
        _disconnectMessage.value = message
        _isConnected.value = false
        _isDisconnected.value = true
        relay?.stop()
        relay = null

        cancelPendingPrompt()
        Timber.w("Disconnected from ${host.nickname}: $message")
        terminalService.onBridgeDisconnected(this, DisconnectReason.UNKNOWN)
    }

    fun close() {
        relay?.stop()
        relay = null
        transportOperations.close()
        transport?.close()
        transport = null
        bridgeJob.cancel()
        cancelPendingPrompt()
        _isConnected.value = false
        _isDisconnected.value = true
    }

    fun setDimensions(cols: Int, newRows: Int, width: Int = 0, height: Int = 0) {
        columns = cols
        rows = newRows
        transportOperations.trySend(TransportOperation.SetDimensions(cols, newRows, width, height))
    }

    fun write(data: ByteArray) {
        transportOperations.trySend(TransportOperation.WriteData(data))
    }
    
    // Control functions for local echo
    fun enableLocalEcho() {
        localEchoEnabled = true
        Timber.d("🔊 Local echo enabled")
    }
    
    fun disableLocalEcho() {
        localEchoEnabled = false
        Timber.d("🔇 Local echo disabled")
    }
    
    fun toggleLocalEcho(): Boolean {
        localEchoEnabled = !localEchoEnabled
        Timber.d("🎚️ Local echo toggled: $localEchoEnabled")
        return localEchoEnabled
    }

    private fun flush() {
        transportOperations.trySend(TransportOperation.Flush)
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
        return runBlocking(Dispatchers.Main) {
            promptForPassword()
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
        return runBlocking(Dispatchers.Main) {
            promptForInput(prompt, echo)
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
        return runBlocking(Dispatchers.Main) {
            promptForHostKeyVerification(hostname, port, fingerprints)
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
    
    // Helper function for local echo
    private fun String.isPrintable(): Boolean {
        return this.all { char -> 
            char.isLetterOrDigit() || char in " !\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
        }
    }
}