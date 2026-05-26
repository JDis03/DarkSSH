package com.darkssh.client.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Simple HTTP health check server for monitoring SFTP server status.
 * 
 * Provides GET /health endpoint returning JSON with server status.
 * Used by DarkDev Server to verify SSH server is reachable before deploying.
 */
class HealthCheckServer(
    private val sftpManager: SftpServerManager
) {
    
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val startTime = System.currentTimeMillis()
    
    companion object {
        private const val DEFAULT_PORT = 8222  // Changed to avoid collision with SFTP on 2222
        private const val HEALTH_PATH = "/health"
    }
    
    /**
     * Starts the health check HTTP server.
     * 
     * @param port Server port (default: 2222)
     */
    suspend fun start(port: Int = DEFAULT_PORT) = withContext(Dispatchers.IO) {
        if (isRunning) {
            Timber.w("Health check server already running")
            return@withContext
        }
        
        try {
            Timber.i("Starting health check server on port $port...")
            
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
            
            isRunning = true
            Timber.i("✓ Health check server started on port $port")
            
            // Accept connections in background
            acceptConnections()
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to start health check server")
            isRunning = false
            throw e
        }
    }
    
    /**
     * Stops the health check server.
     */
    suspend fun stop() = withContext(Dispatchers.IO) {
        if (!isRunning) {
            return@withContext
        }
        
        try {
            Timber.i("Stopping health check server...")
            isRunning = false
            serverSocket?.close()
            serverSocket = null
            Timber.i("✓ Health check server stopped")
        } catch (e: Exception) {
            Timber.w(e, "Error stopping health check server")
        }
    }
    
    /**
     * Accepts incoming HTTP connections and responds to health checks.
     */
    private suspend fun acceptConnections() = withContext(Dispatchers.IO) {
        while (isRunning) {
            try {
                serverSocket?.accept()?.let { client ->
                    handleClient(client)
                }
            } catch (e: IOException) {
                if (isRunning) {
                    Timber.w(e, "Error accepting connection")
                }
            }
        }
    }
    
    /**
     * Handles a single HTTP client request.
     */
    private fun handleClient(client: Socket) {
        try {
            client.use { socket ->
                val input = socket.getInputStream().bufferedReader()
                val output = socket.getOutputStream()
                
                // Read request line
                val requestLine = input.readLine() ?: return
                Timber.v("Health check request: $requestLine")
                
                // Parse request
                val parts = requestLine.split(" ")
                if (parts.size >= 2) {
                    val method = parts[0]
                    val path = parts[1]
                    
                    when {
                        method == "GET" && path.startsWith(HEALTH_PATH) -> {
                            respondHealth(output)
                        }
                        else -> {
                            respond404(output)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error handling health check request")
        }
    }
    
    /**
     * Responds with health check JSON.
     */
    private fun respondHealth(output: OutputStream) {
        val json = JSONObject().apply {
            put("status", "ok")
            put("sftp_port", 22)
            put("sftp_active", sftpManager.isRunning())
            put("uptime_ms", System.currentTimeMillis() - startTime)
            put("timestamp", System.currentTimeMillis())
        }
        
        val body = json.toString()
        val response = buildString {
            appendLine("HTTP/1.1 200 OK")
            appendLine("Content-Type: application/json")
            appendLine("Content-Length: ${body.length}")
            appendLine("Connection: close")
            appendLine()
            append(body)
        }
        
        output.write(response.toByteArray())
        output.flush()
    }
    
    /**
     * Responds with 404 Not Found.
     */
    private fun respond404(output: OutputStream) {
        val body = "Not Found"
        val response = buildString {
            appendLine("HTTP/1.1 404 Not Found")
            appendLine("Content-Type: text/plain")
            appendLine("Content-Length: ${body.length}")
            appendLine("Connection: close")
            appendLine()
            append(body)
        }
        
        output.write(response.toByteArray())
        output.flush()
    }
    
    /**
     * Returns true if server is running.
     */
    fun isRunning(): Boolean = isRunning
}
