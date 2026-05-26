# Spec Delta: Terminal Clipboard Integration

## ADDED Requirements

### R1: OSC 52 Clipboard Write

**Requirement:** When the terminal emulator receives an OSC 52 sequence with clipboard target 'c', the base64-encoded text SHALL be decoded and written to the Android system clipboard.

**Rationale:** Standard terminal feature for server-initiated clipboard operations (vim, tmux, etc.)

**Acceptance Criteria:**
- Given: Terminal receives `\033]52;c;aGVsbG8=\a` (base64 "hello")
- When: Sequence is parsed by `TerminalEmulator.doOscSetTextParameters()`
- Then: Android clipboard contains "hello"

**Test Cases:**
1. Valid base64 text → clipboard updated
2. Empty base64 → clipboard set to empty string
3. Invalid base64 → error logged, clipboard unchanged
4. Non-ASCII (emoji) → clipboard contains UTF-8 text

---

### R2: Clipboard Logging

**Requirement:** All clipboard write operations SHALL be logged with a preview of the text (max 50 characters).

**Rationale:** Debugging OSC 52 sequences and user support.

**Acceptance Criteria:**
- Given: `onCopyTextToClipboard("test123")` is called
- When: Clipboard is updated
- Then: Logcat contains `[OSC52] Copied to clipboard: test123`

**Format:**
```
[OSC52] Copied to clipboard: <text>
[OSC52] Copied to clipboard: <first 50 chars>...  (if text.length > 50)
```

---

### R3: ClipboardManager Injection

**Requirement:** `TerminalBridge` SHALL receive `ClipboardManager` via Hilt dependency injection.

**Rationale:** Testability and Android best practices.

**Implementation:**
- `AppModule.kt`: Provide `ClipboardManager` as singleton
- `TerminalBridge.kt`: Inject via constructor parameter

---

### R4: Error Handling

**Requirement:** Invalid OSC 52 sequences SHALL be logged and SHALL NOT crash the application.

**Rationale:** Robustness against malformed server output.

**Scenarios:**
- Invalid base64 → `Base64.decode()` exception caught → logged
- Null text → No-op (defensive programming)
- Clipboard service unavailable → Exception caught, logged

---

## MODIFIED Requirements

None (new feature, no existing requirements modified)

---

## REMOVED Requirements

None

---

## Invariants

### I1: Clipboard Isolation
**Invariant:** OSC 52 clipboard writes SHALL only affect the DarkSSH app's clipboard space (Android sandbox).

**Justification:** Android 10+ restricts clipboard access per-app. No cross-app clipboard pollution.

---

### I2: No UI Thread Blocking
**Invariant:** Clipboard operations SHALL complete in <10ms on the main thread.

**Justification:** `Base64.decode()` and `setPrimaryClip()` are fast enough for typical sizes (<100KB).

**Future:** If profiling shows >10ms, offload to IO dispatcher.

---

### I3: Idempotency
**Invariant:** Multiple identical OSC 52 sequences SHALL produce the same clipboard state.

**Behavior:** Last write wins (expected for clipboard operations).

---

## Dependencies

### D1: Termux Terminal Emulator
- Already integrated: `TerminalEmulator.doOscSetTextParameters()` parses OSC 52
- Location: `app/src/main/java/com/darkssh/client/terminal/emulator/TerminalEmulator.java:2107`

### D2: Android ClipboardManager
- System service: `Context.CLIPBOARD_SERVICE`
- No special permissions required

---

## Future Work

### F1: Bidirectional Clipboard Sync
**Feature:** Send OSC 52 to server when Android clipboard changes.

**Protocol:**
1. Terminal sends `\033]52;c;?\a` to request clipboard monitoring
2. App listens to `ClipboardManager.addPrimaryClipChangedListener()`
3. On change: encode clipboard to base64, send `\033]52;c;base64\a` to server

**Security:** Only enable if server explicitly requests it.

---

### F2: User Settings
**Feature:** Toggle "Allow clipboard from terminal" in Settings screen.

**Default:** Enabled

**UI:** SettingsScreen → Terminal section → Switch

---

### F3: Clipboard History
**Feature:** Show last N clipboard writes in a drawer.

**UI:** ConsoleScreen → Swipe from right → List of clipboard entries with timestamps

---

## References

- **OSC 52 Spec:** https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h3-Operating-System-Commands
- **Termux Implementation:** `com.termux.app.TermuxTerminalSessionClient.onCopyTextToClipboard()`
- **Kitty Protocol:** https://sw.kovidgoyal.net/kitty/protocol-extensions/#pasting-to-clipboard
- **Android Clipboard:** https://developer.android.com/reference/android/content/ClipboardManager
