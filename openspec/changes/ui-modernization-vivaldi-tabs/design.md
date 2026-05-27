## Context

DarkSSH currently uses a simple navigation model with `NavHost` and `BottomNavigationBar` routing between three destinations: ConsoleScreen, SftpScreen, and Settings. This limits users to one active view per host and requires full screen transitions when switching contexts.

**Current Architecture:**
```
MainActivity
  └─ NavHost
      ├─ BottomNavigationBar
      └─ Routes: console/{hostId}, sftp/{hostId}, settings
```

**Constraints:**
- TerminalBridge instances must survive tab switches (bridges are managed by TerminalService, not recreated on recomposition)
- SFTP connections (SftpClient) are stored in static `activeClients` map in SftpViewModel
- Terminal emulator requires view dimensions before initialization (cannot be created speculatively)
- AndroidView (TerminalView) must be attached/detached correctly to avoid memory leaks
- Room database already exists (hosts, pubkeys, knownhosts) - adding tabs entity

**Stakeholders:**
- End users: Want efficient multi-session workflow
- Developers: Need maintainable architecture that doesn't break existing SSH/SFTP logic

## Goals / Non-Goals

**Goals:**
- Replace bottom navigation with horizontal tab bar showing all active sessions
- Support unlimited concurrent tabs (SSH and SFTP intermixed)
- Persist tab state across app restarts (order, type, associated host)
- Smooth animations for tab creation, closing, and reordering
- Material 3 visual refresh with dark theme and modern elevation
- Maintain existing SSH connection stability (no regressions)

**Non-Goals:**
- Split screen / multi-pane layouts (future enhancement)
- Desktop/tablet specific optimizations (focus on phone first)
- Cloud sync of tab state (local persistence only)
- Tab groups or workspaces (out of scope for v1)

## Decisions

### 1. Tab State Management: ViewModel + Repository + Room

**Decision:** Use MVVM pattern with `TabManager` ViewModel, `TabRepository`, and `Tab` Room entity.

**Rationale:**
- Consistent with existing architecture (HostRepository, PubkeyRepository already use this pattern)
- Room provides free persistence and observability (Flow<List<Tab>>)
- ViewModel survives configuration changes and activity recreation
- Hilt injection simplifies testing and dependency management

**Alternatives Considered:**
- SharedPreferences: Too manual, no Flow support, doesn't scale to complex queries
- In-memory only: Loses tabs on app restart (poor UX)
- Custom file storage: Reinventing the wheel, Room is better

**Implementation:**
```kotlin
@Entity(tableName = "tabs")
data class Tab(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: TabType,  // SSH_TERMINAL or SFTP_BROWSER
    val hostId: Long,
    val position: Int,  // Order in tab bar
    val createdAt: Long = System.currentTimeMillis()
)

enum class TabType {
    SSH_TERMINAL,
    SFTP_BROWSER
}
```

### 2. Tab Bar UI: ScrollableTabRow + HorizontalPager

**Decision:** Use Compose `ScrollableTabRow` for tab headers and `HorizontalPager` for content.

**Rationale:**
- Material 3 native component with proper accessibility, scroll indicators, and animations
- `ScrollableTabRow` automatically handles overflow (horizontal scroll) when many tabs
- `HorizontalPager` provides swipe gestures for free (users expect this on Android)
- Minimal custom code, leverages Compose best practices

**Alternatives Considered:**
- Custom LazyRow + Box: More work, need to implement scroll indicators, selection state manually
- TabLayout from View system: Compose interop is messy, Material 3 styling harder
- Fixed TabRow: Doesn't scale to many tabs (breaks on 5+ tabs)

**Implementation:**
```kotlin
@Composable
fun MainScreen(viewModel: TabManager = hiltViewModel()) {
    val tabs by viewModel.tabs.collectAsState()
    val selectedTab by viewModel.selectedTabId.collectAsState()
    
    Column {
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOfFirst { it.id == selectedTab },
            edgePadding = 0.dp
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = tab.id == selectedTab,
                    onClick = { viewModel.selectTab(tab.id) },
                    text = { Text(tab.getTitle()) },
                    icon = { Icon(tab.getIcon(), null) }
                )
            }
            // [+] button
            IconButton(onClick = { viewModel.showNewTabDialog() }) {
                Icon(Icons.Default.Add, "New tab")
            }
        }
        
        HorizontalPager(
            pageCount = tabs.size,
            state = rememberPagerState()
        ) { page ->
            when (tabs[page].type) {
                TabType.SSH_TERMINAL -> ConsoleScreen(tabs[page].hostId)
                TabType.SFTP_BROWSER -> SftpScreen(tabs[page].hostId)
            }
        }
    }
}
```

