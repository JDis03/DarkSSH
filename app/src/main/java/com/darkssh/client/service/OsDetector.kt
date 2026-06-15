package com.darkssh.client.service

import android.util.Log
import com.darkssh.client.data.model.OsType
import com.trilead.ssh2.Connection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * Detects the operating system of a remote SSH server.
 * Based on Termius OS detection implementation.
 */
class OsDetector {

    companion object {
        private const val TAG = "OsDetector"
        
        // Detection script from Termius (OsDetectExecCommand.java)
        // Detects OS by reading uname and /etc/*release
        private const val OS_DETECT_SCRIPT = 
            "sh -c 'HISTFILE=;SA_OS_TYPE=\"Linux\"\n" +
            "REAL_OS_NAME=`uname`\n" +
            "if [ \"\$REAL_OS_NAME\" != \"\$SA_OS_TYPE\" ] ;\n" +
            "then\n" +
            "\techo `uname` \n" +
            "else\n" +
            "DISTRIB_ID=\"`cat /etc/*release`\"\n" +
            "\techo \$REAL_OS_NAME;\n" +
            "\techo \$DISTRIB_ID;\n" +
            "fi;\n" +
            "exit;'"

        private const val DETECTION_TIMEOUT_MS = 5000L

        // Detect OS type from connected SSH client (Trilead SSH).
        // Executes a shell script that reads uname and /etc/*release.
        // Returns Detected OsType, or UNKNOWN if detection fails/times out
        suspend fun detectOs(connection: Connection): OsType = withContext(Dispatchers.IO) {
            return@withContext withTimeoutOrNull(DETECTION_TIMEOUT_MS) {
                try {
                    val session = connection.openSession()
                    session.use {
                        // Execute the detection script
                        session.execCommand(OS_DETECT_SCRIPT)
                        
                        // Read output
                        val output = session.stdout.bufferedReader().use { it.readText() }
                        val stderr = session.stderr.bufferedReader().use { it.readText() }
                        
                        // Wait for exit status
                        val exitCode = session.exitStatus ?: -1
                        
                        Log.d(TAG, "OS detection script output (exitCode=$exitCode):\n$output")
                        if (stderr.isNotBlank()) {
                            Log.d(TAG, "OS detection stderr:\n$stderr")
                        }
                        
                        if (exitCode == 0 || output.isNotBlank()) {
                            val osType = OsType.parse(output)
                            Log.i(TAG, "Detected OS: ${osType.displayName}")
                            osType
                        } else {
                            Log.w(TAG, "OS detection script failed with exit code $exitCode")
                            OsType.UNKNOWN
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "IO error during OS detection", e)
                    OsType.UNKNOWN
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to detect OS", e)
                    OsType.UNKNOWN
                }
            } ?: run {
                Log.w(TAG, "OS detection timed out after ${DETECTION_TIMEOUT_MS}ms")
                OsType.UNKNOWN
            }
        }
    }
}
