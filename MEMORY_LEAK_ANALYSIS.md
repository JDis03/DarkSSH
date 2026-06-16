# Memory Leak Analysis - Post Tabs Implementation

## Executive Summary

After implementing Vivaldi-style tabs, several memory leak risks and stability issues have been identified. This document outlines critical issues and recommended fixes.

## Critical Issues

### 1. **SftpViewModel - Static Collections Leak** 🔴 HIGH PRIORITY

**Location:** `SftpViewModel.kt:110-141`

**Problem:**
```kotlin
companion object {
    internal val activeClients = ConcurrentHashMap<Long, SftpClient>()
    private val activeTransfers = ConcurrentHashMap<String, TransferProgress>()
    private val activeWorkIds = ConcurrentHashMap<Long, UUID>()
}
```

**Impact:**
- `activeClients` holds SFTP connections indefinitely (never cleaned up)
- `activeTransfers` accumulates transfer state across ViewModel recreations
- `activeWorkIds` tracks WorkManager jobs but never removes completed ones
- **Memory grows unbounded** as user opens/closes tabs

**Evidence:**
- `onCleared()` only cancels `currentTransferJob`, doesn't clean up static maps
- No cleanup when tab is closed or host is deleted
- Connections survive app restart (intentional for background uploads, but leak otherwise)

**Recommended Fix:**
```kotlin
override fun onCleared() {
    super.onCleared()
    currentTransferJob?.cancel()
    
    // Clean up this ViewModel's resources from static maps
    val hostId = _uiState.value.host?.id
    if (hostId != null) {
        // Only disconnect if no active transfers for this host
        if (getActiveWorkId(hostId) == null && getActiveTransfer("upload_$hostId") == null) {
            activeClients[hostId]?.let { client ->
                viewModelScope.launch {
                    try {
                        client.disconnect()
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to disconnect SFTP client on ViewModel clear")
                    }
                }
            }
            activeClients.remove(hostId)
        }
    }
    
    Timber.d("SftpViewModel cleared - cleaned up resources for host $hostId")
}
```

**Additional Fix - WorkManager Cleanup:**
```kotlin
// In observeUploadWork(), after work completes:
if (workInfo.state.isFinished) {
    setActiveWorkId(hostId, null) // Remove from map
}
```

---

### 2. **ConsoleViewModel - Observer Job Accumulation** 🟡 MEDIUM PRIORITY

**Location:** `ConsoleViewModel.kt:42-136`

**Problem:**
```kotlin
private var observeJobs = mutableListOf<Job>()

private fun observeBridge(b: TerminalBridge) {
    observeJobs.add(viewModelScope.launch { ... })  // 4 jobs per bridge
    observeJobs.add(viewModelScope.launch { ... })
    observeJobs.add(viewModelScope.launch { ... })
    observeJobs.add(viewModelScope.launch { ... })
}
```

**Impact:**
- Each `connect()` call adds 4 new coroutine jobs to `observeJobs`
- If user reconnects multiple times, jobs accumulate (even though old ones are cancelled)
- List grows indefinitely: 4 jobs → 8 jobs → 12 jobs → ...

**Evidence:**
- `connect()` calls `observeJobs.forEach { it.cancel() }` but doesn't clear the list before adding new jobs
- `reconnect()` clears the list properly, but `connect()` doesn't

**Recommended Fix:**
```kotlin
fun connect(hostId: Long, tabId: String? = null) {
    connectionJob?.cancel()
    observeJobs.forEach { it.cancel() }
    observeJobs.clear()  // ✅ ADD THIS LINE
    currentTabId = tabId
    currentHostId = hostId
    // ... rest of function
}
```

---

### 3. **TerminalBridge - Scope Not Cancelled on Close** 🟡 MEDIUM PRIORITY

**Location:** `TerminalBridge.kt:59-60, 280-310`

**Problem:**
```kotlin
private val bridgeJob = SupervisorJob()
private val bridgeScope = CoroutineScope(bridgeJob + Dispatchers.Main)

fun close() {
    // ... cleanup code ...
    // ❌ MISSING: bridgeJob.cancel() or bridgeScope.cancel()
}
```

