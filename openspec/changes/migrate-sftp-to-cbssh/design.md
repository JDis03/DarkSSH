# Design: Migrate SFTP Client from sshj to cbssh

## Architecture Overview

### Before Migration

```
app/src/main/java/com/darkssh/client/transport/
└── SftpClient.kt (360 LOC) - sshj/Hierynomus
    ├── data class SftpEntry
    ├── data class TransferProgress
    ├── sealed class SftpAuthState
    └── class SftpClient
        ├── connectWithPassword()
        ├── connectWithKey()
        ├── ls(), stat(), mkdir(), rmdir(), rm(), rename()
        ├── downloadToStream() - uses sshj TransferListener
        ├── downloadFile() - uses sshj TransferListener
        ├── uploadViaSsh() - uses SCP fallback
        ├── uploadFile() - uses sshj FileTransfer
        └── copyFile() - uses sshj RemoteFile streams

Dependencies:
- sshj 0.38.0 (~1.5MB APK contribution)
- sshlib 2.2.47-SNAPSHOT (terminal SSH, unchanged)
- Apache SSHD 2.14.0 (SFTP server, unchanged)
```

### After Migration

```
app/src/main/java/com/darkssh/client/transport/
├── SftpClient.kt (kept as sshj version for fallback during rollout)
├── SftpClient2.kt (NEW) - cbssh-based drop-in replacement
└── cbssh/
    ├── CbsshTransfer.kt (NEW) - high-level wrapper
    └── ScpFallback.kt (NEW) - SCP-style upload fallback

Dependencies:
- cbssh 0.1.0-SNAPSHOT (replaces sshj)
- sshlib 2.2.47-SNAPSHOT (terminal SSH, unchanged)
- Apache SSHD 2.14.0 (SFTP server, unchanged)
```

### Migration Path

```
Phase 1: Wrapper Layer (no user-visible change)
  ↓
Phase 2: Drop-in SftpClient2 (available, not active)
  ↓
Phase 3: Feature Flag (user can toggle in settings)
  ↓
Phase 4: Default to cbssh (gradual rollout)
  ↓
Phase 5: Remove sshj version (cleanup)
  ↓
Phase 6: Remove sshj dependency (APK reduction)
```

---

## Implementation Details

### 1. CbsshTransfer.kt - High-Level Wrapper

**Purpose:** Provide download/upload/copy operations with progress tracking on top of cbssh's low-level SFTP API.

**File:** `app/src/main/java/com/darkssh/client/transport/cbssh/CbsshTransfer.kt`

**Key methods:**

```kotlin
class CbsshTransfer(private val sftp: SftpClient) {
    suspend fun download(
        remotePath: String,
        localFile: File,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): SftpResult<Unit>
    
    suspend fun downloadToStream(
        remotePath: String,
        outputStream: OutputStream,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): SftpResult<Unit>
    
    suspend fun upload(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): SftpResult<Unit>
    
    suspend fun uploadParallel(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
        chunkSize: Long = 10L * 1024 * 1024,
        parallelChunks: Int = 4,
    ): SftpResult<Unit>
    
    suspend fun copy(
        sourcePath: String,
        destPath: String,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): SftpResult<Unit>
}
```

**Design decisions:**
- **Buffer size: 32KB** - Good balance between throughput and memory
- **Download throttling: 100KB intervals** - Matches sshj behavior
- **Upload throttling: 256KB intervals** - Matches sshj behavior (less frequent)
- **Server-side copy buffer: 128KB** - Larger for efficiency
- **Parallel chunks: 4 default** - Matches sshj config

### 2. SftpClient2.kt - Drop-in Replacement

**Purpose:** Same public API as current SftpClient.kt but uses cbssh internally.

**File:** `app/src/main/java/com/darkssh/client/transport/SftpClient2.kt`

**Public API (identical to SftpClient.kt):**

```kotlin
class SftpClient2(
    private val host: Host,
    private val knownHostRepository: KnownHostRepository,
    private val clipboardManager: ClipboardManager,
) {
    suspend fun connectWithPassword(password: String): Result<Unit>
    suspend fun connectWithKey(keyPair: KeyPair): Result<Unit>
    suspend fun disconnect()
    fun setDisconnected()
    suspend fun pwd(): String
    suspend fun ls(path: String): Result<List<SftpEntry>>
    suspend fun downloadToStream(...)
    suspend fun downloadFile(...)
    suspend fun uploadViaSsh(...)
    suspend fun uploadFile(...)
    suspend fun uploadFileParallel(...)
    suspend fun mkdir(path: String): Result<Unit>
    suspend fun rm(path: String): Result<Unit>
    suspend fun rmdir(path: String): Result<Unit>
    suspend fun rename(oldPath: String, newPath: String): Result<Unit>
    suspend fun exists(path: String): Boolean
    suspend fun stat(path: String): Result<SftpEntry?>
    private suspend fun executeCommand(command: String): Result<Pair<String, Int>>
    suspend fun copyFileViaSsh(...)
    suspend fun copyFile(...)
    suspend fun moveFile(...)
}
```

