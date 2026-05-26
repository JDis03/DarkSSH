# Test Manual: SFTP Server en DarkSSH

## Setup inicial (una sola vez)

### 1. Compilar y deployar DarkSSH

```bash
cd /home/dark/Project/clientssh
./gradlew assembleDebug
adb -s 127.0.0.1:15555 install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Iniciar el servicio SFTP manualmente

Por ahora, como no tenemos toggle en UI, vamos a iniciar el servicio vía ADB:

```bash
# Iniciar servicio
adb -s 127.0.0.1:15555 shell am start-foreground-service \
  -n com.darkssh.client.debug/.server.SftpServerService

# Verificar que está corriendo
adb -s 127.0.0.1:15555 shell dumpsys activity services | grep SftpServerService
```

Deberías ver:
- Notificación persistente "DarkSSH Server"
- Estado: "Servers running"

---

## Test 1: Health Check (HTTP)

```bash
# Obtener IP del dispositivo
adb -s 127.0.0.1:15555 shell ip addr show wlan0 | grep "inet " | awk '{print $2}' | cut -d/ -f1

# Test health check (reemplaza <DEVICE-IP>)
curl http://<DEVICE-IP>:2222/health

# Respuesta esperada:
{
  "status": "ok",
  "sftp_port": 22,
  "sftp_active": true,
  "uptime_ms": 12345,
  "timestamp": 1748123456789
}
```

✅ **PASS** si recibes JSON con `"sftp_active": true`
❌ **FAIL** si timeout o connection refused

---

## Test 2: SFTP Connection

```bash
# Conectar via SFTP (password: darkssh)
sftp -P 22 root@<DEVICE-IP>

# Comandos a probar dentro de SFTP:
sftp> pwd                    # Debería mostrar /sdcard/
sftp> ls                     # Lista archivos en /sdcard/
sftp> cd Download            # Navegar a Download/
sftp> ls
sftp> mkdir test-upload      # Crear carpeta de prueba
sftp> quit
```

✅ **PASS** si puedes conectar y navegar
❌ **FAIL** si "Permission denied" o "Connection refused"

---

## Test 3: File Upload

```bash
# Crear archivo de prueba en PC
echo "Test file for SFTP upload" > /tmp/test-upload.txt

# Upload via SFTP
sftp -P 22 root@<DEVICE-IP> <<EOF
cd Download/pending-install
put /tmp/test-upload.txt
ls -l
bye
EOF

# Verificar que llegó al dispositivo
adb -s 127.0.0.1:15555 shell ls -lh /sdcard/Download/pending-install/test-upload.txt

# Verificar logs de broadcast
adb -s 127.0.0.1:15555 logcat -d | grep "FILE_UPLOADED"
```

✅ **PASS** si:
- Archivo existe en `/sdcard/Download/pending-install/`
- Ves log: `Broadcast upload complete: /sdcard/Download/pending-install/test-upload.txt`

❌ **FAIL** si archivo no llegó o no hay broadcast

---

## Test 4: APK Upload (real workflow)

```bash
# Tomar un APK de ejemplo (DarkADB)
APK_PATH="/home/dark/Project/DarkADB/app/build/outputs/apk/debug/app-debug.apk"

# Renombrar para simular DarkDev
cp $APK_PATH /tmp/DarkADB-test.apk

# Upload via SFTP
sftp -P 22 root@<DEVICE-IP> <<EOF
cd Download/pending-install
put /tmp/DarkADB-test.apk
ls -lh DarkADB-test.apk
bye
EOF

# Verificar broadcast específico para APK
adb -s 127.0.0.1:15555 logcat -d | grep "DarkADB-test.apk"

# Verificar que APK está en dispositivo
adb -s 127.0.0.1:15555 shell ls -lh /sdcard/Download/pending-install/DarkADB-test.apk
```

✅ **PASS** si APK llegó y broadcast emitido
❌ **FAIL** si APK corrupto o no hay broadcast

---

## Test 5: Concurrent Uploads

```bash
# Crear múltiples archivos
for i in {1..5}; do
  echo "File $i" > /tmp/file-$i.txt
done

# Upload en paralelo
for i in {1..5}; do
  (sftp -P 22 root@<DEVICE-IP> <<EOF
cd Download/pending-install
put /tmp/file-$i.txt
bye
EOF
  ) &
done

wait

# Verificar que todos llegaron
adb -s 127.0.0.1:15555 shell ls /sdcard/Download/pending-install/file-*.txt

# Contar broadcasts
adb -s 127.0.0.1:15555 logcat -d | grep "Upload complete: /sdcard/Download/pending-install/file-" | wc -l
```

✅ **PASS** si los 5 archivos llegaron y hay 5 broadcasts
❌ **FAIL** si faltan archivos o broadcasts

---

## Troubleshooting

### Problema: Connection refused

**Causa**: Servicio no está corriendo o puerto bloqueado

**Solución**:
```bash
# Verificar servicio
adb -s 127.0.0.1:15555 shell dumpsys activity services | grep -A10 SftpServerService

# Ver logs del servicio
adb -s 127.0.0.1:15555 logcat -d | grep -E "SftpServer|HealthCheck"

# Reiniciar servicio
adb -s 127.0.0.1:15555 shell am force-stop com.darkssh.client.debug
adb -s 127.0.0.1:15555 shell am start-foreground-service \
  -n com.darkssh.client.debug/.server.SftpServerService
```

### Problema: Permission denied

**Causa**: Permisos de storage no otorgados

**Solución**:
```bash
# Otorgar permisos de storage
adb -s 127.0.0.1:15555 shell pm grant com.darkssh.client.debug android.permission.READ_EXTERNAL_STORAGE
adb -s 127.0.0.1:15555 shell pm grant com.darkssh.client.debug android.permission.WRITE_EXTERNAL_STORAGE

# Android 11+ requiere MANAGE_EXTERNAL_STORAGE (solo para debug)
adb -s 127.0.0.1:15555 shell appops set com.darkssh.client.debug MANAGE_EXTERNAL_STORAGE allow
```

### Problema: Health check funciona pero SFTP no

**Causa**: Puerto 22 requiere permisos especiales en Android

**Solución temporal**: Cambiar puerto SFTP a 8022 (no privilegiado)

```kotlin
// En SftpServerService.kt
companion object {
    private const val DEFAULT_SFTP_PORT = 8022  // Cambiar de 22 a 8022
}
```

Luego conectar con: `sftp -P 8022 root@<DEVICE-IP>`

### Problema: Broadcast no llega a DarkADB

**Causa**: DarkADB aún no tiene BroadcastReceiver implementado

**Solución**: Esto lo implementaremos en el siguiente paso (tarea 7.2)

---

## Checklist de éxito

- [ ] Health check responde en puerto 2222
- [ ] SFTP acepta conexiones en puerto 22 (o 8022)
- [ ] Puedo navegar carpetas via SFTP
- [ ] Upload de archivos funciona
- [ ] Broadcast se emite (ver en logcat)
- [ ] Upload de APK funciona
- [ ] Uploads concurrentes funcionan

Si todos los checks pasan → **Servidor SFTP está listo para DarkDev** ✅

---

## Siguiente paso: Agregar Toggle UI

Una vez que el servidor funcione en los tests, agregar toggle en DarkSSH UI para:
- Start/Stop server
- Ver status (running/stopped)
- Ver IP del dispositivo
- Ver puerto SFTP y Health Check
