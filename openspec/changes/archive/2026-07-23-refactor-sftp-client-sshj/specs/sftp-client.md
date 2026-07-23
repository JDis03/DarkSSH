# Spec Delta: SFTP Client Refactoring

## ADDED Requirements

### R1: Single SFTP Client Implementation

**Requirement:** The application SHALL maintain exactly one SFTP client implementation.

**Rationale:** Multiple implementations increase maintenance burden and create confusion.

**Acceptance Criteria:**
- Only one `SftpClient` class exists in codebase
- No conditional logic choosing between implementations
- ViewModel uses single client variable

**Verification:**
```bash
find app/src -name "*SftpClient*.kt" | wc -l
# Expected: 1 (only SftpClient.kt)

grep -r "useSSHJ\|if.*sshj.*else" app/src/main/java/com/darkssh/client/ui/screens/viewmodel/
# Expected: no matches
```

---

### R2: Public Key Authentication Support

**Requirement:** SFTP client SHALL support authentication with SSH keypairs (RSA, ECDSA, Ed25519).

**Rationale:** Standard SSH feature, enables passwordless authentication.

**Acceptance Criteria:**
- `SftpClient` provides `connectWithKey(keyPair: KeyPair): Result<Unit>`
- Accepts `java.security.KeyPair` instances
- Auto-detects key type (RSA/ECDSA/Ed25519)
- Returns `Result.success()` on successful auth
- Returns `Result.failure()` on auth failure or unsupported key type

