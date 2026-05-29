## ADDED Requirements

### Requirement: Screens hide TopAppBar when displayed inside tabs
The system SHALL hide the built-in TopAppBar of ConsoleScreen and SftpScreen when they are rendered inside a tab container, preventing duplicate navigation bars.

#### Scenario: ConsoleScreen in tab shows no TopAppBar
- **WHEN** ConsoleScreen is rendered with inTab = true inside HorizontalPager
- **THEN** the TopAppBar with back button and menu is NOT displayed

#### Scenario: SftpScreen in tab shows no TopAppBar
- **WHEN** SftpScreen is rendered with inTab = true inside HorizontalPager
- **THEN** the TopAppBar with path and navigation buttons is NOT displayed

#### Scenario: ConsoleScreen standalone shows TopAppBar
- **WHEN** ConsoleScreen is rendered with inTab = false (or default) outside tabs
- **THEN** the TopAppBar with back button and menu IS displayed for backward compatibility

#### Scenario: SftpScreen standalone shows TopAppBar
- **WHEN** SftpScreen is rendered with inTab = false (or default) outside tabs
- **THEN** the TopAppBar with path and navigation buttons IS displayed for backward compatibility

### Requirement: Tab content screens accept inTab parameter
ConsoleScreen and SftpScreen SHALL accept an optional Boolean parameter inTab (default: false) to control TopAppBar visibility.

#### Scenario: ConsoleScreen signature includes inTab
- **WHEN** ConsoleScreen function is declared
- **THEN** signature includes parameter: inTab: Boolean = false

#### Scenario: SftpScreen signature includes inTab
- **WHEN** SftpScreen function is declared
- **THEN** signature includes parameter: inTab: Boolean = false

#### Scenario: Backward compatibility maintained
- **WHEN** existing code calls ConsoleScreen or SftpScreen without inTab parameter
- **THEN** default value false is used and TopAppBar is displayed (old behavior preserved)

### Requirement: TabbedMainScreen passes inTab = true to tab content
The system SHALL pass inTab = true when rendering ConsoleScreen or SftpScreen inside HorizontalPager tabs.

#### Scenario: SSH terminal tab hides TopAppBar
- **WHEN** TabbedMainScreen renders SSH_TERMINAL tab type
- **THEN** ConsoleScreen is called with inTab = true parameter

#### Scenario: SFTP browser tab hides TopAppBar
- **WHEN** TabbedMainScreen renders SFTP_BROWSER tab type
- **THEN** SftpScreen is called with inTab = true parameter

### Requirement: Pager and TabManager remain synchronized
The system SHALL maintain bidirectional synchronization between HorizontalPager currentPage and TabManager currentTabIndex.

#### Scenario: User swipes pager updates TabManager
- **WHEN** user swipes HorizontalPager from page 0 to page 1
- **THEN** TabManager.currentTabIndex updates to 1

#### Scenario: TabManager update scrolls pager
- **WHEN** TabManager.currentTabIndex changes to 2 (e.g., new tab created)
- **THEN** HorizontalPager animates scroll to page 2

#### Scenario: Avoid infinite sync loop
- **WHEN** pager page changes trigger TabManager update which triggers pager scroll
- **THEN** guard condition prevents update if values already match (no infinite loop)

### Requirement: SFTP tab creation switches to new tab immediately
The system SHALL switch to newly created SFTP tab automatically without requiring user tap.

#### Scenario: Create SFTP tab from host list
- **WHEN** user taps SFTP button on host in HostListScreen
- **THEN** new SFTP tab is created AND HorizontalPager scrolls to that tab immediately

#### Scenario: Create SSH tab from host list
- **WHEN** user taps host in HostListScreen
- **THEN** new SSH tab is created AND HorizontalPager scrolls to that tab immediately

#### Scenario: Tab creation updates currentTabIndex
- **WHEN** TabManager.createTab() is called
- **THEN** currentTabIndex is set to the new tab's position before coroutine completes

