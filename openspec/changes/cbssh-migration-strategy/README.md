# cbssh-migration-strategy

Analyze cbssh auth/session parity vs sshlib, decide migration order (finish SFTP-to-cbssh first vs migrate terminal sshlib-to-cbssh first), and wire the two known SFTP auth gaps (host key verification, key-based auth)
