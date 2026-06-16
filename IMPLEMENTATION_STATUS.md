# Implementation Status - Tabs & Memory Leaks

## Summary

**Tab Infrastructure:** ✅ **90% Complete** (backend fully implemented, minor cleanup gap)  
**Memory Leaks:** ✅ **80% Fixed** (4/5 critical issues resolved)  
**OpenSpec Tasks:** ✅ **Core tasks done** (Tab Entity, TabManager, HorizontalPager all working)

---

## ✅ Fully Implemented

### 1. Tab Database Schema (OpenSpec Tasks 1.1-1.6)
- ✅ `Tab` Room entity with all fields (id, type, hostId, position, createdAt, title, themeId, osType)
- ✅ `TabType` enum (SSH_TERMINAL, SFTP_BROWSER)
- ✅ `TabDao` with all CRUD operations
- ✅ `TabRepository` with Hilt injection
- ✅ Database migrations: MIGRATION_1_2 (create tabs table), MIGRATION_2_3 (add osType column)
- ✅ Indices on hostId and position

**Files:**
- `app/src/main/java/com/darkssh/client/data/entity/Tab.kt`
- `app/src/main/java/com/darkssh/client/data/dao/TabDao.kt`
- `app/src/main/java/com/darkssh/client/data/repository/TabRepository.kt`
- `app/src/main/java/com/darkssh/client/di/DatabaseModule.kt` (lines 23-50, 59)

---

### 2. TabManager ViewModel (OpenSpec Tasks 2.1-2.10)
- ✅ `StateFlow<List<Tab>>` for active tabs
- ✅ `StateFlow<Int>` for currentTabIndex
- ✅ `createTab(type, hostId, title)` - creates new tab
- ✅ `createTabAndSwitch()` - creates and switches to new tab
- ✅ `createOrSwitchToTab()` - reuses existing tab or creates new one
- ✅ `closeTab(tabId)` - deletes tab and reorders remaining
- ✅ `switchTab(index)` - changes current tab
- ✅ `reorderTabs(fromIndex, toIndex)` - drag-drop support
- ✅ `getCurrentTab()` - gets active tab
- ✅ Tab restoration on ViewModel init (loads from Room)
- ✅ Auto-switch to new tab when created
- ✅ Hilt @Singleton injection

**File:** `app/src/main/java/com/darkssh/client/ui/viewmodel/TabManager.kt`

---

### 3. HorizontalPager Integration (OpenSpec Tasks 9.1-9.9)
- ✅ HorizontalPager below TabBar
- ✅ pageCount = tabs.size
- ✅ pagerState bound to TabManager.currentTabIndex
- ✅ Page content based on Tab.type (SSH_TERMINAL → ConsoleScreen, SFTP_BROWSER → SftpScreen)
- ✅ hostId and tabId passed to screens
- ✅ Swipe gesture detection (onPageChanged → TabManager.switchTab)
- ✅ Empty state when no tabs

**File:** `app/src/main/java/com/darkssh/client/ui/TabbedMainScreen.kt` (lines 137-165)

---

### 4. Terminal Session - Tab Association (OpenSpec Tasks 10.1-10.8)
- ✅ `tabId: String?` parameter in TerminalBridge constructor
- ✅ TerminalService tracks bridges per tabId
- ✅ `getBridgeForTab(tabId)` via `bridges.value.find { it.tabId == tabId }`
- ✅ ConsoleScreen retrieves bridge via tabId
- ✅ Bridge lifecycle: create on tab create, destroy on tab close
- ✅ Terminal session survives tab switch (bridge persists in TerminalService)

**Files:**
- `app/src/main/java/com/darkssh/client/service/TerminalBridge.kt` (line 36)
- `app/src/main/java/com/darkssh/client/service/TerminalService.kt` (line 126)
- `app/src/main/java/com/darkssh/client/ui/screens/viewmodel/ConsoleViewModel.kt` (lines 72-76)

---

### 5. Memory Leak Fixes

#### ✅ Fixed (4/5)
1. **ConsoleViewModel observer accumulation** (leak-002)
   - `observeJobs.clear()` in connect(), reconnect(), onCleared()
   - Lines: 60, 154, 173

2. **TerminalBridge scope not cancelled** (leak-003)
   - `bridgeScope.cancel()` and `bridgeJob.cancel()` in close()
   - Lines: 271-272

3. **SftpViewModel static collections** (leak-001)
   - `onCleared()` disconnects SFTP client if no active transfers
   - Removes from `activeClients` map
   - Lines: 1157-1189

4. **TerminalService bridge removal race** (leak-005)
   - `bridge.close(reason)` called BEFORE removing from list
   - Line: 143

