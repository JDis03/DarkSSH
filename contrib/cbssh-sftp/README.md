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
| Test cbssh SFTP | 1 week | 🔜 Next |
| Implement Progress | 1-2 weeks | ⏸️ Pending |
| Migrate SftpClient | 1 week | ⏸️ Pending |
| Migrate SSH | 1 week | ⏸️ Pending |
| Rollout & Cleanup | 1 week | ⏸️ Pending |
| **Total** | **4-6 weeks** | - |

## Current Status

- ✅ Branch created: `contrib/cbssh-sftp`
- ✅ Directory structure created
- ✅ cbssh upstream cloned
- ✅ **DISCOVERED: cbssh already has SFTP!**
- 🔜 Next: Test cbssh SFTP implementation
- 🔜 Next: Create migration plan

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
