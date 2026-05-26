## Why

DarkSSH needs bidirectional file transfer capabilities. While the SFTP client allows downloading files from remote servers, users need a way to transfer files FROM their Android device TO their desktop/laptop without relying on cloud services or USB cables. An embedded SFTP server enables this by allowing any SFTP client (WinSCP, FileZilla, etc.) to connect to the Android device over the network or via ADB port forwarding.

## What Changes

- Add embedded SFTP server using Apache SSHD
- Server runs on configurable port (default: 2222)
- Password authentication with configurable username/password
- Virtual file system rooted at Android primary storage (`/storage/emulated/0`)
- Start/stop server controls in Settings screen
- Server persists across app restarts (foreground service)
- Port forwarding support via `adb forward` for USB connections

## Capabilities

### New Capabilities
- `sftp-server`: SFTP server implementation with authentication, file operations, and lifecycle management

### Modified Capabilities
<!-- No existing capabilities are modified -->

## Impact

**New Dependencies:**
- `org.apache.sshd:sshd-sftp:2.14.0`
- `org.apache.sshd:sshd-core:2.14.0`

**New Files:**
- `app/src/main/java/com/darkssh/client/server/SftpServerManager.kt`
- `app/src/main/java/com/darkssh/client/server/SftpVirtualFileSystemFactory.kt`

**Modified Files:**
- `app/src/main/java/com/darkssh/client/ui/screens/settings/SettingsScreen.kt` (server controls)
- `app/build.gradle.kts` (dependencies)

**Runtime Impact:**
- Foreground service when server is running
- Network port binding (requires INTERNET permission)
- File system access (existing STORAGE permissions)
