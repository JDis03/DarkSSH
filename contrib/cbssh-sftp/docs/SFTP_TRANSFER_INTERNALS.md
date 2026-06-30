# SFTP Transfer Internals: sshj vs cbssh

## Por qué existe este documento

Durante la migración de sshj→cbssh descubrimos que los downloads eran
10-100× más lentos. Este doc explica por qué, cómo lo resolvimos, y qué
queda pendiente como contribución al upstream cbssh.

---

## Cómo funciona sshj (el baseline correcto)

### Arquitectura de capas

```
App (DarkSSH)
  └── SFTPFileTransfer.download()
        └── SFTPFileTransfer$Downloader.downloadFile()
              └── RemoteFile.ReadAheadRemoteFileInputStream   ← clave
                    └── RemoteFile.asyncRead()               ← SFTP READ async
                          └── SFTPEngine (request/response)
                                └── SSH channel (window: 32MB, packet: 128KB)
```

### ReadAheadRemoteFileInputStream

La clase clave de sshj para downloads rápidos:

```java
class ReadAheadRemoteFileInputStream extends InputStream {
    private final int maxUnconfirmedReads;     // N reads en vuelo simultáneos
    private final Deque<UnconfirmedRead> unconfirmedReads; // cola de promises
    private long currentOffset;
    private int maxReadLength;                 // tamaño de cada chunk

    // Algoritmo: sliding window
    // 1. Pre-envía N SSH_FXP_READ requests simultáneos (asyncRead)
    // 2. Mientras consume respuestas, repone el pipeline
    // 3. Cada request es una Promise que se resuelve cuando llega SSH_FXP_DATA
}
```

### Parámetros de sshj en DarkSSH (SftpClient.kt)

```kotlin
ssh.connection.windowSize = 32 * 1024 * 1024  // 32MB SSH channel window
ssh.connection.maxPacketSize = 128 * 1024       // 128KB max SSH packet
// maxUnconfirmedReads = default (alto, Integer.MAX_VALUE en algunos paths)
// readLength per request = negotiated (típicamente 32KB ó 64KB)
```

### Por qué sshj es rápido

**SSH channel window (32MB):** El servidor puede enviar hasta 32MB de datos
sin esperar confirmación del cliente. Sin esto, con un window de 64KB:
- Servidor envía 64KB → para → espera SSH_MSG_CHANNEL_WINDOW_ADJUST
- Cliente envía WINDOW_ADJUST → servidor envía otros 64KB → para...
- 1 round-trip (~1-10ms en LAN) por cada 64KB = 640KB/s en LAN a 10ms latencia

**Read-ahead pipeline (N requests simultáneos):**
- En lugar de: send READ → wait → send READ → wait → ...
- sshj hace: send READ₁, READ₂, ..., READₙ → collect responses in order
- El servidor puede procesar múltiples reads y enviar datos sin pausas
- N × readLength bytes siempre en vuelo

**Combinación SSH window + pipeline:**
```
Cliente:  [READ@0] [READ@32K] [READ@64K] [READ@96K] →→→
Servidor: ←←←[DATA@0=32K] [DATA@32K=32K] [DATA@64K=32K] [DATA@96K=32K]
          (todos caben en el window de 32MB sin esperar WINDOW_ADJUST)
```

---

## Por qué cbssh era lento (el problema)

### cbssh defaults (antes de nuestro fix)

```kotlin
// SshClient.openSftp() llamaba:
conn.openSessionChannel(
    initialWindowSize = 64 * 1024,   // 64KB ← PROBLEMA PRINCIPAL
    maxPacketSize     = 32 * 1024,   // 32KB
)
```

Con `initialWindowSize = 64KB`:
```
Cliente:  [READ@0] →
Servidor: ←[DATA@0=32K] ←[DATA@32K=32K]   (window: 64KB - 64KB = 0)
Servidor: PARA... espera WINDOW_ADJUST
Cliente:  [WINDOW_ADJUST +64KB] →
Servidor: ←[DATA@64K=32K] ←[DATA@96K=32K] (window: 64KB - 64KB = 0)
Servidor: PARA...
```
= 1 round-trip por cada 64KB = **~160-640 KB/s en WiFi local**

### Intento 1: Pipeline de 8 reads × 128KB (fallido)

```kotlin
// CbsshTransfer: DOWNLOAD_CHUNK_SIZE = 128KB, PIPELINE_DEPTH = 8
// cbssh-fork: maxPacketSize = 256KB (error)
```

**Error:** `ChannelClosedException: SSH channel closed before complete SFTP packet`

