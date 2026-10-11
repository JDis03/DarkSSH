package com.darkssh.client.util

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Test

class FileLoggingPolicyTest {
    @Test
    fun `debug builds persist debug and above but not verbose`() {
        val min = FileLoggingTree.minFilePriority(isDebugBuild = true)
        assertEquals(Log.DEBUG, min)
        assert(Log.VERBOSE < min)
    }

    @Test
    fun `release builds persist only warnings and errors`() {
        val min = FileLoggingTree.minFilePriority(isDebugBuild = false)
        assertEquals(Log.WARN, min)
        assert(Log.INFO < min)
        assert(Log.ERROR >= min)
    }
}
