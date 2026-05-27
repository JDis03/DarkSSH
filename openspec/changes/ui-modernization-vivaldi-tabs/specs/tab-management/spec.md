## ADDED Requirements

### Requirement: User can create new tabs

The system SHALL allow users to create new tabs for SSH terminal or SFTP browser sessions. Each tab SHALL be associated with a specific host from the saved hosts list.

#### Scenario: Create SSH terminal tab
- **WHEN** user taps the [+] button in the tab bar and selects "SSH Terminal" and chooses a host
- **THEN** system creates a new tab, assigns it a unique ID, establishes SSH connection to the host, and displays the terminal screen

#### Scenario: Create SFTP browser tab
- **WHEN** user taps the [+] button and selects "SFTP Browser" and chooses a host
- **THEN** system creates a new tab, assigns it a unique ID, establishes SFTP connection to the host, and displays the file browser

#### Scenario: Create tab from host list
- **WHEN** user long-presses a host in the host list and selects "Open in new tab"
- **THEN** system presents tab type selection (SSH or SFTP) and creates the corresponding tab

#### Scenario: Maximum tab limit reached
- **WHEN** user attempts to create a tab when 10 tabs are already open
- **THEN** system displays a toast message "Maximum 10 tabs reached" and does not create the tab

### Requirement: User can close tabs

The system SHALL allow users to close individual tabs. Closing a tab SHALL disconnect the associated SSH/SFTP session and remove the tab from persistence.

#### Scenario: Close tab with X button
- **WHEN** user taps the [×] button on a tab
- **THEN** system closes the tab, disconnects the session, and removes it from the database

#### Scenario: Close inactive SSH tab
- **WHEN** user closes a tab with an inactive SSH connection (already disconnected)
- **THEN** system immediately closes the tab without confirmation dialog

#### Scenario: Close active SSH tab with confirmation
- **WHEN** user closes a tab with an active SSH connection
- **THEN** system displays confirmation dialog "Close active session?" with Cancel and Close buttons

#### Scenario: Confirm close active SSH tab
- **WHEN** user confirms closing an active SSH tab
- **THEN** system disconnects the SSH session, removes the TerminalBridge, and closes the tab

#### Scenario: Close last remaining tab
- **WHEN** user closes the last tab in the tab bar
- **THEN** system displays the host list screen with no tabs open

#### Scenario: Swipe to close tab
- **WHEN** user swipes left on a tab in the tab bar
- **THEN** system displays swipe action with delete icon and closes the tab when swipe completes

### Requirement: User can reorder tabs

The system SHALL allow users to reorder tabs via drag-and-drop gesture. The new order SHALL persist across app restarts.

#### Scenario: Drag tab to new position
- **WHEN** user long-presses a tab and drags it to a different position
- **THEN** system provides haptic feedback, animates the tab movement, and updates the position field in the database

#### Scenario: Drop tab at new position
- **WHEN** user releases the drag gesture
- **THEN** system finalizes the new tab order and persists it to the Room database

#### Scenario: Restore tab order on app restart
- **WHEN** app launches with existing tabs in the database
- **THEN** system restores tabs in the exact order based on the position field (ascending)

### Requirement: Tabs persist across app restarts

The system SHALL save all open tabs to the Room database and restore them when the app launches. SSH connections SHALL NOT automatically reconnect (user initiates connection manually).

#### Scenario: Save tabs on app background
- **WHEN** app moves to background (onPause)
- **THEN** system saves current tab state (id, type, hostId, position) to Room database

#### Scenario: Restore tabs on app launch
- **WHEN** app launches and database contains tabs
- **THEN** system recreates the tab bar with all tabs in correct order, but does not initiate connections

#### Scenario: Restore selected tab
- **WHEN** app restores tabs from database
- **THEN** system selects the tab that was active when app was last closed (or first tab if none marked)

#### Scenario: Clean up orphaned tabs
- **WHEN** app restores tabs but finds a tab referencing a deleted host
- **THEN** system removes the orphaned tab from the database and logs a warning

### Requirement: Each tab has visual indicators

The system SHALL display visual indicators on each tab showing the tab type (SSH or SFTP), connection status, and host name.

#### Scenario: Display SSH tab icon
- **WHEN** tab represents an SSH terminal session
- **THEN** system displays terminal icon (🖥️) on the tab

#### Scenario: Display SFTP tab icon
- **WHEN** tab represents an SFTP browser session
- **THEN** system displays folder icon (📁) on the tab

#### Scenario: Display host name on tab
- **WHEN** tab is displayed in the tab bar
- **THEN** system shows the host's nickname (or IP if no nickname) truncated to 20 characters with ellipsis

#### Scenario: Highlight active tab
- **WHEN** user views the tab bar
- **THEN** system highlights the currently selected tab with primary color background and elevated appearance

#### Scenario: Show connection status badge
- **WHEN** SSH tab is actively connected
- **THEN** system displays a small green dot indicator on the tab icon

#### Scenario: Show disconnected status
- **WHEN** SSH tab is disconnected or connection failed
- **THEN** system displays tab icon in muted color without status badge

### Requirement: Tab state survives configuration changes

The system SHALL maintain tab state (selected tab, scroll position) when device rotates or configuration changes occur.

#### Scenario: Rotate device with active tab
- **WHEN** user rotates device from portrait to landscape
- **THEN** system preserves the currently selected tab and displays it in the new orientation

#### Scenario: Preserve scroll position in tab bar
- **WHEN** user has scrolled the tab bar to view tabs beyond the screen width, then rotates device
- **THEN** system restores the scroll position to keep the selected tab visible

### Requirement: Tab data model is queryable

The system SHALL provide Room database queries for tab management operations including listing, filtering, and sorting tabs.

#### Scenario: Query all tabs ordered by position
- **WHEN** TabRepository calls getAllTabs()
- **THEN** system returns Flow<List<Tab>> ordered by position ascending

#### Scenario: Query tabs by host ID
- **WHEN** TabRepository calls getTabsByHostId(hostId)
- **THEN** system returns all tabs associated with that host (both SSH and SFTP)

#### Scenario: Query tab by ID
- **WHEN** TabRepository calls getTabById(tabId)
- **THEN** system returns the specific Tab or null if not found

#### Scenario: Delete tab by ID
- **WHEN** TabRepository calls deleteTab(tabId)
- **THEN** system removes the tab from the database and returns success

#### Scenario: Update tab position
- **WHEN** TabRepository calls updateTabPosition(tabId, newPosition)
- **THEN** system updates the position field and reorders other tabs if necessary