**Causa:** El servidor responde a un SSH_FXP_READ de 128KB con un paquete
SSH_FXP_DATA de 128KB. Ese paquete SFTP llega dividido en múltiples
SSH_MSG_CHANNEL_DATA de 32KB cada uno (el `maxPacketSize` real del canal).
`SftpPacketIO.readExact()` tiene que ensamblarlos. Cuando el canal tiene
problemas de window (64KB window + 8×128KB in-flight = overflow), el
servidor cierra el canal con SSH_MSG_CHANNEL_CLOSE.

### Intento 2: Aumentar maxPacketSize a 256KB (fallido)

```kotlin
conn.openSessionChannel(
    initialWindowSize = 16 * 1024 * 1024,  // 16MB ← bien
    maxPacketSize     = 256 * 1024,        // ← también rompe el canal
)
```

**Error:** El servidor intenta enviar DATA packets de hasta 256KB, que el
`SessionChannel` interno de cbssh no maneja bien → canal cerrado.

---

## La solución correcta (fix actual)

```kotlin
// cbssh-fork/SshClient.kt (en openSftp):
conn.openSessionChannel(
    initialWindowSize = 16 * 1024 * 1024,  // 16MB — elimina window stalls
    maxPacketSize     = 32 * 1024,          // 32KB — compatible con servidor
)

// CbsshTransfer:
DOWNLOAD_CHUNK_SIZE    = 32 * 1024  // 32KB per SFTP READ request
DOWNLOAD_PIPELINE_DEPTH = 8         // 8 requests simultáneos = 256KB in-flight
```

**Por qué funciona:**
- `initialWindowSize = 16MB`: servidor puede enviar 16MB sin esperar
- `maxPacketSize = 32KB`: servidor divide respuestas en chunks de 32KB que
  el canal maneja correctamente
- Cada `SSH_FXP_READ` de 32KB → servidor responde con 1 `SSH_MSG_CHANNEL_DATA`
  de 32KB → `SftpPacketIO` lo lee en una sola llamada sin ensamblar
- 8 reads in-flight × 32KB = 256KB siempre circulando

**Velocidad esperada:** 5-20 MB/s en LAN (vs <0.2 MB/s antes)

---

## Comparación de estrategias

| | sshj | cbssh (antes) | cbssh (ahora) |
|---|---|---|---|
| SSH window | 32MB | **64KB** | 16MB |
| maxPacketSize | 128KB | 32KB | 32KB |
| Chunk SFTP READ | ~32-64KB | 32KB | 32KB |
| Requests in-flight | N (alto) | 1 (secuencial) | **8** |
| Bytes in-flight | ~1MB+ | 32KB | 256KB |
| Velocidad LAN ~10ms | 10-50 MB/s | 0.15 MB/s | ~5-20 MB/s |

---

## Qué falta para igualar sshj

### Gap 1: N dinámico de requests (no hardcoded 8)

sshj calcula el número óptimo de requests en vuelo según el tamaño del
archivo y el ancho de banda negociado. Nosotros tenemos 8 fijo.

### Gap 2: Chunk size dinámico

sshj negocia el `readLength` óptimo con el servidor (hasta 32KB típicamente,
el SFTP spec no garantiza más). Nosotros asumimos 32KB.

### Gap 3: Window adjust automático en el canal

cbssh `SessionChannel` debería enviar `SSH_MSG_CHANNEL_WINDOW_ADJUST`
proactivamente cuando el window baja de cierto threshold (como hace sshj con
`adjustThreshold`). Con 16MB inicial esto no es problema hasta que se
transfieran 16MB sin pausa — pero para archivos grandes sigue siendo necesario.

**Nota:** `ForwardingChannel` en cbssh ya tiene `WINDOW_ADJUST_THRESHOLD = 64KB`
pero `SessionChannel` (que usa SFTP) no tiene el mismo mecanismo.

---

## Plan de contribución a connectbot/cbssh

### Lo que vamos a contribuir como PR

**PR 1: `fix(sftp): increase session window for SFTP transfers`**
```kotlin
// SshClient.kt: pasar window grande al abrir sesión SFTP
conn.openSessionChannel(
    initialWindowSize = 16 * 1024 * 1024,
    maxPacketSize = 32 * 1024,
)
```
- Afecta solo a `openSftp()`, no a sesiones de terminal
- Backward compatible (window más grande nunca rompe nada)
- Sin cambios de API pública

**PR 2: `feat(sftp): SftpTransfer — high-level pipelined transfer`**
Extraer `CbsshTransfer` como clase genérica en `sshlib`:
```kotlin
// org.connectbot.sshlib.SftpTransfer
class SftpTransfer(private val sftp: SftpClient) {
    suspend fun download(remotePath: String, dest: OutputStream, onProgress: ...)
    suspend fun upload(localFile: File, remotePath: String, onProgress: ...)
    // pipeline depth configurable
}
```
- Elimina que cada app tenga que implementar su propio loop de transfers
- Tests unitarios incluidos

