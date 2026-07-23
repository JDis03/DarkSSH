# Design: Refactor SFTP Client - Unify on sshj

## Architecture Overview

### Before Refactor

```
transport/
├── SftpClient.kt (357 LOC) ❌ DEAD CODE
│   ├── data class SftpEntry
│   ├── data class TransferProgress
│   ├── sealed class SftpAuthState
│   └── class SftpClient (sshlib/Trilead)
│       ├── connectWithPassword()
│       ├── connectWithKey() ✅ Has pubkey
│       └── manual buffer loops for downloads
│
└── SftpClientSSHJ.kt (296 LOC) ✅ ACTIVE
    └── class SftpClientSSHJ (sshj/Hierynomus)
        ├── connectWithPassword()
        ├── ❌ NO connectWithKey()
        └── TransferListener for downloads

SftpViewModel.kt (776 LOC)
├── private var sftpClient: SftpClient? = null
├── private var sftpClientSSHJ: SftpClientSSHJ? = null
├── private val useSSHJ = true ← Always true!
└── Conditional logic everywhere:
    if (useSSHJ) { sftpClientSSHJ?.foo() } else { sftpClient?.foo() }
```

### After Refactor

```
transport/
└── SftpClient.kt (360 LOC)
    ├── data class SftpEntry
    ├── data class TransferProgress
    ├── sealed class SftpAuthState
    └── class SftpClient (sshj/Hierynomus)
        ├── connectWithPassword()
        ├── connectWithKey() ✅ NEW
        └── TransferListener for downloads

SftpViewModel.kt (700 LOC)
├── private var sftpClient: SftpClient? = null
└── Direct calls:
    sftpClient?.foo()
```

**Net result:**
- -357 LOC (dead `SftpClient.kt` removed)
- -76 LOC (ViewModel simplified)
- +64 LOC (data classes + `connectWithKey()` added to new file)
- **Total: -369 LOC**

---

## Implementation Details

### 1. connectWithKey() Implementation

**File:** `SftpClient.kt`

```kotlin
suspend fun connectWithKey(keyPair: KeyPair): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val ssh = SSHClient(createConfig())
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        
        ssh.connectTimeout = 30000
        ssh.timeout = 0
        
        ssh.connect(host.hostname, if (host.port <= 0) 22 else host.port)
        ssh.connection.keepAlive.keepAliveInterval = 15
        
        // Convert java.security.KeyPair to sshj KeyProvider
        val keyProvider = object : KeyProvider {
            override fun getPublic(): PublicKey = keyPair.public
            override fun getPrivate(): PrivateKey = keyPair.private
            override fun getType(): KeyType = KeyType.fromKey(keyPair.public)
        }
        
        ssh.authPublickey(host.username, keyProvider)
        ssh.useCompression()
        val sftp = ssh.newSFTPClient()
        sshClient = ssh
        sftpClient = sftp
        Timber.d("SSHJ SFTP session opened (pubkey auth) for ${host.hostname}")
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "SSHJ SFTP pubkey auth failed")
        Result.failure(e)
    }
}
```

**Key points:**
- Reuses `createConfig()` helper (extracted from `connectWithPassword()`)
- Wraps `java.security.KeyPair` in sshj's `KeyProvider` interface
- Uses `KeyType.fromKey()` to auto-detect RSA/ECDSA/Ed25519
- Same connection flow as password auth (keepalive, compression, etc.)

---

### 2. ViewModel Simplification

**File:** `SftpViewModel.kt`

#### Before (example from `listDirectory`):
```kotlin
val result = if (useSSHJ) {
    sftpClientSSHJ?.ls(path) ?: run {
        _uiState.value = _uiState.value.copy(isLoading = false, error = "SFTP not connected")
        return@launch
    }
} else {
    sftpClient?.ls(path) ?: run {
        _uiState.value = _uiState.value.copy(isLoading = false, error = "SFTP not connected")
        return@launch
    }
}
```

#### After:
```kotlin
val result = sftpClient?.ls(path) ?: run {
    _uiState.value = _uiState.value.copy(isLoading = false, error = "SFTP not connected")
    return@launch
}
```

**Pattern applied to:**
- `listDirectory()` - List remote files
- `downloadToStream()` - Download to MediaStore
- `uploadFile()` - Upload from local file
- `deleteEntry()` - Delete file/directory
- `createDirectory()` - Create directory
- `renameEntry()` - Rename file/directory
- `tryReconnectIfNeeded()` - Auto-reconnect on connection loss

**Total occurrences simplified:** 15+ conditional branches removed

---

### 3. Data Classes Migration

Moved from old `SftpClient.kt` to new `SftpClient.kt`:

