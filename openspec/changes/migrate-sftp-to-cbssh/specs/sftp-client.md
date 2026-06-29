# Spec Delta: Migrate SFTP Client to cbssh

## ADDED Requirements

### R1: cbssh SFTP Client Implementation

**Requirement:** The application SHALL provide an SFTP client implementation using cbssh (ConnectBot SSH Kotlin library).

**Rationale:** Replace legacy sshj (Java) with modern Kotlin/coroutines-based cbssh for better maintainability, performance, and integration with ConnectBot ecosystem.

**Acceptance Criteria:**
- `SftpClient2.kt` implements same public API as `SftpClient.kt`
- Uses `org.connectbot.sshlib.SshClient` for SSH connections
- Uses `org.connectbot.sshlib.client.sftp.SftpClient` for SFTP operations
- All operations return `Result<Unit>` matching existing API
- Progress callbacks fire at same intervals as sshj (100KB download, 256KB upload)

---

### R2: High-Level Transfer Operations

**Requirement:** The application SHALL provide high-level download/upload/copy operations with progress tracking on top of cbssh's low-level SFTP API.

**Rationale:** cbssh's SFTP API is low-level (open/read/write/close). DarkSSH needs convenient download/upload operations that match sshj's `FileTransfer` API.

**Acceptance Criteria:**
- `CbsshTransfer.kt` provides `download()`, `downloadToStream()`, `upload()`, `uploadParallel()`, `copy()`
- All methods accept optional `onProgress` callback
- Progress throttling matches sshj behavior
- Buffer size of 32KB for transfers
- Returns `SftpResult<T>` for type-safe error handling

