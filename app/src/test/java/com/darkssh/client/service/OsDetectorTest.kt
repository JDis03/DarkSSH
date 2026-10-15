package com.darkssh.client.service

import android.app.Application
import com.darkssh.client.data.model.OsType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.SshSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class OsDetectorTest {
    @Test
    fun `exec output is parsed and detection channel closed`() = runBlocking {
        val client = mock<SshClient>()
        val session = mock<SshSession>()
        val stdout = Channel<ByteArray>(1)
        val stderr = Channel<ByteArray>(1)
        stdout.trySend("Linux\nNAME=Ubuntu\nID=ubuntu\n".toByteArray())
        stderr.trySend("nonfatal release file warning".toByteArray())
        stdout.close()
        stderr.close()
        whenever(client.openSession()).thenReturn(session)
        whenever(session.requestExec(any())).thenReturn(true)
        whenever(session.stdout).thenReturn(stdout)
        whenever(session.stderr).thenReturn(stderr)
        assertEquals(OsType.UBUNTU, OsDetector.detectOs(client))
        verify(session).close()
    }

    @Test(timeout = 5000)
    fun `closing bridge cancellation stops detection and closes its channel`() = runBlocking {
        val client = mock<SshClient>()
        val session = mock<SshSession>()
        val started = CompletableDeferred<Unit>()
        whenever(client.openSession()).thenReturn(session)
        whenever(session.stdout).thenReturn(Channel())
        whenever(session.stderr).thenReturn(Channel())
        doAnswer {
            started.complete(Unit)
            true
        }.whenever(session).requestExec(any())
        val detection = async { OsDetector.detectOs(client) }
        withTimeout(2000) { started.await() }
        detection.cancel()
        detection.join()
        assertTrue(detection.isCancelled)
        verify(session).close()
    }
}