**Internal implementation:**

```kotlin
class SftpClient2(...) {
    private var sshClient: SshClient? = null
    private var sftpClient: SftpClient? = null
    private var transfer: CbsshTransfer? = null
    
    suspend fun connectWithPassword(password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = SshClient(SshClientConfig().apply {
                // Window size, keepalive, etc.
            })
            client.connect(host.hostname, if (host.port <= 0) 22 else host.port)
            client.authenticatePassword(host.username, password)
            sshClient = client
            
            val sftp = client.openSftp().getOrElse { 
                return@withContext Result.failure(it.toException())
            }
            sftpClient = sftp
            transfer = CbsshTransfer(sftp)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun ls(path: String): Result<List<SftpEntry>> = withContext(Dispatchers.IO) {
        val sftp = sftpClient ?: return@withContext Result.failure(IllegalStateException("Not connected"))
        when (val result = sftp.listdir(path)) {
            is SftpResult.Success -> {
                val entries = result.value.mapNotNull { entry ->
                    if (entry.filename == "." || entry.filename == "..") null
                    else SftpEntry(
                        name = entry.filename,
                        path = if (path.endsWith("/")) "$path${entry.filename}" else "$path/${entry.filename}",
                        isDirectory = entry.attrs.permissions != null && entry.attrs.isDirectory,
                        isSymlink = false,  // cbssh doesn't expose this in listdir
                        size = entry.attrs.size ?: 0,
                        permissions = entry.attrs.permissions?.toString(),
                        modifiedTime = entry.attrs.mtime ?: 0,
                    )
                }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                Result.success(entries)
            }
            else -> Result.failure(result.toException())
        }
    }
    
    suspend fun downloadFile(
        remotePath: String,
        localFile: File,
        onProgress: ((TransferProgress) -> Unit)? = null,
    ): Result<Unit> {
        val sftp = sftpClient ?: return Result.failure(IllegalStateException("Not connected"))
        val transfer = transfer ?: return Result.failure(IllegalStateException("Not connected"))
        return when (val result = transfer.download(remotePath, localFile, onProgress)) {
            is SftpResult.Success -> Result.success(Unit)
            else -> Result.failure(result.toException())
        }
    }
    
    // ... etc.
}

private fun SftpResult<*>.toException(): Exception = when (this) {
    is SftpResult.ServerError -> IOException("SFTP error: ${statusCode} ${message}")
    is SftpResult.ProtocolError -> IOException("Protocol error: $message")
    is SftpResult.IoError -> cause ?: IOException("I/O error")
}
```

### 3. Feature Flag Integration

**File:** `app/src/main/java/com/darkssh/client/util/AppPreferences.kt`

**Add:**
```kotlin
var useCbsshSftp: Boolean
    get() = prefs.getBoolean("use_cbssh_sftp", false)  // Default off for safety
    set(value) = prefs.edit().putBoolean("use_cbssh_sftp", value).apply()
```

**UI:** Add toggle in ServerSettingsScreen (advanced settings).

### 4. SftpViewModel.kt Changes

**File:** `app/src/main/java/com/darkssh/client/ui/screens/viewmodel/SftpViewModel.kt`

**Change:**
```kotlin
private var sftpClient: SftpClient? = null

// Replace with:
private var sftpClient: SftpClient? = null  // sshj version (fallback)
private var sftpClient2: SftpClient2? = null  // cbssh version

private suspend fun getActiveClient(): SftpClientInterface? {
    return if (AppPreferences.useCbsshSftp) sftpClient2 else sftpClient
}

// All operations route through getActiveClient()
```

### 5. ScpFallback.kt - SCP-Style Upload

**Purpose:** Replace sshj's `uploadViaSsh()` SCP fallback for large files.

**File:** `app/src/main/java/com/darkssh/client/transport/cbssh/ScpFallback.kt`

