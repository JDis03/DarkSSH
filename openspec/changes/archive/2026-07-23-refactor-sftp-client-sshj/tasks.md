# Tasks: Refactor SFTP Client - Unify on sshj

## Phase 1: Remove Dead Code

- [x] **T1.1** Delete unused `SftpClient.kt` (sshlib implementation)
  - File: `app/src/main/java/com/darkssh/client/transport/SftpClient.kt`
  - Impact: -357 LOC
  - Status: ✅ Completed

## Phase 2: Add Pubkey Authentication

- [x] **T2.1** Extract config creation helper
  - File: `app/src/main/java/com/darkssh/client/transport/SftpClientSSHJ.kt`
  - Extract `createConfig()` from `connectWithPassword()`
  - Reason: Reuse in `connectWithKey()`
  - Status: ✅ Completed

- [x] **T2.2** Add required imports
  - File: `app/src/main/java/com/darkssh/client/transport/SftpClientSSHJ.kt`
  - Add: `import net.schmizz.sshj.common.KeyType`
  - Add: `import net.schmizz.sshj.userauth.keyprovider.KeyProvider`
  - Add: `import java.security.{KeyPair, PrivateKey, PublicKey}`
  - Status: ✅ Completed

- [x] **T2.3** Implement `connectWithKey(keyPair: KeyPair)`
  - File: `app/src/main/java/com/darkssh/client/transport/SftpClientSSHJ.kt`
  - Create `KeyProvider` wrapper for `java.security.KeyPair`
  - Call `ssh.authPublickey(username, keyProvider)`
  - Return `Result.success()` or `Result.failure()`
  - Status: ✅ Completed

## Phase 3: Simplify ViewModel

- [x] **T3.1** Remove dual client variables
  - File: `app/src/main/java/com/darkssh/client/ui/screens/viewmodel/SftpViewModel.kt`
  - Remove: `private var sftpClient: SftpClient? = null`
  - Keep: `private var sftpClientSSHJ: SftpClientSSHJ? = null`
  - Rename later to just `sftpClient`
  - Status: ✅ Completed

- [x] **T3.2** Remove useSSHJ flag
  - File: `app/src/main/java/com/darkssh/client/ui/screens/viewmodel/SftpViewModel.kt`
  - Remove: `private val useSSHJ = true`
  - Status: ✅ Completed

- [x] **T3.3** Simplify `connectWithPassword()`
  - Remove conditional branch
  - Use only `SftpClientSSHJ`
  - Status: ✅ Completed

- [x] **T3.4** Simplify `tryReconnectIfNeeded()`
  - Remove conditional branch
  - Use only `SftpClientSSHJ`
  - Status: ✅ Completed

- [x] **T3.5** Simplify `listDirectory()`
  - Replace conditional `if (useSSHJ) {...} else {...}`
  - Direct call: `sftpClientSSHJ?.ls(path)`
  - Status: ✅ Completed

- [x] **T3.6** Simplify `downloadToStream()`
  - Remove conditional branch
  - Status: ✅ Completed

- [x] **T3.7** Simplify `uploadFile()`
  - Remove conditional branch
  - Status: ✅ Completed

- [x] **T3.8** Simplify `deleteEntry()`
  - Remove conditional branch
  - Simplify: `if (entry.isDirectory) rmdir() else rm()`
  - Status: ✅ Completed

- [x] **T3.9** Simplify `createDirectory()`
  - Remove conditional branch
  - Status: ✅ Completed

- [x] **T3.10** Simplify `renameEntry()` and helpers
  - Remove conditionals in:
    - `doRename()`
    - `checkRenameConflict()`
    - `resolveRenameConflict()`
  - Status: ✅ Completed

- [x] **T3.11** Remove unused import
  - File: `SftpViewModel.kt`
  - Remove: `import com.darkssh.client.transport.SftpClient` (old one)
  - Status: ✅ Completed

## Phase 4: Rename for Clarity

- [x] **T4.1** Rename file
  - From: `app/src/main/java/com/darkssh/client/transport/SftpClientSSHJ.kt`
  - To: `app/src/main/java/com/darkssh/client/transport/SftpClient.kt`
  - Status: ✅ Completed

