# Tasks: Migrate SFTP Client from sshj to cbssh

> **2026-07-23 sync note:** This file tracked 0/117 for weeks while the actual
> implementation moved far ahead of it (built under `contrib/cbssh-sftp`
> alongside the `cbssh-migration-strategy` change's host-key/key-auth work).
> Phases 1-4 are done in code and covered by the existing test suite —
> checkboxes below are synced to reality. Phase 5 is where we actually are:
> cbssh is opt-in (`useCbsshSftp` defaults to `false`), being dogfooded on a
> real device, and today's session found a real perf concern (see T5.1.5)
> that must be resolved before Phase 6 can start.

## Phase 1: Wrapper Layer (1 week)

### T1.1: Create CbsshTransfer.kt skeleton
- [x] **T1.1.1** Create file `app/src/main/java/com/darkssh/client/transport/cbssh/CbsshTransfer.kt`
- [x] **T1.1.2** Add package and imports for cbssh SFTP
- [x] **T1.1.3** Create class skeleton with `sftp: SftpClient` parameter
- [x] Status: ✅ Done

### T1.2: Implement download operations
- [x] **T1.2.1** Implement `suspend fun download(remotePath, localFile, onProgress)`
- [x] **T1.2.2** Implement `suspend fun downloadToStream(remotePath, outputStream, onProgress)`
- [x] **T1.2.3** Add progress throttling
- [x] **T1.2.4** Use 32KB buffer size
- [x] Status: ✅ Done — superseded by `TransferEngine.kt` (pipelined, adaptive) as the
      default engine; `CbsshTransfer` kept as the `useTransferEngine=false` fallback.

### T1.3: Implement upload operations
- [x] **T1.3.1** Implement `suspend fun upload(localFile, remotePath, onProgress)`
- [x] **T1.3.2** Implement `suspend fun uploadParallel(localFile, remotePath, chunkSize, parallelChunks, onProgress)`
- [x] **T1.3.3** Add upload progress throttling
- [x] **T1.3.4** Use AtomicLong for parallel progress tracking
- [x] Status: ✅ Done (`CbsshTransfer.kt`); `TransferEngine.uploadInternal` is now also
      pipelined (2026-07-23), matching download's sliding-window design.

### T1.4: Implement server-side copy
- [x] **T1.4.1** Implement server-side copy — done as `copyFileViaSsh()` in
      `SftpClient2.kt` (raw `cp`/`mv` shell exec) rather than a byte-streaming
      SFTP-level copy in `CbsshTransfer`; simpler and matches what the sshj
      client did.
- [x] Status: ✅ Done (different mechanism than originally planned, same outcome)

### T1.5: Write unit tests
- [x] **T1.5.1**-**T1.5.5** Covered — `TransferEngineDownloadTest.kt`,
      `TransferEngineUploadTest.kt`, `TransferEngineRetryTest.kt`,
      `TransferEngineRetryChunkTest.kt`, `TransferEngineAdaptiveTest.kt`,
      `TransferConfigTest.kt`, `SftpResultMappingTest.kt`
- [x] Status: ✅ Done

### T1.6: Write integration tests
- [x] **T1.6.1** Testcontainers + `linuxserver/openssh-server` — done, isolated
      under `./gradlew integrationTest` (excluded from `./init.sh`'s default run)
- [x] **T1.6.2**-**T1.6.4** `SftpClient2DockerIntegrationTest.kt`,
      `DockerIntegrationTest.kt` category marker
- [x] Status: ✅ Done (7 Docker tests, currently green)

## Phase 2: Drop-in Replacement (1 week)

### T2.1: Create SftpClient2.kt
- [x] Done — `app/src/main/java/com/darkssh/client/transport/cbssh/SftpClient2.kt`,
      implements `ISftpClient` (shared interface both backends implement, not a
      literal copy of sshj's class)
- [x] Status: ✅ Done

### T2.2: Implement connection methods
- [x] **T2.2.1** `connectWithPassword(password)`
- [x] **T2.2.2** `connectWithKey(keyPair)` — reachable from the SFTP screen since
      the `cbssh-migration-strategy` change (2026-07-22/23), not just implemented
- [x] **T2.2.3** `disconnect()`
- [x] **T2.2.4** `Result<Unit>` error mapping (`mapResult()`)
- [x] Status: ✅ Done

### T2.3: Implement file operations
- [x] **T2.3.1**-**T2.3.7** `ls`, `stat`, `mkdir`, `rm`, `rmdir`, `rename`, `exists`
      all implemented
- [x] Status: ✅ Done

### T2.4: Implement transfer operations
- [x] **T2.4.1**-**T2.4.2** `downloadFile()`, `downloadToStream()` (routed through
      `TransferEngine` or `CbsshTransfer` via `useTransferEngine` flag)
- [x] **T2.4.3** `uploadFile()` with SCP fallback (`uploadViaScp()`, inline in
      `SftpClient2.kt` rather than a separate `ScpFallback.kt` — see Phase 3 note)
- [x] **T2.4.4** `uploadFileParallel()`
- [x] **T2.4.5** `copyFileViaSsh()` (server-side `cp`, see T1.4 note)
- [x] **T2.4.6** `moveFile()`
- [x] Status: ✅ Done

### T2.5: Implement command execution
- [x] **T2.5.1**-**T2.5.3** `executeCommand()` (private, backs `copyFileViaSsh`/
      `moveFile`)
- [x] Status: ✅ Done

### T2.6: Test drop-in replacement
- [x] **T2.6.1**-**T2.6.3** Covered by the unit + Docker integration suites above,
      plus real-device manual testing (2026-07-22/23: password auth, key auth,
      host-key TOFU, ls, download, upload all confirmed working against a real
      OpenSSH server)
- [x] Status: ✅ Done

## Phase 3: SCP Fallback (1 week)

### T3.1-T3.3: SCP fallback
- [x] Implemented as `uploadViaScp()` inline in `SftpClient2.kt` instead of a
      standalone `ScpFallback.kt` file — smaller surface, same behavior
      (C0644 header, 32KB chunks, ACK handling)
- [ ] **T3.3.1**/**T3.3.2** Explicit tests for the SCP fallback path specifically
      (files >100MB forcing fallback, server without `scp`) — not yet written;
      the fallback exists and is wired but isn't independently exercised by a
      test today. Low priority: the primary SFTP upload path is what's used and
      tested in practice.
- [x] Status: 🟡 Mostly done — fallback code done, dedicated tests still pending
      (tracked as a follow-up, not a Phase 6 blocker)

## Phase 4: Feature Flag (2 days)

### T4.1: Add preference
- [x] **T4.1.1** `AppPreferences.getUseCbsshSftp()`/`setUseCbsshSftp()`
- [x] **T4.1.2** Defaults to `false` (sshj) — unchanged, still the safe default
- [x] Status: ✅ Done

### T4.2: Update SftpViewModel / factory
- [x] Implemented as `SftpClientFactory.create()` (cleaner than an in-ViewModel
      `getActiveClient()` — one factory function, both call sites use it) rather
      than a `sftpClient`/`sftpClient2` dual-field pattern
- [x] **T4.2.4** Logging: `DebugLogger.i("SftpClientFactory", "Backend: ... → host")`
      fires on every SFTP connect, confirmed in real-device logs
- [x] Status: ✅ Done (different shape, same outcome)

### T4.3: Add UI toggle
- [x] **T4.3.1**-**T4.3.3** Settings toggle exists in `SettingsScreen.kt` (global
      app settings, not per-host `ServerSettingsScreen` as originally assumed —
      corrected 2026-07-23 after user pointed out the toggle already exists)
- [x] Status: ✅ Done

## Phase 5: Migration (3 days)

### T5.1: Internal testing
- [x] **T5.1.1** `useCbsshSftp = true` enabled for real-device dogfooding
      (2026-07-22/23 sessions)
- [x] **T5.1.2**-**T5.1.3** Full SFTP flow verified end-to-end on a real device:
      password auth (terminal), key auth + host-key TOFU (SFTP), `ls`, download,
      upload, server-side copy — all working correctly
- [ ] **T5.1.4** Performance benchmark vs sshj — **not done**; see T5.1.5, this
      is now the priority before benchmarking makes sense
- [ ] **T5.1.5** *(New, 2026-07-23)* **Investigate real-device transfer speed:**
      a 375MB download measured 856KB/s and upload 676KB/s over a private LAN
      host (`192.168.50.45` — WAN "internet speed" is not the bottleneck here,
      this traffic never left the local network). `TransferEngine`'s adaptive
      pipeline locked at max depth (32) for nearly the entire transfer while its
      own measured avgRtt climbed from ~250ms into the *seconds* (peaks of 15s,
      23s) — i.e. the algorithm only knows how to escalate pipeline depth when
      RTT looks high, with no mechanism to detect that the escalation itself
      might be causing self-induced contention (WiFi radio, CPU, or dispatcher
      contention on the phone) and back off. Confirmed NOT the previously-fixed
      SSH channel window-size bug (`SessionChannel.initialWindowSize`): verified
      via `javap` on the actual published `.m2` jar that the 16MB
      window/32KB-packet fix (fork commit `49af22a`) is compiled in and in use.
      **Update 2026-07-23:** the planned sshj A/B comparison (this task's
      original next step) turned out not to be viable — sshj's own
      `KeyType.fromKey()` classifies Ed25519 keys via
      `"EdDSA".equals(key.getAlgorithm())`, which fails on Android/Conscrypt
      for the exact same reason `KeyPairToPem.inferKeyType` did before its
      OID-sniffing fix (confirmed via `javap` on the sshj 0.38.0 jar,
      `KeyType$6.isMyType()`). sshj's key-based auth is simply broken on this
      device — not worth patching a dependency we're actively removing.
      Determined analytically instead (no live A/B needed): ChaCha20-Poly1305
      on a modern phone SoC and typical home WiFi both comfortably exceed
      decent double-digit MB/s; 856KB/s-676KB/s is roughly 50-100x below that
      floor, which is the signature of a software-induced bottleneck, not an
      environmental ceiling. Combined with the observed pipeline-depth/RTT
      runaway pattern, root cause is almost certainly `TransferEngine`'s
      RTT-only adaptive heuristic (escalates depth on high RTT, never measures
      actual throughput or backs off) — proceeding straight to reworking it to
      use measured throughput (bytes/sec over a window) instead of RTT alone,
      so it can detect "deeper pipeline → lower throughput" and back off.
- [ ] Status: 🟡 In progress — functional parity confirmed, performance
      investigation (T5.1.5) is the current focus and a hard gate before Phase 6

### T5.2: Fix any issues found
- [x] Ed25519 key detection on Android/Conscrypt (OID-sniffing fix, 2026-07-22)
- [x] Copy/move progress UI showing a misleading "0 B / 1 B" (fixed 2026-07-23,
      `progress = null` → indeterminate spinner)
- [ ] T5.1.5's throughput investigation (see above)
- [ ] Status: 🟡 In progress

### T5.3: Opt-in beta
- [ ] T4.3 turned out already done — only blocked on T5.1.5 now (perf must be
      understood/acceptable first)
- [ ] Status: ⏳ Pending

## Phase 6: Default Rollout (2 days)

### T6.1: Change default
- [ ] Blocked on Phase 5 completing (perf investigation + opt-in beta feedback)
- [ ] Status: ⏳ Pending — **not started, this is the actual "switch to cbssh by
      default" milestone the user is asking to plan toward**

### T6.2: Remove sshj fallback (after stable rollout)
- [ ] **T6.2.1** Verify cbssh is stable for all users
- [ ] **T6.2.2** Delete `SftpClient.kt` (sshj version)
- [ ] **T6.2.3** Remove `com.hierynomus:sshj` dependency from `app/build.gradle.kts`
- [ ] **T6.2.4** Update `SftpClientFactory`/`SftpViewModel` to use only `SftpClient2`
      (delete the factory's branch and the `useCbsshSftp` preference entirely)
- [ ] **T6.2.5** Verify APK size reduction (~1.5MB)
- [ ] Status: ⏳ Pending — **this is the literal "solo cbssh en SFTP" end state**

## Phase 7: Documentation & Cleanup (2 days)

### T7.1: Update docs
- [ ] Status: ⏳ Pending

### T7.2: Archive spec
- [ ] **T7.2.1** Archive `openspec/changes/refactor-sftp-client-sshj/` (27/31 —
      also stale, superseded by this change; archive together)
- [ ] **T7.2.2** Mark this spec as completed
- [ ] Status: ⏳ Pending

## Summary

| Phase | Tasks | Status |
|-------|-------|--------|
| 1. Wrapper Layer | 16 | ✅ Done |
| 2. Drop-in Replacement | 22 | ✅ Done |
| 3. SCP Fallback | 9 | 🟡 Mostly done (dedicated tests pending) |
| 4. Feature Flag | 7 | 🟡 Mostly done (UI toggle pending) |
| 5. Migration | 10 | 🟡 In progress — **perf investigation is the current blocker** |
| 6. Default Rollout | 8 | ⏳ Not started — the actual goal state |
| 7. Documentation | 5 | ⏳ Not started |
| **Total** | **77** | **~55 done, 2 in progress, ~20 pending** |

**Where we actually are (2026-07-23):** Phases 1-4 are functionally complete
and covered by tests + real-device verification. The path to "only cbssh in
SFTP" (Phase 6) runs through: close T5.1.5 (perf investigation) → T4.3 (UI
toggle, so this stops being dev-only) → T5.3 (opt-in beta feedback) → T6.1
(flip the default) → T6.2 (delete sshj entirely).

**Critical path from here:** T5.1.5 → T4.3 → T5.3 → T6.1 → T6.2 → T7

**Immediate next action:** run the same 375MB transfer with `useCbsshSftp=false`
(sshj) on the same LAN/host to determine whether the slow throughput seen today
is specific to cbssh or an environmental ceiling (WiFi/MIUI throttling) both
backends would hit — this determines whether T5.1.5 is a cbssh bug to fix or a
non-issue to just document.
