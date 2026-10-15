package com.darkssh.client.service

import android.util.Log
import com.darkssh.client.data.model.OsType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.sshlib.SshClient
import java.io.ByteArrayOutputStream
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
        private const val CLEANUP_TIMEOUT_MS = 1000L

        // Retain the stable Trilead path unchanged during the opt-in migration.
        suspend fun detectOs(connection: com.trilead.ssh2.Connection): OsType =
            withContext(Dispatchers.IO) {
                return@withContext withTimeoutOrNull(DETECTION_TIMEOUT_MS) {
                    try {
                        val session = connection.openSession()
                        session.use {
                            session.execCommand(OS_DETECT_SCRIPT)
                            val output = session.stdout.bufferedReader().use { it.readText() }
                            val stderr = session.stderr.bufferedReader().use { it.readText() }
                            val exitCode = session.exitStatus ?: -1
                            Log.d(TAG, "OS detection script output (exitCode=$exitCode):\n$output")
                            if (stderr.isNotBlank()) Log.d(TAG, "OS detection stderr:\n$stderr")
                            if (exitCode == 0 || output.isNotBlank()) OsType.parse(output) else OsType.UNKNOWN
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "IO error during OS detection", e)
                        OsType.UNKNOWN
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to detect OS", e)
                        OsType.UNKNOWN
                    }
                } ?: OsType.UNKNOWN
            }

        // Detect OS type from a connected SSH client.
        // Executes a shell script that reads uname and /etc/*release.
        // Returns Detected OsType, or UNKNOWN if detection fails/times out
        suspend fun detectOs(connection: SshClient): OsType =
            withContext(Dispatchers.IO) {
                return@withContext withTimeoutOrNull(DETECTION_TIMEOUT_MS) {
                    try {
                        val session = connection.openSession()
                        try {
                            // openSession may return null after catching cancellation internally.
                            currentCoroutineContext().ensureActive()
                            if (session == null) {
                                throw IOException("Failed to open OS detection session")
                            }

                            coroutineScope {
                                // Drain both channels while exec runs to avoid backpressure deadlocks.
                                val stdout = async { readOutput(session.stdout) }
                                val stderrOutput = async { readOutput(session.stderr) }
                                if (!session.requestExec(OS_DETECT_SCRIPT)) {
                                    throw IOException("OS detection exec request was rejected")
                                }

                                val output = stdout.await()
                                val stderr = stderrOutput.await()
                                Log.d(TAG, "OS detection script output:\n$output")
                                if (stderr.isNotBlank()) {
                                    Log.d(TAG, "OS detection stderr:\n$stderr")
                                }

                                // As before, useful stdout is enough to identify the OS even
                                // if /etc/*release included an unreadable file or no exit status arrived.
                                if (output.isNotBlank()) {
                                    val osType = OsType.parse(output)
                                    Log.i(TAG, "Detected OS: ${osType.displayName}")
                                    osType
                                } else {
                                    Log.w(TAG, "OS detection script returned no output")
                                    OsType.UNKNOWN
                                }
                            }
                        } finally {
                            if (session != null) {
                                withContext(NonCancellable) {
                                    withTimeoutOrNull(CLEANUP_TIMEOUT_MS) {
                                        session.close()
                                    }
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
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

        private suspend fun readOutput(channel: ReceiveChannel<ByteArray>): String {
            val output = ByteArrayOutputStream()
            for (chunk in channel) {
                output.write(chunk)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }
}
