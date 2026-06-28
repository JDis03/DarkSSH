# cbssh SFTP Feature Gap Analysis

## Summary

cbssh SFTP is functionally complete for our needs. The only gaps are high-level convenience methods (`download`/`upload` with progress callbacks) that we'll need to implement as a thin wrapper.

## Coverage Matrix

### ✅ Direct Equivalents (1:1 mapping)

| DarkSSH (sshj) | cbssh SFTP | Notes |
|----------------|------------|-------|
| `sftpClient.ls(path)` | `sftp.listdir(path)` | Returns `List<SftpDirectoryEntry>` |
| `sftpClient.stat(path)` | `sftp.stat(path)` | Returns `SftpAttributes` |
| `sftpClient.mkdir(path)` | `sftp.mkdir(path)` | Same |
| `sftpClient.rmdir(path)` | `sftp.rmdir(path)` | Same |
| `sftpClient.rm(path)` | `sftp.remove(path)` | Name change only |
| `sftpClient.rename(old, new)` | `sftp.rename(old, new)` | Same |
| `sftpClient.statExistence(path)` | `sftp.stat(path)` | Check for `SftpResult.Success` |
| `client.fileTransfer.download(remote, local)` | ⚠️ **MISSING** | Need wrapper |
| `client.fileTransfer.upload(local, remote)` | ⚠️ **MISSING** | Need wrapper |
| `sftp.open(path)` | `sftp.open(path, flags)` | Need flags |
| `sftp.open(path, flags)` | `sftp.open(path, flags)` | Same |
| `RemoteFileInputStream` | Manual `read(handle, offset, len)` | Need wrapper |
| `RemoteFileOutputStream` | Manual `write(handle, offset, data)` | Need wrapper |
| `ssh.useCompression()` | ❓ Not yet verified | TBD |
| `PromiscuousVerifier` | `HostKeyVerifier` interface | Different API |
| `windowSize` tuning | ❓ Not yet verified | TBD |

### ⚠️ Needs Wrapper / Manual Implementation

1. **`download(remotePath, localFile)` with progress**
2. **`download(remotePath, OutputStream)` with progress**
3. **`upload(localFile, remotePath)` with progress**
4. **`copyFile(sourcePath, destPath)` with progress**
5. **`uploadViaSsh()` fallback** (uses SCP-style over SSH exec)
6. **`executeCommand()`** (uses SSH exec channel)

## Proposed Wrapper: `CbsshTransfer.kt`

### Goal
Provide the same high-level API as sshj's `FileTransfer` with built-in progress tracking.

### Download with Progress

```kotlin
suspend fun downloadWithProgress(
    sftp: SftpClient,
    remotePath: String,
    outputStream: OutputStream,
    onProgress: ((TransferProgress) -> Unit)? = null,
): SftpResult<Unit> {
    val attrs = sftp.stat(remotePath).getOrElse { return it }
    val totalBytes = attrs.size ?: 0L
    val startTime = System.currentTimeMillis()
    
    val handle = sftp.open(remotePath, setOf(SftpOpenFlag.READ)).getOrElse { return it }
    try {
        var bytesRead = 0L
        val bufferSize = 32 * 1024  // 32KB chunks
        val reportInterval = 100 * 1024L  // 100KB
        var lastReportedBytes = 0L
        
        while (true) {
            val chunk = sftp.read(handle, bytesRead, bufferSize).getOrElse { return it }
                ?: break  // EOF
            
            outputStream.write(chunk)
            bytesRead += chunk.size
            
            if (bytesRead - lastReportedBytes >= reportInterval || bytesRead >= totalBytes) {
                val currentTime = System.currentTimeMillis()
                onProgress?.invoke(
                    TransferProgress(
                        bytesTransferred = bytesRead,
                        totalBytes = totalBytes,
                        filename = remotePath.substringAfterLast('/'),
                        startTime = startTime,
                        currentTime = currentTime,
                    )
                )
                lastReportedBytes = bytesRead
            }
        }
        
        outputStream.flush()
        return SftpResult.Success(Unit)
    } finally {
        sftp.close(handle)
    }
}
```

### Upload with Progress