**Impact:**
- `bridgeScope` continues running after `close()` is called
- Coroutines launched in `startConnection()` and `processTransportOperations()` may leak
- Relay thread may not terminate cleanly

**Recommended Fix:**
```kotlin
fun close() {
    if (isClosed) return
    isClosed = true
    
    Timber.d("Closing bridge for ${host.nickname}")
    
    // Cancel all coroutines first
    bridgeScope.cancel()  // ✅ ADD THIS
    
    // Then cleanup resources
    relay?.stop()
    transport?.close()
    darkTerminalSession?.finishIfRunning()
    
    _isConnected.value = false
    _isDisconnected.value = true
}
```

---

### 4. **TerminalService - Bridge Removal Race Condition** 🟢 LOW PRIORITY

**Location:** `TerminalService.kt:141-161`

**Problem:**
```kotlin
fun onBridgeDisconnected(bridge: TerminalBridge, reason: DisconnectReason) {
    val currentBridges = _bridges.value.toMutableList()
    currentBridges.remove(bridge)
    _bridges.value = currentBridges
    
    // ... notification update ...
    
    bridge.close()  // ⚠️ Called AFTER removal from list
}
```

**Impact:**
- If another thread accesses `bridges.value` between removal and `close()`, it sees a bridge that's about to be closed
- Minor race condition, unlikely to cause crashes but could cause UI flicker

**Recommended Fix:**
```kotlin
fun onBridgeDisconnected(bridge: TerminalBridge, reason: DisconnectReason) {
    // Close bridge FIRST
    bridge.close()
    
    // Then remove from list
    val currentBridges = _bridges.value.toMutableList()
    currentBridges.remove(bridge)
    _bridges.value = currentBridges
    
    // ... rest of function
}
```

---

### 5. **Missing Tab Cleanup on Tab Close** 🔴 HIGH PRIORITY

**Location:** Multiple files (no centralized tab cleanup)

**Problem:**
- When user closes a tab, there's no cleanup of:
  - Associated `TerminalBridge` in `TerminalService`
  - Associated `SftpClient` in `SftpViewModel.activeClients`
  - Observer jobs in `ConsoleViewModel`
  - Room database `Tab` entity

**Impact:**
- Bridges and SFTP clients leak when tabs are closed
- Database grows with orphaned tab records
- Memory usage increases with each tab open/close cycle

**Recommended Fix:**

Create a centralized tab cleanup function:

```kotlin
// In TerminalService.kt
fun closeTab(tabId: String) {
    val bridge = _bridges.value.find { it.tabId == tabId }
    if (bridge != null) {
        onBridgeDisconnected(bridge, DisconnectReason.USER_REQUESTED)
    }
    
    // Clean up SFTP client if exists
    // (requires refactoring SftpViewModel.activeClients to use tabId instead of hostId)
    
    // Delete from Room
    serviceScope.launch {
        tabRepository.deleteTab(tabId)
    }
}
```

**Note:** This requires refactoring `SftpViewModel.activeClients` from `ConcurrentHashMap<Long, SftpClient>` (hostId) to `ConcurrentHashMap<String, SftpClient>` (tabId) to support multiple tabs per host.

---

## Stability Issues

### 6. **Terminal Resize Race Condition** ✅ FIXED

**Status:** Already fixed in commit `437979b8`

**Fix Applied:**
- `TerminalBuffer.externalToInternalRow()` now clamps instead of throwing
- `TerminalRenderer.render()` uses `screen.getScreenRows()` instead of `mEmulator.mRows`

---

### 7. **Silent Disconnect Detection** ✅ FIXED

**Status:** Already fixed in commit `437979b8`

**Fix Applied:**
- Moved from `LaunchedEffect` to `ConsoleViewModel.observeBridge()`
- Added `wasConnected` flag to avoid false positives during initial connection

---

## OpenSpec Tasks Status

### Completed Tasks (from `tasks.md`)

Based on code review, the following tasks are **DONE**:

