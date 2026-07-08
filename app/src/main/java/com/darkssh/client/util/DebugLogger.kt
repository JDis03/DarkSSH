package com.darkssh.client.util

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Debug logger that writes to files (like DarkKeyboard)
 * Logs are saved to {cacheDir}/logs/darkssh_*.log
 */
class FileLoggingTree(private val context: Context) : Timber.Tree() {

    companion object {
        private const val TAG = "FileLoggingTree"
        private const val MAX_LOG_FILES = 5
        private const val MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024 // 5MB
        private const val LOG_PREFIX = "darkssh_"
        private const val LOG_SUFFIX = ".log"
    }

    private val logDir: File = File(context.cacheDir, "logs").apply {
        if (!exists()) mkdirs()
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private var currentLogFile: File? = null
    private var fileWriter: FileWriter? = null

    init {
        initLogFile()
        cleanOldLogs()
        android.util.Log.i(TAG, "Initialized. Log dir: ${logDir.absolutePath}")
    }

    private fun initLogFile() {
        try {
            val timestamp = fileNameFormat.format(Date())
            currentLogFile = File(logDir, "${LOG_PREFIX}${timestamp}${LOG_SUFFIX}")
            fileWriter = FileWriter(currentLogFile, true)

            // Write header
            fileWriter?.apply {
                write("=".repeat(80) + "\n")
                write("DarkSSH Log Session Started\n")
                write("Timestamp: ${dateFormat.format(Date())}\n")
                write("Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
                write("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
                write("=".repeat(80) + "\n\n")
                flush()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun cleanOldLogs() {
        try {
            val logFiles = logDir.listFiles { file ->
                file.name.startsWith(LOG_PREFIX) && file.name.endsWith(LOG_SUFFIX)
            }?.sortedByDescending { it.lastModified() } ?: return

            logFiles.drop(MAX_LOG_FILES).forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun rotateIfNeeded() {
        currentLogFile?.let { file ->
            if (file.length() > MAX_FILE_SIZE_BYTES) {
                try {
                    fileWriter?.close()
                    initLogFile()
                    cleanOldLogs()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            rotateIfNeeded()

            val timestamp = dateFormat.format(Date())
            val level = when (priority) {
                android.util.Log.VERBOSE -> "V"
                android.util.Log.DEBUG -> "D"
                android.util.Log.INFO -> "I"
                android.util.Log.WARN -> "W"
                android.util.Log.ERROR -> "E"
                android.util.Log.ASSERT -> "A"
                else -> "?"
            }

            val logLine = StringBuilder()
            logLine.append("[$timestamp] ")
            logLine.append("$level/")
            logLine.append(tag ?: "DarkSSH")
            logLine.append(": ")
            logLine.append(message)
            logLine.append("\n")

            t?.let {
                logLine.append("Exception: ${it.javaClass.simpleName}: ${it.message}\n")
                logLine.append(it.stackTraceToString())
                logLine.append("\n")
            }

            synchronized(this) {
                fileWriter?.apply {
                    write(logLine.toString())
                    flush()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun close() {
        try {
            fileWriter?.apply {
                write("\n" + "=".repeat(80) + "\n")
                write("Log Session Ended: ${dateFormat.format(Date())}\n")
                write("=".repeat(80) + "\n")
                flush()
                close()
            }
            fileWriter = null
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getLogFiles(): List<File> {
        return logDir.listFiles { file ->
            file.name.startsWith(LOG_PREFIX) && file.name.endsWith(LOG_SUFFIX)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun getCurrentLogFile(): File? = currentLogFile

    fun clearAllLogs() {
        try {
            fileWriter?.close()
            logDir.listFiles()?.forEach { it.delete() }
            initLogFile()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// Helper object for easy logging with DarkKeyboard-style formatting
object DebugLogger {
    private const val APP_TAG = "DarkSSH"
    
    fun d(component: String, message: String) {
        Timber.tag("$APP_TAG/$component").d(message)
    }
    
    fun i(component: String, message: String) {
        Timber.tag("$APP_TAG/$component").i(message)
    }
    
    fun w(component: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Timber.tag("$APP_TAG/$component").w(throwable, message)
        } else {
            Timber.tag("$APP_TAG/$component").w(message)
        }
    }
    
    fun e(component: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Timber.tag("$APP_TAG/$component").e(throwable, message)
        } else {
            Timber.tag("$APP_TAG/$component").e(message)
        }
    }
    
    fun v(component: String, message: String) {
        Timber.tag("$APP_TAG/$component").v(message)
    }
    
    // Specialized loggers for common components
    object Tab {
        fun created(tabId: String, hostId: Long, type: String) {
            d("TabManager", "Tab created: id=$tabId, host=$hostId, type=$type")
        }

        fun closed(tabId: String) {
            d("TabManager", "Tab closed: id=$tabId")
        }

        fun switched(fromIndex: Int, toIndex: Int) {
            d("TabManager", "Tab switched: $fromIndex -> $toIndex")
        }

        fun updated(count: Int, hostId: Long) {
            d("TabManager", "Updated $count tab(s) for host $hostId (host renamed)")
        }
    }
    
    object Bridge {
        fun created(tabId: String?, hostId: Long) {
            d("TerminalBridge", "Bridge created: tabId=$tabId, host=$hostId")
        }
        
        fun connected(host: String) {
            i("TerminalBridge", "Connected to $host")
        }
        
        fun disconnected(host: String, reason: String) {
            w("TerminalBridge", "Disconnected from $host: $reason")
        }
        
        fun error(host: String, error: String, throwable: Throwable? = null) {
            e("TerminalBridge", "Error on $host: $error", throwable)
        }
    }
    
    object UI {
        fun volumeZoom(direction: String, fontSize: Float) {
            v("UI/Zoom", "Volume $direction: fontSize=$fontSize")
        }
        
        fun tabVisible(tabId: String, isVisible: Boolean) {
            v("UI/Tab", "Tab $tabId visibility: $isVisible")
        }
    }
}
