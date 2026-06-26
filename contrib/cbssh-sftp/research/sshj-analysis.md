# sshj SFTP Implementation Analysis

## Overview

This document analyzes the sshj SFTP implementation to understand what needs to be ported to cbssh.

## Repository

- **URL**: https://github.com/hierynomus/sshj
- **License**: Apache 2.0 (compatible with cbssh)
- **Language**: Java
- **SFTP Package**: `net.schmizz.sshj.sftp`

## Key Classes to Port

### 1. SFTPClient.java

**Location**: `src/main/java/net/schmizz/sshj/sftp/SFTPClient.java`

**Purpose**: Main SFTP client API

**Key Methods**:
```java
// Directory operations
List<RemoteResourceInfo> ls(String path)
void mkdir(String path)
void rmdir(String path)

// File operations
void get(String source, String dest)
void put(String source, String dest)
void rm(String path)
void rename(String oldPath, String newPath)

// File attributes
FileAttributes stat(String path)
void chmod(String path, int perms)
String readlink(String path)

// Advanced
StatVFS statVFS(String path)
```

**cbssh Port**:
```kotlin
interface SftpClient {
    suspend fun ls(path: String): List<RemoteFile>
    suspend fun mkdir(path: String, mode: Int = 0755)
    suspend fun rmdir(path: String)
    
    suspend fun get(remotePath: String, localPath: String): Flow<TransferProgress>
    suspend fun put(localPath: String, remotePath: String): Flow<TransferProgress>
    suspend fun rm(path: String)
    suspend fun rename(oldPath: String, newPath: String)
    
    suspend fun stat(path: String): FileAttributes
    suspend fun chmod(path: String, mode: Int)
    suspend fun readlink(path: String): String
    
    suspend fun statVfs(path: String): FilesystemStats
}
```

### 2. SFTPEngine.java

**Location**: `src/main/java/net/schmizz/sshj/sftp/SFTPEngine.java`

**Purpose**: SFTP protocol engine (packet handling)

**Key Responsibilities**:
- Send/receive SFTP packets
- Request ID management
- Response handling
- Error handling

**cbssh Port**:
```kotlin
internal class SftpEngine(
    private val connection: SshConnection
) {
    private val requestId = AtomicInteger(0)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<SftpResponse>>()
    
    suspend fun sendRequest(request: SftpRequest): SftpResponse
    suspend fun init(version: Int = 3)
    suspend fun close()
}
```

### 3. PacketType.java

**Location**: `src/main/java/net/schmizz/sshj/sftp/PacketType.java`

**Purpose**: SFTP packet type definitions

**Packet Types**:
```java
INIT(1), VERSION(2), OPEN(3), CLOSE(4), READ(5), WRITE(6),
LSTAT(7), FSTAT(8), SETSTAT(9), FSETSTAT(10), OPENDIR(11),
READDIR(12), REMOVE(13), MKDIR(14), RMDIR(15), REALPATH(16),
STAT(17), RENAME(18), READLINK(19), SYMLINK(20),
STATUS(101), HANDLE(102), DATA(103), NAME(104), ATTRS(105),
EXTENDED(200), EXTENDED_REPLY(201)
```

**cbssh Port**:
```kotlin
enum class SftpPacketType(val code: Byte) {
    INIT(1), VERSION(2), OPEN(3), CLOSE(4), READ(5), WRITE(6),
    LSTAT(7), FSTAT(8), SETSTAT(9), FSETSTAT(10), OPENDIR(11),
    READDIR(12), REMOVE(13), MKDIR(14), RMDIR(15), REALPATH(16),
    STAT(17), RENAME(18), READLINK(19), SYMLINK(20),
    STATUS(101), HANDLE(102), DATA(103), NAME(104), ATTRS(105),
    EXTENDED(200), EXTENDED_REPLY(201);
    
    companion object {
        fun fromCode(code: Byte): SftpPacketType? = values().find { it.code == code }
    }
}
```

### 4. FileAttributes.java

**Location**: `src/main/java/net/schmizz/sshj/sftp/FileAttributes.java`

**Purpose**: File metadata (size, permissions, timestamps)

**cbssh Port**:
```kotlin
data class FileAttributes(
    val size: Long = 0,
    val uid: Int = 0,
    val gid: Int = 0,
    val mode: FileMode = FileMode(0),
    val atime: Long = 0,
    val mtime: Long = 0,
    val extended: Map<String, String> = emptyMap()
) {
    val isDirectory: Boolean get() = mode.isDirectory
    val isRegularFile: Boolean get() = mode.isRegularFile
    val isSymlink: Boolean get() = mode.isSymlink
}

data class FileMode(val mask: Int) {
    val isDirectory: Boolean get() = (mask and 0x4000) != 0
    val isRegularFile: Boolean get() = (mask and 0x8000) != 0
    val isSymlink: Boolean get() = (mask and 0xA000) != 0
    
    val permissions: Int get() = mask and 0x1FF
}
```

### 5. RemoteResourceInfo.java

**Location**: `src/main/java/net/schmizz/sshj/sftp/RemoteResourceInfo.java`

**Purpose**: Directory entry information

**cbssh Port**:
```kotlin
data class RemoteFile(
    val name: String,
    val path: String,
    val attributes: FileAttributes
) {
    val size: Long get() = attributes.size
    val mtime: Long get() = attributes.mtime
    val isDirectory: Boolean get() = attributes.isDirectory
    val isRegularFile: Boolean get() = attributes.isRegularFile
    val isSymlink: Boolean get() = attributes.isSymlink
}
```

### 6. SFTPPacket.java

**Location**: `src/main/java/net/schmizz/sshj/sftp/SFTPPacket.java`

