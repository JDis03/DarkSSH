/*
 * DarkSSH SFTP - JUnit4 @Category marker for Docker-based integration tests
 *
 * Tests annotated with @Category(DockerIntegrationTest::class) spin up a
 * real SSH server via Testcontainers and require a running Docker daemon.
 * They are excluded from the regular unit test tasks (see app/build.gradle.kts)
 * and only run via the dedicated `./gradlew integrationTest` task.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.darkssh.client.transport.cbssh

/**
 * Marker interface (no members) used purely as a JUnit4 `@Category` tag.
 *
 * Usage:
 * ```kotlin
 * @Category(DockerIntegrationTest::class)
 * class MyIntegrationTest { ... }
 * ```
 */
interface DockerIntegrationTest
