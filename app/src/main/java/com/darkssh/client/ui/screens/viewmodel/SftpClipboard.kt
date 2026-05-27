package com.darkssh.client.ui.screens.viewmodel

import com.darkssh.client.transport.SftpEntry

/**
 * Internal clipboard for SFTP file operations (copy/cut/paste)
 * Similar to File Manager+ implementation
 */
object SftpClipboard {
    enum class Operation {
        COPY, CUT
    }

    data class ClipboardData(
        val files: List<SftpEntry>,
        val operation: Operation,
        val hostId: Long,
        val sourcePath: String
    )

    private var data: ClipboardData? = null

    fun copy(files: List<SftpEntry>, hostId: Long, sourcePath: String) {
        data = ClipboardData(files, Operation.COPY, hostId, sourcePath)
    }

    fun cut(files: List<SftpEntry>, hostId: Long, sourcePath: String) {
        data = ClipboardData(files, Operation.CUT, hostId, sourcePath)
    }

    fun paste(): ClipboardData? = data

    fun clear() {
        data = null
    }

    fun hasData(): Boolean = data != null

    fun getOperation(): Operation? = data?.operation

    fun getFileCount(): Int = data?.files?.size ?: 0
}
