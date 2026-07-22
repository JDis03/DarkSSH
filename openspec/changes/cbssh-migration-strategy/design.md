## Context

DarkSSH runs two SSH stacks side by side:

- **`sshlib`** (`org.connectbot:sshlib` 2.2.47-SNAPSHOT, Trilead-derived, blocking/thread
  API) — used exclusively by the terminal (`app/src/main/java/com/darkssh/client/transport/SSH.kt`).
  It drives interactive PTY sessions, real TOFU host-key verification against
  `KnownHostRepository`, `keyboard-interactive`/`password`/`publickey` auth with a
  host-specific-key override (`Host.pubkeyId`), compression, and a keepalive thread.
- **`cbssh`** (`org.connectbot.sshlib:sshlib` 0.3.2-SNAPSHOT, ConnectBot's newer
  Kotlin-coroutine library, source at `/home/dark/Project/cbssh-fork/`) — used only by
  `SftpClient2.kt` as the sshj replacement for SFTP. It was built as a narrow drop-in
  (implements `ISftpClient`), so it only exercises cbssh's SFTP subsystem path and
  leaves host-key verification hardcoded to `true`.
- **`sshj`** (`com.hierynomus:sshj` 0.38.0) — the legacy SFTP client (`SftpClient.kt`)
  that cbssh is replacing. Still present; its removal is tracked in
  `openspec/changes/migrate-sftp-to-cbssh/` and is **not** re-litigated here.

This change exists to answer two questions with evidence from the actual cbssh source
(read directly from `/home/dark/Project/cbssh-fork/sshlib/src/main/kotlin/org/connectbot/sshlib/`,
not from its README or the sshj-migration proposal, which understated cbssh's maturity):

1. Does cbssh actually have everything sshlib provides for the **terminal**, or would a
   terminal migration hit a capability gap?
2. Given the answer to (1), in what order should DarkSSH tackle "finish removing sshj
   from SFTP" vs. "migrate the terminal from sshlib to cbssh"?

And then to close the two concrete SFTP auth gaps that exist *today* regardless of
which order is chosen (they block SFTP from ever reaching parity with the terminal on
either path).

## Goals / Non-Goals

**Goals:**
- Produce a evidence-based feature-parity matrix between cbssh and sshlib for
  everything the terminal (`SSH.kt`) currently relies on.
- Record an explicit, rationale-backed recommendation for migration sequencing.
- Fix `SftpClient2`'s host-key verification (currently `HostKeyVerifier { true }`) to
  use the same TOFU trust store (`KnownHostRepository`) as the terminal.
- Make `SftpClient2.connectWithKey` reachable from the SFTP connection path for hosts
  with `Host.pubkeyId` set (it is fully implemented today but never called).
- Remove the stale `transport/cbssh/PENDING/` directory (leftover from before cbssh was
  an active Gradle dependency).

**Non-Goals:**
- Actually deleting `SftpClient.kt` / the `sshj` dependency (tracked in
  `migrate-sftp-to-cbssh`).
- Writing the terminal's sshlib→cbssh migration code. This change only produces the
  recommendation; the migration itself is future work, sized and sequenced separately
  once this recommendation is accepted.
- Adding SFTP features cbssh doesn't need for parity (port forwarding, agent
  forwarding, SOCKS5) — noted as available but out of scope.

## Decisions

### Decision 1: Feature-parity matrix (cbssh vs. sshlib), by terminal capability

Read directly from cbssh source: `SshClient.kt`, `SshClientConfig.kt`,
`HostKeyVerifier.kt`, `AuthHandler.kt`, `AuthMethod.kt`, `SessionChannel.kt`. Compared
against sshlib usage in `SSH.kt`.