```kotlin
data class SftpEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long,
    val permissions: String?,
    val modifiedTime: Long?,
)

data class TransferProgress(
    val transferred: Long,
    val total: Long,
    val filePath: String,
    val startTime: Long = System.currentTimeMillis(),
    val currentTime: Long = System.currentTimeMillis(),
) {
    val percentage: Int
    val elapsedSeconds: Double
    val speed: Long
    val speedFormatted: String
}

sealed class SftpAuthState {
    data object Idle : SftpAuthState()
    data object Connecting : SftpAuthState()
    data class NeedsPassword(val hostname: String, val username: String) : SftpAuthState()
    data object Authenticating : SftpAuthState()
    data object Authenticated : SftpAuthState()
    data class Failed(val message: String) : SftpAuthState()
}
```

**No changes** - copied verbatim to maintain compatibility.

---

## File Changes

| File | Before | After | Change | Reason |
|------|--------|-------|--------|--------|
| `SftpClient.kt` | 357 LOC (sshlib) | 360 LOC (sshj) | Replaced | Remove dead code, add data classes |
| `SftpClientSSHJ.kt` | 296 LOC | — | Deleted (renamed) | Renamed to SftpClient.kt |
| `SftpViewModel.kt` | 776 LOC | 700 LOC | -76 LOC | Removed conditionals |
| **Total** | 1429 LOC | 1060 LOC | **-369 LOC** | |

---

## Testing Strategy

### Unit Tests (Future)
```kotlin
class SftpClientTest {
    @Test
    fun `connectWithKey uses KeyProvider correctly`() {
        val keyPair = generateEd25519KeyPair()
        val client = SftpClient(testHost)
        
        val result = runBlocking { client.connectWithKey(keyPair) }
        
        assertThat(result.isSuccess).isTrue()
    }
    
    @Test
    fun `connectWithKey handles unsupported key types`() {
        val invalidKeyPair = generateInvalidKeyPair()
        val client = SftpClient(testHost)
        
        val result = runBlocking { client.connectWithKey(invalidKeyPair) }
        
        assertThat(result.isFailure).isTrue()
    }
}
```

### Manual Testing
1. **Password auth** (existing feature, regression test)
   - Connect to SSH server with password
   - List directory, download file, upload file
   - Expected: Works as before

2. **Pubkey auth** (new feature)
   - Generate SSH keypair in app
   - Add pubkey to server `~/.ssh/authorized_keys`
   - Connect to SFTP with keypair
   - Expected: Authentication succeeds, SFTP operations work

3. **Auto-reconnect** (existing feature, regression test)
   - Connect to SFTP
   - Kill SSH connection on server (`pkill -9 sshd`)
   - Trigger SFTP operation (list directory)
   - Expected: Auto-reconnects with stored password

---

## Edge Cases

### 1. KeyPair Type Detection
**Scenario:** Different key types (RSA, ECDSA, Ed25519)

**Handling:** `KeyType.fromKey(keyPair.public)` auto-detects:
- RSA → `KeyType.RSA`
- ECDSA-256 → `KeyType.ECDSA256`
- ECDSA-384 → `KeyType.ECDSA384`
- ECDSA-521 → `KeyType.ECDSA521`
- Ed25519 → `KeyType.ED25519`

**Fallback:** If unsupported type, `fromKey()` throws → caught in `try-catch` → `Result.failure()`

### 2. Server Rejects Pubkey
**Scenario:** Key not in `authorized_keys`

**Handling:**
- `ssh.authPublickey()` throws `UserAuthException`
- Caught in `try-catch`
- Returns `Result.failure(e)`
- ViewModel shows error: "Pubkey authentication failed"

### 3. Encrypted Private Keys
**Scenario:** KeyPair has encrypted private key

**Current limitation:** Not supported (requires passphrase prompt)

**Future work:** Add `connectWithKey(keyPair, passphrase)` overload

---

## Migration Path

No migration needed - this is internal refactoring. Existing users won't notice any changes (except new pubkey auth feature becomes available).

---

## Performance Impact

**No change** - Same sshj library, same network operations.

Minor improvements:
- ✅ Less branching in ViewModel (slight CPU savings)
- ✅ Less memory (one client instance instead of two variables)

---

## Rollback Plan

If critical bugs are discovered:

1. **Revert commits** - Git revert to before refactor
2. **Emergency fix** - Keep sshj but restore old structure temporarily
3. **Root cause** - Fix issue in new implementation
4. **Re-apply** - Refactor again with fix

**Likelihood:** Very low - existing code paths unchanged, only dead code removed.

---

## References

- **sshj KeyProvider:** https://github.com/hierynomus/sshj/blob/master/src/main/java/net/schmizz/sshj/userauth/keyprovider/KeyProvider.java
- **KeyType enum:** https://github.com/hierynomus/sshj/blob/master/src/main/java/net/schmizz/sshj/common/KeyType.java
- **SSH pubkey auth RFC:** https://datatracker.ietf.org/doc/html/rfc4252#section-7
