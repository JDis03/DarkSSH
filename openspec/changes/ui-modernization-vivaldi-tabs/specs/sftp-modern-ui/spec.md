## ADDED Requirements

### Requirement: File browser uses card-based layout
The system SHALL display files and folders as Material 3 cards with elevation, rounded corners, and visual hierarchy instead of simple list items.

#### Scenario: Display file as card
- **WHEN** SFTP screen shows files in a directory
- **THEN** each file appears as a card with file icon, name, size, modified date, and subtle elevation

#### Scenario: Distinguish folders from files
- **WHEN** directory contains both files and folders
- **THEN** folders display with folder icon and different card styling (higher elevation or accent tint)

#### Scenario: Card shows file metadata
- **WHEN** user views file card
- **THEN** card displays: icon (based on extension), filename, size (human-readable), modified timestamp, permissions indicator

#### Scenario: Empty directory state
- **WHEN** directory contains no files
- **THEN** system displays centered empty state card with illustration and "No files" message

### Requirement: Breadcrumb navigation for path
The system SHALL display current directory path as interactive breadcrumb bar allowing quick navigation to parent directories.

#### Scenario: Display breadcrumb path
- **WHEN** user navigates to `/home/user/projects/myapp/src/`
- **THEN** breadcrumb shows: [🏠] / home / user / projects / myapp / src with each segment tappable

#### Scenario: Navigate to parent via breadcrumb
- **WHEN** user taps "projects" segment in breadcrumb
- **THEN** system navigates directly to `/home/user/projects/` without intermediate navigation

#### Scenario: Breadcrumb scrolls horizontally
- **WHEN** path exceeds screen width
- **THEN** breadcrumb scrolls horizontally with current directory always visible on right

#### Scenario: Root directory shortcut
- **WHEN** user taps [🏠] icon in breadcrumb
- **THEN** system navigates to user's home directory (~)

### Requirement: Swipe actions for file operations
The system SHALL support swipe gestures on file cards to reveal contextual actions (delete, rename, download, permissions).

#### Scenario: Swipe left reveals delete
- **WHEN** user swipes file card left
- **THEN** delete button slides in from right with red background and trash icon

#### Scenario: Swipe right reveals download
- **WHEN** user swipes file card right
- **THEN** download button slides in from left with cyan background and download icon

#### Scenario: Long-press shows full menu
- **WHEN** user long-presses file card
- **THEN** system displays bottom sheet with all actions: Download, Delete, Rename, Permissions, Copy, Move

#### Scenario: Swipe dismisses selection
- **WHEN** user swipes card in opposite direction after revealing action
- **THEN** action button slides out and card returns to neutral state

### Requirement: Drag-drop upload from Android system
The system SHALL support drag-drop file upload from Android file picker or other apps via system share intent.

#### Scenario: Drag file into SFTP view
- **WHEN** user drags file from file manager onto DarkSSH SFTP screen
- **THEN** system highlights drop zone and uploads file to current directory on drop

#### Scenario: Share file to DarkSSH
- **WHEN** user shares file from another app and selects DarkSSH
- **THEN** DarkSSH opens SFTP screen, prompts for destination directory, and uploads file

#### Scenario: Multiple file upload
- **WHEN** user selects "Upload" FAB and picks 5 files from file picker
- **THEN** system uploads all 5 files concurrently with individual progress cards

#### Scenario: Upload progress cards
- **WHEN** files are uploading
- **THEN** SFTP screen shows temporary progress cards above file list with filename, progress bar, speed, cancel button

### Requirement: Visual feedback for file operations
The system SHALL provide immediate visual feedback (animations, toasts, snackbars) for all file operations with undo support where applicable.

#### Scenario: Delete animation
- **WHEN** user deletes file via swipe action
- **THEN** card animates out with fade+slide, snackbar appears with "Undo" button for 5 seconds

#### Scenario: Rename inline editing
- **WHEN** user taps rename action
- **THEN** filename becomes editable text field with keyboard focus, [✓] and [✗] buttons appear

#### Scenario: Copy/paste visual indicator
- **WHEN** user copies files to clipboard
- **THEN** selected cards show cyan border, snackbar displays "3 files copied" with paste action

#### Scenario: Error state on failed operation
- **WHEN** file operation fails (permission denied, disk full)
- **THEN** card shakes with error animation, error snackbar shows with retry button

### Requirement: Search and filter functionality
The system SHALL provide search bar to filter files by name with real-time updates and filter chips for file types.

#### Scenario: Search files by name
- **WHEN** user types "config" in search bar
- **THEN** file list filters to show only files/folders containing "config" (case-insensitive)

#### Scenario: Clear search
- **WHEN** user taps [×] in search bar
- **THEN** search text clears and full file list reappears

#### Scenario: Filter by file type
- **WHEN** user taps "Images" filter chip
- **THEN** file list shows only files with extensions: .jpg, .png, .gif, .webp, .svg

#### Scenario: Multiple filter chips
- **WHEN** user enables "Documents" and "Archives" chips
- **THEN** file list shows files matching either filter category (OR logic)

### Requirement: Multi-select mode for batch operations
The system SHALL support multi-select mode enabling batch download, delete, or move of multiple files.

#### Scenario: Enter multi-select mode
- **WHEN** user long-presses a file card
- **THEN** card shows checkbox overlay, FAB changes to batch actions menu, other cards become tappable for selection

#### Scenario: Select multiple files
- **WHEN** user taps 3 file cards in multi-select mode
- **THEN** all 3 cards show checked state, top bar displays "3 selected"

#### Scenario: Batch delete
- **WHEN** user selects 5 files and taps delete action
- **THEN** confirmation dialog shows "Delete 5 files?", on confirm all cards animate out and files are deleted

#### Scenario: Exit multi-select mode
- **WHEN** user taps back button or [×] in top bar during multi-select
- **THEN** all checkboxes disappear, selections clear, FAB returns to normal state

### Requirement: Sorting and view modes
The system SHALL support multiple sort orders (name, size, date, type) and view modes (grid, list, compact list).

#### Scenario: Sort by name ascending
- **WHEN** user selects "Sort by Name ↑" from menu
- **THEN** files are sorted alphabetically A-Z with folders first

#### Scenario: Sort by size descending
- **WHEN** user selects "Sort by Size ↓" from menu
- **THEN** largest files appear first, folders grouped at top

#### Scenario: Switch to grid view
- **WHEN** user taps grid icon in toolbar
- **THEN** files display as 2-column grid with larger icons and less metadata

#### Scenario: Switch to compact list
- **WHEN** user taps compact list icon
- **THEN** files display as dense list with smaller icons, single-line layout for more items per screen

### Requirement: File preview and quick actions
The system SHALL display file thumbnails for images and provide quick actions (share, open with) directly from file card.

#### Scenario: Image thumbnail preview
- **WHEN** file is image type (.jpg, .png)
- **THEN** card displays thumbnail preview loaded asynchronously with Coil

#### Scenario: Quick share action
- **WHEN** user taps share icon on file card
- **THEN** system downloads file to cache, opens Android share sheet with file attached

#### Scenario: Open with action
- **WHEN** user taps "Open" on downloaded file
- **THEN** system opens Android intent chooser with apps supporting file MIME type

#### Scenario: Text file preview
- **WHEN** user taps small text file (.txt, .log, .conf)
- **THEN** bottom sheet slides up showing file content preview (first 500 lines) with syntax highlighting
