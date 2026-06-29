# cbssh SFTP - Implementation Reference

This directory contains the actual implementation code for the cbssh migration wrapper.

## Files

### `CbsshTransfer.kt`
High-level wrapper providing download/upload/copy with progress tracking.
Fills the gap left by sshj's `FileTransfer` and `TransferListener`.

**Status**: Design complete (see `../design/cbssh-transfer-design.md`)

### `SftpClient2.kt` (planned)
Drop-in replacement for `SftpClient.kt` using cbssh instead of sshj.
Same public API to allow easy switching via feature flag.

**Status**: Not yet implemented

### `ScpFallback.kt` (planned)
SCP-style upload fallback using SSH exec channel.
Replaces sshj's `uploadViaSsh()` for large files.

**Status**: Not yet implemented

## Migration Mapping

| sshj API | cbssh Equivalent | Status |
|----------|------------------|--------|
| `SSHClient(config)` | `SshClient(SshClientConfig)` | ✅ Direct |
| `ssh.connect(host, port)` | `client.connect(host, port)` | ✅ Same |
| `ssh.authPassword(user, pass)` | `client.authenticatePassword(user, pass)` | ✅ Same |
| `ssh.authPublickey(user, keyProvider)` | `client.authenticatePublicKey(user, keyPair)` | ⚠️ Different API |
| `ssh.useCompression()` | ❓ Not verified | TBD |
| `ssh.newSFTPClient()` | `client.openSftp()` | ✅ Direct |
| `sftp.ls(path)` | `sftp.listdir(path)` | ⚠️ Different return type |
| `sftp.stat(path)` | `sftp.stat(path)` | ✅ Same |
| `sftp.statExistence(path)` | `sftp.stat(path)` | ⚠️ Check result type |
| `sftp.mkdir(path)` | `sftp.mkdir(path)` | ✅ Same |
| `sftp.rmdir(path)` | `sftp.rmdir(path)` | ✅ Same |
| `sftp.rm(path)` | `sftp.remove(path)` | ⚠️ Name change |
| `sftp.rename(old, new)` | `sftp.rename(old, new)` | ✅ Same |
| `sftp.open(path)` | `sftp.open(path, setOf(SftpOpenFlag.READ))` | ⚠️ Need flags |
| `sftp.open(path, flags)` | `sftp.open(path, flags)` | ✅ Same |
| `RemoteFileInputStream` | `sftp.read(handle, offset, len)` | ⚠️ Manual loop |
| `RemoteFileOutputStream` | `sftp.write(handle, offset, data)` | ⚠️ Manual loop |
| `fileTransfer.download()` | `CbsshTransfer.download()` | ⚠️ Need wrapper |
| `fileTransfer.upload()` | `CbsshTransfer.upload()` | ⚠️ Need wrapper |
| `TransferListener` | `onProgress: ((TransferProgress) -> Unit)` | ⚠️ Manual throttling |
| `StreamCopier.Listener.reportProgress()` | Same callback | ⚠️ Need wrapper |
| `client.startSession().use { exec(cmd) }` | `client.openSession().use { session.requestExec(cmd) }` | ⚠️ Different API |
| `PromiscuousVerifier` | Custom `HostKeyVerifier` | ⚠️ Different API |
| `KeyProvider` interface | Pass `KeyPair` directly | ✅ Simpler |

## Key API Differences

### 1. Error Handling

**sshj (Java):**
```java
try {
    sftp.ls(path);
} catch (SFTPException e) {
    // handle error
}
```

**cbssh (Kotlin):**
```kotlin
when (val result = sftp.listdir(path)) {
    is SftpResult.Success -> processEntries(result.value)
    is SftpResult.ServerError -> showError(result.message)
    is SftpResult.ProtocolError -> logError(result.message)
    is SftpResult.IoError -> showError(result.cause?.message ?: "I/O error")
}
```

### 2. File I/O

**sshj (Java):**
```java
RemoteFile file = sftp.open(path);
try (InputStream is = file.RemoteFileInputStream()) {
    byte[] buffer = new byte[32 * 1024];
    int read;
    while ((read = is.read(buffer)) != -1) {
        outputStream.write(buffer, 0, read);
    }
}
```

**cbssh (Kotlin):**
```kotlin
val handle = sftp.open(path, setOf(SftpOpenFlag.READ)).getOrThrow()
try {
    var offset = 0L
    val bufferSize = 32 * 1024
    while (true) {
        val chunk = sftp.read(handle, offset, bufferSize).getOrThrow() ?: break
        outputStream.write(chunk)
        offset += chunk.size
    }
} finally {
    sftp.close(handle)
}
```

### 3. Progress Reporting