- [x] **T4.2** Rename class
  - File: `app/src/main/java/com/darkssh/client/transport/SftpClient.kt`
  - From: `class SftpClientSSHJ`
  - To: `class SftpClient`
  - Status: ✅ Completed

- [x] **T4.3** Update ViewModel imports
  - File: `app/src/main/java/com/darkssh/client/ui/screens/viewmodel/SftpViewModel.kt`
  - Change: `import ...SftpClientSSHJ` → `import ...SftpClient`
  - Status: ✅ Completed

- [x] **T4.4** Update ViewModel variable name
  - File: `app/src/main/java/com/darkssh/client/ui/screens/viewmodel/SftpViewModel.kt`
  - Rename all: `sftpClientSSHJ` → `sftpClient`
  - Status: ✅ Completed

## Phase 5: Move Data Classes

- [x] **T5.1** Add SftpEntry to new SftpClient.kt
  - File: `app/src/main/java/com/darkssh/client/transport/SftpClient.kt`
  - Copy from old file (deleted in T1.1)
  - Status: ✅ Completed

- [x] **T5.2** Add TransferProgress to new SftpClient.kt
  - File: `app/src/main/java/com/darkssh/client/transport/SftpClient.kt`
  - Copy from old file
  - Status: ✅ Completed

- [x] **T5.3** Add SftpAuthState to new SftpClient.kt
  - File: `app/src/main/java/com/darkssh/client/transport/SftpClient.kt`
  - Copy from old file
  - Status: ✅ Completed

## Phase 6: Verification

- [x] **T6.1** Build project
  - Command: `./gradlew assembleDebug`
  - Expected: Build succeeds
  - Status: ✅ Completed

- [ ] **T6.2** Test password authentication (regression)
  - Manual: Connect to SFTP server with password
  - Operations: ls, download, upload
  - Expected: All work as before
  - Status: ⏳ Pending device connection

- [ ] **T6.3** Test pubkey authentication (new feature)
  - Manual: Generate keypair in app
  - Manual: Add pubkey to server ~/.ssh/authorized_keys
  - Manual: Connect with keypair
  - Expected: Authentication succeeds, SFTP works
  - Status: ⏳ Pending device connection

- [ ] **T6.4** Test auto-reconnect (regression)
  - Manual: Connect, kill server connection, trigger operation
  - Expected: Auto-reconnects successfully
  - Status: ⏳ Pending device connection

- [ ] **T6.5** Check APK size
  - Command: `ls -lh app/build/outputs/apk/debug/app-debug.apk`
  - Expected: No significant change (same dependencies)
  - Status: ⏳ Pending build comparison

## Phase 7: Documentation

- [x] **T7.1** Update proposal.md
  - Document what/why/how
  - Status: ✅ Completed

- [x] **T7.2** Update design.md
  - Architecture diagrams
  - Implementation details
  - Status: ✅ Completed

- [x] **T7.3** Update tasks.md
  - Complete checklist
  - Status: ✅ Completed (this file)

- [x] **T7.4** Create spec delta
  - File: `openspec/changes/refactor-sftp-client-sshj/specs/sftp-client.md`
  - Document requirements added/modified
  - Status: ✅ Completed

## Summary

| Phase | Tasks | Completed | Pending |
|-------|-------|-----------|---------|
| 1. Remove Dead Code | 1 | 1 | 0 |
| 2. Add Pubkey Auth | 3 | 3 | 0 |
| 3. Simplify ViewModel | 11 | 11 | 0 |
| 4. Rename | 4 | 4 | 0 |
| 5. Move Data Classes | 3 | 3 | 0 |
| 6. Verification | 5 | 1 | 4 |
| 7. Documentation | 4 | 4 | 0 |
| **Total** | **31** | **27** | **4** |

**Progress:** 87% complete (27/31 tasks done)

**Remaining work:**
- Testing with device (T6.2-T6.4)
- APK size comparison (T6.5)
- Spec delta creation (T7.4)