#### ⚠️ Partially Fixed (1/5)
5. **Missing centralized tab cleanup** (leak-004)
   - ✅ TerminalBridge cleanup exists (TabbedMainScreen.kt lines 94-100)
   - ❌ **SftpClient cleanup missing** when tab closes
   - ❌ No centralized `TerminalService.closeTab(tabId)` function

---

## ⚠️ Gaps & Issues

### 1. SFTP Client Not Cleaned on Tab Close (HIGH PRIORITY)

**Problem:**
When user closes a tab with SFTP session, the `SftpClient` in `SftpViewModel.activeClients` is NOT disconnected.

**Current Behavior:**
```kotlin
// TabbedMainScreen.kt line 92
onCloseTab = { tabId ->
    // ✅ Closes TerminalBridge
    val bridgeToClose = terminalService?.bridges?.value?.find { it.tabId == tabId }
    if (bridgeToClose != null) {
        terminalService.onBridgeDisconnected(bridgeToClose, DisconnectReason.USER_REQUESTED)
    }
    
    // ❌ Does NOT close SftpClient
    tabManager.closeTab(tabId)
}
```

**Impact:**
- SFTP connections leak when SFTP tabs are closed
- `activeClients` map grows unbounded
- Memory leak (though mitigated by `onCleared()` cleanup when ViewModel is destroyed)

**Recommended Fix:**
```kotlin
// In TabbedMainScreen.kt onCloseTab
onCloseTab = { tabId ->
    val tab = tabManager.tabs.value.find { it.id == tabId }
    
    // Close TerminalBridge if SSH tab
    if (tab?.type == TabType.SSH_TERMINAL) {
        val bridgeToClose = terminalService?.bridges?.value?.find { it.tabId == tabId }
        if (bridgeToClose != null) {
            terminalService.onBridgeDisconnected(bridgeToClose, DisconnectReason.USER_REQUESTED)
        }
    }
    
    // Close SftpClient if SFTP tab
    if (tab?.type == TabType.SFTP_BROWSER) {
        SftpViewModel.activeClients[tab.hostId]?.let { client ->
            viewModelScope.launch {
                try {
                    client.disconnect()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to disconnect SFTP on tab close")
                }
            }
            SftpViewModel.activeClients.remove(tab.hostId)
        }
    }
    
    tabManager.closeTab(tabId)
}
```

**Alternative (Better):** Create centralized cleanup in TerminalService:
```kotlin
// In TerminalService.kt
fun closeTab(tabId: String) {
    // Close TerminalBridge if exists
    val bridge = bridges.value.find { it.tabId == tabId }
    if (bridge != null) {
        onBridgeDisconnected(bridge, DisconnectReason.USER_REQUESTED)
    }
    
    // Close SftpClient if exists
    // (requires refactoring activeClients to use tabId instead of hostId)
}
```

---

### 2. SFTP activeClients Uses hostId Instead of tabId (MEDIUM PRIORITY)

**Problem:**
`SftpViewModel.activeClients` is keyed by `hostId` (Long), not `tabId` (String).

