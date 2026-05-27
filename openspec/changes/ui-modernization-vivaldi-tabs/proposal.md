## Why

DarkSSH currently uses a traditional BottomNavigationBar pattern (Terminal | SFTP | Settings) that limits multi-tasking and doesn't leverage modern Android UX patterns. Users need to frequently switch between SSH terminal and SFTP views of the same host, losing context each time. Modern SSH clients like Termius and browsers like Vivaldi demonstrate that a tab-based interface enables superior workflow efficiency and visual elegance. This change modernizes DarkSSH's UI to match industry-leading UX while maintaining Material 3 design principles.

## What Changes

- Replace BottomNavigationBar with **Vivaldi-style horizontal tab bar at top** showing all active connections
- Each tab represents an independent view (SSH terminal or SFTP browser) with icon, hostname, and type indicator
- Implement **swipe gestures** to navigate between tabs (HorizontalPager)
- Add **tab management**: create new tabs with [+] button, close tabs with [×], reorder via drag
- Modernize visual design with **Termius-inspired dark theme**: deep backgrounds (#1A1A1A), cyan accent (#00B8D4), elevated cards, smooth animations
- Implement **ScrollableTabRow** with Material 3 styling for unlimited concurrent sessions
- Add **persistent tab state** (save/restore tabs across app restarts)
- Support **split view** for tablets (future enhancement, spec placeholder)
- Redesign **SFTP browser** with modern file cards, breadcrumb navigation, and swipe actions
- Update **terminal rendering** with optional theme presets (Dracula, Nord, One Dark, Solarized)

## Capabilities

### New Capabilities
- `tab-management`: Tab lifecycle (create, close, reorder), state persistence, visual indicators
- `tab-navigation`: Swipe gestures, scroll behavior, active tab highlighting, keyboard shortcuts
- `modern-theme-system`: Dark theme palette, Material 3 color tokens, dynamic elevation, accent colors
- `terminal-themes`: Terminal color scheme presets (foreground, background, ANSI colors), font customization
- `sftp-modern-ui`: Card-based file browser, breadcrumb path navigation, swipe actions (delete/rename), drag-drop upload

### Modified Capabilities
- `terminal-session`: Add tab association (each terminal session linked to specific tab ID)
- `sftp-session`: Add tab association (each SFTP connection linked to specific tab ID)

## Impact

**Affected Code:**
- `MainActivity.kt`: Replace BottomNavigationBar with TabRow + HorizontalPager
- `Navigation.kt`: Refactor navigation graph to support dynamic tab-based routing
- `ConsoleScreen.kt`: Integrate with tab lifecycle, handle tab switching without disconnecting
- `SftpScreen.kt`: Redesign file browser UI, integrate with tab system
- `Theme.kt`: Extend with new color tokens, elevation values, typography scales
- `TerminalBridge.kt`: Track tab ID for each bridge instance
- `TerminalService.kt`: Associate bridges with tab IDs for state recovery

**New Files:**
- `TabManager.kt`: Tab state management, persistence, lifecycle coordination
- `TabBar.kt`: Composable for top tab row with Material 3 styling
- `TabItem.kt`: Data class representing tab (id, type, hostId, title, icon)
- `ThemePresets.kt`: Terminal color scheme definitions (Dracula, Nord, etc.)
- `ModernSftpCard.kt`: Redesigned file/folder card component
- `BreadcrumbBar.kt`: Path navigation component (already exists, enhance)

**Database:**
- Add `Tab` entity to Room database for persistence
- Add `TabDao` for CRUD operations
- Migration: Add `tabs` table with columns (id, type, hostId, position, createdAt)

**Dependencies:**
- No new dependencies (uses existing Compose + Material 3)
- Leverage `accompanist-pager` if needed for HorizontalPager (or native Compose Pager)

**APIs:**
- Internal state management APIs (TabManager, TabRepository)
- No external API changes

**Breaking Changes:**
- None (purely UI/UX enhancement, no data model changes for Host/Pubkey entities)
