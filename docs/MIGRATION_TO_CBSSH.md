# Migration Plan: SSHJ → cbssh (ConnectBot SSH Kotlin)

## Overview

**Current State**:
- SSH Terminal: `sshlib` (Trilead SSH2 / ConnectBot) - Java
- SFTP Client: `sshj` (Hierynomus SSHJ) - Java
- SFTP Server: `sshd` (Apache SSHD) - Java

**Target State**:
- SSH Terminal: `cbssh` (ConnectBot SSH Kotlin) - Kotlin
- SFTP Client: `cbssh` (when SFTP support is ready) - Kotlin
- SFTP Server: `sshd` (Apache SSHD) - Keep as-is

## Why Migrate?

### Benefits of cbssh

1. **Kotlin-First**
   - Native coroutines support
   - Null safety
   - Better integration with Compose/Android
   - Modern Kotlin idioms

2. **Same Maintainers**
   - ConnectBot team (same as sshlib)
   - Proven track record
   - Active development

3. **Unified Codebase**
   - Single SSH library for both terminal and SFTP
   - Consistent API across features
   - Easier maintenance

4. **Performance**
   - Kotlin coroutines instead of threads
   - Better memory management
   - Async/await patterns

5. **Future-Proof**
   - sshlib is legacy (Java, old patterns)
   - cbssh is the future of ConnectBot
   - Active development and improvements

### Current Limitations

**cbssh Status** (as of 2026):
- ✅ SSH connection and authentication
- ✅ Terminal session (PTY)
- ✅ Port forwarding
- ⚠️ SFTP support: **IN DEVELOPMENT**
- ⚠️ SCP support: **IN DEVELOPMENT**

**Repository**: https://github.com/connectbot/cbssh

## Migration Strategy

### Phase 1: Research & Preparation (1-2 weeks)

**Goals**:
- Monitor cbssh development progress
- Understand API differences
- Create proof-of-concept

**Tasks**:
1. Clone cbssh repository and review code
2. Check SFTP implementation status
3. Review cbssh issues/PRs for SFTP progress
4. Create small test app with cbssh
5. Document API differences vs sshlib/sshj

**Deliverables**:
- `docs/CBSSH_API_COMPARISON.md`
- `docs/CBSSH_SFTP_STATUS.md`
- Proof-of-concept app in `/experiments/cbssh-poc/`

---

### Phase 2: SSH Terminal Migration (2-3 weeks)

**Priority**: HIGH (cbssh is ready for SSH)

**Current Implementation**:
- `app/src/main/java/com/darkssh/client/transport/SSH.kt` (sshlib)
- Uses `com.trilead.ssh2.Connection`
- Blocking I/O with threads

**Target Implementation**:
- Migrate to cbssh
- Use Kotlin coroutines
- Suspend functions for async operations

**Migration Steps**:

1. **Add cbssh Dependency**
   ```toml
   # gradle/libs.versions.toml
   cbssh = "0.x.x"  # Check latest version
   
   [libraries]
   cbssh = { module = "org.connectbot:cbssh", version.ref = "cbssh" }
   ```

2. **Create New SSH Implementation**
   - Create `SSH2.kt` (cbssh-based) alongside `SSH.kt`
   - Implement same `AbsTransport` interface
   - Use coroutines instead of threads

3. **Update TerminalBridge**
   - Support both SSH.kt (legacy) and SSH2.kt (cbssh)
   - Add feature flag to switch between implementations
   - Test both in parallel

4. **Gradual Rollout**
   - Week 1: Internal testing with SSH2.kt
   - Week 2: Beta users with feature flag
   - Week 3: Full rollout, deprecate SSH.kt

5. **Remove Legacy**
   - Delete `SSH.kt` (sshlib)
   - Remove sshlib dependency
   - Rename `SSH2.kt` → `SSH.kt`