**Impact:**
- Cannot have multiple SFTP tabs to the same host (second tab reuses first tab's connection)
- Closing one SFTP tab disconnects ALL SFTP tabs to that host

**Current:**
```kotlin
companion object {
    internal val activeClients = ConcurrentHashMap<Long, SftpClient>()  // ❌ hostId
}
```

**Recommended:**
```kotlin
companion object {
    internal val activeClients = ConcurrentHashMap<String, SftpClient>()  // ✅ tabId
}
```

**Files to Update:**
- `SftpViewModel.kt` (lines 112, 147-151, all usages of `activeClients[hostId]`)
- `UploadWorker.kt` (if it accesses `activeClients`)

---

### 3. No Material 3 Dark Theme (LOW PRIORITY)

**Status:** Not implemented (still using hardcoded colors)

**OpenSpec Tasks:** 3.1-3.8

**Impact:** Visual inconsistency, no theme customization

---

### 4. No Terminal Theme Presets (LOW PRIORITY)

**Status:** Not implemented

**OpenSpec Tasks:** 5.1-5.10 (Dracula, Nord, One Dark, Solarized Dark)

**Impact:** Users cannot customize terminal colors

---

## 📊 OpenSpec Task Completion

| Task Group | Status | Completion |
|------------|--------|------------|
| 1. Database Schema - Tab Entity | ✅ DONE | 6/6 (100%) |
| 2. Tab State Management - TabManager | ✅ DONE | 10/10 (100%) |
| 3. Theme System - Dark Palette | ❌ NOT STARTED | 0/8 (0%) |
| 4. Theme Persistence - DataStore | ❌ NOT STARTED | 0/8 (0%) |
| 5. Terminal Themes - Presets | ❌ NOT STARTED | 0/10 (0%) |
| 6. Terminal Font Customization | ❌ NOT STARTED | 0/10 (0%) |
| 7. Tab Bar UI - ScrollableTabRow | ✅ DONE | 9/9 (100%) |
| 8. Tab Drag-Drop Reordering | ⚠️ PARTIAL | 4/7 (57%) |
| 9. HorizontalPager Integration | ✅ DONE | 9/9 (100%) |
| 10. Terminal Session - Tab Association | ✅ DONE | 8/8 (100%) |
| 11. SFTP Session - Tab Association | ⚠️ PARTIAL | 4/6 (67%) |
| 12-20. SFTP Modern UI | ❌ NOT STARTED | 0/80+ (0%) |
| 21. Terminal Theme Preview UI | ❌ NOT STARTED | 0/7 (0%) |
| 22. Migration and Cleanup | ⚠️ PARTIAL | 3/7 (43%) |
| 23. Testing and Validation | ❌ NOT STARTED | 0/10 (0%) |
| 24. Performance Optimization | ❌ NOT STARTED | 0/7 (0%) |
| 25. Documentation and Release | ❌ NOT STARTED | 0/8 (0%) |

**Overall:** ~60/279 tasks completed (**21.5%**)

**Core Functionality:** ~46/60 core tasks completed (**76.7%**)

---

## 🎯 Recommended Next Steps

### Immediate (This Session)
1. **Fix SFTP client cleanup on tab close** (30 minutes)
   - Add SFTP disconnect in `TabbedMainScreen.onCloseTab`
   - Test: open SFTP tab, close it, verify connection closed

### Short Term (Next Session)
2. **Refactor SFTP activeClients to use tabId** (2-3 hours)
   - Change key from `Long` to `String`
   - Update all usages in SftpViewModel, UploadWorker
   - Enables multiple SFTP tabs per host

3. **Add centralized TerminalService.closeTab()** (1 hour)
   - Consolidates cleanup logic
   - Easier to maintain

### Medium Term (Future Sprints)
4. **Implement Material 3 theme system** (4-6 hours)
   - Tasks 3.1-3.8
   - Improves visual consistency

5. **Add terminal theme presets** (4-6 hours)
   - Tasks 5.1-5.10
   - User customization

6. **SFTP Modern UI** (20-30 hours)
   - Tasks 12-20
   - Card layout, breadcrumbs, swipe actions, multi-select

---

## 🧪 Testing Checklist

### Memory Leak Verification
- [ ] Open 5 SSH tabs, close all → verify `TerminalService.bridges` is empty
- [ ] Open 3 SFTP tabs, close all → verify `SftpViewModel.activeClients` is empty
- [ ] Reconnect 10 times → verify `ConsoleViewModel.observeJobs.size` stays at 4
- [ ] Profile with Android Studio → verify heap doesn't grow after tab cycles

### Tab Functionality
- [x] Create SSH tab → works
- [x] Create SFTP tab → works
- [x] Switch between tabs → works
- [x] Close tab → works (but SFTP client leaks)
- [ ] Reorder tabs via drag-drop → needs testing
- [x] Tab persistence across app restart → works (Room)

### Edge Cases
- [ ] Close last tab → app should show empty state
- [ ] Open 10+ tabs → verify scrolling works
- [ ] Rotate device → verify tabs survive configuration change
- [ ] Kill app process → verify tabs restore on relaunch

---

## 📁 Key Files

### Tab Infrastructure
- `data/entity/Tab.kt` - Tab entity
- `data/dao/TabDao.kt` - Tab DAO
- `data/repository/TabRepository.kt` - Tab repository
- `ui/viewmodel/TabManager.kt` - Tab state management
- `ui/TabbedMainScreen.kt` - Tab UI (HorizontalPager)
- `ui/components/TabBar.kt` - Tab bar component
- `di/DatabaseModule.kt` - Migrations

### Memory Leak Fixes
- `ui/screens/viewmodel/ConsoleViewModel.kt` - Observer cleanup
- `ui/screens/viewmodel/SftpViewModel.kt` - Static map cleanup
- `service/TerminalBridge.kt` - Scope cancellation
- `service/TerminalService.kt` - Bridge close ordering

### Documentation
- `MEMORY_LEAK_ANALYSIS.md` - Detailed leak analysis
- `feature_list.json` - Feature tracking
- `openspec/changes/ui-modernization-vivaldi-tabs/tasks.md` - OpenSpec tasks