**Implementation:**
```kotlin
suspend fun uploadViaScp(
    session: SshSession,
    localFile: File,
    remotePath: String,
    onProgress: ((TransferProgress) -> Unit)? = null,
): SftpResult<Unit> {
    if (!session.requestExec("scp -t $remotePath")) {
        return SftpResult.ProtocolError("Failed to exec scp")
    }
    
    val header = "C0644 ${localFile.length()} ${localFile.name}\n"
    session.write(header.toByteArray())
    
    val ack = session.read()
    if (ack?.first() != 0x00.toByte()) {
        return SftpResult.ProtocolError("No ACK after header")
    }
    
    var offset = 0L
    val buffer = ByteArray(32 * 1024)
    localFile.inputStream().use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            session.write(buffer.copyOf(read))
            offset += read
            // Throttled progress callback
        }
    }
    
    session.write(byteArrayOf(0x00))
    val finalAck = session.read()
    if (finalAck?.first() != 0x00.toByte()) {
        return SftpResult.ProtocolError("No final ACK")
    }
    
    session.sendEof()
    return SftpResult.Success(Unit)
}
```

---

## API Mapping (sshj → cbssh)

### Connection

| sshj | cbssh | Notes |
|------|-------|-------|
| `SSHClient(config)` | `SshClient(SshClientConfig)` | Same role |
| `ssh.connect(host, port)` | `client.connect(host, port)` | Same |
| `ssh.authPassword(user, pass)` | `client.authenticatePassword(user, pass)` | Same |
| `ssh.authPublickey(user, keyProvider)` | `client.authenticatePublicKey(user, keyPair)` | Direct KeyPair |
| `ssh.useCompression()` | ❓ TBD | Need verification |
| `ssh.connection.windowSize = X` | ❓ TBD | Need verification |
| `ssh.connection.maxPacketSize = X` | ❓ TBD | Need verification |
| `ssh.connection.keepAlive.keepAliveInterval = 15` | ❓ TBD | Need verification |
| `ssh.addHostKeyVerifier(PromiscuousVerifier())` | Custom `HostKeyVerifier` | Different API |

### SFTP Client

| sshj | cbssh | Notes |
|------|-------|-------|
| `ssh.newSFTPClient()` | `client.openSftp()` | Same |
| `sftp.ls(path)` | `sftp.listdir(path)` | Returns `SftpDirectoryEntry` |
| `sftp.stat(path)` | `sftp.stat(path)` | Returns `SftpAttributes` |
| `sftp.statExistence(path)` | `sftp.stat(path)` | Check `SftpResult.Success` |
| `sftp.mkdir(path)` | `sftp.mkdir(path)` | Same |
| `sftp.rmdir(path)` | `sftp.rmdir(path)` | Same |
| `sftp.rm(path)` | `sftp.remove(path)` | Name change |
| `sftp.rename(old, new)` | `sftp.rename(old, new)` | Same |
| `sftp.realpath(path)` | `sftp.realpath(path)` | Same |
| `sftp.open(path)` | `sftp.open(path, setOf(SftpOpenFlag.READ))` | Need flags |
| `sftp.open(path, flags)` | `sftp.open(path, flags)` | Same |

### File I/O

| sshj | cbssh | Notes |
|------|-------|-------|
| `RemoteFileInputStream` | `sftp.read(handle, offset, len)` | Manual loop |
| `RemoteFileOutputStream` | `sftp.write(handle, offset, data)` | Manual loop |
| `fileTransfer.download(remote, local)` | `CbsshTransfer.download(remote, local)` | Wrapper |
| `fileTransfer.upload(local, remote)` | `CbsshTransfer.upload(local, remote)` | Wrapper |
| `TransferListener.file(name, size)` | `onProgress: ((TransferProgress) -> Unit)` | Callback |
| `StreamCopier.Listener.reportProgress()` | Same callback | Wrapper |

### Commands

| sshj | cbssh | Notes |
|------|-------|-------|
| `client.startSession().use { it.exec(cmd) }` | `client.openSession().use { it.requestExec(cmd) }` | Different |
| `Command.inputStream` | `session.read()` | Returns ByteArray? |
| `Command.errorStream` | `session.read()` | Same channel |
| `Command.join()` | Suspend until EOF | Coroutine-based |
| `Command.exitStatus` | ❓ TBD | Need verification |

---

## Testing Strategy

### Unit Tests

```kotlin
class CbsshTransferTest {
    @Test
    fun `download reports progress at 100KB intervals`() = runBlocking {
        val sftp = mockk<SftpClient>()
        val output = ByteArrayOutputStream()
        
        every { sftp.stat(any()) } returns SftpResult.Success(...)
        every { sftp.open(any(), any()) } returns SftpResult.Success(handle)
        every { sftp.read(any(), any(), any()) } returnsMany ...
        
        val reports = mutableListOf<TransferProgress>()
        CbsshTransfer(sftp).downloadToStream("/test", output) {
            reports.add(it)
        }
        
        assertTrue(reports.size >= 10)
        assertTrue(reports.last().bytesTransferred == 1_048_576L)
    }
    
    @Test
    fun `upload handles file not found`() = runBlocking {
        val sftp = mockk<SftpClient>()
        val result = CbsshTransfer(sftp).upload(
            File("/nonexistent"),
            "/remote",
        )
        assertTrue(result is SftpResult.IoError)
    }
}
```