**API:**
```kotlin
class CbsshTransfer(sftp: SftpClient) {
    suspend fun download(
        remotePath: String,
        localFile: File,
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

---

### R3: Gradual Rollout via Feature Flag

**Requirement:** The application SHALL allow users to toggle between sshj and cbssh SFTP implementations via settings.

**Rationale:** Enable safe, gradual rollout with ability to rollback if issues discovered.

**Acceptance Criteria:**
- `AppPreferences.useCbsshSftp: Boolean` preference exists
- Default value is `false` (sshj) for safety
- Setting can be changed in ServerSettingsScreen
- Setting persists across app restarts
- ViewModel routes operations to selected implementation

**Behavior:**
- When `useCbsshSftp = false`: Use `SftpClient` (sshj) - existing behavior
- When `useCbsshSftp = true`: Use `SftpClient2` (cbssh) - new implementation

---

### R4: SCP Fallback for Large Files

**Requirement:** The application SHALL provide SCP-style upload fallback for large files using SSH exec channel.

**Rationale:** For large files (>100MB), SCP can be faster than SFTP. sshj has built-in SCP fallback. cbssh requires manual implementation.

**Acceptance Criteria:**
- `ScpFallback.kt` implements SCP upload protocol
- Uses `SshSession.requestExec("scp -t $remotePath")`
- Sends SCP header (C0644 format), file data, terminator
- Handles ACK bytes (0x00) for flow control
- Reports progress to callback
- Falls back gracefully if server lacks scp command

---

### R5: SftpResult Error Mapping

**Requirement:** The application SHALL map cbssh's `SftpResult` to existing `Result<Unit>` error semantics.

**Rationale:** Maintain existing error handling patterns in ViewModel while using cbssh underneath.

**Acceptance Criteria:**
- `SftpResult.Success` → `Result.success(Unit)`
- `SftpResult.ServerError` → `Result.failure(IOException(message))`
- `SftpResult.ProtocolError` → `Result.failure(IOException(message))`
- `SftpResult.IoError` → `Result.failure(cause ?: IOException("I/O error"))`
- Mapping centralized in extension function

---

## MODIFIED Requirements

### M1: SSH Library Stack

**Before:**
- sshlib (ConnectBot, Java) - Terminal SSH
- sshj (Hierynomus, Java) - SFTP client
- Apache SSHD (Apache, Java) - SFTP server
- **3 SSH libraries total**

**After:**
- sshlib (ConnectBot, Java) - Terminal SSH *(unchanged)*
- cbssh (ConnectBot, Kotlin) - SFTP client
- Apache SSHD (Apache, Java) - SFTP server *(unchanged)*
- **Same count, but cbssh shares maintainers with sshlib**

**Impact:**
- Unified SSH ecosystem (ConnectBot for both terminal and SFTP)
- Reduced APK size by ~1.5MB after sshj removal
- Modern Kotlin API (coroutines, null safety)

---

### M2: SFTP Authentication Methods

**Before:**
- Password: `SftpClient.connectWithPassword(password: String)`
- Pubkey: `SftpClient.connectWithKey(keyPair: KeyPair)`

**After:**
- Password: `SftpClient2.connectWithPassword(password: String)` (same signature)
- Pubkey: `SftpClient2.connectWithKey(keyPair: KeyPair)` (same signature)

**Impact:** No API changes for consumers.

---

### M3: Progress Tracking

**Before:**
- sshj's `TransferListener` interface
- `file(name, size)` returns `StreamCopier.Listener`
- `reportProgress(transferred)` callback

**After:**
- Direct lambda: `onProgress: ((TransferProgress) -> Unit)?`
- Same `TransferProgress` data class
- Same throttling intervals (100KB/256KB)

**Impact:** Cleaner API, less ceremony.

---

## REMOVED Requirements

### R_OLD_1: sshj Dependency

**Removed:** Dependency on `com.hierynomus:sshj:0.38.0`.

**Reason:** Replaced by cbssh (Kotlin/coroutines).

**Migration:** After rollout complete, remove sshj from `app/build.gradle.kts` dependencies block.

---

### R_OLD_2: sshj-based SftpClient.kt

**Removed:** Original `SftpClient.kt` (sshj implementation) after successful rollout.

**Reason:** No longer needed once cbssh is stable.

**Migration:** ViewModel updated to use `SftpClient2` exclusively. Original file deleted.

---

## Invariants

### I1: SFTP Protocol Compatibility

**Invariant:** All SFTP operations SHALL use SFTP protocol v3 or higher.

**Justification:** cbssh `SftpClient` supports v3+. All modern SSH servers support v3+.

**Verification:** cbssh negotiates highest common version during connection.

---

### I2: Progress Tracking Consistency

**Invariant:** All file transfer operations (download/upload/copy) SHALL report progress via callbacks.

**Mechanism:** cbssh wrapper `CbsshTransfer` invokes callback with `TransferProgress` at throttled intervals.

**Throttling:**
- Download: 100KB intervals
- Upload: 256KB intervals
- Copy (server-side): 5MB intervals

```kotlin
class TransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val filename: String,
    val startTime: Long,
    val currentTime: Long,
) {
    val percentage: Float = bytesTransferred.toFloat() / totalBytes
    val speedBytesPerSecond: Long = ...
}
```

---

### I3: Connection Lifecycle

**Invariant:** SFTP session SHALL remain open until explicit `disconnect()` or connection error.

**Auto-reconnect:** On connection errors (timeout, broken pipe, etc.), ViewModel attempts reconnection with stored credentials.

**States:**
- `Idle` → Initial state
- `Connecting` → Authentication in progress
- `Authenticated` → Connected, ready for operations
- `Failed(message)` → Auth failure or connection error

---

### I4: Error Handling Pattern

**Invariant:** All public methods SHALL return `Result<Unit>` (success/failure) rather than throwing.

**Justification:** Kotlin idiomatic error handling, matches existing API.

**Implementation:** `SftpResult` from cbssh mapped to `Result<Unit>` via extension function.

---

## Dependencies

### Removed (after rollout)

| Library | Version | Reason |
|---------|---------|--------|
| `com.hierynomus:sshj` | 0.38.0 | Replaced by cbssh |

### Added

| Library | Version | Usage |
|---------|---------|-------|
| `org.connectbot:cbssh` | 0.1.0-SNAPSHOT | SFTP client + future SSH terminal migration |
| `org.testcontainers:junit-jupiter` | 1.19.x | Integration tests |

### Unchanged

| Library | Version | Usage |
|---------|---------|-------|
| `sshlib` | 2.2.47-SNAPSHOT | Terminal SSH (unchanged) |
| `Apache SSHD` | 2.14.0 | SFTP server (unchanged) |

---

## Migration Guide

### For Developers

**Before (sshj only):**
```kotlin
val client = SftpClient(host, knownHostRepo, clipboard)
client.connectWithPassword(password)
client.ls(path)
```

**After (cbssh via SftpClient2):**
```kotlin
val client = SftpClient2(host, knownHostRepo, clipboard)
client.connectWithPassword(password)
client.ls(path)
// Same API, different implementation
```

**Direct cbssh usage (advanced):**
```kotlin
val sshClient = SshClient(SshClientConfig().apply {
    connectTimeout = 30_000
})
sshClient.connect(host.hostname, host.port)
sshClient.authenticatePassword(username, password)
val sftp = sshClient.openSftp().getOrThrow()
val transfer = CbsshTransfer(sftp)
transfer.download(remotePath, localFile) { progress ->
    // progress.bytesTransferred, etc.
}
```

### For Users

**No changes** - Existing SFTP functionality works identically.

**New capability:** Can opt-in to cbssh implementation via Settings → Advanced → Use cbssh SFTP.

**APK size reduction:** ~1.5MB smaller after sshj removal.

---

## Testing Strategy

### Regression Tests

Verify existing functionality unchanged:
1. **Password authentication** - Connect, ls, download, upload all work
2. **Pubkey authentication** - Same operations with KeyPair
3. **File operations** - mkdir, rmdir, rm, rename, stat
4. **Progress callbacks** - Fire at correct intervals
5. **Auto-reconnect** - Trigger reconnect on connection loss

### New Feature Tests

1. **High-level operations** - CbsshTransfer wrapper correctness
2. **Parallel uploads** - Chunk synchronization
3. **SCP fallback** - Large file uploads
4. **Error mapping** - All SftpResult types to Result<Unit>

### Integration Tests (testcontainers)

```kotlin
@Testcontainers
class SftpClient2IntegrationTest {
    @Container
    val sshServer = GenericContainer("openssh-server")
        .withExposedPorts(22)
    