**Testing Checklist**:
- [ ] SSH connection (password auth)
- [ ] SSH connection (key auth)
- [ ] Host key verification
- [ ] Terminal I/O (stdin/stdout)
- [ ] PTY resize (SIGWINCH)
- [ ] OS detection
- [ ] Reconnection after disconnect
- [ ] Multiple concurrent sessions
- [ ] Connection timeout handling
- [ ] Cancellation (back button during connect)

**Risks**:
- API differences may require refactoring
- Coroutines integration with existing code
- Performance regression (unlikely but test)

**Rollback Plan**:
- Keep SSH.kt until SSH2.kt is proven stable
- Feature flag allows instant rollback
- No database migrations needed

---

### Phase 3: SFTP Client Migration (4-6 weeks)

**Priority**: MEDIUM (wait for cbssh SFTP support)

**Blockers**:
- ⚠️ cbssh SFTP implementation must be complete
- ⚠️ cbssh SFTP must reach feature parity with sshj

**Current Implementation**:
- `app/src/main/java/com/darkssh/client/transport/SftpClient.kt` (sshj)
- Uses `net.schmizz.sshj.SSHClient`
- Blocking I/O with callbacks

**Target Implementation**:
- Migrate to cbssh SFTP
- Use Kotlin coroutines and Flow
- Suspend functions for file operations

**Prerequisites** (check before starting):
1. cbssh SFTP implementation is merged
2. cbssh SFTP supports:
   - [ ] File listing (ls)
   - [ ] File upload (put)
   - [ ] File download (get)
   - [ ] File delete (rm)
   - [ ] Directory operations (mkdir, rmdir)
   - [ ] File rename (mv)
   - [ ] File attributes (stat, chmod)
   - [ ] Symlink support
   - [ ] Large file transfers (>1GB)
   - [ ] Transfer progress callbacks
   - [ ] Concurrent transfers

**Migration Steps**:

1. **Monitor cbssh Development**
   - Subscribe to cbssh repository notifications
   - Check for SFTP-related PRs/issues
   - Test SFTP implementation when available

2. **Create New SFTP Implementation**
   - Create `SftpClient2.kt` (cbssh-based)
   - Implement same interface as `SftpClient.kt`
   - Use Flow for progress updates

3. **Update SftpViewModel**
   - Support both SftpClient.kt and SftpClient2.kt
   - Add feature flag to switch implementations
   - Test both in parallel

4. **Gradual Rollout**
   - Week 1-2: Internal testing
   - Week 3-4: Beta users
   - Week 5-6: Full rollout

5. **Remove Legacy**
   - Delete `SftpClient.kt` (sshj)
   - Remove sshj dependency
   - Rename `SftpClient2.kt` → `SftpClient.kt`

**Testing Checklist**:
- [ ] Connect to SFTP server
- [ ] List files and directories
- [ ] Navigate directory tree
- [ ] Upload small files (<10MB)
- [ ] Upload large files (>100MB)
- [ ] Download small files
- [ ] Download large files
- [ ] Delete files
- [ ] Rename files
- [ ] Create directories
- [ ] Delete directories
- [ ] File permissions (chmod)
- [ ] Symlink handling
- [ ] Transfer progress updates
- [ ] Cancel transfer mid-operation
- [ ] Multiple concurrent transfers
- [ ] Background uploads (WorkManager)
- [ ] Resume interrupted transfers

**Risks**:
- cbssh SFTP may not be ready for months/years
- Feature gaps compared to sshj
- Performance differences
- Breaking API changes during development

**Rollback Plan**:
- Keep SftpClient.kt until SftpClient2.kt is proven
- Feature flag allows instant rollback
- No data loss (SFTP is stateless)

---

### Phase 4: Optimization & Cleanup (1-2 weeks)

**Goals**:
- Remove all legacy code
- Optimize performance
- Update documentation

**Tasks**:

1. **Remove Legacy Dependencies**
   ```diff
   # gradle/libs.versions.toml
   - sshlib = "2.2.47-SNAPSHOT"
   - sshj = "0.38.0"
   + cbssh = "1.0.0"  # Assuming stable release
   ```