### Integration Tests (with testcontainers)

```kotlin
@Testcontainers
class SftpClient2IntegrationTest {
    @Container
    val sshServer = GenericContainer("openssh-server")
    
    @Test
    fun `full SFTP workflow with cbssh`() = runBlocking {
        val host = Host(hostname = "localhost", port = sshServer.firstMappedPort, username = "test", ...)
        val client = SftpClient2(host, ...)
        
        assertTrue(client.connectWithPassword("test").isSuccess)
        
        val entries = client.ls("/tmp").getOrThrow()
        assertTrue(entries.isNotEmpty())
        
        // Create test file
        val testFile = File.createTempFile("test", ".txt")
        testFile.writeText("Hello SFTP!")
        
        // Upload with progress
        var lastProgress = 0L
        val result = client.uploadFile(testFile, "/tmp/uploaded.txt") {
            lastProgress = it.bytesTransferred
        }
        assertTrue(result.isSuccess)
        assertEquals(testFile.length(), lastProgress)
        
        // Download with progress
        val downloadedFile = File.createTempFile("downloaded", ".txt")
        client.downloadFile("/tmp/uploaded.txt", downloadedFile) {}
        assertEquals(testFile.readText(), downloadedFile.readText())
        
        // Cleanup
        client.rm("/tmp/uploaded.txt")
        client.disconnect()
    }
}
```

### Performance Benchmarks

```kotlin
class SftpBenchmark {
    @Test
    fun `download 100MB file comparison`() {
        // sshj baseline
        val sshjClient = SftpClient(host, ...)
        sshjClient.connectWithPassword("test").getOrThrow()
        val sshjStart = System.currentTimeMillis()
        sshjClient.downloadFile("/100mb.bin", localFile)
        val sshjDuration = System.currentTimeMillis() - sshjStart
        
        // cbssh implementation
        val cbsshClient = SftpClient2(host, ...)
        cbsshClient.connectWithPassword("test").getOrThrow()
        val cbsshStart = System.currentTimeMillis()
        cbsshClient.downloadFile("/100mb.bin", localFile2)
        val cbsshDuration = System.currentTimeMillis() - cbsshStart
        
        // cbssh should be at least as fast
        assertTrue(cbsshDuration <= sshjDuration * 1.1)  // Within 10%
    }
}
```

---

## Rollout Strategy

### Stage 1: Internal Testing (Week 4)
- Enable `useCbsshSftp = false` by default
- Allow manual toggle in settings
- Internal team tests all SFTP operations

### Stage 2: Opt-in Beta (Week 5)
- Add toggle to ServerSettingsScreen
- Document known limitations
- Collect user feedback

### Stage 3: Default Rollout (Week 6)
- Change default to `useCbsshSftp = true`
- Monitor crash reports
- Easy rollback via flag

### Stage 4: Cleanup (Week 6+)
- Remove sshj-based SftpClient.kt
- Remove sshj dependency from build.gradle
- Update documentation

---

## Open Questions

1. **Compression:** Does cbssh support `useCompression()`? Need to verify.
2. **Window tuning:** Can we configure window size in cbssh? Need to verify.
3. **Keep-alive:** How does cbssh handle keep-alive? Need to verify.
4. **Host key verification:** How to implement permissive verifier during migration?
5. **Symlink detection:** cbssh's `listdir` doesn't expose symlink info directly. How to detect?
6. **Exit status:** How to get command exit status from cbssh's SSH session?
7. **Permissions:** How to map SftpAttributes.permissions (int) to SSH octal format?

These questions will be answered during Phase 1 implementation.

---

## References

- **cbssh SFTP source:** `/home/dark/Project/cbssh-upstream/sshlib/src/main/kotlin/org/connectbot/sshlib/client/sftp/`
- **Feature gap analysis:** `contrib/cbssh-sftp/research/feature-gap-analysis.md`
- **Transfer design:** `contrib/cbssh-sftp/design/cbssh-transfer-design.md`
- **Implementation notes:** `contrib/cbssh-sftp/implementation/README.md`
- **Current SFTP client:** `app/src/main/java/com/darkssh/client/transport/SftpClient.kt`
- **Previous spec:** `openspec/changes/refactor-sftp-client-sshj/` (obsoleted)
