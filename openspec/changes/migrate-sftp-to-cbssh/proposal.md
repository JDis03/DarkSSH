# Proposal: Migrate SFTP Client from sshj to cbssh

## What

Replace sshj (Java) SFTP client with cbssh (Kotlin/coroutines) while maintaining the same public API through a drop-in replacement.

## Why

**Problem:**
- Current SFTP client uses sshj 0.38.0 (Java library from 2023)
- DarkSSH already uses ConnectBot's sshlib for terminal SSH - we have TWO SSH libraries
- sshj is in maintenance mode - limited new features
- Inconsistent async patterns (callbacks vs Kotlin coroutines)
- Large dependency footprint (~1.5MB APK contribution)

**Impact of keeping sshj:**
- Stuck on legacy Java patterns
- Two SSH stacks to maintain (sshlib for terminal, sshj for SFTP)
- Missing modern Kotlin features (suspend functions, null safety, Result types)
- Higher memory usage (thread-based vs coroutine-based)
- No integration with same maintainers' roadmap

## Discovery: cbssh Already Has SFTP!

**IMPORTANT**: When we started planning, we thought cbssh lacked SFTP. **It already has full SFTP support!**

cbssh SFTP features:
- ✅ Complete SFTP v3 protocol implementation
- ✅ Kotlin coroutines (all suspend functions)
- ✅ SftpResult type (no exceptions for error handling)
- ✅ Request pipelining (concurrent operations)
- ✅ Integration tests with testcontainers + OpenSSH
- ✅ Same maintainers as sshlib (ConnectBot team)

See `contrib/cbssh-sftp/research/cbssh-sftp-status.md` for details.

## Goals

1. ✅ **Replace sshj with cbssh** - Use ConnectBot's modern Kotlin SSH library
2. ✅ **Maintain public API** - Same method signatures via wrapper layer
3. ✅ **Drop-in replacement** - `SftpClient2.kt` implements same interface
4. ✅ **Gradual rollout** - Feature flag for parallel running
5. ✅ **Better progress tracking** - Consistent callbacks with throttling
6. ✅ **Remove sshj dependency** - Reduce APK size by ~1.5MB

## Non-Goals

- Migrating terminal SSH from sshlib to cbssh (out of scope, separate project)
- Changing SFTP protocol version
- Adding new SFTP features beyond current functionality
- Replacing Apache SSHD for SFTP server (kept as-is)

## Success Criteria

1. ✅ All SFTP operations work identically to sshj implementation
2. ✅ No regressions in download/upload/copy/mkdir/rm/rename
3. ✅ Progress callbacks fire at same intervals (100KB download, 256KB upload)
4. ✅ Both password and pubkey authentication work
5. ✅ Feature flag allows toggling between sshj and cbssh
6. ✅ APK size reduced by ~1.5MB after sshj removal
7. ✅ Same SFTP error semantics (Result<Unit> for failures)

## Technical Approach

### Phase 1: Wrapper Layer
Create `CbsshTransfer.kt` providing high-level download/upload/copy with progress tracking.

### Phase 2: Drop-in Replacement
Create `SftpClient2.kt` implementing the same public API as current `SftpClient.kt`.

### Phase 3: Feature Flag
Add `useCbsshSftp` preference in `AppPreferences` for gradual rollout.

### Phase 4: Migration
Update `SftpViewModel.kt` to use `SftpClient2` when flag enabled.

### Phase 5: SCP Fallback
Implement SCP-style upload using SSH exec for large files (replaces sshj's SCP fallback).

### Phase 6: Rollout & Cleanup
- Enable for internal testing
- Monitor stability
- Enable for all users
- Remove sshj dependency
- Delete `SftpClient.kt` (sshj version)

## Timeline

| Phase | Duration | Status |
|-------|----------|--------|
| 1. CbsshTransfer wrapper | 1 week | ⏳ Pending |
| 2. SftpClient2 drop-in | 1 week | ⏳ Pending |
| 3. Feature flag | 2 days | ⏳ Pending |
| 4. Migration | 3 days | ⏳ Pending |
| 5. SCP fallback | 1 week | ⏳ Pending |
| 6. Rollout & cleanup | 1 week | ⏳ Pending |
| **Total** | **4-6 weeks** | - |

## Alternatives Considered

### Alternative 1: Keep sshj
**Rejected** - Stuck on legacy Java patterns, two SSH stacks, larger APK.

### Alternative 2: Migrate to Apache SSHD Client
**Rejected** - Lower-level API, would require manual progress tracking like sshlib.

### Alternative 3: Wait for cbssh to "stabilize"
**Rejected** - cbssh SFTP is already production-ready with full test coverage.

## Dependencies

### Removed (after rollout)
- `com.hierynomus:sshj:0.38.0` (~1.5MB)

### Added
- `org.connectbot:cbssh:0.1.0-SNAPSHOT` (TBD exact version)

### Changed
- None (Apache SSHD for SFTP server unchanged)

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| API differences break behavior | Medium | High | Wrapper layer + parallel testing |
| Progress callback throttling differs | Low | Medium | Match sshj intervals exactly |
| Error type mapping inconsistent | Medium | Medium | Centralized error mapping |
| Performance regression | Low | Medium | Benchmark before/after |
| Large file handling | Low | High | Test with >1GB files |
| Concurrent transfers | Medium | Medium | Test parallel uploads |

## References

- **cbssh repository:** https://github.com/connectbot/cbssh
- **cbssh SFTP source:** `contrib/cbssh-upstream/sshlib/src/main/kotlin/org/connectbot/sshlib/client/sftp/`
- **Feature gap analysis:** `contrib/cbssh-sftp/research/feature-gap-analysis.md`
- **Transfer wrapper design:** `contrib/cbssh-sftp/design/cbssh-transfer-design.md`
- **Migration plan:** `docs/MIGRATION_TO_CBSSH.md`
- **Current SFTP client:** `app/src/main/java/com/darkssh/client/transport/SftpClient.kt`
- **Previous spec:** `openspec/changes/refactor-sftp-client-sshj/` (obsoleted by this proposal)