**sshj (Java):**
```java
fileTransfer.transferListener = new TransferListener() {
    @Override
    public StreamCopier.Listener file(String name, long size) {
        return transferred -> {
            // report progress
        };
    }
};
fileTransfer.download(remotePath, localPath);
```

**cbssh (Kotlin):**
```kotlin
CbsshTransfer(sftp).download(remotePath, localFile) { progress ->
    // progress.bytesTransferred, progress.totalBytes, etc.
}
```

## Implementation Plan

### Phase 1: Wrapper Layer (1-2 weeks)

1. Create `CbsshTransfer.kt` with high-level operations
2. Write unit tests with mocked SftpClient
3. Write integration tests with testcontainers

### Phase 2: Drop-in Replacement (1 week)

1. Create `SftpClient2.kt` implementing same interface as `SftpClient.kt`
2. Map sshj errors to Result<Unit>
3. Add feature flag in AppPreferences

### Phase 3: SCP Fallback (1 week)

1. Create `ScpFallback.kt` using SSH exec channel
2. Implement SCP protocol (C0644 header, ACK flow)
3. Test with large files

### Phase 4: Migration (1 week)

1. Update `SftpViewModel.kt` to use `SftpClient2` when flag enabled
2. Verify all UI flows still work
3. Add telemetry for stability monitoring

### Phase 5: Rollout (1 week)

1. Enable for internal testing
2. Monitor crash reports
3. Enable for all users
4. Remove sshj dependency

## Testing Strategy

### Unit Tests
```kotlin
@Test
fun `download reports progress at 100KB intervals`() = runBlocking {
    val sftp = mockk<SftpClient>()
    val output = ByteArrayOutputStream()
    
    // Mock sftp to return 1MB of data
    every { sftp.stat(any()) } returns SftpResult.Success(...)
    every { sftp.open(any(), any()) } returns SftpResult.Success(...)
    every { sftp.read(any(), any(), any()) } returns ...
    
    val progressReports = mutableListOf<TransferProgress>()
    CbsshTransfer(sftp).downloadToStream("/test", output) {
        progressReports.add(it)
    }
    
    assertTrue(progressReports.size >= 10)  // 1MB / 100KB = 10 reports
}
```

### Integration Tests
```kotlin
@Testcontainers
class CbsshTransferIntegrationTest {
    @Container
    val sshServer = GenericContainer("openssh-server")
    
    @Test
    fun `download 100MB file with progress`() = runBlocking {
        val client = SshClient(...)
        client.connect("localhost", sshServer.firstMappedPort)
        client.authenticatePassword("user", "pass")
        val sftp = client.openSftp().getOrThrow()
        
        var lastProgress = 0L
        val result = CbsshTransfer(sftp).download("/large-file", localFile) {
            assertTrue(it.bytesTransferred > lastProgress)
            lastProgress = it.bytesTransferred
        }
        
        assertTrue(result is SftpResult.Success)
        assertEquals(100L * 1024 * 1024, lastProgress)
    }
}
```

## Performance Expectations

Based on cbssh's coroutine-based architecture, we expect:

| Metric | sshj | cbssh | Improvement |
|--------|------|-------|-------------|
| Connection setup | ~500ms | ~500ms | Same |
| 100MB download | ~8s | ~7s | ~10% faster |
| 100MB upload | ~10s | ~8s | ~20% faster |
| Concurrent transfers | 4-8 | 8-16 | 2x throughput |
| Memory usage | Higher (threads) | Lower (coroutines) | ~30% less |

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| API differences | Medium | Wrapper layer abstracts differences |
| Error type mapping | Medium | Centralized mapping function |
| Progress throttling | Low | Match sshj intervals exactly |
| Connection stability | Low | Same SSH protocol underneath |
| Large file support | Medium | Test with files >1GB |
| Concurrent transfers | Medium | Test parallel uploads |
| Host key verification | Low | Use permissive verifier during migration |

## Open Questions

1. Does cbssh support compression? Need to verify
2. Does cbssh expose window size tuning? Need to verify
3. Does cbssh support keepalive configuration? Need to verify
4. What's the overhead of `SftpResult` vs exceptions in hot paths?
5. How does cbssh handle network timeouts vs sshj?

## References

- cbssh SFTP source: `/home/dark/Project/cbssh-fork/sshlib/src/main/kotlin/org/connectbot/sshlib/client/sftp/`
- cbssh tests: `/home/dark/Project/cbssh-fork/sshlib/src/test/kotlin/org/connectbot/sshlib/client/sftp/`
- cbssh fork repo: https://github.com/JDis03/cbssh
- cbssh upstream: https://github.com/connectbot/cbssh
- DarkSSH current SFTP: `/home/dark/Project/clientssh/app/src/main/java/com/darkssh/client/transport/SftpClient.kt`
- Migration plan: `/home/dark/Project/clientssh/docs/MIGRATION_TO_CBSSH.md`