2. **Code Cleanup**
   - Remove `SSH.kt` (sshlib)
   - Remove `SftpClient.kt` (sshj)
   - Remove feature flags
   - Update imports across codebase

3. **Performance Tuning**
   - Profile memory usage
   - Optimize coroutine dispatchers
   - Tune buffer sizes
   - Test with 10+ concurrent connections

4. **Documentation Updates**
   - Update `ARCHITECTURE.md`
   - Update `README.md`
   - Create `docs/CBSSH_MIGRATION_COMPLETE.md`
   - Update code comments

5. **Testing**
   - Full regression test suite
   - Performance benchmarks
   - Memory leak detection
   - Battery usage profiling

---

## Timeline Estimate

| Phase | Duration | Depends On | Status |
|-------|----------|------------|--------|
| Phase 1: Research | 1-2 weeks | - | ⏳ Can start now |
| Phase 2: SSH Terminal | 2-3 weeks | Phase 1 | ⏳ Can start now |
| Phase 3: SFTP Client | 4-6 weeks | cbssh SFTP ready | ⏸️ Blocked |
| Phase 4: Cleanup | 1-2 weeks | Phase 2 & 3 | ⏸️ Blocked |
| **Total** | **8-13 weeks** | cbssh SFTP | - |

**Critical Path**: cbssh SFTP implementation

---

## Decision Points

### When to Start Phase 2 (SSH Terminal)?

**Start when**:
- ✅ cbssh has stable SSH connection support
- ✅ cbssh supports password and key authentication
- ✅ cbssh supports PTY (terminal sessions)
- ✅ Team has bandwidth for migration work

**Current Status**: ✅ **READY** (cbssh SSH is stable)

### When to Start Phase 3 (SFTP Client)?

**Start when**:
- ⚠️ cbssh SFTP implementation is merged to main
- ⚠️ cbssh SFTP has basic file operations (ls, get, put, rm)
- ⚠️ cbssh SFTP has transfer progress support
- ⚠️ cbssh SFTP is documented

**Current Status**: ⏸️ **BLOCKED** (cbssh SFTP in development)

**Monitoring**:
- Check https://github.com/connectbot/cbssh/issues
- Search for "SFTP" in issues/PRs
- Subscribe to repository notifications
- Test development branches periodically

---

## RECOMMENDED: Contribute SFTP to cbssh

**Instead of waiting, we should contribute SFTP implementation to cbssh.**

This is the **preferred approach** because:
1. We control the timeline
2. We ensure our use cases are covered
3. We help the entire ConnectBot ecosystem
4. We learn deeply about SSH/SFTP internals

### Strategy: Port sshj SFTP to cbssh

**Approach**: Use sshj as reference implementation and port to Kotlin/coroutines.

**Why sshj as reference?**
- ✅ Mature, battle-tested SFTP implementation
- ✅ We already use it (know it works)
- ✅ Well-documented code
- ✅ Apache 2.0 license (compatible)
- ✅ Covers all SFTP features we need

**What to port from sshj**:
1. **Core SFTP Protocol** (`net.schmizz.sshj.sftp`)
   - Packet encoding/decoding
   - Request/response handling
   - File attributes parsing
   - Error handling

2. **File Operations** (`SFTPClient.java`)
   - `ls()` - List directory
   - `get()` - Download file
   - `put()` - Upload file
   - `rm()` - Delete file
   - `mkdir()` - Create directory
   - `rmdir()` - Remove directory
   - `rename()` - Rename/move file
   - `stat()` - Get file attributes
   - `chmod()` - Change permissions
   - `readlink()` - Read symlink

3. **Transfer Features**
   - Progress callbacks
   - Large file support (chunked transfer)
   - Concurrent transfers
   - Resume support (if possible)

4. **Advanced Features**
   - Symlink handling
   - File locking
   - Extended attributes
   - Statvfs (filesystem stats)

