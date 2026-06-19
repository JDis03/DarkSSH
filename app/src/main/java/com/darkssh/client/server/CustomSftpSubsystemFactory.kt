package com.darkssh.client.server

import android.content.Context
import org.apache.sshd.common.channel.Channel
import org.apache.sshd.server.command.Command
import org.apache.sshd.sftp.server.SftpEventListener
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import timber.log.Timber
import java.nio.file.CopyOption
import java.nio.file.Path

/**
 * Custom SFTP subsystem factory that listens for file uploads and broadcasts events.
 */
class CustomSftpSubsystemFactory(
    private val context: Context,
) : SftpSubsystemFactory() {
    init {
        // Add event listener for upload detection
        addSftpEventListener(
            object : SftpEventListener {
                override fun written(
                    session: org.apache.sshd.server.session.ServerSession?,
                    remoteHandle: String?,
                    localHandle: org.apache.sshd.sftp.server.FileHandle?,
                    offset: Long,
                    data: ByteArray?,
                    dataOffset: Int,
                    dataLen: Int,
                    thrown: Throwable?,
                ) {
                    // File write operation - track for completion
                    if (thrown == null && localHandle != null) {
                        Timber.v("Write: offset=$offset len=$dataLen")
                    }
                }

                override fun closed(
                    session: org.apache.sshd.server.session.ServerSession?,
                    remoteHandle: String?,
                    handle: org.apache.sshd.sftp.server.Handle?,
                    thrown: Throwable?,
                ) {
                    // File closed after upload - broadcast completion
                    if (thrown == null && handle is org.apache.sshd.sftp.server.FileHandle && remoteHandle != null) {
                        try {
                            // Get file path from handle - handle stores the Path
                            val filePath = handle.toString() // Path representation

                            Timber.i("✓ Upload complete: $filePath")

                            // Try to get actual File to check existence and size
                            try {
                                val javaFile = java.io.File(filePath)
                                if (javaFile.exists()) {
                                    val size = javaFile.length()

                                    // Broadcast to DarkADB for instant APK detection
                                    val manager = SftpServerManager(context.applicationContext)
                                    manager.broadcastUploadComplete(javaFile.absolutePath, size)
                                }
                            } catch (e: Exception) {
                                // Fallback: broadcast with unknown size
                                val manager = SftpServerManager(context.applicationContext)
                                manager.broadcastUploadComplete(filePath, -1)
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Error broadcasting upload completion")
                        }
                    }
                }

                override fun creating(
                    session: org.apache.sshd.server.session.ServerSession?,
                    path: Path?,
                    attrs: MutableMap<String, *>?,
                ) {
                    Timber.v("Creating: $path")
                }

                override fun created(
                    session: org.apache.sshd.server.session.ServerSession?,
                    path: Path?,
                    attrs: MutableMap<String, *>?,
                    thrown: Throwable?,
                ) {
                    if (thrown == null) {
                        Timber.v("Created: $path")
                    }
                }

                override fun moving(
                    session: org.apache.sshd.server.session.ServerSession?,
                    srcPath: Path?,
                    dstPath: Path?,
                    opts: MutableCollection<CopyOption>?,
                ) {
                    Timber.v("Moving: $srcPath -> $dstPath")
                }

                override fun moved(
                    session: org.apache.sshd.server.session.ServerSession?,
                    srcPath: Path?,
                    dstPath: Path?,
                    opts: MutableCollection<CopyOption>?,
                    thrown: Throwable?,
                ) {
                    if (thrown == null) {
                        Timber.v("Moved: $srcPath -> $dstPath")
                    }
                }
            },
        )
    }

    // Note: createSubsystem signature varies by Apache MINA SSHD version
    // Removing override to use parent implementation
}