    @Test
    fun `full workflow with cbssh`() = runBlocking {
        val host = Host(hostname = "localhost", port = sshServer.firstMappedPort, ...)
        val client = SftpClient2(host, ...)
        
        client.connectWithPassword("test").getOrThrow()
        
        // Test all operations
        client.ls("/tmp")
        client.uploadFile(testFile, "/tmp/upload.txt") {}
        client.downloadFile("/tmp/upload.txt", downloadedFile) {}
        client.rm("/tmp/upload.txt")
        
        client.disconnect()
    }
}
```

### Performance Benchmarks

| Metric | sshj baseline | cbssh target | Acceptable |
|--------|---------------|--------------|------------|
| 100MB download | TBD | TBD | Within 10% |
| 100MB upload | TBD | TBD | Within 10% |
| Concurrent transfers | TBD | TBD | At least as fast |
| Memory usage | TBD | TBD | Less or equal |
| APK size | TBD | -1.5MB | Required |

---

## Rollout Plan

### Stage 1: Internal Testing (Week 4)
- Enable `useCbsshSftp = false` by default
- Internal team tests all operations
- Compare behavior vs sshj

### Stage 2: Opt-in Beta (Week 5)
- Add toggle to ServerSettingsScreen
- Document in release notes
- Collect user feedback

### Stage 3: Default Rollout (Week 6)
- Change default to `useCbsshSftp = true`
- Monitor crash reports
- Easy rollback via flag

### Stage 4: Cleanup (Week 6+)
- Remove sshj dependency
- Delete original `SftpClient.kt`
- Update documentation

---

## Rollback Plan

If critical issues discovered during rollout:

**Step 1:** Identify issue
- Check which implementation is active (log)
- Determine if cbssh-specific or both implementations

**Step 2:** Rollback option A - Feature flag
```kotlin
// In AppPreferences.kt
var useCbsshSftp: Boolean
    get() = prefs.getBoolean("use_cbssh_sftp", false)  // Force false
    set(value) = ...
```

**Step 3:** Rollback option B - Remove cbssh entirely
```bash
# Revert commits
git revert <commit-hash>
./gradlew assembleDebug
```

**Likelihood of rollback needed:** Low - cbssh is well-tested with same maintainers as sshlib.

---

## References

- **cbssh repository:** https://github.com/connectbot/cbssh
- **cbssh SFTP source:** `/home/dark/Project/cbssh-fork/sshlib/src/main/kotlin/org/connectbot/sshlib/client/sftp/`
- **Feature gap analysis:** `contrib/cbssh-sftp/research/feature-gap-analysis.md`
- **Transfer wrapper design:** `contrib/cbssh-sftp/design/cbssh-transfer-design.md`
- **Implementation notes:** `contrib/cbssh-sftp/implementation/README.md`
- **Migration plan:** `docs/MIGRATION_TO_CBSSH.md`
- **Previous spec (obsoleted):** `openspec/changes/refactor-sftp-client-sshj/`