### 3. Bridge Lifecycle: Keep Bridges Alive Across Tab Switches

**Decision:** Bridges remain in TerminalService, not destroyed when tab becomes inactive. Use `DisposableEffect` to attach/detach TerminalView from bridge.

**Rationale:**
- Existing pattern already works (see Session 5 bugfix in skill.md)
- SSH connection stays alive when switching tabs (no reconnection delay)
- Terminal scrollback history preserved
- Minimal changes to ConsoleScreen

**Alternatives Considered:**
- Destroy bridge on tab hide: Would drop SSH connection, bad UX
- Keep TerminalView in composition: Wastes memory, AndroidView lifecycle issues
- Multiple Activity instances: Overkill, complicates state management

**Implementation:**
```kotlin
@Composable
fun ConsoleScreen(hostId: Long, tabId: String) {
    val bridge = remember(hostId) {
        TerminalService.getOrCreateBridge(hostId)
    }
    
    DisposableEffect(tabId) {
        // Attach view to bridge when tab becomes active
        onDispose {
            // Detach view but keep bridge alive
            bridge.detachView()
        }
    }
    
    AndroidView(factory = { ctx ->
        TerminalView(ctx).apply {
            bridge.attachView(this)
        }
    })
}
```

### 4. SFTP Connection: Reuse Existing Static Map Pattern

**Decision:** Keep `SftpViewModel.activeClients` static map, associate each tab with its own SftpClient instance.

**Rationale:**
- Already working in current codebase
- SftpClient connections survive ViewModel death (configuration changes)
- Simple to look up client by hostId when tab becomes active

**Alternatives Considered:**
- Store SftpClient in TabManager: Violates separation of concerns, complicates ViewModel
- Create/destroy client per tab switch: Expensive (SSH handshake), bad UX
- Singleton SftpClient per host: Current approach, just need to associate with tabId

**Implementation:**
```kotlin
companion object {
    internal val activeClients = ConcurrentHashMap<String, SftpClient>()
    // Key: "${hostId}_${tabId}" to allow multiple SFTP tabs per host
}
```

### 5. Theme System: Material 3 Color Tokens + Custom Extensions

**Decision:** Extend MaterialTheme with custom color tokens for terminal themes. Store theme preference in DataStore.

**Rationale:**
- Material 3 already provides semantic colors (surface, onSurface, primary, etc.)
- Custom terminal colors (ANSI palette) stored separately as Kotlin object
- DataStore for preferences (better than SharedPreferences, Flow support)

**Alternatives Considered:**
- Hardcode terminal colors: Not customizable
- CSS-style theme files: Overkill, parsing complexity
- Multiple MaterialTheme instances: Doesn't work well with Compose

**Implementation:**
```kotlin
object TerminalThemes {
    val Dracula = TerminalTheme(
        background = Color(0xFF282A36),
        foreground = Color(0xFFF8F8F2),
        cursor = Color(0xFFF8F8F2),
        ansi = listOf(
            Color(0xFF21222C), // black
            Color(0xFFFF5555), // red
            // ... 14 more colors
        )
    )
    val Nord = TerminalTheme(...)
    val OneDark = TerminalTheme(...)
}

data class TerminalTheme(
    val background: Color,
    val foreground: Color,
    val cursor: Color,
    val ansi: List<Color> // 16 colors
)
```

### 6. Tab Reordering: Drag-and-Drop with ReorderableState