### Implementation Plan

#### Step 1: Study sshj SFTP (1 week)

**Files to review**:
```
sshj/src/main/java/net/schmizz/sshj/sftp/
├── SFTPClient.java          # Main API
├── SFTPEngine.java          # Protocol engine
├── PacketType.java          # SFTP packet types
├── Request.java             # Request handling
├── Response.java            # Response handling
├── FileAttributes.java      # File metadata
├── FileMode.java            # Permissions
├── RemoteFile.java          # Remote file handle
└── RemoteResourceInfo.java  # Directory entry
```

**Tasks**:
- [ ] Read sshj SFTP code thoroughly
- [ ] Understand packet format
- [ ] Map sshj classes to cbssh architecture
- [ ] Identify Kotlin/coroutine opportunities
- [ ] Document protocol flow

#### Step 2: Design cbssh SFTP API (1 week)

**Goal**: Design idiomatic Kotlin API with coroutines

**Example API Design**:
```kotlin
// cbssh SFTP API (proposed)
interface SftpClient {
    // Connection
    suspend fun connect()
    suspend fun disconnect()
    
    // Directory operations
    suspend fun ls(path: String): List<RemoteFile>
    suspend fun mkdir(path: String, mode: Int = 0755)
    suspend fun rmdir(path: String)
    
    // File operations
    suspend fun get(remotePath: String, localPath: String): Flow<TransferProgress>
    suspend fun put(localPath: String, remotePath: String): Flow<TransferProgress>
    suspend fun rm(path: String)
    suspend fun rename(oldPath: String, newPath: String)
    
    // File attributes
    suspend fun stat(path: String): FileAttributes
    suspend fun chmod(path: String, mode: Int)
    suspend fun readlink(path: String): String
    
    // Advanced
    suspend fun statvfs(path: String): FilesystemStats
}

data class RemoteFile(
    val name: String,
    val path: String,
    val size: Long,
    val mode: Int,
    val mtime: Long,
    val isDirectory: Boolean,
    val isSymlink: Boolean
)

data class TransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val percentage: Float,
    val speed: Long // bytes/sec
)
```

**Design Principles**:
- Suspend functions for async operations
- Flow for progress updates
- Data classes for immutable data
- Null safety (no nullable types unless necessary)
- Extension functions for convenience

**Tasks**:
- [ ] Design API with cbssh maintainers
- [ ] Get feedback on API design
- [ ] Document API in Kotlin docs
- [ ] Create API proposal PR

#### Step 3: Implement Core Protocol (2-3 weeks)

**Port from sshj**:
1. **Packet Types** (`PacketType.java` → `SftpPacketType.kt`)
   ```kotlin
   enum class SftpPacketType(val code: Byte) {
       INIT(1),
       VERSION(2),
       OPEN(3),
       CLOSE(4),
       READ(5),
       WRITE(6),
       LSTAT(7),
       FSTAT(8),
       SETSTAT(9),
       FSETSTAT(10),
       OPENDIR(11),
       READDIR(12),
       REMOVE(13),
       MKDIR(14),
       RMDIR(15),
       REALPATH(16),
       STAT(17),
       RENAME(18),
       READLINK(19),
       SYMLINK(20),
       STATUS(101),
       HANDLE(102),
       DATA(103),
       NAME(104),
       ATTRS(105),
       EXTENDED(200),
       EXTENDED_REPLY(201)
   }
   ```

2. **Packet Encoding/Decoding**
   - Port `SFTPPacket.java` → `SftpPacket.kt`
   - Use Kotlin ByteBuffer extensions
   - Coroutine-safe buffer handling

3. **Request/Response Handling**
   - Port `Request.java` → `SftpRequest.kt`
   - Port `Response.java` → `SftpResponse.kt`
   - Use coroutines for async I/O

4. **File Attributes**
   - Port `FileAttributes.java` → `FileAttributes.kt`
   - Port `FileMode.java` → `FileMode.kt`
   - Use data classes

