# Tasks: Migrate SFTP Client from sshj to cbssh

## Phase 1: Wrapper Layer (1 week)

### T1.1: Create CbsshTransfer.kt skeleton
- [ ] **T1.1.1** Create file `app/src/main/java/com/darkssh/client/transport/cbssh/CbsshTransfer.kt`
- [ ] **T1.1.2** Add package and imports for cbssh SFTP
- [ ] **T1.1.3** Create class skeleton with `sftp: SftpClient` parameter
- [ ] Status: ⏳ Pending

### T1.2: Implement download operations
- [ ] **T1.2.1** Implement `suspend fun download(remotePath, localFile, onProgress)`
- [ ] **T1.2.2** Implement `suspend fun downloadToStream(remotePath, outputStream, onProgress)`
- [ ] **T1.2.3** Add 100KB throttling for download progress
- [ ] **T1.2.4** Use 32KB buffer size
- [ ] Status: ⏳ Pending

### T1.3: Implement upload operations
- [ ] **T1.3.1** Implement `suspend fun upload(localFile, remotePath, onProgress)`
- [ ] **T1.3.2** Implement `suspend fun uploadParallel(localFile, remotePath, chunkSize, parallelChunks, onProgress)`
- [ ] **T1.3.3** Add 256KB throttling for upload progress
- [ ] **T1.3.4** Use AtomicLong for parallel progress tracking
- [ ] Status: ⏳ Pending

### T1.4: Implement server-side copy
- [ ] **T1.4.1** Implement `suspend fun copy(sourcePath, destPath, onProgress)`
- [ ] **T1.4.2** Use 128KB buffer for server-side copy
- [ ] **T1.4.3** Add 5MB throttling for copy progress
- [ ] Status: ⏳ Pending

### T1.5: Write unit tests
- [ ] **T1.5.1** Test download progress throttling (100KB intervals)
- [ ] **T1.5.2** Test upload progress throttling (256KB intervals)
- [ ] **T1.5.3** Test error handling for non-existent files
- [ ] **T1.5.4** Test parallel upload with chunking
- [ ] **T1.5.5** Test EOF detection in download loop
- [ ] Status: ⏳ Pending

### T1.6: Write integration tests
- [ ] **T1.6.1** Add testcontainers dependency for OpenSSH server
- [ ] **T1.6.2** Test full download/upload cycle with real server
- [ ] **T1.6.3** Test large file (>100MB) transfer
- [ ] **T1.6.4** Test concurrent transfers
- [ ] Status: ⏳ Pending

## Phase 2: Drop-in Replacement (1 week)

### T2.1: Create SftpClient2.kt
- [ ] **T2.1.1** Create file `app/src/main/java/com/darkssh/client/transport/SftpClient2.kt`
- [ ] **T2.1.2** Copy data classes (SftpEntry, TransferProgress, SftpAuthState) from SftpClient.kt
- [ ] **T2.1.3** Add class declaration matching SftpClient constructor
- [ ] Status: ⏳ Pending

### T2.2: Implement connection methods
- [ ] **T2.2.1** Implement `connectWithPassword(password)` using `SshClient`
- [ ] **T2.2.2** Implement `connectWithKey(keyPair)` using `SshClient`
- [ ] **T2.2.3** Implement `disconnect()` and `setDisconnected()`
- [ ] **T2.2.4** Add error mapping from SftpResult to Result<Unit>
- [ ] Status: ⏳ Pending

### T2.3: Implement file operations
- [ ] **T2.3.1** Implement `ls(path)` using `sftp.listdir()`
- [ ] **T2.3.2** Implement `stat(path)` using `sftp.stat()`
- [ ] **T2.3.3** Implement `mkdir(path)` using `sftp.mkdir()`
- [ ] **T2.3.4** Implement `rm(path)` using `sftp.remove()`
- [ ] **T2.3.5** Implement `rmdir(path)` using `sftp.rmdir()`
- [ ] **T2.3.6** Implement `rename(oldPath, newPath)` using `sftp.rename()`
- [ ] **T2.3.7** Implement `exists(path)` using `sftp.stat()` with error check
- [ ] Status: ⏳ Pending

### T2.4: Implement transfer operations
- [ ] **T2.4.1** Implement `downloadFile()` using `CbsshTransfer.download()`
- [ ] **T2.4.2** Implement `downloadToStream()` using `CbsshTransfer.downloadToStream()`
- [ ] **T2.4.3** Implement `uploadFile()` with SCP fallback
- [ ] **T2.4.4** Implement `uploadFileParallel()` using `CbsshTransfer.uploadParallel()`
- [ ] **T2.4.5** Implement `copyFile()` using `CbsshTransfer.copy()`
- [ ] **T2.4.6** Implement `moveFile()` (rename + delete source)
- [ ] Status: ⏳ Pending

### T2.5: Implement command execution
- [ ] **T2.5.1** Implement `executeCommand()` using SSH session
- [ ] **T2.5.2** Handle output collection and exit status
- [ ] **T2.5.3** Handle errors gracefully
- [ ] Status: ⏳ Pending

### T2.6: Test drop-in replacement
- [ ] **T2.6.1** Run same tests as SftpClient (regression test)
- [ ] **T2.6.2** Verify identical behavior for all operations
- [ ] **T2.6.3** Verify progress callbacks fire at correct intervals
- [ ] Status: ⏳ Pending

