## ADDED Requirements

### Requirement: Server lifecycle management
The system SHALL allow users to start and stop the SFTP server on demand through the Settings screen.

#### Scenario: Start server successfully
- **WHEN** user navigates to Settings → Server Settings and presses "Start Server"
- **THEN** SFTP server binds to port 2222 and accepts connections
- **AND** persistent notification shows "SFTP Server Running"
- **AND** server state persists across app restarts

#### Scenario: Stop server
- **WHEN** user presses "Stop Server" while server is running
- **THEN** SFTP server unbinds port 2222 and rejects new connections
- **AND** existing connections are gracefully closed
- **AND** foreground notification is dismissed

#### Scenario: Server auto-restart after app kill
- **WHEN** server is running and app is killed by system
- **THEN** server restarts when app relaunches
- **AND** previous configuration (port, credentials) is restored

### Requirement: Password authentication
The system SHALL authenticate SFTP clients using username and password credentials.

#### Scenario: Successful authentication
- **WHEN** SFTP client connects with correct username and password
- **THEN** server accepts connection and allows file operations
- **AND** authentication event is logged with username

#### Scenario: Failed authentication
- **WHEN** SFTP client connects with incorrect credentials
- **THEN** server rejects connection with "Authentication failed" error
- **AND** failed authentication attempt is logged

#### Scenario: Default credentials
- **WHEN** server is started for the first time
- **THEN** default credentials are `root:darkssh`
- **AND** user can modify credentials in Settings

### Requirement: File system access
The system SHALL provide SFTP clients with access to Android primary storage rooted at `/storage/emulated/0`.

#### Scenario: List directory contents
- **WHEN** SFTP client requests directory listing for `/`
- **THEN** server returns contents of `/storage/emulated/0`
- **AND** directories include Downloads/, Pictures/, Documents/, etc.

#### Scenario: Upload file
- **WHEN** SFTP client uploads file to `/test.txt`
- **THEN** file is written to `/storage/emulated/0/test.txt`
- **AND** file is readable by other Android apps
- **AND** file appears in device file manager

#### Scenario: Download file
- **WHEN** SFTP client downloads `/Pictures/photo.jpg`
- **THEN** server reads `/storage/emulated/0/Pictures/photo.jpg`
- **AND** file contents are transmitted correctly

#### Scenario: Create directory
- **WHEN** SFTP client creates directory `/NewFolder`
- **THEN** directory is created at `/storage/emulated/0/NewFolder`
- **AND** directory is visible in Android file manager

#### Scenario: Delete file
- **WHEN** SFTP client deletes `/test.txt`
- **THEN** file is removed from `/storage/emulated/0/test.txt`
- **AND** file no longer exists in device storage

#### Scenario: Access outside root
- **WHEN** SFTP client attempts to access `/data` or `../../../system`
- **THEN** server treats all paths as relative to `/storage/emulated/0`
- **AND** no access to system directories outside storage root

### Requirement: Network and USB connectivity
The system SHALL support SFTP connections over local network and via ADB port forwarding.

#### Scenario: Network connection
- **WHEN** SFTP client connects to `<device-ip>:2222` over WiFi
- **THEN** server accepts connection and authenticates
- **AND** all file operations work as expected

#### Scenario: ADB port forwarding
- **WHEN** user runs `adb forward tcp:2222 tcp:2222`
- **AND** SFTP client connects to `localhost:2222`
- **THEN** server accepts connection via forwarded port
- **AND** all file operations work as expected

### Requirement: Error handling
The system SHALL handle SFTP protocol errors and file system errors gracefully.

#### Scenario: Port already in use
- **WHEN** user starts server but port 2222 is already bound
- **THEN** server displays error "Port 2222 already in use"
- **AND** server state remains stopped

#### Scenario: Permission denied
- **WHEN** SFTP client attempts to write to read-only directory
- **THEN** server returns "Permission denied" error
- **AND** operation fails gracefully without crash

#### Scenario: File not found
- **WHEN** SFTP client requests `/nonexistent.txt`
- **THEN** server returns "No such file or directory" error

#### Scenario: Invalid credentials format
- **WHEN** user sets empty username or password
- **THEN** Settings screen shows validation error
- **AND** "Start Server" button is disabled

### Requirement: Performance and resource management
The system SHALL minimize battery and memory usage when server is idle.

#### Scenario: Idle server
- **WHEN** server is running with no active connections
- **THEN** CPU usage is near zero
- **AND** memory footprint is under 10MB

#### Scenario: Multiple concurrent connections
- **WHEN** 3 SFTP clients connect simultaneously
- **THEN** server handles all connections without degradation
- **AND** file transfers do not interfere with each other

#### Scenario: Large file transfer
- **WHEN** SFTP client uploads 100MB file
- **THEN** transfer completes successfully
- **AND** memory usage remains stable (streaming, not buffering entire file)

### Requirement: Configuration persistence
The system SHALL persist server configuration across app restarts and device reboots.

#### Scenario: Configuration saved
- **WHEN** user changes username to "admin" and password to "secret"
- **AND** restarts app
- **THEN** credentials remain "admin:secret"

#### Scenario: Server state restored
- **WHEN** server is running and user force-stops app
- **AND** relaunches app
- **THEN** server resumes running state
- **AND** foreground notification reappears