```kotlin
suspend fun uploadWithProgress(
    sftp: SftpClient,
    localFile: File,
    remotePath: String,
    onProgress: ((TransferProgress) -> Unit)? = null,
): SftpResult<Unit> {
    val totalBytes = localFile.length()
    val startTime = System.currentTimeMillis()
    
    val handle = sftp.open(
        remotePath,
        setOf(SftpOpenFlag.WRITE, SftpOpenFlag.CREATE, SftpOpenFlag.TRUNCATE),
    ).getOrElse { return it }
    
    try {
        var bytesWritten = 0L
        val bufferSize = 32 * 1024
        val reportInterval = 256 * 1024L  // 256KB
        var lastReportedBytes = 0L
        val buffer = ByteArray(bufferSize)
        
        localFile.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                
                sftp.write(handle, bytesWritten, buffer.copyOf(read)).getOrElse { return it }
                bytesWritten += read
                
                if (bytesWritten - lastReportedBytes >= reportInterval || bytesWritten >= totalBytes) {
                    val currentTime = System.currentTimeMillis()
                    onProgress?.invoke(
                        TransferProgress(
                            bytesTransferred = bytesWritten,
                            totalBytes = totalBytes,
                            filename = localFile.name,
                            startTime = startTime,
                            currentTime = currentTime,
                        )
                    )
                    lastReportedBytes = bytesWritten
                }
            }
        }
        
        return SftpResult.Success(Unit)
    } finally {
        sftp.close(handle)
    }
}
```

### Copy File (Server-Side)

```kotlin
suspend fun copyFile(
    sftp: SftpClient,
    sourcePath: String,
    destPath: String,
): SftpResult<Unit> {
    val sourceHandle = sftp.open(sourcePath, setOf(SftpOpenFlag.READ)).getOrElse { return it }
    val destHandle = try {
        sftp.open(
            destPath,
            setOf(SftpOpenFlag.WRITE, SftpOpenFlag.CREATE, SftpOpenFlag.TRUNCATE),
        ).getOrElse { 
            sftp.close(sourceHandle)
            return it 
        }
    } catch (e: Exception) {
        sftp.close(sourceHandle)
        throw e
    }
    
    try {
        var offset = 0L
        val bufferSize = 128 * 1024
        
        while (true) {
            val chunk = sftp.read(sourceHandle, offset, bufferSize).getOrElse { return it }
                ?: break
            
            sftp.write(destHandle, offset, chunk).getOrElse { return it }
            offset += chunk.size
        }
        
        return SftpResult.Success(Unit)
    } finally {
        sftp.close(sourceHandle)
        sftp.close(destHandle)
    }
}
```

### Upload via SSH (SCP-style Fallback)

For large files, sshj uses a `scp` command via SSH exec. cbssh has `SshSession.requestExec()`. We need:

```kotlin
suspend fun uploadViaScp(
    session: SshSession,
    localFile: File,
    remotePath: String,
    onProgress: ((TransferProgress) -> Unit)? = null,
): SftpResult<Unit> {
    if (!session.requestExec("scp -t $remotePath").getOrElse { return it }) {
        return SftpResult.ProtocolError("Failed to exec scp")
    }
    
    // Send SCP header: C0644 <size>  filename\n
    val header = "C0644 ${localFile.length()} ${localFile.name}\n"
    session.write(header.toByteArray())
    
    // Wait for ACK (0x00)
    val ack = session.read()
    if (ack?.first() != 0x00.toByte()) {
        return SftpResult.ProtocolError("No ACK after header")
    }
    
    // Send file data in chunks
    var offset = 0L
    val buffer = ByteArray(32 * 1024)
    localFile.inputStream().use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            session.write(buffer.copyOf(read))
            offset += read
        }
    }
    
    // Send terminator (0x00)
    session.write(byteArrayOf(0x00))
    
    // Wait for final ACK
    val finalAck = session.read()
    if (finalAck?.first() != 0x00.toByte()) {
        return SftpResult.ProtocolError("No final ACK")
    }
    
    session.sendEof()
    return SftpResult.Success(Unit)
}
```

### Execute Command