**PR 3: `feat(ssh): configurable SSH-level keepalive`**
```kotlin
// SshClientConfig: añadir keepAliveIntervalMs: Long (default = 0 = disabled)
// SshClient: lanzar coroutine que envía SSH_MSG_IGNORE cada interval
conn.writePacket(SshEnums.MessageType.SSH_MSG_IGNORE.id().toInt(), ByteArray(0))
delay(keepAliveIntervalMs)
```
- Previene muerte de conexión por VPN/NAT/routers idle timeout
- SSH_MSG_IGNORE es la estrategia más simple (no espera respuesta)
- sshj lo implementa como `Heartbeater` que envía SSH_MSG_IGNORE cada N segs

**Cuándo:** Después de que el testing en DarkSSH confirme que los downloads
funcionan correctamente con el fix actual.

---

## Keepalive: sshj vs cbssh (por qué cbssh se muere con VPN)

### Cómo sshj mantiene conexiones vivas

sshj tiene **dos clases de keepalive** en `net.schmizz.keepalive.*`:

| Clase | Mecanismo | Uso |
|-------|-----------|-----|
| `Heartbeater` | Envía `SSH_MSG_IGNORE` periódico | Anti-NAT/VPN idle timeout |
| `KeepAliveRunner` | Envía `keepalive@openssh.com` GLOBAL_REQUEST con maxAliveCount | Detección real de conexión muerta |

En DarkSSH se activa con:
```kotlin
// SftpClient.kt
ssh.connection.keepAlive.keepAliveInterval = 15  // 15 segundos
```
Por defecto arranca `KeepAliveRunner` (envía `keepalive@openssh.com` y espera respuesta).

### Por qué cbssh se muere con VPN

`grep -rn "keepalive\|KeepAlive" cbssh-fork/sshlib/src/main/kotlin/`:
```
(no matches)
```

**cbssh NO TIENE NINGÚN mecanismo de keepalive.**

Cuando activas VPN:
1. El router/VPN cambia la ruta de red del teléfono
2. El socket TCP al servidor SSH se queda sin tráfico durante el switch
3. El NAT/VPN/firewall cierra la conexión TCP por idle timeout (típico: 60-300s)
4. cbssh no se da cuenta porque no envía `SSH_MSG_IGNORE` ni `keepalive@openssh.com`
5. La próxima operación SFTP falla con `ChannelClosedException` o timeout
6. Usuario tiene que cerrar y abrir manualmente

### Fix: SSH_MSG_IGNORE periódico

SSH_MSG_IGNORE (RFC 4253 §11.2):
```
byte      SSH_MSG_IGNORE
string    data            (arbitrary, ignorado por el receptor)
```

- Cero overhead — solo un byte de payload
- El servidor lo ignora silenciosamente
- No requiere respuesta
- Mantiene el socket TCP vivo (NAT no lo cierra)

### Implementación

En cbssh-fork añadir a `SshClientConfig`:
```kotlin
val keepAliveIntervalMs: Long = 0L  // 0 = disabled
```

En `SshClient.connect()`, lanzar:
```kotlin
if (config.keepAliveIntervalMs > 0) {
    keepAliveJob = connectionScope.launch {
        while (isActive) {
            delay(config.keepAliveIntervalMs)
            try {
                conn.writePacket(
                    SshEnums.MessageType.SSH_MSG_IGNORE.id().toInt(),
                    ByteArray(0),
                )
            } catch (e: Exception) {
                logger.warn("Keepalive failed", e)
                break
            }
        }
    }
}
```

Y en `disconnect()`:
```kotlin
keepAliveJob?.cancel()
```

**Esto se incluye en PR 3** del plan de contribución a cbssh upstream.

---

## Archivos relevantes

| Archivo | Propósito |
|---------|-----------|
| `cbssh-fork/sshlib/.../SshClient.kt` | `openSftp()` con window 16MB |
| `cbssh-fork/sshlib/.../SshClientConfig.kt` | Config del cliente (futuro: keepAliveIntervalMs) |
| `cbssh-fork/sshlib/.../SshConnection.kt` | `openSessionChannel()` defaults |
| `cbssh-fork/sshlib/.../SftpDispatcher.kt` | Request/response matching por ID |
| `cbssh-fork/sshlib/.../SftpPacketIO.kt` | Lee paquetes del canal SSH |
| `cbssh-fork/sshlib/.../SessionChannel.kt` | Canal SSH (window tracking) |
| `cbssh-fork/sshlib/.../PacketIO.kt` | Encoding de paquetes SSH |
| `app/.../cbssh/CbsshTransfer.kt` | Pipeline download/upload (nuestro) |
| `app/.../cbssh/SftpClient2.kt` | ISftpClient impl con cbssh |
| `app/.../transport/SftpClient.kt` | ISftpClient impl con sshj (baseline) |
