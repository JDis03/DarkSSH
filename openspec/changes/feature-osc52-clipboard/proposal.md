# Proposal: OSC 52 Clipboard Integration

## What

Implement bidirectional clipboard sync between the terminal and Android system clipboard using OSC 52 escape sequences.

## Why

**Problem:**
- Server applications (vim, tmux, etc.) can send OSC 52 sequences to copy text to clipboard
- Currently these sequences are parsed but not connected to Android clipboard
- Users cannot copy text from terminal apps to Android clipboard automatically

**Impact:**
- Poor UX: users must long-press and manually select text
- Missing standard terminal feature (available in Kitty, iTerm2, Termux, etc.)
- SSH workflows broken (vim `:w !pbcopy`, tmux copy-mode, etc.)

**Use cases:**
1. Vim/Neovim: yank to system clipboard with `"+y`
2. Tmux: copy-mode with `y` key sends to clipboard
3. SSH scripts: `echo "data" | base64 | printf "\033]52;c;%s\a"`

## Goals

1. ✅ **Server → Android**: Parse OSC 52, decode base64, write to Android clipboard
2. 🎯 **Android → Server** (future): Send OSC 52 on clipboard change (if terminal requests it)
3. ✅ **Security**: Only allow clipboard operations when terminal is focused
4. ✅ **Logging**: Log clipboard operations for debugging

## Non-Goals

- OSC 52 query (server asks for clipboard content) — not implemented yet
- Clipboard history — Android handles this natively
- Rich text / HTML clipboard — only plain text supported in OSC 52

## Success Criteria

1. ✅ Vim command `echo "test" | base64 | printf "\033]52;c;%s\a"` copies "test" to Android
2. ✅ Neovim `"+y` copies yanked text to Android clipboard
3. ✅ Tmux copy-mode sends to Android clipboard
4. ✅ No clipboard writes when terminal is not focused (security)
5. ✅ Logs show `[OSC52] Copied to clipboard: <preview>` on success

## Technical Approach

**Current state:**
- `TerminalEmulator.doOscSetTextParameters()` parses OSC 52 at line 2107
- `mSession.onCopyTextToClipboard(clipboardText)` is called with decoded text
- `TerminalBridge.onCopyTextToClipboard()` is a no-op (empty implementation)

**Proposed changes:**
1. Inject `ClipboardManager` into `TerminalBridge` via Hilt
2. Implement `onCopyTextToClipboard()` to call `clipboardManager.setPrimaryClip()`
3. Add logging with text preview (max 50 chars)
4. Guard with focus check (only copy if terminal is active)

## Alternatives Considered

1. **Manual selection only** (current) — Poor UX, breaks workflows
2. **Polling clipboard** — Battery drain, security risk
3. **Custom protocol** — Non-standard, requires server changes

## References

- OSC 52 spec: https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h3-Operating-System-Commands
- Termux implementation: `TermuxTerminalSessionClient.onCopyTextToClipboard()`
- iTerm2 docs: https://iterm2.com/documentation-escape-codes.html