| Terminal capability (used in `SSH.kt`) | sshlib API | cbssh equivalent | Verdict |
|---|---|---|---|
| Host key verification (TOFU, per-host+algo, prompt+persist) | `ServerHostKeyVerifier.verifyServerHostKey()` → `KnownHostRepository` | `HostKeyVerifier.verify(key): Boolean` (suspend) + optional `addKeys()`/`removeKeys()` hooks | **Superset.** cbssh's interface is `suspend`, so a `DarkSshHostKeyVerifier` can call the DB and show a UI prompt directly — no `runBlocking(Dispatchers.IO)` workaround needed like `SSH.kt` requires today. cbssh ships a reference `KnownHostsVerifier` (plain `known_hosts` file, no TOFU-prompt flow) but the interface is designed for a custom implementation like ours. |
| `password` auth | `conn.authenticateWithPassword()` | `client.authenticatePassword()` | Equal |
| `publickey` auth, multiple loaded keys | `conn.authenticateWithPublicKey(username, KeyPair)` loop | `client.authenticatePublicKey(username, privateKeyData: String/ByteArray, passphrase)` — takes PEM/raw key material, not a `java.security.KeyPair` | **API shape differs.** `SftpClient2` already bridges this via `KeyPairToPem.toPem()`. A terminal migration would need the same adapter, or use the lower-level `AuthHandler` (`onPublicKeysNeeded`/`onSignatureRequest`) to sign with an already-loaded `KeyPair` directly, avoiding the extra PEM round-trip. |
| `keyboard-interactive` auth | `conn.authenticateWithKeyboardInteractive(username, InteractiveCallback)` | `client.authenticateKeyboardInteractive(username, KeyboardInteractiveCallback)` | Equal (callback-based, same shape) |
| `none` auth probe | `conn.authenticateWithNone()` | Handled internally by cbssh's `AuthHandler`-driven flow (`none → publickey probe → sign → keyboard-interactive → password`, per RFC 4252) | Equal or better — cbssh can drive the entire RFC 4252 fallback chain in one `authenticate(username, handler)` call instead of `SSH.kt`'s manual `AUTH_TRIES` loop. |
| Host-specific key override (`Host.pubkeyId`, skip other keys) | Manual `if (hostPubkeyId != null) { ... } else { tryPublicKeyAuth() }` in `SSH.kt` | Same pattern implementable: call `authenticatePublicKey()` with only that key's material, or restrict `AuthHandler.onPublicKeysNeeded()` to one key | Equal (app-level logic, not a library gap) |
| Interactive PTY session (`requestPTY`, `startShell`) | `conn.openSession()` → `sess.requestPTY()` → `sess.startShell()` | `client.openSession()` → `session.requestPty()` → `session.requestShell()` | Equal |
| Terminal resize | `sess.resizePTY(cols, rows, w, h)` | `session.resizeTerminal(widthChars, heightRows, widthPixels, heightPixels)` (sends `window-change`) | Equal |
| stdin/stdout/stderr streams | `sess.stdin/stdout/stderr` (blocking `OutputStream`/`InputStream`) | `session.write(ByteArray)` (suspend) / `session.stdout`, `session.stderr` (`ReceiveChannel<ByteArray>`) | **API shape differs** (coroutine channels vs. blocking streams) but strictly more capable — no dedicated reader thread needed. |
| Compression toggle (`Host.compression`) | `conn.setCompression(true)` | `SshClientConfig { enableCompression = true }` | Equal |
| Keepalive (`SSH_MSG_IGNORE` every 30s) | Manual `Thread` + `conn.sendIgnorePacket()` in `SSH.kt` | Built into `SshClientConfig.keepAliveIntervalMs` — no manual thread needed | cbssh is simpler/safer here (already used this way in `SftpClient2`). |
| Disconnect detection | `ConnectionMonitor.connectionLost(Throwable?)` callback | `SshClient.disconnectedFlow: SharedFlow<Throwable?>` | Equal (flow is more idiomatic Kotlin) |
| Agent forwarding, local/remote/dynamic port forwarding, SOCKS5, jump-host chaining | Not used by DarkSSH today | Present in cbssh (`AgentChannel`, `LocalPortForwarder`, `RemotePortForwarder`, `DynamicPortForwarder`, `openDirectTcpipTransport`) | Bonus capability, not required for parity, noted for future DarkSSH features. |

