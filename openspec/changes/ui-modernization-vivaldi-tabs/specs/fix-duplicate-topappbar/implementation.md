# Implementation Guide: Fix Duplicate TopAppBar

## Overview
This document provides exact code changes to implement the fix-duplicate-topappbar spec.

## File: ConsoleScreen.kt

### Location 1: Function Signature (Line ~72)

**Before:**
```kotlin
@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConsoleScreen(
    hostId: Long,
    onBack: () -> Unit,
    terminalService: TerminalService? = null,
    modifier: Modifier = Modifier,
    viewModel: ConsoleViewModel = hiltViewModel(),
) {
```

**After:**
```kotlin
@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConsoleScreen(
    hostId: Long,
    onBack: () -> Unit,
    terminalService: TerminalService? = null,
    modifier: Modifier = Modifier,
    inTab: Boolean = false,
    viewModel: ConsoleViewModel = hiltViewModel(),
) {
```

**Diff:**
```diff
@Composable
fun ConsoleScreen(
    hostId: Long,
    onBack: () -> Unit,
    terminalService: TerminalService? = null,
    modifier: Modifier = Modifier,
+   inTab: Boolean = false,
    viewModel: ConsoleViewModel = hiltViewModel(),
) {
```

---

### Location 2: TopAppBar Conditional Start (Line ~113)

**Before:**
```kotlin
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.imeAnimationTarget,
        topBar = {
            TopAppBar(
                title = { Text(host?.nickname?.ifBlank { host?.hostname } ?: "Terminal") },
                navigationIcon = {
```

**After:**
```kotlin
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.imeAnimationTarget,
        topBar = {
            if (!inTab) {
                TopAppBar(
                    title = { Text(host?.nickname?.ifBlank { host?.hostname } ?: "Terminal") },
                    navigationIcon = {
```

**Diff:**
```diff
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.imeAnimationTarget,
        topBar = {
+           if (!inTab) {
                TopAppBar(
                    title = { Text(host?.nickname?.ifBlank { host?.hostname } ?: "Terminal") },
```

---

### Location 3: TopAppBar Conditional End (Line ~170)

**Before:**
```kotlin
                    }
                },
            )
        },
    ) { innerPadding ->
```

**After:**
```kotlin
                    }
                },
            )
            }
        },
    ) { innerPadding ->
```

**Diff:**
```diff
                    }
                },
            )
+           }
        },
    ) { innerPadding ->
```

---

## File: SftpScreen.kt

### Location 1: Main Function Signature (Line ~97)

**Before:**
```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("ktlint:standard:function-naming")
@Composable
fun SftpScreen(
    hostId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SftpViewModel = hiltViewModel(),
) {
```

**After:**
```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("ktlint:standard:function-naming")
@Composable
fun SftpScreen(
    hostId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    inTab: Boolean = false,
    viewModel: SftpViewModel = hiltViewModel(),
) {
```

**Diff:**
```diff
@Composable
fun SftpScreen(
    hostId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
+   inTab: Boolean = false,
    viewModel: SftpViewModel = hiltViewModel(),
) {
```

---

### Location 2: Main TopAppBar Start (Line ~291)

**Before:**
```kotlin
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.imeAnimationTarget,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.currentPath,
```

**After:**
```kotlin
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.imeAnimationTarget,
        topBar = {
            if (!inTab) {
                TopAppBar(
                    title = {
                        Text(
                            uiState.currentPath,
```

**Diff:**
```diff
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.imeAnimationTarget,
        topBar = {
+           if (!inTab) {
                TopAppBar(
                    title = {
                        Text(
                            uiState.currentPath,
```

---

### Location 3: Main TopAppBar End (Line ~400-402)

**Search Pattern:**
```kotlin
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
```

**After:**
```kotlin
                },
            )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
```

**Diff:**
```diff
                },
            )
+           }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
```

---

### Location 4: SftpConnectingScreen Function (Line ~542)

**Before:**
```kotlin
@Composable
private fun SftpConnectingScreen(hostname: String) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SFTP - $hostname") },
```

**After:**
```kotlin
@Composable
private fun SftpConnectingScreen(hostname: String, inTab: Boolean = false) {
    Scaffold(
        topBar = {
            if (!inTab) {
                TopAppBar(
                    title = { Text("SFTP - $hostname") },
```

**And close the if:**
```kotlin
            )
            }
        },
    ) { paddingValues ->
```

---

### Location 5: SftpErrorScreen Function (Line ~631)

**Before:**
```kotlin
@Composable
private fun SftpErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SFTP Connection Failed") },
```

**After:**
```kotlin
@Composable
private fun SftpErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    inTab: Boolean = false,
) {
    Scaffold(
        topBar = {
            if (!inTab) {
                TopAppBar(
                    title = { Text("SFTP Connection Failed") },
```