**Tasks**:
- [ ] Implement packet encoding/decoding
- [ ] Implement request/response handling
- [ ] Implement file attributes parsing
- [ ] Write unit tests for protocol layer

#### Step 4: Implement File Operations (2-3 weeks)

**Port from sshj**:
1. **Directory Listing** (`ls`)
   ```kotlin
   suspend fun ls(path: String): List<RemoteFile> = withContext(Dispatchers.IO) {
       val handle = opendir(path)
       try {
           buildList {
               while (true) {
                   val entries = readdir(handle) ?: break
                   addAll(entries)
               }
           }
       } finally {
           close(handle)
       }
   }
   ```

2. **File Download** (`get`)
   ```kotlin
   suspend fun get(remotePath: String, localPath: String): Flow<TransferProgress> = flow {
       val handle = open(remotePath, OpenMode.READ)
       val attrs = fstat(handle)
       val totalBytes = attrs.size
       var bytesRead = 0L
       
       File(localPath).outputStream().use { output ->
           while (bytesRead < totalBytes) {
               val chunk = read(handle, bytesRead, CHUNK_SIZE)
               output.write(chunk)
               bytesRead += chunk.size
               emit(TransferProgress(bytesRead, totalBytes, bytesRead.toFloat() / totalBytes, calculateSpeed()))
           }
       }
       close(handle)
   }
   ```

3. **File Upload** (`put`)
   ```kotlin
   suspend fun put(localPath: String, remotePath: String): Flow<TransferProgress> = flow {
       val file = File(localPath)
       val totalBytes = file.length()
       var bytesWritten = 0L
       
       val handle = open(remotePath, OpenMode.WRITE or OpenMode.CREATE or OpenMode.TRUNCATE)
       file.inputStream().use { input ->
           val buffer = ByteArray(CHUNK_SIZE)
           while (true) {
               val read = input.read(buffer)
               if (read == -1) break
               write(handle, bytesWritten, buffer, 0, read)
               bytesWritten += read
               emit(TransferProgress(bytesWritten, totalBytes, bytesWritten.toFloat() / totalBytes, calculateSpeed()))
           }
       }
       close(handle)
   }
   ```

4. **Other Operations**
   - `rm()` - Delete file
   - `mkdir()` - Create directory
   - `rmdir()` - Remove directory
   - `rename()` - Rename/move
   - `stat()` - Get attributes
   - `chmod()` - Change permissions

**Tasks**:
- [ ] Implement all file operations
- [ ] Add progress tracking
- [ ] Handle large files (>1GB)
- [ ] Write integration tests

#### Step 5: Testing & Documentation (1-2 weeks)

**Testing**:
1. **Unit Tests**
   - Packet encoding/decoding
   - File attributes parsing
   - Error handling

2. **Integration Tests**
   - Connect to real SFTP server
   - Test all file operations
   - Test large file transfers
   - Test concurrent operations

3. **Performance Tests**
   - Benchmark vs sshj
   - Memory usage profiling
   - Throughput testing

**Documentation**:
1. **API Documentation**
   - KDoc for all public APIs
   - Usage examples
   - Migration guide from sshj

2. **Protocol Documentation**
   - SFTP packet format
   - Request/response flow
   - Error codes

**Tasks**:
- [ ] Write comprehensive tests
- [ ] Write API documentation
- [ ] Create usage examples
- [ ] Performance benchmarks

#### Step 6: Submit PR to cbssh (1 week)

**PR Preparation**:
1. Clean up code
2. Run all tests
3. Format with ktlint
4. Write PR description
5. Add examples to README