**Implementation:**
```kotlin
suspend fun connectWithKey(keyPair: KeyPair): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        // Wrap java.security.KeyPair in sshj KeyProvider
        val keyProvider = object : KeyProvider {
            override fun getPublic() = keyPair.public
            override fun getPrivate() = keyPair.private
            override fun getType() = KeyType.fromKey(keyPair.public)
        }
        ssh.authPublickey(host.username, keyProvider)
        // ... establish SFTP session
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

**Test Cases:**
1. RSA 2048 key → authentication succeeds
2. ECDSA P-256 key → authentication succeeds
3. Ed25519 key → authentication succeeds
4. Key not in server's `authorized_keys` → authentication fails gracefully
5. Unsupported key type → returns failure with error message

---

### R3: Data Class Locality

**Requirement:** SFTP-related data classes SHALL be defined in the same file as `SftpClient`.

**Rationale:** Cohesion - related types grouped together for discoverability.

**Data classes:**
- `SftpEntry` - Remote file/directory metadata
- `TransferProgress` - Upload/download progress tracking
- `SftpAuthState` - Authentication state machine

**Location:** `app/src/main/java/com/darkssh/client/transport/SftpClient.kt`

---

## MODIFIED Requirements

### M1: SFTP Authentication Methods

**Before:**
- Password authentication only (in active implementation)
- Pubkey authentication existed but in unused code path

**After:**
- Password authentication: `connectWithPassword(password: String)`
- Pubkey authentication: `connectWithKey(keyPair: KeyPair)` ← NEW

**Impact:** Enables passwordless workflows, SSH key management integration.

---

### M2: Code Complexity

**Before:**
- Dual implementation (sshlib + sshj)
- Conditional branching in ViewModel (15+ locations)
- 1429 total LOC across 3 files

**After:**
- Single implementation (sshj only)
- Direct method calls (no conditionals)
- 1060 total LOC (-369 LOC = 26% reduction)

**Metrics:**

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| SFTP client files | 2 | 1 | -50% |
| Dead code LOC | 357 | 0 | -100% |
| ViewModel LOC | 776 | 700 | -10% |
| Conditional branches | 15+ | 0 | -100% |

---

## REMOVED Requirements

### R_OLD_1: Support for Alternative SFTP Implementations

**Removed:** Ability to switch between sshlib and sshj implementations.

**Reason:** `useSSHJ` flag was always `true` in production. Dead code removed.

**Migration:** None needed - users were already on sshj implementation.

---

## Invariants

### I1: SFTP Protocol Compatibility

**Invariant:** All SFTP operations SHALL use SFTP protocol v3 or higher.

**Justification:** sshj `SFTPClient` supports v3-v6. All modern SSH servers support v3+.

**Verification:** sshj negotiates highest common version during connection.

---

### I2: Progress Tracking Consistency

**Invariant:** All file transfer operations (download/upload) SHALL report progress via callbacks.

**Mechanism:** sshj `TransferListener` interface

```kotlin
transfer.transferListener = object : TransferListener {
    override fun file(name: String, size: Long): StreamCopier.Listener {
        return object : StreamCopier.Listener {
            override fun reportProgress(transferred: Long) {
                onProgress(TransferProgress(transferred, size, ...))
            }
        }
    }
}
```

**Guarantee:** Every transfer operation provides real-time progress updates.

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

## Dependencies

### Unchanged

| Library | Version | Usage |
|---------|---------|-------|
| sshj | 0.38.0 | SFTP client (existing) |
| sshlib | 2.2.47-SNAPSHOT | Terminal SSH (existing) |
| Apache SSHD | 2.14.0 | SFTP server (existing) |

**No new dependencies added.**

---

## Migration Guide

### For Developers

**Before (dual implementation):**
```kotlin
val client = if (useSSHJ) {
    SftpClientSSHJ(host)
} else {
    SftpClient(host)  // Old sshlib version
}
client.connectWithPassword(password)
```

**After (unified):**
```kotlin
val client = SftpClient(host)
client.connectWithPassword(password)
// OR
client.connectWithKey(keyPair)
```

### For Users

**No changes** - Existing SFTP functionality works identically.

**New capability:** Can now authenticate with SSH keys instead of passwords (requires app update to expose UI).

---

## Testing Strategy

### Regression Tests

Verify existing functionality unchanged:

1. **Password authentication**
   - Connect to server with valid password → succeeds
   - Connect with invalid password → fails gracefully
   - Auto-reconnect on connection loss → succeeds

2. **File operations**
   - List directory → returns entries
   - Download file → progress reported, file saved
   - Upload file → progress reported, file transferred
   - Create directory → directory created
   - Delete file/directory → file removed
   - Rename file → file renamed

### New Feature Tests

Verify pubkey authentication:

1. **Valid keypair**
   - RSA 2048 key in `authorized_keys` → authentication succeeds
   - ECDSA P-256 key → authentication succeeds
   - Ed25519 key → authentication succeeds

2. **Invalid scenarios**
   - Key not in `authorized_keys` → auth fails with clear error
   - Corrupt private key → auth fails gracefully
   - Server doesn't support pubkey auth → falls back to error

### Performance Tests

- Download 100MB file → same speed as before (sshj unchanged)
- Upload 100MB file → same speed as before
- List directory with 1000+ files → same performance

**Expected:** No performance regression (same underlying library).

---

## Rollback Plan

If critical issues discovered:

**Step 1:** Identify issue
- Is it in new `connectWithKey()`? → Fix in place (isolated code)
- Is it in refactored ViewModel? → Revert to previous version

**Step 2:** Hotfix or revert
```bash
# Option A: Hotfix
git commit --fixup HEAD~1
./gradlew assembleRelease

# Option B: Full revert
git revert HEAD~5..HEAD
./gradlew assembleRelease
```

**Step 3:** Root cause analysis
- Run full test suite
- Check logs for exceptions
- Reproduce on development device

**Likelihood of rollback needed:** Very low - minimal logic changes, mostly code deletion.

---

## References

- **sshj KeyProvider:** https://github.com/hierynomus/sshj/blob/master/src/main/java/net/schmizz/sshj/userauth/keyprovider/KeyProvider.java
- **SSH Public Key Auth (RFC 4252):** https://datatracker.ietf.org/doc/html/rfc4252#section-7
- **SFTP Protocol v3:** https://datatracker.ietf.org/doc/html/draft-ietf-secsh-filexfer-02
- **Original discussion:** Decision log in `proposal.md` and `design.md`