**Conclusion:** cbssh is a **strict superset** of what `SSH.kt` uses from sshlib today.
There is no capability sshlib has that cbssh lacks. The only real work in a terminal
migration would be adapting two API-shape differences (blocking streams → suspend/
channel-based I/O; `KeyPair` → PEM material for pubkey auth) — both mechanical, both
already solved once in `SftpClient2`/`KeyPairToPem.kt`.

### Decision 2: Migration order — finish SFTP-to-cbssh (remove sshj) first

**Chosen: finish the SFTP-side sshj removal (`migrate-sftp-to-cbssh`) before starting
any terminal sshlib→cbssh migration.**

Rationale:
- **Blast radius.** The terminal is DarkSSH's primary, highest-traffic feature — every
  session opens one. SFTP is a secondary screen. A regression from a library swap is
  far cheaper to absorb on SFTP (users can retry a transfer) than on the terminal
  (users can lose an active interactive session, mid-command).
- **Momentum and unknowns already retired.** The SFTP migration is materially further
  along: `SftpClient2.kt`, `CbsshTransfer.kt`, `TransferEngine.kt` exist and 7 Docker
  integration tests (`SftpClient2DockerIntegrationTest.kt`) already pass against a real
  OpenSSH server. Finishing it (feature-flag rollout, delete `SftpClient.kt`, drop
  `sshj`) is bounded, well-scoped work with most unknowns already resolved.
  A terminal migration is a fresh, larger effort (interactive I/O adapter, PTY resize
  timing, `InteractiveCallback`/keyboard-interactive UX, reconnect/keepalive parity —
  see `progress.md` history of terminal reconnect bugs) that has not started.
- **This change's own fixes reduce future terminal-migration risk.** Building the
  `DarkSshHostKeyVerifier` adapter and validating `connectWithKey` wiring against a real
  cbssh connection *now*, on the lower-stakes SFTP path, directly de-risks reusing that
  exact adapter for the terminal later — we get to prove the host-key-verification
  adapter works against cbssh in production before it ever touches the terminal.
- **Avoids running three SSH stacks at once.** Starting the terminal migration before
  finishing SFTP would mean sshj + sshlib + cbssh all live simultaneously during the
  overlap window. Sequencing keeps DarkSSH down to two stacks at any given time
  (sshlib + cbssh today → cbssh-only once both migrations land).

Alternative considered — terminal first: **Rejected.** Would front-load the riskiest,
least-proven migration (interactive PTY correctness under coroutines) before the team
has any production experience running cbssh outside of SFTP, and delays finishing a
nearly-complete SFTP migration for no correctness benefit — the matrix in Decision 1
shows no blocking capability gap that would force reordering.

### Decision 3: SFTP host key verification — `DarkSshHostKeyVerifier` adapter

Introduce `app/src/main/java/com/darkssh/client/transport/cbssh/DarkSshHostKeyVerifier.kt`
implementing cbssh's `org.connectbot.sshlib.HostKeyVerifier`:

```kotlin
class DarkSshHostKeyVerifier(
    private val host: Host,
    private val knownHostRepository: KnownHostRepository,
    private val onUnknownKey: suspend (algo: String, fingerprints: String) -> Boolean,
) : HostKeyVerifier {
    override suspend fun verify(key: PublicKey): Boolean {
        val existing = knownHostRepository.getByHostIdAndAlgo(host.id, key.type)
        if (existing.isEmpty()) {
            val accepted = onUnknownKey(key.type, buildFingerprints(key.type, key.encoded))
            if (accepted) knownHostRepository.insert(KnownHost(...))
            return accepted
        }
        return existing.any { it.hostKey == Base64.encodeToString(key.encoded, NO_WRAP) }
    }
}
```

This mirrors `SSH.kt`'s inner `HostKeyVerifier` class exactly (same TOFU semantics,
same `KnownHostRepository` schema, same fingerprint format), but is `suspend`-native —
no `runBlocking` bridge needed, since cbssh's interface is already a coroutine.