- ✅ **10.1-10.8**: Terminal Session - Tab Association (tabId in TerminalBridge, getBridgeForTab)
- ✅ **Partial 11.1-11.6**: SFTP Session - Tab Association (activeClients exists, but uses hostId not tabId)

### High Priority Pending Tasks

From `openspec/changes/ui-modernization-vivaldi-tabs/tasks.md`:

1. **Database Schema - Tab Entity** (Tasks 1.1-1.6) - NOT STARTED
   - No `Tab` Room entity exists yet
   - No migration for tabs table
   - **Blocker for proper tab persistence**

2. **Tab State Management** (Tasks 2.1-2.10) - NOT STARTED
   - No `TabManager` ViewModel
   - No tab creation/deletion/reordering logic
   - **Blocker for multi-tab UI**

3. **Theme System** (Tasks 3.1-3.8) - NOT STARTED
   - Still using hardcoded colors
   - No Material 3 darkColorScheme

4. **HorizontalPager Integration** (Tasks 9.1-9.9) - NOT STARTED
   - No pager for tab content
   - **Blocker for swipe navigation**

### Current Implementation Gap

**What exists:**
- `TerminalBridge` has `tabId` parameter
- `TerminalService.openConnection()` accepts `tabId`
- `ConsoleViewModel` tracks `currentTabId`

**What's missing:**
- No UI for creating/managing tabs
- No persistence (Room Tab entity)
- No TabManager ViewModel
- No HorizontalPager for tab content
- SFTP still uses hostId instead of tabId

**Conclusion:** Tab infrastructure is partially implemented (backend), but **no user-facing tab UI exists yet**.

---

## Recommendations

### Immediate Actions (This Sprint)

1. **Fix SftpViewModel static map leaks** (1-2 hours)
   - Add cleanup in `onCleared()`
   - Remove completed WorkManager jobs from `activeWorkIds`

2. **Fix ConsoleViewModel observer accumulation** (30 minutes)
   - Add `observeJobs.clear()` in `connect()`

3. **Fix TerminalBridge scope leak** (30 minutes)
   - Add `bridgeScope.cancel()` in `close()`

4. **Update feature_list.json** (15 minutes)
   - Mark memory leak fixes as new tasks
   - Update OpenSpec task status

### Next Sprint

5. **Implement Tab Entity + TabManager** (4-6 hours)
   - Tasks 1.1-1.6, 2.1-2.10 from OpenSpec
   - Enables proper tab lifecycle management

6. **Refactor SFTP to use tabId** (2-3 hours)
   - Change `activeClients` key from `Long` (hostId) to `String` (tabId)
   - Enables multiple SFTP tabs per host

7. **Add centralized tab cleanup** (2 hours)
   - `TerminalService.closeTab(tabId)`
   - Cleanup bridge, SFTP client, Room record

### Future Work

8. **Implement HorizontalPager UI** (6-8 hours)
   - Tasks 9.1-9.9 from OpenSpec
   - User-facing tab navigation

9. **Add Material 3 theme system** (4-6 hours)
   - Tasks 3.1-3.8 from OpenSpec
   - Consistent dark theme

---

## Testing Checklist

After fixes are applied:

- [ ] Open 5 SSH tabs, close all → verify no leaked bridges in `TerminalService.bridges`
- [ ] Open 3 SFTP tabs, upload files, close tabs → verify `activeClients` is empty
- [ ] Reconnect to same host 10 times → verify `observeJobs.size` stays at 4
- [ ] Profile with Android Studio Profiler → verify no growing heap after tab open/close cycles
- [ ] Check logcat for "TerminalBridge closed" messages → verify `bridgeScope.cancel()` is called

---

## Appendix: Memory Profiling Commands

```bash
# Connect to device
adb devices

# Start profiling
adb shell am start -n com.darkssh.client/.ui.MainActivity
adb shell dumpsys meminfo com.darkssh.client

# After opening/closing 10 tabs
adb shell dumpsys meminfo com.darkssh.client

# Compare heap growth
```

Expected result: Heap size should stabilize after fixes, not grow linearly with tab count.
