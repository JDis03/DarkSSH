## Context

DarkSSH is an Android SSH client that already has an SFTP **client** for downloading files from remote servers. This design adds the reverse capability: an embedded SFTP **server** that allows desktop SFTP clients to connect to the Android device and transfer files bidirectionally.

**Current state:**
- SFTP client uses Apache SSHD library (already a dependency)
- App has storage permissions for `/storage/emulated/0`
- No server component exists

**Constraints:**
- Must work over network AND via `adb forward` (USB debugging)
- Android 7.0+ (API 24+)
- Minimal battery impact (start/stop on demand)

## Goals / Non-Goals

**Goals:**
- Enable file transfers FROM Android TO desktop via SFTP
- Support any standard SFTP client (WinSCP, FileZilla, `sftp`, paramiko)
- Simple configuration (username, password, port)
- Persistent server state across app restarts

**Non-Goals:**
- Public key authentication (future enhancement)
- Multiple user accounts
- Fine-grained permissions (Android storage permissions are sufficient)
- Web UI for file management
- FTP/WebDAV protocols

## Decisions

### 1. Apache SSHD for Server Implementation
**Choice:** Use Apache SSHD `SshServer` + `SftpSubsystemFactory`

**Rationale:**
- Already used for SFTP client (no new dependency)
- Battle-tested, RFC-compliant SFTP implementation
- Supports password and pubkey auth
- Native virtual file system abstraction

**Alternatives considered:**
- Custom SFTP implementation: Too complex, reinventing the wheel
- JSch server: Unmaintained, fewer features

### 2. Virtual File System Root
**Choice:** Root at `/storage/emulated/0` (Android primary storage)

**Rationale:**
- Users expect to see Downloads/, Pictures/, Documents/
- Matches Android storage semantics
- Already have STORAGE permissions
- Consistent with SFTP client behavior

**Alternatives considered:**
- App-private directory: Too limited, users want access to media
- Full `/` access: Security risk, permission complexity

### 3. Password Authentication Only (Initial Version)
**Choice:** Username/password auth with hardcoded default (`root:darkssh`)

**Rationale:**
- Simplest to implement and configure
- Sufficient for local network / ADB use cases
- Public key auth can be added later

**Trade-off:**
- Password transmitted over network (mitigated by SSH encryption)
- Single user account (sufficient for personal device)

### 4. Foreground Service for Server Lifecycle
**Choice:** Server runs in foreground service, not background

**Rationale:**
- Persistent notification keeps server visible
- Avoids Android background execution limits
- User knows when server is running (security)

**Alternatives considered:**
- Background service: Would be killed by Doze/App Standby
- WorkManager: Not suitable for long-running network services

### 5. State Persistence via DataStore
**Choice:** Save server running state + config in Preferences DataStore

**Rationale:**
- Server auto-restarts after app is killed
- Preserves user's last configuration
- Lightweight, async-friendly

## Risks / Trade-offs

**[R1] Server exposed on network → Security risk**
- **Mitigation:** Default to localhost binding, warn user about network exposure
- **Future:** Add option for LAN-only binding

**[R2] Password logged in debug builds → Credential leak**
- **Mitigation:** Remove password logging before release, use `BuildConfig.DEBUG` guards
- **Status:** Currently logging password for testing (MUST remove)

**[R3] File system race conditions → Crashes or corruption**
- **Mitigation:** Apache SSHD handles locking, Android storage is already multi-process safe

**[R4] Battery drain from idle server → User complaints**
- **Mitigation:** Manual start/stop controls, no auto-start on boot (yet)

## Migration Plan

**Deployment:**
1. Build and install debug APK with server feature
2. User navigates to Settings → Server Settings
3. Configure credentials, press Start
4. Connect from desktop via `sftp -P 2222 root@<device-ip>`
5. For USB: `adb forward tcp:2222 tcp:2222 && sftp -P 2222 root@localhost`

**Rollback:**
- No database migrations, purely additive feature
- Server disabled by default, no impact if not used

## Open Questions

- **Q1:** Should server auto-start on app launch if previously running?
  - **Answer:** No for now (security/battery), can add toggle later

- **Q2:** Expose server port in UI or keep hardcoded?
  - **Answer:** Hardcoded 2222 for now, configurable in future

- **Q3:** Add public key authentication?
  - **Answer:** Out of scope for v1, add in Phase 2: Key Management