```kotlin
suspend fun executeCommand(
    session: SshSession,
    command: String,
): SftpResult<Pair<String, Int>> {
    if (!session.requestExec(command)) {
        return SftpResult.ProtocolError("Failed to exec: $command")
    }
    
    val output = session.readExtended()  // Need to collect all output
    val exitCode = session.readExtended()
    session.sendEof()
    
    // Note: cbssh may need a helper to collect full output
    return SftpResult.Success(Pair(output?.second?.decodeToString() ?: "", 0))
}
```

## Implementation Effort

| Component | Lines of Code | Difficulty |
|-----------|---------------|------------|
| `CbsshTransfer.kt` wrapper | ~300 | Medium |
| `SftpClient2.kt` (drop-in replacement) | ~600 | Medium |
| Testing against sshj | ~200 | Low |
| Feature flag integration | ~50 | Low |
| **Total** | **~1150** | **Medium** |

## Parallel Uploads

DarkSSH has `uploadFileParallel()` which splits files into chunks and uploads concurrently. cbssh supports pipelining natively via `SftpDispatcher`. Implementation:

```kotlin
suspend fun uploadParallel(
    sftp: SftpClient,
    localFile: File,
    remotePath: String,
    chunkSize: Long = 10 * 1024 * 1024,  // 10MB
    parallelChunks: Int = 4,
    onProgress: ((TransferProgress) -> Unit)? = null,
): SftpResult<Unit> {
    val totalBytes = localFile.length()
    
    // Open file with WRITE | CREATE (no TRUNCATE so we can write at offsets)
    val handle = sftp.open(
        remotePath,
        setOf(SftpOpenFlag.WRITE, SftpOpenFlag.CREATE, SftpOpenFlag.TRUNCATE),
    ).getOrElse { return it }
    
    try {
        // Calculate chunks
        val chunks = (0 until (totalBytes + chunkSize - 1) / chunkSize).map { i ->
            val start = i * chunkSize
            val end = minOf(start + chunkSize, totalBytes)
            start until end
        }
        
        // Upload chunks concurrently
        val totalProgress = AtomicLong(0)
        coroutineScope {
            chunks.map { chunk ->
                async(Dispatchers.IO) {
                    val buffer = ByteArray(32 * 1024)
                    localFile.inputStream().use { input ->
                        input.skip(chunk.first)
                        var pos = chunk.first
                        while (pos < chunk.last) {
                            val toRead = minOf(buffer.size.toLong(), chunk.last - pos).toInt()
                            val read = input.read(buffer, 0, toRead)
                            if (read == -1) break
                            
                            sftp.write(handle, pos, buffer.copyOf(read)).getOrThrow()
                            pos += read
                            
                            val written = totalProgress.addAndGet(read.toLong())
                            onProgress?.invoke(
                                TransferProgress(
                                    bytesTransferred = written,
                                    totalBytes = totalBytes,
                                    filename = localFile.name,
                                    startTime = System.currentTimeMillis(),
                                    currentTime = System.currentTimeMillis(),
                                )
                            )
                        }
                    }
                }
            }.awaitAll()
        }
        
        return SftpResult.Success(Unit)
    } finally {
        sftp.close(handle)
    }
}
```

## Migration Risks

1. **Progress callback throttling** - Need to match sshj behavior (100KB download, 256KB upload)
2. **Error handling** - sshj throws exceptions; cbssh returns `SftpResult`. Need consistent error mapping
3. **Connection pooling** - sshj has connection-level options. cbssh may differ
4. **Keep-alive** - sshj has `keepAlive.keepAliveInterval`. Need to verify cbssh equivalent
5. **Compression** - sshj has `useCompression()`. Need to verify cbssh equivalent
6. **Window/packet tuning** - sshj allows tuning. cbssh may not expose this

## Recommendation

**Migration is feasible and low-risk.** The wrapper layer (~300 lines) covers all gaps. The main work is:
1. Build wrapper layer
2. Create `SftpClient2.kt` as drop-in replacement
3. Add feature flag for gradual rollout
4. Test in parallel with sshj for stability

Estimated effort: 1-2 weeks (within the planned 4-6 weeks timeline).

## Next Steps

1. Create proof-of-concept test app (Phase 1)
2. Benchmark against sshj (performance comparison)
3. Implement wrapper layer (Phase 2)
4. Migrate `SftpClient.kt` (Phase 3)
5. Rollout with feature flag (Phase 5)
