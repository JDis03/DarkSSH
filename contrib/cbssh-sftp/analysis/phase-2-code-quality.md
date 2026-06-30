# Phase 2 Code Quality Analysis

## Issues Identificados

### A1. Warnings de compilación (10 total)

**Problema:** Elvis operator innecesario en `Throwable.cause`

```kotlin
// ❌ MAL - cause es no-null en IoError
result.cause ?: IOException("I/O error")

// ✅ BIEN
result.cause  // Ya es no-null
```

**Archivos afectados:**
- `CbsshTransfer.kt` líneas 89, 99, 115, 377, 450, 460
- `SftpClient2.kt` líneas 230, 492, 583, 594

**Causa raíz:** `SftpResult.IoError.cause` es `Throwable` (no-nullable), no `Throwable?`. El operador `?:` siempre retorna el izquierdo.

**Fix:** Eliminar `?: X` cuando el operando izquierdo ya es no-null.

---

### A2. `SftpEntry2` vs `SftpEntry` - Duplicación

**Problema:** Se creó `SftpEntry2` con la misma estructura que `SftpEntry`

```kotlin
// En SftpClient.kt (original sshj)
data class SftpEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long,
    val permissions: String?,
    val modifiedTime: Long?,
)

// En SftpClient2.kt (nuevo cbssh) - DUPLICADO
data class SftpEntry2(...)  // Misma estructura
```

**Impacto:** Incompatibilidad con ViewModel existente que espera `SftpEntry`.

**Fix:** Eliminar `SftpEntry2`, importar `SftpEntry` desde el package original `com.darkssh.client.transport`.

---

### A3. `connectWithKey()` no implementado

**Problema:** cbssh no acepta `KeyPair` directamente, solo PEM string

```kotlin
// sshj (funciona)
ssh.authPublickey(username, keyProvider)

// cbssh (requiere cambio)
client.authenticatePublicKey(username, privateKeyData: ByteArray, passphrase: String?)
// o
client.authenticate(username, AuthHandler)
```

**Opciones:**

**Opción A**: Convertir `KeyPair` → PEM usando BouncyCastle (~30 líneas)
- Ventaja: API idéntica al original
- Desventaja: Agrega dependencia de BC para codificación

**Opción B**: Implementar `AuthHandler` que use `KeyPair` (~50 líneas)
- Ventaja: Más flexible, soporta agent signing futuro
- Desventaja: Más código

**Opción C**: Temporal - devolver UnsupportedOperationException
- Ventaja: Simple
- Desventaja: Funcionalidad rota

**Recomendación:** Opción A (PEM conversion) - mantiene API idéntica.

---

### A4. HostKeyVerifier permisivo - Inseguro

**Problema:** Acepta cualquier host key

```kotlin
val verifier = object : HostKeyVerifier {
    override suspend fun verify(key: PublicKey): Boolean = true
}
```

**Impacto:** MITM attack possible.

**Fix:** Usar `KnownHostRepository` de DarkSSH para verificar contra host keys conocidos.

---

### A5. `executeCommand()` no captura exit code

**Problema:** Implementación actual siempre retorna exit code 0

```kotlin
// Actual
Result.success(Pair(outputBuilder.toString(), 0))  // ← Siempre 0!
```

**Comparación sshj:**
```kotlin
val exitCode = cmd.exitStatus ?: -1
val errorOutput = cmd.errorStream.bufferedReader().readText()
```

**Fix:** cbssh no expone exit code directamente. Necesitamos parsear el output o usar un wrapper.

**Opciones:**
- Buscar API alternativa en cbssh (puede no existir)
- Hacerlo best-effort (parsear output para inferir éxito)
- Documentar como limitación

---

### A6. `size: Long?` incompatibilidad con `Int`

**Problema:** cbssh `attrs.size` es `Long?` pero `attrs.mtime` es `Int?`

```kotlin
size = attrs.size ?: 0L,        // ✅ OK (Long? → Long)
modifiedTime = attrs.mtime?.toLong() ?: 0L,  // ⚠️ Conversión implícita
```

**Impacto:** mtime viene como Unix timestamp en segundos (Int), pero nuestro data class espera ms (Long). Diferencia de magnitud 1000x.

**Fix:** Multiplicar por 1000 si son segundos Unix timestamp:
```kotlin
modifiedTime = (attrs.mtime?.toLong() ?: 0L) * 1000L  // seconds → ms
```

---

### A7. Compression no soportado

**Problema:** sshj tiene `ssh.useCompression()`, no hemos explorado cbssh

**Fix:** Buscar `SshClientConfig` para ver si hay compression config.

---

## Priorización

| # | Issue | Severidad | Esfuerzo | Recomendación |
|---|-------|-----------|----------|---------------|
| A1 | Warnings | Baja | 10 min | Hacer ahora |
| A2 | SftpEntry2 duplicado | Alta | 5 min | Hacer ahora |
| A6 | mtime Int vs Long | Alta | 5 min | Hacer ahora |
| A3 | connectWithKey | Alta | 1-2 hrs | ✅ Hecho |
| A4 | HostKeyVerifier | Media | 30 min | Hacer después |
| A5 | Exit code | Baja | 1 hr | Hacer después |
| A7 | Compression | Baja | 30 min | Investigar |

---

## Plan de Implementación

**Sprint 1 (ahora): Issues críticos**
1. Eliminar warnings de compilación (A1)
2. Eliminar SftpEntry2, usar SftpEntry original (A2)
3. Corregir conversión de mtime (A6)
4. Implementar connectWithKey con PEM (A3)

**Sprint 2 (después): Issues de calidad**
5. HostKeyVerifier con KnownHostRepository (A4)
6. Exit code para executeCommand (A5)
7. Investigar compression (A7)

**Sprint 3: Tests**
- Integration tests con testcontainers
- Unit tests para PEM conversion
- Regression tests vs sshj