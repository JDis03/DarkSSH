## ADDED Requirements

### Requirement: SFTP host key verification
The SFTP client (`SftpClient2`, cbssh-backed) SHALL verify the server's host key
against the same per-host known-hosts trust store (`KnownHostRepository`) the terminal
(`SSH.kt`) uses, instead of unconditionally trusting every host key.

#### Scenario: Known host key matches
- **WHEN** the SFTP client connects to a host whose current host key algorithm+key
  already exists in `KnownHostRepository` for that `Host.id`
- **THEN** the connection proceeds without prompting the user

#### Scenario: Known host key does not match (potential MITM)
- **WHEN** the SFTP client connects to a host and `KnownHostRepository` has a stored key
  for that `Host.id`+algorithm that does NOT match the key presented by the server
- **THEN** the connection is rejected and no prompt is shown (fails closed, matching
  `SSH.kt`'s existing terminal behavior for a key mismatch)

#### Scenario: Unknown host key (first connection)
- **WHEN** the SFTP client connects to a host with no stored key for that `Host.id`+
  algorithm in `KnownHostRepository`
- **THEN** the user is prompted with the key's fingerprints (MD5 and SHA256, same
  format as the terminal's prompt) and the connection proceeds only if the user accepts

#### Scenario: User accepts an unknown host key
- **WHEN** the user accepts an unknown host key fingerprint prompt during an SFTP
  connection
- **THEN** the accepted key is persisted to `KnownHostRepository` for that `Host.id`+
  algorithm so subsequent SFTP connections to the same host do not prompt again

#### Scenario: User rejects an unknown host key
- **WHEN** the user rejects an unknown host key fingerprint prompt during an SFTP
  connection
- **THEN** the SFTP connection attempt fails and no key is persisted

### Requirement: SFTP key-based authentication reachability
The SFTP connection path SHALL authenticate using a host's assigned SSH key
(`Host.pubkeyId`) when one is configured, instead of always prompting for a password.

#### Scenario: Host has an assigned key
- **WHEN** the user connects to the SFTP screen for a `Host` with `pubkeyId` set to an
  existing, resolvable `Pubkey`
- **THEN** the SFTP client authenticates using that key via `connectWithKey`, without
  prompting for a password

#### Scenario: Assigned key is encrypted
- **WHEN** the user connects to the SFTP screen for a `Host` whose assigned `Pubkey` is
  passphrase-encrypted and not already unlocked in memory
- **THEN** the user is prompted for the key's passphrase before `connectWithKey` is
  attempted, mirroring the terminal's key-unlock prompt behavior

#### Scenario: Host has no assigned key
- **WHEN** the user connects to the SFTP screen for a `Host` with `pubkeyId == null`
- **THEN** the SFTP client authenticates using `connectWithPassword` as it does today
