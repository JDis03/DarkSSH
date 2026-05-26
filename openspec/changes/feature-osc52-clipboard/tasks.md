# Tasks: OSC 52 Clipboard Integration

## Phase 1: Core Implementation

- [ ] **T1.1** Add `ClipboardManager` provider in `AppModule.kt`
  - File: `app/src/main/java/com/darkssh/client/di/AppModule.kt`
  - Add `@Provides @Singleton fun provideClipboardManager(...)`
  
- [ ] **T1.2** Inject `ClipboardManager` into `TerminalBridge`
  - File: `app/src/main/java/com/darkssh/client/service/TerminalBridge.kt`
  - Add constructor parameter: `private val clipboardManager: ClipboardManager`
  
- [ ] **T1.3** Implement `onCopyTextToClipboard()` in `TerminalBridge`
  - File: `app/src/main/java/com/darkssh/client/service/TerminalBridge.kt`
  - Create `ClipData.newPlainText("terminal", text)`
  - Call `clipboardManager.setPrimaryClip(clip)`
  - Add Timber log with preview (max 50 chars)

## Phase 2: Testing

- [ ] **T2.1** Manual test: printf OSC 52 sequence
  - SSH to server
  - Run: `printf "\033]52;c;$(echo -n "test123" | base64)\a"`
  - Verify "test123" appears in Android clipboard (Gboard paste menu)
  
- [ ] **T2.2** Manual test: vim clipboard
  - SSH to server with vim installed
  - Open vim, type text
  - Run: `:echo system('echo -n ' . shellescape(@") . ' | base64 | xargs -I {} printf "\\033]52;c;{};\\a"')`
  - Verify text copied to Android clipboard
  
- [ ] **T2.3** Manual test: tmux copy-mode
  - SSH to server with tmux
  - Enter copy-mode (`Ctrl+b [`)
  - Select text, press `y`
  - Verify text copied to Android clipboard (requires tmux OSC 52 config)
  
- [ ] **T2.4** Check logcat output
  - Run: `adb logcat | grep OSC52`
  - Verify: `[OSC52] Copied to clipboard: <text>`

## Phase 3: Edge Cases

- [ ] **T3.1** Test empty clipboard
  - Run: `printf "\033]52;c;\a"` (empty base64)
  - Verify: clipboard set to empty string (no crash)
  
- [ ] **T3.2** Test invalid base64
  - Run: `printf "\033]52;c;INVALID!!!\a"`
  - Verify: Error logged, clipboard unchanged
  
- [ ] **T3.3** Test emoji / non-ASCII
  - Run: `printf "\033]52;c;$(echo -n "hello 👋" | base64)\a"`
  - Verify: "hello 👋" in clipboard
  
- [ ] **T3.4** Test large text (10KB)
  - Generate 10KB base64: `head -c 10000 /dev/urandom | base64 | xargs printf "\033]52;c;%s\a"`
  - Verify: No crash, clipboard set

## Phase 4: Documentation

- [ ] **T4.1** Update skill summary
  - File: `/home/dark/.agents/skills/darkssh-client/SKILL.md`
  - Add OSC 52 to "Done" list
  
- [ ] **T4.2** Update session log
  - Document testing results
  - Add commit hash when implemented

## Phase 5: Future Enhancements (Not in Scope)

- [ ] **T5.1** Add Settings toggle for OSC 52
  - UI: "Allow clipboard from terminal" switch
  - Default: enabled
  
- [ ] **T5.2** Add toast notification on clipboard write
  - UI: "Copied to clipboard" snackbar
  - Preference: "Show clipboard notifications"
  
- [ ] **T5.3** Implement OSC 52 query (Android → Server)
  - Listen to `ClipboardManager.addPrimaryClipChangedListener()`
  - Send `\033]52;c;base64\a` when clipboard changes
  - Requires terminal to request it first (security)

## Verification Checklist

After all tasks complete:

- [ ] ✅ OSC 52 sequences copy text to Android clipboard
- [ ] ✅ Vim/tmux workflows work as expected
- [ ] ✅ Edge cases handled (empty, invalid, emoji, large)
- [ ] ✅ Logs show clipboard operations
- [ ] ✅ No crashes or performance issues
- [ ] ✅ Documentation updated
