# Proposal: Refactor SFTP Client - Unify on sshj

## What

Consolidate SFTP client implementation by removing unused code and unifying on sshj library.

## Why

**Problem:**
- Two SFTP client implementations coexisting:
  - `SftpClient.kt` (357 LOC) - sshlib/Trilead - **NOT USED**
  - `SftpClientSSHJ.kt` (296 LOC) - sshj - **ACTIVE** (`useSSHJ = true`)
- Conditional logic in ViewModel choosing between implementations
- Missing pubkey authentication in active implementation
- Confusing naming (two classes with "SftpClient" in the name)

**Impact:**
- Code maintenance burden (357 dead LOC)
- Cognitive overhead (developers must understand both implementations)
- Missing feature (pubkey auth not implemented in sshj version)
- Potential bugs (unused code paths never tested)

**Context:**
sshj was chosen over sshlib for SFTP because:
- Better progress tracking API (`TransferListener` with automatic callbacks)
- Higher-level download/upload API (`transfer.download()` vs manual buffer loops)
- Less code to maintain (simpler, less error-prone)

## Goals

1. ✅ **Remove dead code** - Delete unused `SftpClient.kt` (sshlib implementation)
2. ✅ **Feature parity** - Add `connectWithKey()` to sshj implementation
3. ✅ **Simplify ViewModel** - Remove `useSSHJ` flag and conditional branches
4. ✅ **Clear naming** - Rename `SftpClientSSHJ` → `SftpClient` (no ambiguity)
5. ✅ **Maintainability** - Single implementation, clearer architecture

## Non-Goals

- Migrating to Apache SSHD (evaluated but rejected - see alternatives)
- Migrating terminal SSH from sshlib to sshj (out of scope)
- Changing SFTP protocol or adding new SFTP features

## Success Criteria

1. ✅ Only one SFTP client implementation exists
2. ✅ Pubkey authentication works (new feature)
3. ✅ All existing SFTP operations work unchanged (password auth, downloads, uploads, etc.)
4. ✅ ViewModel simplified (no conditional logic)
5. ✅ Build passes, APK compiles
6. ✅ -357 LOC (dead code eliminated)

## Technical Approach

### Phase 1: Remove Dead Code
- Delete `app/src/main/java/com/darkssh/client/transport/SftpClient.kt`
- Impact: 357 LOC removed

### Phase 2: Add Missing Feature
Add `connectWithKey(keyPair: KeyPair)` to `SftpClientSSHJ`:
```kotlin
suspend fun connectWithKey(keyPair: KeyPair): Result<Unit> {
    val keyProvider = object : KeyProvider {
        override fun getPublic() = keyPair.public
        override fun getPrivate() = keyPair.private
        override fun getType() = KeyType.fromKey(keyPair.public)
    }
    ssh.authPublickey(host.username, keyProvider)
    // ...
}
```

### Phase 3: Simplify ViewModel
- Remove `private val useSSHJ = true` flag
- Remove `private var sftpClient: SftpClient?` (old implementation)
- Keep only `private var sftpClient: SftpClientSSHJ?`
- Replace all conditionals:
  ```kotlin
  // Before
  val result = if (useSSHJ) {
      sftpClientSSHJ?.ls(path)
  } else {
      sftpClient?.ls(path)
  }
  
  // After
  val result = sftpClient?.ls(path)
  ```

### Phase 4: Rename for Clarity
- Rename file: `SftpClientSSHJ.kt` → `SftpClient.kt`
- Rename class: `class SftpClientSSHJ` → `class SftpClient`
- Update imports in `SftpViewModel.kt`

### Phase 5: Move Data Classes
Data classes were in old `SftpClient.kt`, need to be in new file:
- `SftpEntry` (file metadata)
- `TransferProgress` (download/upload progress)
- `SftpAuthState` (authentication state machine)

## Alternatives Considered

### Alternative 1: Keep Both Implementations
**Rejected** - Dead code is a liability. The sshlib implementation was never activated (`useSSHJ` always true).

### Alternative 2: Migrate to Apache SSHD Client
**Evaluated and Rejected**

**Pros:**
- Already using Apache SSHD for server
- Would consolidate dependencies (-1.5MB APK)
- More modern library

**Cons:**
- Lower-level API (manual progress tracking like sshlib)
- More code needed (~30 LOC vs 10 LOC for downloads)
- High refactor risk (296 LOC to rewrite)
- Loses best feature of sshj (automatic `TransferListener` callbacks)

**Decision:** Keep sshj for SFTP client. Apache SSHD is better for server use cases.

### Alternative 3: Migrate EVERYTHING to Apache SSHD
Including terminal SSH (replace sshlib).

**Pros:**
- Complete unification of SSH stack
- Could share `ClientSession` between terminal and SFTP

**Cons:**
- Massive refactor (SSH.kt + SftpClient.kt + TerminalBridge.kt)
- High risk of regressions
- Terminal SSH with sshlib works perfectly
- 1-2 weeks effort for marginal gain

**Decision:** Not worth it. Keep sshlib for terminal, sshj for SFTP, sshd for server.

## Dependencies

### Removed
- None (sshj was already a dependency)

### Added
- None

### Changed
- None (continue using sshj 0.38.0)

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Pubkey auth doesn't work | Low | Medium | Test with actual SSH keys before deploying |
| Breaking existing SFTP | Very Low | High | Existing code unchanged (only dead code removed) |
| Missing edge cases | Low | Low | Data classes copied exactly, no logic changes |

## References

- **sshj documentation:** https://github.com/hierynomus/sshj
- **Decision context:** Session discussion about Apache SSHD vs sshj (May 22, 2026)
- **Original implementation:** `SftpClientSSHJ` created for better progress API