**Purpose**: SFTP packet encoding/decoding

**Key Methods**:
```java
void putString(String str)
void putUInt32(long uint32)
void putUInt64(long uint64)
void putFileAttributes(FileAttributes attrs)

String readString()
long readUInt32()
long readUInt64()
FileAttributes readFileAttributes()
```

**cbssh Port**:
```kotlin
class SftpPacket(private val buffer: ByteBuffer) {
    fun putString(str: String) {
        val bytes = str.toByteArray(Charsets.UTF_8)
        putUInt32(bytes.size.toLong())
        buffer.put(bytes)
    }
    
    fun putUInt32(value: Long) {
        buffer.putInt(value.toInt())
    }
    
    fun putUInt64(value: Long) {
        buffer.putLong(value)
    }
    
    fun readString(): String {
        val length = readUInt32().toInt()
        val bytes = ByteArray(length)
        buffer.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }
    
    fun readUInt32(): Long = buffer.getInt().toLong() and 0xFFFFFFFFL
    fun readUInt64(): Long = buffer.getLong()
}
```

## Protocol Flow

### Connection Initialization

```
Client                          Server
  |                               |
  |--- SSH_FXP_INIT (version=3) ->|
  |                               |
  |<-- SSH_FXP_VERSION (version=3)|
  |                               |
```

### Directory Listing (ls)

```
Client                          Server
  |                               |
  |--- SSH_FXP_OPENDIR (path) --->|
  |                               |
  |<-- SSH_FXP_HANDLE (handle) ---|
  |                               |
  |--- SSH_FXP_READDIR (handle) ->|
  |                               |
  |<-- SSH_FXP_NAME (entries) ----|
  |                               |
  |--- SSH_FXP_READDIR (handle) ->|
  |                               |
  |<-- SSH_FXP_STATUS (EOF) ------|
  |                               |
  |--- SSH_FXP_CLOSE (handle) --->|
  |                               |
  |<-- SSH_FXP_STATUS (OK) -------|
  |                               |
```

### File Download (get)

```
Client                          Server
  |                               |
  |--- SSH_FXP_OPEN (path, READ)->|
  |                               |
  |<-- SSH_FXP_HANDLE (handle) ---|
  |                               |
  |--- SSH_FXP_READ (handle, 0) ->|
  |                               |
  |<-- SSH_FXP_DATA (chunk) ------|
  |                               |
  |--- SSH_FXP_READ (handle, N) ->|
  |                               |
  |<-- SSH_FXP_DATA (chunk) ------|
  |                               |
  |--- SSH_FXP_READ (handle, M) ->|
  |                               |
  |<-- SSH_FXP_STATUS (EOF) ------|
  |                               |
  |--- SSH_FXP_CLOSE (handle) --->|
  |                               |
  |<-- SSH_FXP_STATUS (OK) -------|
  |                               |
```

### File Upload (put)

```
Client                          Server
  |                               |
  |--- SSH_FXP_OPEN (path, WRITE)->|
  |                               |
  |<-- SSH_FXP_HANDLE (handle) ---|
  |                               |
  |--- SSH_FXP_WRITE (handle, 0, data)->|
  |                               |
  |<-- SSH_FXP_STATUS (OK) -------|
  |                               |
  |--- SSH_FXP_WRITE (handle, N, data)->|
  |                               |
  |<-- SSH_FXP_STATUS (OK) -------|
  |                               |
  |--- SSH_FXP_CLOSE (handle) --->|
  |                               |
  |<-- SSH_FXP_STATUS (OK) -------|
  |                               |
```

## Key Differences: sshj vs cbssh

| Aspect | sshj (Java) | cbssh (Kotlin) |
|--------|-------------|----------------|
| Async | Callbacks | Coroutines (suspend) |
| Progress | Listeners | Flow |
| Errors | Exceptions | Result/sealed classes |
| Nullability | @Nullable | Nullable types (?) |
| Collections | Java Collections | Kotlin Collections |
| I/O | Blocking | Non-blocking (suspend) |

## Porting Strategy

### Phase 1: Core Protocol
1. Port `PacketType` → `SftpPacketType` (enum)
2. Port `SFTPPacket` → `SftpPacket` (encoding/decoding)
3. Port `FileAttributes` → `FileAttributes` (data class)
4. Port `FileMode` → `FileMode` (data class)

### Phase 2: Engine
1. Port `SFTPEngine` → `SftpEngine` (coroutines)
2. Implement request/response handling
3. Implement error handling

### Phase 3: Client API
1. Port `SFTPClient` → `SftpClient` (interface)
2. Implement directory operations
3. Implement file operations
4. Add Flow-based progress tracking

### Phase 4: Advanced Features
1. Implement symlink handling
2. Implement extended attributes
3. Implement statvfs
4. Optimize performance

## Testing Strategy

### Unit Tests
- Packet encoding/decoding
- File attributes parsing
- Error handling

### Integration Tests
- Connect to real SFTP server (OpenSSH)
- Test all file operations
- Test large file transfers (>1GB)
- Test concurrent operations

### Performance Tests
- Benchmark vs sshj
- Memory usage profiling
- Throughput testing

## Next Steps

1. **Study cbssh architecture** - Understand how SSH connection works
2. **Design API** - Create Kotlin API proposal
3. **Contact maintainers** - Get feedback on design
4. **Start implementation** - Begin with core protocol

## References

- [sshj GitHub](https://github.com/hierynomus/sshj)
- [SFTP Draft v3](https://datatracker.ietf.org/doc/html/draft-ietf-secsh-filexfer-02)
- [SSH RFCs](https://www.ietf.org/rfc/rfc4251.txt)
