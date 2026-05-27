## 1. Database Schema - Tab Entity

- [ ] 1.1 Create Tab Room entity (id: String UUID, type: TabType enum, hostId: Long, position: Int, createdAt: Long)
- [ ] 1.2 Create TabType enum with values: SSH_TERMINAL, SFTP_BROWSER
- [ ] 1.3 Create TabDao with insert, delete, update, queryAll, queryByHostId, updatePosition
- [ ] 1.4 Create TabRepository with Hilt injection wrapping TabDao operations
- [ ] 1.5 Create database migration (add tabs table with schema)
- [ ] 1.6 Test migration on existing database (verify no data loss)

## 2. Tab State Management - TabManager ViewModel

- [ ] 2.1 Create TabManager ViewModel with StateFlow<List<Tab>> for active tabs
- [ ] 2.2 Implement createTab(type: TabType, hostId: Long): Tab function
- [ ] 2.3 Implement closeTab(tabId: String) with Room deletion
- [ ] 2.4 Implement reorderTabs(fromIndex: Int, toIndex: Int) with position updates
- [ ] 2.5 Add StateFlow<Int> for currentTabIndex (selected tab)
- [ ] 2.6 Implement switchTab(index: Int) function
- [ ] 2.7 Add tab restoration on ViewModel init (load from Room)
- [ ] 2.8 Implement saveTabState() on app pause
- [ ] 2.9 Inject TabManager with @Singleton Hilt scope
- [ ] 2.10 Add unit tests for tab lifecycle operations

## 3. Theme System - Dark Palette Implementation

- [ ] 3.1 Create DarkSSHTheme.kt with darkColorScheme() Material 3 extension
- [ ] 3.2 Define color tokens: background #1A1A1A, surface #2B2B2B, primary #00B8D4 (cyan)
- [ ] 3.3 Define onSurface #FFFFFF (87% opacity), onPrimary #000000
- [ ] 3.4 Add elevation tonal system (2dp = 5% tint, 16dp = 12% tint)
- [ ] 3.5 Create Typography tokens with Roboto font family (display, headline, title, body, label)
- [ ] 3.6 Update DarkSSHApp.kt to apply DarkSSHTheme wrapping MaterialTheme
- [ ] 3.7 Replace all hardcoded colors with MaterialTheme.colorScheme tokens
- [ ] 3.8 Test theme on all screens (MainActivity, ConsoleScreen, SftpScreen, SettingsScreen)

## 4. Theme Persistence - DataStore Integration

- [ ] 4.1 Create ThemePreferences.kt with DataStore<Preferences>
- [ ] 4.2 Add saveThemePreference(theme: String) suspend function
- [ ] 4.3 Add getThemePreference(): Flow<String> function
- [ ] 4.4 Inject ThemePreferences with Hilt @Singleton
- [ ] 4.5 Update DarkSSHApp to collect themePreference Flow on launch
- [ ] 4.6 Apply saved theme before setContent {} (avoid flash)
- [ ] 4.7 Default to "dark" theme if no preference exists
- [ ] 4.8 Test theme persistence across app restarts

## 5. Terminal Themes - Color Scheme Presets

