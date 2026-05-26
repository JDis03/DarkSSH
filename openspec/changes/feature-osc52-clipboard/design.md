# Design: OSC 52 Clipboard Integration

## Architecture

```
Server sends OSC 52
    ↓
TerminalEmulator.doOscSetTextParameters() (line 2107)
    ↓ parses "52;c;base64data"
    ↓ decodes base64 → plaintext
    ↓
mSession.onCopyTextToClipboard(text)
    ↓
TerminalBridge.onCopyTextToClipboard(text)
    ↓
ClipboardManager.setPrimaryClip(ClipData)
    ↓
Android System Clipboard ✅
```

## Implementation Plan

### 1. Inject ClipboardManager

**File:** `app/src/main/java/com/darkssh/client/service/TerminalBridge.kt`

```kotlin
class TerminalBridge @Inject constructor(
    private val clipboardManager: ClipboardManager  // Add this
) : TerminalSessionClient {
    // ...
}
```

**Module:** `app/src/main/java/com/darkssh/client/di/AppModule.kt`

```kotlin
@Provides
@Singleton
fun provideClipboardManager(
    @ApplicationContext context: Context
): ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
```

### 2. Implement onCopyTextToClipboard

**File:** `app/src/main/java/com/darkssh/client/service/TerminalBridge.kt`

```kotlin
override fun onCopyTextToClipboard(text: String) {
    val preview = if (text.length > 50) text.take(50) + "..." else text
    Timber.d("[OSC52] Copied to clipboard: $preview")
    
    val clip = ClipData.newPlainText("terminal", text)
    clipboardManager.setPrimaryClip(clip)
}
```

### 3. Add Security Guard (Future Enhancement)

**Option A:** Only allow if terminal is visible
```kotlin
private var isTerminalFocused = false

fun onTerminalFocused() { isTerminalFocused = true }
fun onTerminalBlurred() { isTerminalFocused = false }

override fun onCopyTextToClipboard(text: String) {
    if (!isTerminalFocused) {
        Timber.w("[OSC52] Clipboard write blocked: terminal not focused")
        return
    }
    // ... rest of implementation
}
```

**Decision:** Not implementing focus guard in v1 — clipboard API is already sandboxed per-app in Android.

### 4. Testing Strategy

**Manual testing:**
1. SSH to server
2. Run: `printf "\033]52;c;$(echo -n "test123" | base64)\a"`
3. Open Android "Gboard" → long press paste button → verify "test123" appears
4. Check logcat for `[OSC52] Copied to clipboard: test123`

**Unit test:**
```kotlin
@Test
fun `onCopyTextToClipboard writes to clipboard`() {
    val mockClipboard = mock<ClipboardManager>()
    val bridge = TerminalBridge(mockClipboard)
    
    bridge.onCopyTextToClipboard("test")
    
    verify(mockClipboard).setPrimaryClip(any())
}
```

## Data Flow

### OSC 52 Format
```
ESC ] 52 ; selection ; base64data BEL
  |    |      |            |        |
  |    |      |            |        \a (Bell) or ESC \ (ST)
  |    |      |            \-- base64-encoded text
  |    |      \-- selection target ('c' = clipboard, 's' = primary)
  |    \-- OSC command number
  \-- ESC ] (Operating System Command)
```

**Example:**
```bash
# Copy "hello" to clipboard
echo -n "hello" | base64  # → aGVsbG8=
printf "\033]52;c;aGVsbG8=\a"
```

### TerminalEmulator Parsing

**File:** `app/src/main/java/com/darkssh/client/terminal/emulator/TerminalEmulator.java:2107`

```java
case 52: // Manipulate Selection Data
    int startIndex = textParameter.indexOf(";") + 1;
    try {
        String clipboardText = new String(
            Base64.decode(textParameter.substring(startIndex), 0),
            StandardCharsets.UTF_8
        );
        mSession.onCopyTextToClipboard(clipboardText);
    } catch (Exception e) {
        Logger.logError(mClient, LOG_TAG, "OSC 52 invalid: " + textParameter);
    }
    break;
```

**Current behavior:**
- ✅ Parses `textParameter` = "c;aGVsbG8="
- ✅ Extracts base64 after first `;` → "aGVsbG8="
- ✅ Decodes to "hello"
- ✅ Calls `onCopyTextToClipboard("hello")`
- ❌ No-op in current implementation

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| Empty base64 | Decode returns empty string → set clipboard to "" (valid) |
| Invalid base64 | `Base64.decode()` throws → caught, logged, no clipboard write |
| Non-ASCII text (emoji) | UTF-8 decode → works (tested in Termux) |
| Very long text (>1MB) | Android clipboard handles truncation automatically |
| Rapid successive OSC 52 | Each call overwrites previous → last one wins (expected) |
| Terminal not visible | ⚠️ No guard in v1 — Android sandboxes clipboard per-app anyway |

## Performance

- `Base64.decode()`: O(n), fast for typical clipboard sizes (<100KB)
- `setPrimaryClip()`: Binder call, ~1-5ms
- **No UI thread blocking**: Both operations are fast enough for main thread
- **Future optimization**: If clipboard ops >10ms, offload to IO dispatcher

## Security

**Android Clipboard API constraints:**
- Apps cannot read other apps' clipboard without user interaction (Android 10+)
- Apps can only write to their own clipboard space
- Clipboard access requires no special permissions

**OSC 52 attack vectors:**
- ❌ **Clipboard poisoning**: Server could spam clipboard with malicious data
  - Mitigation: Trust SSH server (authenticated connection)
- ❌ **Data exfiltration**: Malicious terminal could send clipboard to attacker
  - Not applicable — OSC 52 is server → client, not client → server
- ✅ **Focus-based guard**: Not needed in v1 (app-sandboxed clipboard)

## Future Enhancements

1. **Bidirectional sync**: Send `OSC 52;?` query when Android clipboard changes
2. **User preference**: Toggle "Allow OSC 52 clipboard" in Settings
3. **Notification**: Toast when clipboard is written (opt-in)
4. **Clipboard history**: Show last N OSC 52 clips in UI

## References

- Termux OSC 52: `com.termux.app.TermuxTerminalSessionClient.onCopyTextToClipboard()`
- Kitty OSC 52: https://sw.kovidgoyal.net/kitty/protocol-extensions/#pasting-to-clipboard
- Android ClipboardManager: https://developer.android.com/reference/android/content/ClipboardManager