**Decision:** Use `org.burnoutcrew.reorderable:reorderable` library (or Compose foundation's experimental DragAndDrop API if stable).

**Rationale:**
- Native feel, users expect drag-to-reorder on Android
- Library provides haptic feedback and smooth animations
- Updates Room database on drop (persist new order)

**Alternatives Considered:**
- Long-press menu with "Move left/right": Clunky, slow
- Disable reordering: Bad UX, tabs get cluttered
- Custom gesture detector: Complex, need to handle edge cases

**Implementation:**
```kotlin
val reorderableState = rememberReorderableLazyListState(
    onMove = { from, to -> viewModel.reorderTabs(from.index, to.index) }
)

LazyRow(state = reorderableState.listState) {
    items(tabs, key = { it.id }) { tab ->
        ReorderableItem(reorderableState, key = tab.id) {
            TabChip(tab, isDragging = it)
        }
    }
}
```

## Risks / Trade-offs

### Risk: Tab Proliferation (Users Open Too Many Tabs)

**Scenario:** User opens 20+ tabs, app consumes excessive memory, becomes sluggish.

**Mitigation:**
- Implement tab limit (max 10 tabs) with toast warning
- Add "Close all" and "Close others" context menu actions
- Display memory warning if approaching limits
- Consider lazy loading tab content (only render visible + adjacent tabs)

### Risk: TerminalView Lifecycle Issues

**Scenario:** AndroidView detach/reattach causes rendering glitches or crashes.

**Mitigation:**
- Thoroughly test tab switching with active SSH sessions
- Use `DisposableEffect` carefully to avoid double-attach
- Add logging to track view lifecycle events
- Regression test: Switch tabs while running `top` command (continuous output)

### Risk: Database Migration Failure

**Scenario:** Room migration from current schema to schema+tabs fails on user devices.

**Mitigation:**
- Write and test migration thoroughly (add `AutoMigrationSpec` if needed)
- Fallback: Drop and recreate database (acceptable for early dev, not production)
- Provide migration testing instructions in tasks.md

### Trade-off: Increased Complexity vs Better UX

**Trade-off:** Adding tabs increases code complexity (new entities, ViewModels, navigation logic) but significantly improves user experience.

**Decision:** Worth it. Modern SSH clients (Termius, Prompt, Blink) all use tabs. Users expect this pattern.

### Trade-off: Memory Overhead of Multiple Views

**Trade-off:** Keeping multiple TerminalView instances in memory (even if detached) uses more RAM than single-view approach.

**Mitigation:**
- Detach AndroidView when tab is not visible (frees Canvas memory)
- Keep bridge alive (small memory footprint) but destroy view
- Monitor memory usage in profiling, optimize if needed

### Trade-off: Custom Navigation vs NavHost

**Trade-off:** Replacing NavHost with manual tab management loses deep linking and back stack handling.

**Decision:** Acceptable. SSH client doesn't need deep links. Back button can close current tab or exit app (standard tab browser behavior).

## Migration Plan

**Phase 1: Foundation (No Visual Changes)**
1. Add Tab entity, TabDao, TabRepository to Room
2. Create database migration (version N → N+1)
3. Implement TabManager ViewModel with CRUD operations
4. Add DataStore for theme preferences
5. Unit tests for TabManager, TabRepository

**Phase 2: UI Implementation (Gradual Rollout)**
1. Replace BottomNavigationBar with ScrollableTabRow in MainActivity
2. Implement HorizontalPager for tab content
3. Wire TabManager to UI (create, select, close tabs)
4. Update ConsoleScreen and SftpScreen to accept tabId parameter
5. Test tab switching with active SSH session

**Phase 3: Visual Polish**
1. Apply Termius-inspired color scheme
2. Add terminal theme presets (Dracula, Nord, One Dark)
3. Implement tab reordering with drag-and-drop
4. Add animations (tab open, close, switch)
5. Update SFTP browser with modern card design

**Phase 4: Stability & Testing**
1. Regression testing on all existing features
2. Memory profiling with 10 concurrent tabs
3. Test SSH keepalive across tab switches
4. Handle edge cases (close last tab, reorder while switching, etc.)

**Rollback Strategy:**
- Keep old navigation code in git history
- If critical bugs found, revert MainActivity changes
- Database migration is one-way (tabs data lost on rollback, but hosts/keys preserved)

## Open Questions

1. **Should we limit tab count?**
   - Proposal: Max 10 tabs, show warning at 8
   - Decision: Resolve in implementation, test memory impact

2. **Tab close confirmation?**
   - Should closing a tab with active SSH session show confirmation dialog?
   - Decision: Yes, if session is connected. No confirmation for SFTP tabs.

3. **Default tab behavior on app start?**
   - Restore previous tabs or start with empty tab bar + host list?
   - Proposal: Restore tabs (better UX, matches browser behavior)
   - Decision: Make configurable in Settings

4. **Tablet layout?**
   - Should we implement split-screen for tablets in this change?
   - Decision: No, out of scope. Add spec placeholder for future enhancement.

5. **Tab icons?**
   - Custom icons per host or generic terminal/folder icons?
   - Proposal: Generic icons (🖥️ for SSH, 📁 for SFTP) + hostname text
   - Consider: User-uploadable host icons (future enhancement)
