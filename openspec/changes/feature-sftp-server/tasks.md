# Tasks: SFTP Server Implementation

## 1. Dependencies and Project Setup

- [x] 1.1 Add Apache SSHD dependencies to `app/build.gradle.kts`
- [x] 1.2 Sync Gradle and verify dependencies resolve

## 2. Core Server Implementation

- [x] 2.1 Create `SftpServerManager.kt` with `SshServer` initialization
- [x] 2.2 Implement `PasswordAuthenticator` with username/password validation
- [x] 2.3 Configure `SftpSubsystemFactory` for SFTP protocol support
- [x] 2.4 Add `start()` method to bind server to port 2222
- [x] 2.5 Add `stop()` method to unbind and close connections
- [x] 2.6 Add server state tracking (`isRunning: Boolean`)

## 3. Virtual File System

- [x] 3.1 Create `SftpVirtualFileSystemFactory.kt` implementing `FileSystemFactory`
- [x] 3.2 Implement `getFileSystem()` to return root at `/storage/emulated/0`
- [x] 3.3 Test directory listing works for Android storage directories
- [x] 3.4 Test file upload creates files in correct location
- [x] 3.5 Test file download reads from correct location
- [x] 3.6 Test path traversal protection (no access outside root)

## 4. Settings UI Integration

- [x] 4.1 Add "Server Settings" section to `SettingsScreen.kt`
- [x] 4.2 Add "Start Server" button with enabled/disabled state
- [x] 4.3 Add "Stop Server" button visible when server is running
- [x] 4.4 Add server status indicator (Running/Stopped)
- [x] 4.5 Add username and password input fields
- [x] 4.6 Validate username and password are non-empty before allowing start

## 5. State Persistence

- [x] 5.1 Add server config to Preferences DataStore (username, password, port)
- [x] 5.2 Add server running state to DataStore
- [x] 5.3 Load config on app start and restore server if previously running
- [x] 5.4 Save config changes immediately when user edits credentials

## 6. Foreground Service (if applicable)

- [ ] 6.1 Create `SftpServerService` extending `Service`
- [ ] 6.2 Implement `startForeground()` with persistent notification
- [ ] 6.3 Add notification channel for SFTP server
- [ ] 6.4 Add "Stop Server" action to notification
- [ ] 6.5 Update `AndroidManifest.xml` with service declaration and `FOREGROUND_SERVICE` permission

## 7. Testing

- [x] 7.1 Test authentication with correct credentials (paramiko client)
- [x] 7.2 Test authentication failure with incorrect credentials
- [x] 7.3 Test directory listing returns expected contents
- [x] 7.4 Test file upload and verify file exists on device
- [x] 7.5 Test file download and verify contents match
- [x] 7.6 Test ADB port forwarding (`adb forward tcp:2222 tcp:2222`)
- [ ] 7.7 Test network connection over WiFi
- [ ] 7.8 Test large file transfer (100MB+)
- [ ] 7.9 Test multiple concurrent connections

## 8. Error Handling

- [ ] 8.1 Handle port already in use error
- [ ] 8.2 Handle permission denied errors gracefully
- [ ] 8.3 Handle file not found errors
- [ ] 8.4 Add try/catch around server start/stop with user-friendly error messages

## 9. Logging and Debugging

- [x] 9.1 Add Timber logs for server start/stop events
- [x] 9.2 Add Timber logs for authentication attempts (username, success/failure)
- [ ] 9.3 **CRITICAL: Remove password logging from production builds**
- [x] 9.4 Add logs for file operations (upload, download, delete)

## 10. Documentation and Cleanup

- [x] 10.1 Create OpenSpec proposal for SFTP server feature
- [x] 10.2 Create OpenSpec design document
- [x] 10.3 Create OpenSpec spec for `sftp-server` capability
- [x] 10.4 Create OpenSpec tasks checklist
- [ ] 10.5 Update `/home/dark/.agents/skills/darkssh-client/SKILL.md` with SFTP server completion
- [ ] 10.6 Add usage instructions to `TEST_SFTP_SERVER.md`

## 11. Future Enhancements (Not in Scope)

- [ ] 11.1 Add public key authentication
- [ ] 11.2 Add configurable port in UI
- [ ] 11.3 Add auto-start on boot option
- [ ] 11.4 Add network interface binding (localhost vs LAN)
- [ ] 11.5 Add connection history/logs screen
- [ ] 11.6 Add file transfer statistics (bytes sent/received)