- [ ] 5.1 Create TerminalTheme data class (name, background, foreground, cursor, ansi0-15: List<Color>)
- [ ] 5.2 Create ThemePresets.kt with predefined themes: Dracula, Nord, One Dark, Solarized Dark
- [ ] 5.3 Add Dracula theme (background #282A36, foreground #F8F8F2, ANSI colors)
- [ ] 5.4 Add Nord theme (background #2E3440, foreground #D8DEE9, ANSI colors)
- [ ] 5.5 Add One Dark theme (background #282C34, foreground #ABB2BF, ANSI colors)
- [ ] 5.6 Add Solarized Dark theme (background #002B36, foreground #839496, ANSI colors)
- [ ] 5.7 Create TerminalThemeManager with StateFlow<TerminalTheme> per tab
- [ ] 5.8 Add setThemeForTab(tabId: String, theme: TerminalTheme) function
- [ ] 5.9 Persist terminal theme preference per tab in Room (add themeId column to Tab entity)
- [ ] 5.10 Update TerminalRenderer to apply theme colors to TerminalEmulator

## 6. Terminal Font Customization

- [ ] 6.1 Add JetBrains Mono font files to app/src/main/res/font/
- [ ] 6.2 Add Fira Code font files (with ligature support)
- [ ] 6.3 Add Ubuntu Mono font files
- [ ] 6.4 Add Roboto Mono font files (fallback)
- [ ] 6.5 Create TerminalFont enum (JETBRAINS_MONO, FIRA_CODE, UBUNTU_MONO, ROBOTO_MONO)
- [ ] 6.6 Add font selection in TerminalThemeManager
- [ ] 6.7 Update TerminalRenderer to apply selected Typeface to TerminalEmulator
- [ ] 6.8 Add font size slider in settings (8sp to 24sp range)
- [ ] 6.9 Persist font preference in DataStore
- [ ] 6.10 Test ligature rendering with Fira Code (-> == != operators)

## 7. Tab Bar UI - ScrollableTabRow

- [ ] 7.1 Create TabBar.kt composable with ScrollableTabRow
- [ ] 7.2 Add Tab items with icon, hostname, type indicator (SSH icon vs SFTP icon)
- [ ] 7.3 Implement selectedTabIndex state binding
- [ ] 7.4 Add onClick handler to switch tabs via TabManager.switchTab()
- [ ] 7.5 Add [+] button trailing ScrollableTabRow for createTab()
- [ ] 7.6 Add [×] close button on each tab with confirmation dialog
- [ ] 7.7 Style tabs with Material 3 primary indicator (cyan underline for selected)
- [ ] 7.8 Add auto-scroll to selected tab when tabIndex changes
- [ ] 7.9 Test with 1, 3, 10 tabs (verify scrolling, overflow handling)

## 8. Tab Drag-Drop Reordering

- [ ] 8.1 Add ReorderableState from compose-reorderable library (or custom implementation)
- [ ] 8.2 Wrap ScrollableTabRow with detectReorderAfterLongPress modifier
- [ ] 8.3 Implement onMove callback to update tab positions in TabManager
- [ ] 8.4 Add haptic feedback on drag start (HapticFeedback.LongPress)
- [ ] 8.5 Animate tab positions during reorder (animateItemPlacement modifier)
- [ ] 8.6 Persist new tab order to Room on drag end
- [ ] 8.7 Test reordering 5 tabs with different types (SSH + SFTP mixed)

## 9. HorizontalPager Integration

- [ ] 9.1 Add HorizontalPager composable below TabBar in MainActivity
- [ ] 9.2 Set pageCount = tabs.size from TabManager StateFlow
- [ ] 9.3 Bind pagerState.currentPage to TabManager.currentTabIndex
- [ ] 9.4 Render page content based on Tab.type (SSH_TERMINAL → ConsoleScreen, SFTP_BROWSER → SftpScreen)
- [ ] 9.5 Pass hostId and tabId to ConsoleScreen and SftpScreen composables
- [ ] 9.6 Add swipe gesture detection (onPageChanged → TabManager.switchTab)
- [ ] 9.7 Implement smooth scroll to tab on TabBar click
- [ ] 9.8 Add page transition animations (fade + slide)
- [ ] 9.9 Test swipe navigation with 3 tabs (left/right swipe)

## 10. Terminal Session - Tab Association

- [ ] 10.1 Add tabId: String parameter to TerminalBridge constructor
- [ ] 10.2 Update TerminalService to track bridges per tabId (Map<String, TerminalBridge>)
- [ ] 10.3 Implement getBridgeForTab(tabId: String): TerminalBridge? function
- [ ] 10.4 Update ConsoleScreen to retrieve bridge via tabId instead of hostId
- [ ] 10.5 Implement bridge lifecycle: create on tab create, detach on tab hide, destroy on tab close
- [ ] 10.6 Add DisposableEffect in ConsoleScreen to attach/detach bridge on tab switch
- [ ] 10.7 Test terminal session survives tab switch (type in tab 1, switch to tab 2, switch back to tab 1 → output preserved)
- [ ] 10.8 Test SSH connection stays alive when tab is not visible (background tab)

## 11. SFTP Session - Tab Association

- [ ] 11.1 Update SftpViewModel to accept tabId parameter in init
- [ ] 11.2 Refactor activeClients map key from hostId to tabId (ConcurrentHashMap<String, SftpClient>)
- [ ] 11.3 Update SftpScreen to retrieve client via tabId
- [ ] 11.4 Implement SFTP client lifecycle per tab: connect on tab create, persist on tab hide, disconnect on tab close
- [ ] 11.5 Test SFTP connection survives tab switch (browse in tab 1, switch to tab 2, switch back → directory state preserved)
- [ ] 11.6 Test multiple SFTP tabs to same host (each has independent connection)

## 12. SFTP Modern UI - Card-Based Layout

- [ ] 12.1 Create ModernSftpCard.kt composable (replaces simple Row layout)
- [ ] 12.2 Add Material 3 Card with 2dp elevation, rounded corners (8dp)
- [ ] 12.3 Display file icon (based on extension: .jpg → image icon, .pdf → document icon)
- [ ] 12.4 Display filename, size (human-readable: KB, MB, GB), modified timestamp
- [ ] 12.5 Add folder icon and higher elevation for directories
- [ ] 12.6 Implement empty state card (centered, illustration + "No files" text)
- [ ] 12.7 Add onClick handler for file (download) and folder (navigate)
- [ ] 12.8 Test with directory containing 0, 10, 100 files

## 13. SFTP Breadcrumb Navigation

- [ ] 13.1 Create BreadcrumbBar.kt composable
- [ ] 13.2 Parse current path into segments (e.g., "/home/user/projects" → ["home", "user", "projects"])
- [ ] 13.3 Display [🏠] home icon + segments as tappable chips
- [ ] 13.4 Implement onSegmentClick(index: Int) to navigate to parent directory
- [ ] 13.5 Add horizontal scroll for long paths (auto-scroll to end)
- [ ] 13.6 Style breadcrumb with Material 3 tertiary color for segments
- [ ] 13.7 Test with deep path (6+ levels) on small screen (verify scrolling)

## 14. SFTP Swipe Actions

- [ ] 14.1 Add SwipeableCard wrapper around ModernSftpCard
- [ ] 14.2 Implement swipe left reveals delete button (red background, trash icon)
- [ ] 14.3 Implement swipe right reveals download button (cyan background, download icon)
- [ ] 14.4 Add onClick handlers for revealed actions
- [ ] 14.5 Implement swipe dismissal (swipe opposite direction hides action)
- [ ] 14.6 Add haptic feedback on action button press
- [ ] 14.7 Add long-press to show bottom sheet with all actions (Download, Delete, Rename, Permissions, Copy, Move)
- [ ] 14.8 Test swipe gestures on 5 files (verify smooth animation)

## 15. SFTP Drag-Drop Upload

- [ ] 15.1 Add drag-drop listener to SftpScreen composable
- [ ] 15.2 Highlight drop zone when drag enters (show dashed border overlay)
- [ ] 15.3 Handle drop event: extract file URI, start upload
- [ ] 15.4 Implement share intent receiver in AndroidManifest.xml
- [ ] 15.5 Update MainActivity to handle ACTION_SEND intent (redirect to SFTP tab)
- [ ] 15.6 Add "Upload" FAB in SftpScreen (opens file picker)
- [ ] 15.7 Support multiple file selection in file picker
- [ ] 15.8 Display upload progress cards above file list during upload
- [ ] 15.9 Test drag-drop from file manager app
- [ ] 15.10 Test share file from gallery app

## 16. SFTP Visual Feedback - Animations

- [ ] 16.1 Add delete animation: card fadeOut + slideOutHorizontally
- [ ] 16.2 Add rename inline editing: TextField with focus, [✓] [✗] buttons
- [ ] 16.3 Add copy/paste visual indicator: cyan border on selected cards
- [ ] 16.4 Add error state animation: card shake effect on operation failure
- [ ] 16.5 Add Snackbar with "Undo" button for delete (5 second timeout)
- [ ] 16.6 Add Snackbar for successful operations ("File uploaded", "3 files copied")
- [ ] 16.7 Test animations on delete, rename, copy operations

## 17. SFTP Search and Filter

- [ ] 17.1 Add SearchBar at top of SftpScreen (Material 3 search component)
- [ ] 17.2 Implement search query StateFlow in SftpViewModel
- [ ] 17.3 Filter file list in real-time based on query (case-insensitive substring match)
- [ ] 17.4 Add [×] clear button in search bar
- [ ] 17.5 Create filter chips: All, Images, Documents, Videos, Archives, Code
- [ ] 17.6 Implement filter logic per chip (extension-based: Images = .jpg/.png/.gif)
- [ ] 17.7 Support multiple filter selection (OR logic)
- [ ] 17.8 Test search with 100 files (verify smooth filtering)

## 18. SFTP Multi-Select Mode

- [ ] 18.1 Add long-press detection on ModernSftpCard to enter multi-select mode
- [ ] 18.2 Show checkbox overlay on all cards in multi-select mode
- [ ] 18.3 Track selected files in StateFlow<Set<String>> (file paths)
- [ ] 18.4 Update top bar to show "X selected" text
- [ ] 18.5 Replace FAB with batch actions menu (Delete, Download, Move)
- [ ] 18.6 Implement batch delete with confirmation dialog
- [ ] 18.7 Implement batch download (queue multiple downloads)
- [ ] 18.8 Add [×] button in top bar to exit multi-select mode
- [ ] 18.9 Test selecting 10 files, batch delete, exit mode

## 19. SFTP Sorting and View Modes

- [ ] 19.1 Add sort menu in top bar (Name ↑, Name ↓, Size ↑, Size ↓, Date ↑, Date ↓)
- [ ] 19.2 Implement sort logic in SftpViewModel with StateFlow<SortOrder>
- [ ] 19.3 Add view mode icons in top bar (List, Grid, Compact)
- [ ] 19.4 Create GridView layout (2-column LazyVerticalGrid)
- [ ] 19.5 Create CompactView layout (dense single-line items)
- [ ] 19.6 Persist sort order and view mode in DataStore
- [ ] 19.7 Test sorting 50 files by each criterion
- [ ] 19.8 Test view mode switching (verify smooth transition)

## 20. SFTP File Preview

- [ ] 20.1 Add Coil dependency for image loading (if not already present)
- [ ] 20.2 Implement thumbnail loading for image files in ModernSftpCard
- [ ] 20.3 Add "Share" quick action button on file cards
- [ ] 20.4 Implement share action: download to cache, open Android share sheet
- [ ] 20.5 Add "Open" quick action for downloaded files
- [ ] 20.6 Implement open action: launch intent chooser with file MIME type
- [ ] 20.7 Create TextPreviewBottomSheet composable for text files
- [ ] 20.8 Implement text file preview on tap (first 500 lines, syntax highlighting optional)
- [ ] 20.9 Test image thumbnail loading with 20 images (verify async loading)
- [ ] 20.10 Test text preview with large .log file (verify pagination)

## 21. Terminal Theme Preview UI

- [ ] 21.1 Create ThemePreviewCard composable
- [ ] 21.2 Display sample terminal output: "user@host:~$ ls -la" with ANSI colors
- [ ] 21.3 Show all 16 ANSI colors in preview grid (8 normal + 8 bright)
- [ ] 21.4 Add theme selection list in SettingsScreen
- [ ] 21.5 Update preview immediately on theme selection (before applying)
- [ ] 21.6 Add "Apply" button to confirm theme change
- [ ] 21.7 Test theme switching (preview updates, terminal updates on apply)

## 22. Migration and Cleanup

- [ ] 22.1 Remove BottomNavigationBar from MainActivity.kt
- [ ] 22.2 Remove NavHost navigation graph (replaced by HorizontalPager)
- [ ] 22.3 Remove old Screen.kt sealed class (if using route-based navigation)
- [ ] 22.4 Update all theme references from old color scheme to new tokens
- [ ] 22.5 Run database migration on test device (verify tabs table created)
- [ ] 22.6 Test app with fresh install (no existing data)
- [ ] 22.7 Test app with existing user data (Host, Pubkey entities intact)

## 23. Testing and Validation

- [ ] 23.1 Create integration test for tab lifecycle (create, switch, close)
- [ ] 23.2 Create integration test for terminal session persistence across tab switches
- [ ] 23.3 Create integration test for SFTP session persistence across tab switches
- [ ] 23.4 Test with 1 SSH tab (verify no regressions)
- [ ] 23.5 Test with 3 SSH tabs + 2 SFTP tabs (5 total tabs, all active)
- [ ] 23.6 Test with 10 tabs (verify scrolling, performance)
- [ ] 23.7 Test tab reordering via drag-drop (verify positions persist)
- [ ] 23.8 Test theme switching (dark theme applied, terminal themes work)
- [ ] 23.9 Test SFTP modern UI (card layout, breadcrumb, swipe actions)
- [ ] 23.10 Profile with Android Studio Profiler (no memory leaks, smooth 60fps)

## 24. Performance Optimization

- [ ] 24.1 Optimize tab switching latency (target: <100ms frame time)
- [ ] 24.2 Lazy load tab content (don't initialize hidden tabs until visible)
- [ ] 24.3 Add remember {} and derivedStateOf {} for expensive computations
- [ ] 24.4 Profile SFTP file list rendering (LazyColumn with keys)
- [ ] 24.5 Optimize image thumbnail loading (cache, downsampling)
- [ ] 24.6 Measure APK size impact (new fonts, assets)
- [ ] 24.7 Run Gradle build with R8 shrinking (verify no runtime crashes)

## 25. Documentation and Release

- [ ] 25.1 Update README.md with Vivaldi tabs feature description
- [ ] 25.2 Add screenshots of new UI to docs/images/
- [ ] 25.3 Document terminal theme customization in user guide
- [ ] 25.4 Document SFTP modern UI features (swipe actions, multi-select)
- [ ] 25.5 Create release notes for UI modernization
- [ ] 25.6 Commit all changes with conventional commit messages
- [ ] 25.7 Merge feature/ui-modernization-vivaldi-tabs to dev
- [ ] 25.8 Archive OpenSpec change: `npx openspec archive --change ui-modernization-vivaldi-tabs`
