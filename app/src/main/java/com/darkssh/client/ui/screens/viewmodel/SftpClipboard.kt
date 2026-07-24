package com.darkssh.client.ui.screens.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.darkssh.client.transport.SftpEntry

/**
 * Internal clipboard for SFTP file operations (copy/cut/paste)
 * Similar to File Manager+ implementation
 */
object SftpClipboard {
    enum class Operation {
        COPY,
        CUT,
    }

    data class ClipboardData(
        val files: List<SftpEntry>,
        val operation: Operation,
        val hostId: Long,
        val sourcePath: String,
    )

    // Backed by Compose's snapshot state (not a plain var) so that any @Composable
    // reading hasData()/getOperation()/getFileCount() (via SftpViewModel's passthrough
    // functions) is automatically recomposed when copy()/cut()/clear() mutate it.
    // Previously this was `private var data: ClipboardData? = null`, a plain var that
    // Compose has no way to observe — tapping Copy/Cut updated this object correctly,
    // but the Paste button's `if (viewModel.hasClipboardData())` in SftpScreen never
    // recomposed to reflect it, making Copy/Cut appear completely broken until some
    // unrelated recomposition happened to occur (e.g. navigating to another folder).
    private var data: ClipboardData? by mutableStateOf(null)

    fun copy(
        files: List<SftpEntry>,
        hostId: Long,
        sourcePath: String,
    ) {
        data = ClipboardData(files, Operation.COPY, hostId, sourcePath)
    }

    fun cut(
        files: List<SftpEntry>,
        hostId: Long,
        sourcePath: String,
    ) {
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
