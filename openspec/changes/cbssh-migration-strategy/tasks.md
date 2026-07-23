## 1. Research & Decision (already completed while authoring this change)

- [x] 1.1 Read cbssh source (`SshClient.kt`, `SshClientConfig.kt`, `HostKeyVerifier.kt`,
      `AuthHandler.kt`, `AuthMethod.kt`, `SessionChannel.kt`) and `SSH.kt` to build the
      feature-parity matrix
- [x] 1.2 Record the cbssh-vs-sshlib feature-parity matrix in `design.md` (Decision 1)
- [x] 1.3 Record the migration-sequencing decision (SFTP-cleanup-first) with rationale
      in `design.md` (Decision 2)

## 2. SFTP host key verification

- [x] 2.1 Create `DarkSshHostKeyVerifier` implementing cbssh's
      `org.connectbot.sshlib.HostKeyVerifier`, backed by `KnownHostRepository`
      (mirrors `SSH.kt`'s inner `HostKeyVerifier` TOFU logic, per design Decision 3)
- [x] 2.2 Extract shared fingerprint-formatting helper (MD5 + SHA256) usable by both
      `SSH.kt` and the new SFTP verifier, to avoid copy-pasted logic drifting
      (`util/SshFingerprint.kt`; `SSH.kt` refactored to delegate to it, behavior
      unchanged)
- [x] 2.3 Add SFTP-side host-key-confirmation UI state to `SftpViewModel`
      (`HostKeyPrompt` + `CompletableDeferred`-based `promptForUnknownHostKey`/
      `respondToHostKeyPrompt`) and a Compose `AlertDialog` in `SftpScreen.kt` to
      collect it
- [x] 2.4 Wire `DarkSshHostKeyVerifier` into `SftpClient2.connectWithPassword` and
      `connectWithKey` (via `buildHostKeyVerifier()`), replacing the hardcoded
      `HostKeyVerifier { true }`. Fails closed (rejects) if no `KnownHostRepository`
      was supplied, instead of falling back to always-trust.
- [x] 2.5 Unit test `DarkSshHostKeyVerifier`: known-match accepts silently, known-mismatch
      rejects without prompting, unknown key prompts and persists on accept, unknown key
      does not persist on reject (`DarkSshHostKeyVerifierTest.kt`, 4 tests, mocked
      `KnownHostRepository`, no Docker required)
- [ ] 2.6 Docker integration test: SFTP connection to a host whose key was previously
      accepted and stored succeeds without prompting — **follow-up**, not yet written;
      requires driving `SftpClient2` itself (not just cbssh's `SshClient`) against the
      test container with a real `KnownHostRepository`/Room instance
- [ ] 2.7 Docker integration test: SFTP connection with a mismatched stored key is
      rejected — **follow-up**, same blocker as 2.6

## 3. SFTP key-based authentication wiring

- [x] 3.1 In `SftpViewModel`'s connect path, branch on `host.pubkeyId`: resolve the
      `Pubkey`, unlock it (prompting for its passphrase if encrypted, reusing
      `PubkeyUtils.convertToKeyPair`) and call `SftpClient2.connectWithKey(keyPair)`
      instead of always calling `connectWithPassword` (`connectUsingHostKey`,
      `resolveKeyPairForHost`; also wired into `tryReconnectIfNeeded` and
      `dismissAuthError` so reconnects and retries use the key too, matching
      `SSH.kt`'s "never fall back to password for a key-assigned host" behavior)
- [x] 3.2 Add an SFTP-side passphrase-prompt mechanism for encrypted keys
      (`KeyPassphrasePrompt` + `promptForKeyPassphrase`/`respondToKeyPassphrase`,
      same `CompletableDeferred` pattern as 2.3; unlocked keys cached in
      `SftpViewModel.loadedKeypairs` so reconnects don't re-prompt, mirroring
      `TerminalService.loadedKeypairs`)
- [ ] 3.3 Docker integration test: SFTP connection using a host-assigned key succeeds —
      **follow-up**, same blocker as 2.6 (needs `SftpClient2` + real key material
      driven against the test container)
- [ ] 3.4 Docker integration test: SFTP connection using a wrong/mismatched key is
      rejected — **follow-up**, same blocker as 2.6
- [x] 3.5 Manual verification: connect to a real host with `pubkeyId` set from the SFTP
      screen and confirm no password prompt appears — verified on a real Android device
      (Xiaomi 25069PTEBG, Android 16/API 36) against a real SSH server: log shows
      `Key 'dark' unlocked successfully` → host key trusted silently (previously
      accepted) → `Conectado (pubkey)` → `Key auth succeeded`, no password prompt at
      any point. Required two follow-up fixes first (Ed25519 key detection on Conscrypt,
      see `KeyPairToPem.kt`) — both confirmed working in the same successful log.

## 4. Cleanup

- [x] 4.1 Remove `app/src/main/java/com/darkssh/client/transport/cbssh/PENDING/`
      (stale pre-dependency-activation leftover; cbssh has been a live dependency for
      multiple sessions)
- [ ] 4.2 Sync `openspec/changes/migrate-sftp-to-cbssh/tasks.md` checkboxes to reflect
      real completion state (`SftpClient2.kt`, `CbsshTransfer.kt`, `TransferEngine.kt`,
      Docker integration tests already exist but are tracked as 0/117) — tracked here
      as a pointer task; the actual sync happens in that change, not this one

## 5. Verification

- [x] 5.1 `./init.sh` green (90 unit tests — 86 pre-existing + 4 new
      `DarkSshHostKeyVerifierTest` — zero Docker required)
- [x] 5.2 `./gradlew integrationTest` green — the 7 pre-existing Docker tests still pass
      unchanged (no regression from the host-key/key-auth wiring). Does **not** yet
      include new Docker tests for the host-key/key-auth scenarios — see 2.6/2.7/3.3/3.4
- [x] 5.3 `assembleDebug` builds successfully
- [x] 5.4 `openspec validate cbssh-migration-strategy` passes