## Phase 3: SCP Fallback (1 week)

### T3.1: Create ScpFallback.kt
- [ ] **T3.1.1** Create file `app/src/main/java/com/darkssh/client/transport/cbssh/ScpFallback.kt`
- [ ] **T3.1.2** Implement SCP header construction (C0644 format)
- [ ] **T3.1.3** Implement ACK reading (0x00 byte)
- [ ] Status: ⏳ Pending

### T3.2: Implement SCP upload
- [ ] **T3.2.1** Implement `uploadViaScp(session, localFile, remotePath, onProgress)`
- [ ] **T3.2.2** Send file data in 32KB chunks
- [ ] **T3.2.3** Send terminator byte (0x00)
- [ ] **T3.2.4** Wait for final ACK
- [ ] **T3.2.5** Add progress throttling
- [ ] Status: ⏳ Pending

### T3.3: Test SCP fallback
- [ ] **T3.3.1** Test with files >100MB (where SCP is faster)
- [ ] **T3.3.2** Test against server without scp command (should fallback gracefully)
- [ ] **T3.3.3** Test permissions preservation (C0644 vs C0755)
- [ ] Status: ⏳ Pending

## Phase 4: Feature Flag (2 days)

### T4.1: Add preference
- [ ] **T4.1.1** Add `useCbsshSftp: Boolean` to AppPreferences.kt
- [ ] **T4.1.2** Default to `false` (sshj) for safety
- [ ] Status: ⏳ Pending

### T4.2: Update SftpViewModel
- [ ] **T4.2.1** Add `sftpClient2: SftpClient2?` field alongside `sftpClient`
- [ ] **T4.2.2** Add `getActiveClient()` method based on preference
- [ ] **T4.2.3** Route all operations through `getActiveClient()`
- [ ] **T4.2.4** Add logging to track which implementation is used
- [ ] Status: ⏳ Pending

### T4.3: Add UI toggle
- [ ] **T4.3.1** Add toggle in ServerSettingsScreen
- [ ] **T4.3.2** Add warning text about experimental status
- [ ] **T4.3.3** Show current implementation in status
- [ ] Status: ⏳ Pending

## Phase 5: Migration (3 days)

### T5.1: Internal testing
- [ ] **T5.1.1** Enable `useCbsshSftp = true` on internal builds
- [ ] **T5.1.2** Run full SFTP test suite against test server
- [ ] **T5.1.3** Verify all operations work identically
- [ ] **T5.1.4** Performance benchmark vs sshj
- [ ] Status: ⏳ Pending

### T5.2: Fix any issues found
- [ ] **T5.2.1** Address API differences discovered
- [ ] **T5.2.2** Fix edge cases
- [ ] **T5.2.3** Add missing features
- [ ] Status: ⏳ Pending

### T5.3: Opt-in beta
- [ ] **T5.3.1** Document how to enable cbssh SFTP in settings
- [ ] **T5.3.2** Release opt-in beta build
- [ ] **T5.3.3** Collect user feedback
- [ ] Status: ⏳ Pending

## Phase 6: Default Rollout (2 days)

### T6.1: Change default
- [ ] **T6.1.1** Change default `useCbsshSftp` to `true`
- [ ] **T6.1.2** Monitor crash reports for regressions
- [ ] **T6.1.3** Add telemetry for stability tracking
- [ ] Status: ⏳ Pending

### T6.2: Remove sshj fallback (after stable rollout)
- [ ] **T6.2.1** Verify cbssh is stable for all users
- [ ] **T6.2.2** Delete `SftpClient.kt` (sshj version)
- [ ] **T6.2.3** Remove sshj dependency from `app/build.gradle.kts`
- [ ] **T6.2.4** Update `SftpViewModel` to use only `SftpClient2`
- [ ] **T6.2.5** Verify APK size reduction (~1.5MB)
- [ ] Status: ⏳ Pending

## Phase 7: Documentation & Cleanup (2 days)

### T7.1: Update docs
- [ ] **T7.1.1** Update `docs/MIGRATION_TO_CBSSH.md` with implementation status
- [ ] **T7.1.2** Update `contrib/cbssh-sftp/README.md` timeline
- [ ] **T7.1.3** Add migration guide for developers
- [ ] Status: ⏳ Pending

### T7.2: Archive spec
- [ ] **T7.2.1** Archive `openspec/changes/refactor-sftp-client-sshj/`
- [ ] **T7.2.2** Mark this spec as completed
- [ ] Status: ⏳ Pending

## Summary

| Phase | Tasks | Status |
|-------|-------|--------|
| 1. Wrapper Layer | 16 | ⏳ Pending |
| 2. Drop-in Replacement | 22 | ⏳ Pending |
| 3. SCP Fallback | 9 | ⏳ Pending |
| 4. Feature Flag | 7 | ⏳ Pending |
| 5. Migration | 10 | ⏳ Pending |
| 6. Default Rollout | 8 | ⏳ Pending |
| 7. Documentation | 5 | ⏳ Pending |
| **Total** | **77** | - |

**Estimated effort:** 4-6 weeks (1 developer)

**Critical path:** Phase 1 → Phase 2 → Phase 4 → Phase 5 → Phase 6

**Dependencies:**
- cbssh must be added as dependency in build.gradle.kts
- testcontainers dependency for integration tests
