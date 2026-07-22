## Why

DarkSSH currently runs two independent SSH stacks: the classic Trilead-based `sshlib`
for the terminal (`SSH.kt`) — with real TOFU host-key verification against
`KnownHostRepository`, keyboard-interactive support, and per-host key auth — and the
newer Kotlin/coroutine `cbssh` library for SFTP (`SftpClient2.kt`), which was left with
a hardcoded "always trust" host-key verifier and unreachable (dead) key-based-auth
wiring when it was built as a drop-in sshj replacement. Before finishing the sshj
removal and before deciding whether cbssh can also eventually replace sshlib in the
terminal, we need to: (a) verify with evidence — not guesswork — that cbssh's
auth/session primitives are a real superset of what sshlib provides, (b) close the two
known SFTP auth gaps against DarkSSH's actual trust model, and (c) record which
migration order carries the least risk: finishing the SFTP-side sshj removal first, or
starting the terminal's sshlib→cbssh migration first.

## What Changes

- **Research (this change's core deliverable):** a comparative feature-parity matrix
  between cbssh (`SshClient` / `HostKeyVerifier` / `AuthHandler` / `SessionChannel`) and
  sshlib (`Connection` / `ServerHostKeyVerifier` / `InteractiveCallback` / `Session`),
  covering host-key verification, auth methods (`none`, `publickey`, keyboard-interactive,
  password), PTY/shell/resize, compression, keepalive, and disconnect detection —
  documented in `design.md`.
- **Decision (ADR):** record the recommended migration sequencing (finish sshj removal
  from SFTP first vs. start the sshlib→cbssh terminal migration first) with rationale,
  in `design.md`. This change does **not** start the terminal migration itself — it only
  produces the recommendation that a future change will act on.
- **Implement — SFTP host key verification:** replace `SftpClient2`'s hardcoded
  `HostKeyVerifier { true }` with a real adapter backed by `KnownHostRepository`
  (prompt-on-first-use, persist per host+algorithm, reject on mismatch), matching
  `SSH.kt`'s existing terminal behavior.
- **Implement — SFTP key-based auth wiring:** make `SftpClient2.connectWithKey` (already
  implemented, currently unreachable) actually get called from the SFTP connection path
  when a `Host` has `pubkeyId` assigned, matching the terminal's host-specific-key
  behavior.
- **Cleanup:** remove the stale `transport/cbssh/PENDING/` leftover directory from an
  earlier pre-dependency migration stage (cbssh has been an active build dependency for
  several sessions; the `.pending`-file activation instructions no longer apply).
- **Out of scope for this change:** actually deleting `SftpClient.kt` (sshj) and the
  `sshj` Gradle dependency — tracked separately in `openspec/changes/migrate-sftp-to-cbssh/`;
  and actually writing the terminal's sshlib→cbssh migration code — that becomes its own
  future change, informed by this change's recommendation.

## Capabilities

### New Capabilities
- `sftp-authentication`: SFTP connections (`SftpClient2`/cbssh) verify the server's host
  key against the same per-host trust store the terminal uses (prompt-on-first-use,
  persist, reject on mismatch instead of always succeeding) and support authenticating
  with a host's assigned SSH key end-to-end from the SFTP screen, reaching parity with
  the terminal's `SSH.kt` auth behavior.

### Modified Capabilities
<!-- openspec/specs/ has no existing capability specs yet in this project — nothing to modify. -->

## Impact

- **Affected code:**
  - `app/src/main/java/com/darkssh/client/transport/cbssh/SftpClient2.kt` — replace the
    inline `HostKeyVerifier { true }` in both `connectWithPassword` and `connectWithKey`
    with a real verifier; ensure `connectWithKey` is exercised end-to-end.
  - New adapter class (name TBD in design, e.g. `DarkSshHostKeyVerifier`) bridging
    cbssh's `org.connectbot.sshlib.HostKeyVerifier` interface to
    `KnownHostRepository`, mirroring `SSH.kt`'s inner `HostKeyVerifier` class.
  - `app/src/main/java/com/darkssh/client/ui/screens/viewmodel/SftpViewModel.kt` —
    invoke `connectWithKey` when the target `Host.pubkeyId` is set, instead of always
    calling `connectWithPassword`.
  - SFTP screen gains a host-key-confirmation prompt path (the SFTP screen has no
    `TerminalBridge`, so it needs its own dialog/callback mechanism — design TBD).
- **Affected docs:** `openspec/changes/migrate-sftp-to-cbssh/` (referenced for context,
  not modified by this change); `app/src/main/java/com/darkssh/client/transport/cbssh/PENDING/`
  (removed as cleanup).
- **Dependencies:** none added or removed — cbssh and sshlib are both already present.
- **Compatibility:** `ISftpClient`'s public method signatures are unchanged
  (`connectWithKey` already exists in the interface); the SFTP screen gains an additive
  host-key-confirmation UI step that does not exist today (today it silently trusts any
  host key).
