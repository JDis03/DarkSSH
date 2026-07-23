# cbssh Migration Project

**UPDATE**: cbssh ALREADY HAS SFTP! 🎉

This directory contains research and migration work for moving from sshj to cbssh.

## Project Goal

Migrate DarkSSH from sshj (Java) to cbssh (Kotlin) for SFTP operations.

## Directory Structure

```
contrib/cbssh-sftp/
├── README.md                 # This file
├── research/                 # Research and analysis
│   ├── sshj-analysis.md     # Analysis of sshj SFTP implementation
│   ├── cbssh-architecture.md # cbssh architecture study
│   └── protocol-notes.md    # SFTP protocol notes
├── design/                   # API design and proposals
│   ├── api-proposal.md      # cbssh SFTP API design
│   ├── api-examples.kt      # Code examples
│   └── data-classes.kt      # Data class definitions
├── implementation/           # Implementation work
│   ├── packet-types.kt      # SFTP packet types
│   ├── packet-codec.kt      # Packet encoding/decoding
│   ├── sftp-client.kt       # Main SFTP client
│   └── file-operations.kt   # File operations
├── tests/                    # Test code
│   ├── unit/                # Unit tests
│   └── integration/         # Integration tests
└── docs/                     # Documentation
    ├── migration-guide.md   # Migration guide from sshj
    └── usage-examples.md    # Usage examples
```

## Upstream Repositories

- **cbssh fork**: `/home/dark/Project/cbssh-fork/` (JDis03/cbssh)
- **cbssh upstream**: https://github.com/connectbot/cbssh (for syncing)
- **cbssh fork**: (To be created on GitHub)
- **sshj reference**: https://github.com/hierynomus/sshj

## Timeline (REVISED)

| Phase | Duration | Status |
|-------|----------|--------|
| Research | 1 week | ✅ Done (cbssh has SFTP!) |
| Test cbssh SFTP | 1 week | ✅ Done |
| Implement Progress | 1-2 weeks | ✅ Done (`TransferEngine`, adaptive pipelining) |
| Migrate SftpClient | 1 week | ✅ Done (`SftpClient2`) |
| Migrate SSH (terminal) | 1 week | ⏸️ Not started — separate, tracked in `openspec/changes/cbssh-migration-strategy/` |
| Rollout & Cleanup | 1 week | ✅ Done (2026-07-23 — sshj removed entirely, see below) |
| **Total** | **4-6 weeks** | **SFTP side complete** |

## Current Status (2026-07-23)

**SFTP migration is complete and sshj has been fully removed from the app.**
`SftpClient2` (cbssh-backed) is the only SFTP client left — `SftpClient.kt`
(sshj), `SftpClientFactory.kt`, and the `useCbsshSftp` feature flag were all
deleted once real-device testing confirmed functional parity plus real
performance wins (adaptive pipelining + a circuit breaker fix for a
self-congestion bug found during dogfooding: +77% download / +377% upload
throughput on the same test transfer). See
`openspec/changes/archive/2026-07-23-migrate-sftp-to-cbssh/` for the full
executed task list and history.

Terminal SSH (`SSH.kt`) is unaffected by this — it still uses the older
`sshlib` (Java/Trilead), not cbssh. Migrating the terminal is a separate,
not-yet-started effort tracked in `openspec/changes/cbssh-migration-strategy/`.

- ✅ Branch created: `contrib/cbssh-sftp`
- ✅ Directory structure created
- ✅ cbssh upstream cloned
- ✅ **DISCOVERED: cbssh already has SFTP!**
- ✅ SFTP fully migrated, sshj removed

## References

- [cbssh Repository](https://github.com/connectbot/cbssh)
- [sshj Repository](https://github.com/hierynomus/sshj)
- [SFTP Draft Spec](https://datatracker.ietf.org/doc/html/draft-ietf-secsh-filexfer-02)
- [Migration Plan](../../docs/MIGRATION_TO_CBSSH.md)

## Important Discovery

**cbssh already has full SFTP support!**

See `research/cbssh-sftp-status.md` for details.

No need to contribute - we can migrate directly.

## Notes

This is now a **migration project** - moving DarkSSH from sshj to cbssh.

Timeline reduced from 8-11 weeks to 4-6 weeks.
