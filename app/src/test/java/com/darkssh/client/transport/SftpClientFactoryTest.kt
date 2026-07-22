/*
 * DarkSSH SFTP Client - cbssh Migration
 * Tests for SftpClientFactory backend selection.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport

import androidx.test.core.app.ApplicationProvider
import com.darkssh.client.data.entity.Host
import com.darkssh.client.transport.cbssh.SftpClient2
import com.darkssh.client.util.AppPreferences
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [SftpClientFactory.create].
 *
 * Verifies the factory returns the correct backend (sshj legacy vs cbssh new)
 * based on the `useCbsshSftp` preference. Uses Robolectric because AppPreferences
 * requires Android Context for SharedPreferences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SftpClientFactoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val testHost = Host(
        id = 1L,
        nickname = "test",
        hostname = "example.com",
        port = 22,
        username = "user",
    )

    @Before
    fun setup() {
        // Reset preference before each test
        AppPreferences.setUseCbsshSftp(context, false)
    }

    @After
    fun teardown() {
        // Clean up so we don't leak state to other tests
        AppPreferences.setUseCbsshSftp(context, false)
    }

    @Test
    fun `factory returns sshj SftpClient when useCbsshSftp is false`() {
        AppPreferences.setUseCbsshSftp(context, false)

        val client: ISftpClient = SftpClientFactory.create(testHost, context)

        assertNotNull(client)
        assertTrue(
            "Expected sshj SftpClient (legacy) when useCbsshSftp=false, got: ${client::class.simpleName}",
            client is SftpClient,
        )
    }

    @Test
    fun `factory returns cbssh SftpClient2 when useCbsshSftp is true`() {
        AppPreferences.setUseCbsshSftp(context, true)

        val client: ISftpClient = SftpClientFactory.create(testHost, context)

        assertNotNull(client)
        assertTrue(
            "Expected cbssh SftpClient2 when useCbsshSftp=true, got: ${client::class.simpleName}",
            client is SftpClient2,
        )
    }

    @Test
    fun `factory result is always an ISftpClient`() {
        // Sanity: regardless of flag, the contract holds.
        AppPreferences.setUseCbsshSftp(context, false)
        val legacy: ISftpClient = SftpClientFactory.create(testHost, context)
        assertTrue(legacy is ISftpClient)

        AppPreferences.setUseCbsshSftp(context, true)
        val modern: ISftpClient = SftpClientFactory.create(testHost, context)
        assertTrue(modern is ISftpClient)
    }
}
