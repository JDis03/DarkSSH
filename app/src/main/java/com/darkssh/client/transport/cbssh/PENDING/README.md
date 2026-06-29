# cbssh Migration - Pending Implementation

## Status: Phase 1 Code Complete, Awaiting Dependency

This directory contains the code for the cbssh SFTP migration. The files
here have a `.pending` extension so they don't break the build until
cbssh is added as a dependency.

## Files (pending activation)

- `CbsshTransfer.kt.pending` - High-level transfer wrapper (download/upload/copy)
- `TransferProgress.kt.pending` - Progress data class

## Why Pending?

cbssh (ConnectBot SSH) brings in heavy dependencies:
- Tink (Google's crypto library)
- Kyber (post-quantum cryptography)
- ktor-network
- kstatemachine
- jbcrypt

Adding cbssh as a dependency increases the build time and APK size
significantly. We want to keep these files ready but not active until
we're ready to test.

## How to Activate

When ready to add cbssh as dependency:

### Option 1: Add to Maven Local (recommended)

```bash
# In cbssh-fork directory
./gradlew publishToMavenLocal
```

Then in `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation("org.connectbot:cbssh:0.1.0-SNAPSHOT")
    // ...
}
```

### Option 2: Use as Gradle Submodule

In `settings.gradle.kts`:
```kotlin
include(":app", ":cbssh-sshlib")

project(":cbssh-sshlib").projectDir = file("/home/dark/Project/cbssh-fork/sshlib")
```

In `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":cbssh-sshlib"))
    // ...
}
```

### Activate the files

```bash
cd app/src/main/java/com/darkssh/client/transport/cbssh/
mv PENDING/CbsshTransfer.kt.pending ./CbsshTransfer.kt
mv PENDING/TransferProgress.kt.pending ./TransferProgress.kt
```

## Timeline

| Phase | Status |
|-------|--------|
| Phase 1: CbsshTransfer wrapper | ✅ Code written (pending activation) |
| Phase 2: SftpClient2 drop-in | ⏳ Next |
| Phase 3: SCP fallback | ⏳ Pending |
| Phase 4: Feature flag | ⏳ Pending |
| Phase 5: Migration | ⏳ Pending |
| Phase 6: Default rollout | ⏳ Pending |
| Phase 7: Cleanup | ⏳ Pending |

## References

- **Spec:** `openspec/changes/migrate-sftp-to-cbssh/`
- **Design:** `openspec/changes/migrate-sftp-to-cbssh/design.md`
- **Tasks:** `openspec/changes/migrate-sftp-to-cbssh/tasks.md`
- **cbssh source:** `/home/dark/Project/cbssh-fork/sshlib/`
- **cbssh fork repo:** https://github.com/JDis03/cbssh