**PR Description Template**:
```markdown
# Add SFTP support to cbssh

## Summary
This PR adds full SFTP (SSH File Transfer Protocol) support to cbssh, ported from the mature sshj implementation.

## Features
- ✅ Directory listing (ls)
- ✅ File upload/download (put/get)
- ✅ File operations (rm, mkdir, rmdir, rename)
- ✅ File attributes (stat, chmod)
- ✅ Progress tracking (Flow-based)
- ✅ Large file support (chunked transfer)
- ✅ Symlink handling
- ✅ Coroutine-based async API

## API Example
[code example]

## Testing
- Unit tests: 95% coverage
- Integration tests: All passing
- Performance: On par with sshj

## References
- SFTP Draft: https://datatracker.ietf.org/doc/html/draft-ietf-secsh-filexfer-02
- sshj reference: https://github.com/hierynomus/sshj
```

**Tasks**:
- [ ] Create PR
- [ ] Address review feedback
- [ ] Iterate until merged

### Total Effort Estimate

| Phase | Duration | Deliverable |
|-------|----------|-------------|
| Study sshj | 1 week | Understanding document |
| Design API | 1 week | API proposal PR |
| Core Protocol | 2-3 weeks | Packet handling |
| File Operations | 2-3 weeks | All SFTP operations |
| Testing & Docs | 1-2 weeks | Tests + documentation |
| PR Review | 1 week | Merged PR |
| **Total** | **8-11 weeks** | cbssh SFTP support |

**Team Size**: 1 developer (full-time)

### Benefits of Contributing

1. **Control Timeline**
   - Don't wait for cbssh team
   - Prioritize features we need
   - Fix bugs immediately

2. **Perfect Fit**
   - API designed for our use cases
   - Performance optimized for mobile
   - Features we actually use

3. **Community Impact**
   - Help ConnectBot ecosystem
   - Other SSH apps benefit
   - Open source contribution

4. **Learning**
   - Deep SSH/SFTP knowledge
   - Kotlin coroutines mastery
   - Protocol implementation skills

5. **Reputation**
   - Major contribution to popular library
   - Portfolio piece
   - Community recognition

### Risks & Mitigation

**Risk: PR Not Accepted**
- **Mitigation**: Discuss API design with maintainers first
- **Fallback**: Fork cbssh and maintain our own version

**Risk: Maintenance Burden**
- **Mitigation**: Get it merged upstream, let cbssh team maintain
- **Fallback**: Minimal maintenance if well-tested

**Risk: Time Investment**
- **Mitigation**: 8-11 weeks is acceptable for long-term benefit
- **Fallback**: Keep using sshj if contribution fails

---

## Recommendations (UPDATED)

### RECOMMENDED PATH: Contribute to cbssh

**Instead of waiting, we should actively contribute SFTP to cbssh.**

### Short Term (Next 3 months)

1. **Contact cbssh Maintainers** 📧
   - Open GitHub issue: "Proposal: Add SFTP support"
   - Discuss API design
   - Get buy-in before coding
   - Establish collaboration

2. **Start Phase 1 (Research)** ✅
   - Clone cbssh repository
   - Study sshj SFTP implementation
   - Design cbssh SFTP API
   - Create proof-of-concept

3. **Start Contributing SFTP** 🚀
   - Implement core protocol (2-3 weeks)
   - Implement file operations (2-3 weeks)
   - Write tests and docs (1-2 weeks)
   - Submit PR to cbssh

### Medium Term (3-6 months)

4. **Complete SFTP Contribution** ✅
   - Address PR review feedback
   - Get SFTP merged to cbssh
   - Wait for cbssh release with SFTP

5. **Start Phase 2 (SSH Terminal)** 🚀
   - Migrate SSH.kt to cbssh
   - Run in parallel with sshlib
   - Gradual rollout with feature flag

6. **Start Phase 3 (SFTP Client)** 🎯
   - Migrate SftpClient.kt to cbssh
   - Use our contributed SFTP implementation
   - Full testing and rollout

### Long Term (6-12 months)

7. **Complete Phase 4 (Cleanup)** 🧹
   - Remove sshlib dependency
   - Remove sshj dependency
   - Full Kotlin SSH stack with cbssh
   - Celebrate! 🎉

### Timeline Comparison