**And close the if:**
```kotlin
            )
            }
        },
    ) { paddingValues ->
```

---

## File: TabbedMainScreen.kt

### Location 1: Collect currentTabIndex (Line ~36)

**Before:**
```kotlin
@Composable
fun TabbedMainScreen(
    terminalService: TerminalService? = null,
    tabManager: TabManager = hiltViewModel(),
) {
    val tabs by tabManager.tabs.collectAsState()
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
```

**After:**
```kotlin
@Composable
fun TabbedMainScreen(
    terminalService: TerminalService? = null,
    tabManager: TabManager = hiltViewModel(),
) {
    val tabs by tabManager.tabs.collectAsState()
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val currentTabIndex by tabManager.currentTabIndex.collectAsState()
```

**Diff:**
```diff
    val tabs by tabManager.tabs.collectAsState()
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
+   val currentTabIndex by tabManager.currentTabIndex.collectAsState()
```

---

### Location 2: Add Sync LaunchedEffects (After pagerState declaration)

**Add these two LaunchedEffects:**

```kotlin
    // Sync pager → tabManager
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentTabIndex) {
            tabManager.switchTab(pagerState.currentPage)
        }
    }

    // Sync tabManager → pager
    LaunchedEffect(currentTabIndex) {
        if (pagerState.currentPage != currentTabIndex && currentTabIndex < tabs.size) {
            pagerState.animateScrollToPage(currentTabIndex)
        }
    }
```

---

### Location 3: Pass inTab to ConsoleScreen (Line ~85)

**Before:**
```kotlin
                    when (tab.type) {
                        TabType.SSH_TERMINAL -> {
                            ConsoleScreen(
                                hostId = tab.hostId,
                                onBack = { /* Tabs don't have back */ },
                                terminalService = terminalService,
                            )
                        }
```

**After:**
```kotlin
                    when (tab.type) {
                        TabType.SSH_TERMINAL -> {
                            ConsoleScreen(
                                hostId = tab.hostId,
                                onBack = { /* Tabs don't have back */ },
                                terminalService = terminalService,
                                inTab = true,
                            )
                        }
```

**Diff:**
```diff
                        TabType.SSH_TERMINAL -> {
                            ConsoleScreen(
                                hostId = tab.hostId,
                                onBack = { /* Tabs don't have back */ },
                                terminalService = terminalService,
+                               inTab = true,
                            )
                        }
```

---

### Location 4: Pass inTab to SftpScreen (Line ~92)

**Before:**
```kotlin
                        TabType.SFTP_BROWSER -> {
                            SftpScreen(
                                hostId = tab.hostId,
                                onBack = { /* Tabs don't have back */ },
                            )
                        }
```

**After:**
```kotlin
                        TabType.SFTP_BROWSER -> {
                            SftpScreen(
                                hostId = tab.hostId,
                                onBack = { /* Tabs don't have back */ },
                                inTab = true,
                            )
                        }
```

**Diff:**
```diff
                        TabType.SFTP_BROWSER -> {
                            SftpScreen(
                                hostId = tab.hostId,
                                onBack = { /* Tabs don't have back */ },
+                               inTab = true,
                            )
                        }
```

---

## Verification Commands

### Compile Check:
```bash
./gradlew :app:compileDebugKotlin
```
**Expected:** BUILD SUCCESSFUL

### Full Build:
```bash
./gradlew :app:assembleDebug
```
**Expected:** BUILD SUCCESSFUL, APK generated

### Install:
```bash
./gradlew :app:installDebug
```
**Expected:** Installed on 1 device

### Launch:
```bash
adb shell monkey -p com.darkssh.client.debug 1
```

---

## Test Cases

### TC1: No Duplicate TopAppBar in SSH Tab
1. Open app
2. Tap host "ddd"
3. **Verify:** Only TabBar visible at top, NO "← ddd ⋮" below

### TC2: No Duplicate TopAppBar in SFTP Tab
1. Tap SFTP button on host
2. **Verify:** Only TabBar visible, NO path bar below

### TC3: SFTP Tab Switches Immediately
1. Tap SFTP button
2. **Verify:** New tab created AND pager scrolls to it automatically

### TC4: Swipe Navigation Works
1. Create 2 tabs
2. Swipe left/right
3. **Verify:** Content changes smoothly

### TC5: Close Tab Works
1. Tap [×] on a tab
2. **Verify:** Tab closes, others remain

---

## Success Criteria

✅ All test cases pass  
✅ Build succeeds without errors  
✅ No visual regression (TopAppBars gone)  
✅ SFTP tab switching works  
✅ Backward compatibility maintained (old nav code still works)