**Open sub-problem:** the SFTP screen has no `TerminalBridge` (the terminal's existing
`promptForHostKeyVerificationBlocking` lives on `TerminalBridge`, which SFTP doesn't
have). `onUnknownKey` needs an SFTP-side equivalent — likely a `SftpViewModel` state
flow the SFTP screen collects to show a Compose confirmation dialog, analogous to how
the terminal's `ConsoleScreen` shows its host-key dialog today. This UI wiring is left
for the tasks phase to size in detail.

### Decision 4: SFTP key-based auth wiring

`SftpClient2.connectWithKey(keyPair: KeyPair)` is fully implemented (PEM conversion +
`authenticatePublicKey`) but has zero callers. Fix: in `SftpViewModel`'s connect path,
mirror `SSH.kt`'s `tryHostPubkeyAuth` logic — if `host.pubkeyId != null`, resolve/unlock
the matching `Pubkey` (prompting for its passphrase via a new SFTP-side prompt if
encrypted, same `PubkeyUtils.convertToKeyPair` call the terminal uses) and call
`connectWithKey(keyPair)` instead of always calling `connectWithPassword(password)`.

## Risks / Trade-offs

- **[Risk] Two prompt mechanisms (TerminalBridge vs. SFTP) drift in UX/copy over time.**
  → Mitigation: extract the fingerprint-formatting and host-key-dialog copy into a
  shared util so both surfaces render identically; only the trigger/plumbing differs.
- **[Risk] `connectWithKey` has not been exercised against a real server since it was
  written (no test coverage today).** → Mitigation: add a Docker integration test
  (extending `SftpClient2DockerIntegrationTest.kt`) for key-based auth success and
  wrong-key rejection before wiring it into the UI.
- **[Risk] Decision 2's "SFTP first" ordering could be second-guessed mid-flight if the
  terminal migration turns out to be trivial.** → Mitigation: this design's matrix
  (Decision 1) is the artifact to revisit — if a future session finds a capability gap
  invalidating it, that's a reason to formally reopen this decision, not silently
  reorder.
- **[Trade-off] Building `DarkSshHostKeyVerifier` now (SFTP-scoped) instead of a
  library-agnostic verifier usable by both stacks immediately.** Accepted: sshlib's
  `ServerHostKeyVerifier` and cbssh's `HostKeyVerifier` have different interface shapes
  (blocking vs. suspend); a truly shared implementation isn't possible without changing
  `SSH.kt` too, which is out of scope here. The cbssh version is written so its
  core TOFU logic (not the interface glue) can be lifted into the terminal migration
  later.

## Migration Plan

1. Implement `DarkSshHostKeyVerifier` + SFTP host-key-confirmation UI, wire into
   `SftpClient2` (replacing the `{ true }` stub) — verify against Docker integration
   tests (accept known key, reject mismatched key, prompt+persist unknown key).
2. Wire `connectWithKey` into `SftpViewModel`'s connection path for hosts with
   `pubkeyId` set — verify with a new Docker integration test (key auth success, wrong
   key rejected).
3. Remove `transport/cbssh/PENDING/`.
4. Update `openspec/changes/migrate-sftp-to-cbssh/tasks.md` checkboxes to reflect real
   completion state (currently stale at 0/117 despite substantial completed work) —
   tracked as a follow-up, not blocking this change.
5. No rollback concerns: both fixes are additive/corrective on the already-experimental
   `SftpClient2` path, which sits behind the existing `useTransferEngine`/cbssh SFTP
   code path already in production use for SFTP only.

## Open Questions

- Exact SFTP-side host-key-confirmation dialog mechanism (new `SftpViewModel` state +
  Compose dialog vs. reusing a shared component) — resolve during `tasks.md` sizing.
- Whether encrypted-key passphrase prompting for SFTP should reuse
  `bridge.promptForInputBlocking`'s pattern or needs a net-new SFTP prompt primitive
  (SFTP has no `TerminalBridge`) — resolve during `tasks.md` sizing.
- Timing of the actual terminal sshlib→cbssh migration (separate future change) is
  intentionally left unscheduled pending this change's fixes landing and the SFTP
  sshj-removal change completing, per Decision 2.