**Option A: Wait for cbssh SFTP**
- Timeline: Unknown (could be years)
- Control: None
- Risk: High (may never happen)

**Option B: Contribute SFTP to cbssh** ⭐ RECOMMENDED
- Timeline: 8-11 weeks (predictable)
- Control: Full (we implement it)
- Risk: Low (we control the code)
- Bonus: Help open source community

---

## Success Metrics

### Technical Metrics

- ✅ 100% feature parity with current implementation
- ✅ No performance regression (latency, throughput)
- ✅ Memory usage ≤ current implementation
- ✅ Battery usage ≤ current implementation
- ✅ All tests passing (unit + integration)
- ✅ Zero crashes in production (30 days)

### Code Quality Metrics

- ✅ Kotlin idiomatic code (no Java patterns)
- ✅ Coroutines instead of threads
- ✅ Flow instead of callbacks
- ✅ Null safety (no !! operators)
- ✅ Code coverage ≥ 80%
- ✅ Documentation complete

### User Impact Metrics

- ✅ No user-facing changes (transparent migration)
- ✅ No data loss
- ✅ No connection failures
- ✅ Same or better performance
- ✅ User satisfaction maintained

---

## Risk Mitigation

### Risk: cbssh SFTP Never Completes

**Probability**: Medium  
**Impact**: High

**Mitigation**:
1. Keep sshj as fallback
2. Consider contributing to cbssh
3. Evaluate alternative libraries (if any)
4. Accept hybrid approach (cbssh SSH + sshj SFTP)

### Risk: Performance Regression

**Probability**: Low  
**Impact**: Medium

**Mitigation**:
1. Benchmark before migration
2. Profile during development
3. A/B test with feature flag
4. Optimize hot paths
5. Rollback if needed

### Risk: API Breaking Changes

**Probability**: Medium (cbssh is young)  
**Impact**: Medium

**Mitigation**:
1. Pin cbssh version
2. Test before upgrading
3. Maintain compatibility layer
4. Gradual migration (not big bang)

### Risk: Team Bandwidth

**Probability**: High  
**Impact**: Medium

**Mitigation**:
1. Prioritize Phase 2 (SSH) first
2. Delay Phase 3 (SFTP) until ready
3. Incremental migration (not all at once)
4. Feature flags for easy rollback

---

## References

### cbssh Repository
- **GitHub**: https://github.com/connectbot/cbssh
- **Issues**: https://github.com/connectbot/cbssh/issues
- **PRs**: https://github.com/connectbot/cbssh/pulls

### Current Libraries
- **sshlib**: https://github.com/connectbot/sshlib
- **sshj**: https://github.com/hierynomus/sshj
- **Apache SSHD**: https://github.com/apache/mina-sshd

### SFTP Protocol
- **RFC 4251**: SSH Protocol Architecture
- **RFC 4252**: SSH Authentication Protocol
- **RFC 4253**: SSH Transport Layer Protocol
- **RFC 4254**: SSH Connection Protocol
- **SFTP Draft**: https://datatracker.ietf.org/doc/html/draft-ietf-secsh-filexfer-02

---

## Next Steps (ACTION PLAN)

### Week 1: Contact & Research

1. **Contact cbssh Maintainers**
   - [ ] Open GitHub issue: "Proposal: Add SFTP support to cbssh"
   - [ ] Explain our use case and timeline
   - [ ] Ask for feedback on API design
   - [ ] Establish collaboration channel (Discord/Slack?)

2. **Study sshj SFTP**
   - [ ] Clone sshj repository
   - [ ] Read `SFTPClient.java` and related files
   - [ ] Document packet format and protocol flow
   - [ ] Identify what to port vs what to redesign

3. **Study cbssh Architecture**
   - [ ] Clone cbssh repository
   - [ ] Understand SSH connection handling
   - [ ] Review coroutine patterns used
   - [ ] Identify where SFTP fits in architecture

### Week 2-3: Design & Prototype