### Requirement: TopAppBar conditional rendering uses if block
The system SHALL wrap TopAppBar in Scaffold.topBar using if (!inTab) { } block to conditionally render.

#### Scenario: TopAppBar wrapped in if block
- **WHEN** Scaffold.topBar lambda is defined
- **THEN** TopAppBar and all its content are wrapped in if (!inTab) { TopAppBar(...) }

#### Scenario: Empty topBar when inTab = true
- **WHEN** inTab parameter is true
- **THEN** topBar lambda executes but produces no composable output (empty)

#### Scenario: Full TopAppBar when inTab = false
- **WHEN** inTab parameter is false
- **THEN** topBar lambda renders complete TopAppBar with title, navigation, and actions

### Requirement: All TopAppBars in SftpScreen are conditional
SftpScreen has multiple TopAppBars (main, connecting, error) and ALL SHALL respect inTab parameter.

#### Scenario: Main SftpScreen TopAppBar conditional
- **WHEN** main SftpScreen function renders with inTab = true
- **THEN** TopAppBar with path and actions is NOT displayed

#### Scenario: SftpConnectingScreen TopAppBar conditional
- **WHEN** SftpConnectingScreen is rendered with inTab = true
- **THEN** TopAppBar with "Connecting..." title is NOT displayed

#### Scenario: SftpErrorScreen TopAppBar conditional
- **WHEN** SftpErrorScreen is rendered with inTab = true
- **THEN** TopAppBar with "Connection Failed" title is NOT displayed

#### Scenario: Helper functions accept inTab parameter
- **WHEN** SftpConnectingScreen or SftpErrorScreen are declared
- **THEN** both accept inTab: Boolean = false parameter

### Requirement: Implementation preserves existing functionality
Changes SHALL NOT break existing navigation flows or screen behavior outside of tabbed context.

#### Scenario: NavGraph navigation still works
- **WHEN** old navigation code uses composable(Screen.Console.route)
- **THEN** ConsoleScreen displays with TopAppBar (inTab defaults to false)

#### Scenario: Direct screen usage still works
- **WHEN** developer uses ConsoleScreen directly in another composable
- **THEN** TopAppBar is displayed unless inTab = true is explicitly passed

#### Scenario: All menu actions still work
- **WHEN** TopAppBar is displayed (inTab = false)
- **THEN** all menu items (Disconnect, Reconnect, Toggle Keyboard, A+, A-) function correctly

### Requirement: Code changes are minimal and surgical
Implementation SHALL modify only function signatures and TopAppBar rendering, avoiding refactoring of unrelated code.

#### Scenario: ConsoleScreen changes limited to 3 locations
- **WHEN** ConsoleScreen.kt is modified
- **THEN** changes occur at exactly 3 locations: function signature, if (!inTab) opening, if closing brace

#### Scenario: SftpScreen changes limited to specific functions
- **WHEN** SftpScreen.kt is modified
- **THEN** only SftpScreen, SftpConnectingScreen, SftpErrorScreen functions are modified

#### Scenario: TabbedMainScreen changes limited to tab rendering
- **WHEN** TabbedMainScreen.kt is modified
- **THEN** changes are only in HorizontalPager content lambda and LaunchedEffect blocks

### Requirement: Build succeeds without warnings
Implementation SHALL compile successfully without introducing new Kotlin compiler warnings or errors.

#### Scenario: Clean compilation
- **WHEN** ./gradlew :app:compileDebugKotlin is executed
- **THEN** task completes with BUILD SUCCESSFUL and zero errors

#### Scenario: No new warnings
- **WHEN** compilation output is examined
- **THEN** no new warnings appear beyond pre-existing deprecation warnings

#### Scenario: APK assembles successfully
- **WHEN** ./gradlew :app:assembleDebug is executed
- **THEN** APK is generated in app/build/outputs/apk/debug/app-debug.apk