4. **Design cbssh SFTP API**
   - [ ] Write API proposal document
   - [ ] Create Kotlin interface definitions
   - [ ] Design data classes
   - [ ] Get feedback from cbssh maintainers

5. **Create Proof-of-Concept**
   - [ ] Implement basic SFTP connection
   - [ ] Implement simple `ls` operation
   - [ ] Test with real SFTP server
   - [ ] Validate API design

### Week 4-6: Core Implementation

6. **Implement SFTP Protocol**
   - [ ] Port packet types from sshj
   - [ ] Implement packet encoding/decoding
   - [ ] Implement request/response handling
   - [ ] Write unit tests

### Week 7-9: File Operations

7. **Implement All SFTP Operations**
   - [ ] Directory listing (`ls`)
   - [ ] File download (`get`) with progress
   - [ ] File upload (`put`) with progress
   - [ ] File operations (`rm`, `mkdir`, `rmdir`, `rename`)
   - [ ] File attributes (`stat`, `chmod`)
   - [ ] Write integration tests

### Week 10-11: Polish & Submit

8. **Testing & Documentation**
   - [ ] Write comprehensive tests
   - [ ] Write API documentation
   - [ ] Create usage examples
   - [ ] Performance benchmarks

9. **Submit PR**
   - [ ] Clean up code
   - [ ] Format with ktlint
   - [ ] Write PR description
   - [ ] Submit to cbssh

### Week 12+: Review & Merge

10. **PR Review Process**
    - [ ] Address review feedback
    - [ ] Make requested changes
    - [ ] Iterate until approved
    - [ ] Celebrate merge! 🎉

### After Merge: Migrate DarkSSH

11. **Wait for cbssh Release**
    - [ ] Wait for cbssh version with SFTP
    - [ ] Test new cbssh version
    - [ ] Update dependencies

12. **Migrate DarkSSH**
    - [ ] Phase 2: Migrate SSH Terminal to cbssh
    - [ ] Phase 3: Migrate SFTP Client to cbssh
    - [ ] Phase 4: Remove legacy dependencies
    - [ ] Full Kotlin SSH stack! 🚀

---

## Conclusion

Migrating to cbssh is a **strategic long-term investment** that will:
- Modernize the codebase (Kotlin, coroutines)
- Unify SSH implementation (one library)
- Future-proof the app (active development)
- Improve maintainability (idiomatic Kotlin)
- Help the open source community

**RECOMMENDED APPROACH**: ⭐

**Don't wait for cbssh SFTP — contribute it ourselves!**

1. **Contact cbssh maintainers** (Week 1)
2. **Study sshj SFTP implementation** (Week 1-2)
3. **Design cbssh SFTP API** (Week 2-3)
4. **Implement SFTP in cbssh** (Week 4-9)
5. **Test, document, submit PR** (Week 10-11)
6. **Address review feedback** (Week 12+)
7. **Migrate DarkSSH to cbssh** (After merge)

**Timeline**: 
- SFTP contribution: 8-11 weeks
- DarkSSH migration: 4-6 weeks
- **Total**: 12-17 weeks (3-4 months)

**Benefits**:
- ✅ Control timeline (no waiting)
- ✅ Perfect API for our needs
- ✅ Help ConnectBot ecosystem
- ✅ Learn SSH/SFTP deeply
- ✅ Portfolio contribution
- ✅ Full Kotlin SSH stack

**Status**: 🚀 Ready to start — contact cbssh maintainers this week!

---

## TL;DR

**Current**: sshlib (SSH) + sshj (SFTP) — all Java, old patterns

**Goal**: cbssh (SSH + SFTP) — all Kotlin, coroutines, modern

**Problem**: cbssh doesn't have SFTP yet

**Solution**: Port sshj SFTP to cbssh ourselves (8-11 weeks)

**Why**: Control timeline, help community, learn deeply, perfect fit

**Next Step**: Open GitHub issue on cbssh proposing SFTP contribution
