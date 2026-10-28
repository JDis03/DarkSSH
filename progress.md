## 2026-10-28 20:09 — ClientSSH
**Summary**: Resuelto fallo preexistente de R8 en assembleRelease (bug-021). Se agregaron reglas -dontwarn para net.i2p.crypto.eddsa.** y org.bouncycastle.** referenciadas opcionalmente por Apache SSHD en proguard-rules.pro.
**Verified**: ./gradlew assembleRelease BUILD SUCCESSFUL (produce app-release-unsigned.apk de 24MB), ./init.sh BUILD SUCCESSFUL (148 tests).
**Completed**: none
---
## 2026-10-28 — Corrección build Release R8 (bug-021)
**Summary**: Resuelto el fallo preexistente de R8 en la build release (`minifyReleaseWithR8`). Se agregaron reglas `-dontwarn` en `app/proguard-rules.pro` para `net.i2p.crypto.eddsa.**`, `org.bouncycastle.crypto.prng.**` y `org.bouncycastle.pqc.crypto.ntruprime.**`, que corresponden a dependencias criptográficas opcionales de Apache Mina SSHD no requeridas en Android.
**Verified**: `./gradlew assembleRelease` pasa en 45s produciendo `app-release-unsigned.apk` de 24MB con minificación y stripping de logs R8; `./init.sh` BUILD SUCCESSFUL con 148 tests unitarios.
**Completed**: bug-021
---
## 2026-10-28 — Correcciones UX y logs en dispositivo (bug-020)
**Summary**: Corregidos 4 issues de UX y logging descubiertos en pruebas en dispositivo real (Xiaomi 25069PTEBG): (1) En HostEditorScreen.kt, portText se almacena ahora como String en vez de Int; se valida 1..65535 sólo dígitos y permite borrar completamente el campo sin reset prematuro a 22 ni salto de cursor al inicio (evita que escribir '2299' resulte en '229922'). (2) En SshFingerprint.kt, se cambió Base64.URL_SAFE por Base64.NO_WRAP or Base64.NO_PADDING para coincidir de forma idéntica con el formato OpenSSH de ssh-keygen -lf (43 caracteres base64 estándar con '+' y '/', sin '=' final). (3) En DarkSshHostKeyVerifier.kt, el tag de logging ahora es configurable (CbsshTerminalTransport pasa 'TerminalHostKey', SftpClient2 pasa 'SftpHostKey'). (4) En TerminalBridge.kt, dispatchDisconnect ahora es idempotente con guard `if (_isClosed || _isDisconnected.value) return`, eliminando notificaciones y logs repetidos al fallar la conexión. (5) En TerminalBridge.kt, onTerminalSizeChanged incluye ahora [nickname|tabId].
**Verified**: SshFingerprintTest verifica algoritmo, 43 chars sin padding y preservación de '+' y '/'; TerminalBridgeDisconnectTest verifica idempotencia de dispatchDisconnect; DarkSshHostKeyVerifierTest 5/5 pasa; ./init.sh BUILD SUCCESSFUL (todos los tests unitarios pasando); assembleDebug y -PuseCbsshTerminal=true assembleDebug exitosos.
**Completed**: bug-020
---
## 2026-10-15 06:47 — ClientSSH
**Summary**: Migración terminal cbssh experimental implementada junto a trilead. Tras corrección del usuario, SSH.kt quedó idéntico a HEAD y sigue por defecto; CbsshTerminalTransport sólo se activa en debug con -PuseCbsshTerminal=true. TerminalTransportFactory no hace fallback entre backends; OsDetector soporta ambas conexiones. Tests para auth/IO/timeouts y 11 casos OpenSSH reales. Pendiente paridad MFA/DSA y Android; NO quitar trilead ni cambiar default sin aceptación. Cambios sin commit.
**Verified**: ./init.sh: 146 tests, 0 fallos; integrationTest:18/18 (11 cbssh terminal+7 SFTP); selector y assembleDebug probados con opt-in y con default; BuildConfig USE_CBSSH_TERMINAL true/false confirmado, APK final default false/trilead. git diff SSH.kt vacío, git diff --check limpio, feature_list.json válido. Sin dispositivo adb; release no revalidada.
**Completed**: none
---
## 2026-10-15 — Terminal cbssh opt-in, trilead estable conservado
**Summary**: En contrib/cbssh-sftp se añadió CbsshTerminalTransport con PTY xterm-256color, shell, resize, script, keepalive, compresión y AuthHandler/SshSigning. Por corrección explícita del usuario NO sustituye todavía al estable: SSH.kt/trilead se restauró idéntico a HEAD y es el default. TerminalTransportFactory elige el backend antes de conectar, sin fallback automático entre clientes ante errores. USE_CBSSH_TERMINAL=false en builds normales y release; sólo debug permite opt-in con `./gradlew -PuseCbsshTerminal=true assembleDebug`. OsDetector conserva la ruta trilead y añade la ruta cbssh.
**Compatibility**: Se detectó que la estrategia cbssh selecciona sólo KBI si el servidor anuncia KBI+password; SSH conserva un fallback explícito a authenticatePassword para evitar repetir KBI hasta MaxAuthTries. En tests Robolectric, ConscryptMode.OFF evita mezclar la detección ChaCha20ParameterSpec de SunJCE con cifrado Conscrypt; no se cambian cifrados en producción.
**Verified**: ./init.sh (146 tests, 0 fallos), ./gradlew integrationTest (18 tests, 0 fallos: 11 de terminal cbssh real OpenSSH y 7 SFTP), selector y assembleDebug con opt-in y default; BuildConfig generado true/false confirmado. APK final generado con default trilead; git diff --check limpio. Docker verifica shell, resize con stty, RSA/SHA2, clave asignada, TOFU y clave cambiada sin pedir contraseña, script, SO, EOF conservando salida, close durante read y durante conexión sin banner.
**Pending**: NO cambiar el default ni retirar trilead hasta paridad y aceptación. La revisión detectó gaps cbssh0.5.1 en MFA publickey+password/KBI (pierde métodos tras partial success) y DSA (SshSigning no lo soporta aunque la app lo permita). Sin dispositivo adb: validar Ed25519/Conscrypt, KBI/OTP, Stay Connected, reconnect, Ctrl+dígitos y transferencias >1 GiB. El timeout interno de KEX cbssh sigue aplicando aunque el watchdog local excluya diálogos. No se revalidó release (R8 EdDSA previo). Cambios sin commit; versiones y progreso previos conservados.
---
## 2026-10-11 05:54 — ClientSSH
**Summary**: Paso 1 migración: commit 6241bb33 de cambios pendientes; Kotlin 2.4.20/KSP 2.3.12/Hilt 2.60.1; cbssh 0.5.1-SNAPSHOT upstream (sshlib+protocol) publicado a mavenLocal y usado por DarkSSH.
**Verified**: compileDebugKotlin OK, ./init.sh BUILD SUCCESSFUL, assembleDebug OK; verificado que upstream contiene d23e6e6 (gate KEX). Sin prueba en dispositivo.
**Completed**: none
---
## 2026-09-26 00:31 — ClientSSH
**Summary**: Seguridad: (1) HostKeyPolicy compartido: rechazo de claves no fijadas incluso de otro algoritmo (cierra downgrade MITM que recibía la contraseña guardada), preferencia de algoritmos conocidos en trilead (setServerHostKeyAlgorithms) y cbssh (hostKeyAlgorithms), mensaje claro de clave cambiada, botón 'Forget host key' con confirmación en Edit Host. (2) Backups: allowBackup=false y data_extraction_rules excluyendo DB/prefs/files en cloud y device-transfer.
**Verified**: HostKeyPolicyTest 3/3; DarkSshHostKeyVerifierTest 5/5 (incluye caso downgrade RSA); ./init.sh BUILD SUCCESSFUL; assembleDebug OK; git diff --check limpio. Pendiente smoke test en dispositivo; build release sigue rota por EdDSA (preexistente).
**Completed**: none
---
## 2026-09-26 00:25 — ClientSSH
**Summary**: Política de logs: eliminado el log de SSH.write que registraba el primer byte de cada tecla (reconstruía contraseñas tecleadas). FileLoggingTree ahora persiste DEBUG+ sólo en builds debug (VERBOSE queda en logcat) y WARN+ en release. ProGuard elimina android.util.Log v/d/i en release. Test FileLoggingPolicyTest. Detectado que la build release ya fallaba en R8 por EdDSA (preexistente, TODO).
**Verified**: FileLoggingPolicyTest 2/2; ./init.sh BUILD SUCCESSFUL; git diff --check limpio; minifyReleaseWithR8 falla igual con y sin el cambio (preexistente).
**Completed**: none
---
## 2026-09-26 00:16 — ClientSSH
**Summary**: Fix: con Stay Connected el cliente SSH seguía pidiendo la clave. Causa: keyboard-interactive (usado por OpenSSH/PAM) nunca usaba ni guardaba la clave recordada. Ahora el prompt de contraseña oculto de keyboard-interactive usa la clave guardada una vez por conexión, guarda la tecleada tras autenticar (persistente sólo con Stay Connected) y la borra si es rechazada; excluye prompts de cambio de contraseña. CredentialStore sólo persiste/lee del almacén cifrado con Stay Connected, y SFTP respeta el flag.
**Verified**: KeyboardInteractivePasswordTest 3/3; ./init.sh BUILD SUCCESSFUL; git diff --check limpio. Pendiente prueba en dispositivo.
**Completed**: none
---
## 2026-09-21 17:01 — ClientSSH
**Summary**: Implementado Ctrl+0 a Ctrl+9 distinguibles para la consola SSH. TerminalView intercepta sólo Ctrl puro sobre fila numérica o keypad antes del fallback ASCII y envía CSI-u ESC[48;5u a ESC[57;5u. Así Ctrl+2 y Ctrl+3 ya no colapsan a NUL/Escape. Añadidas pruebas para los diez dígitos, keypad y modificadores incompatibles.
**Verified**: Baseline ./init.sh BUILD SUCCESSFUL; ./gradlew testDebugUnitTest --tests com.darkssh.client.terminal.emulator.KeyHandlerControlDigitTest BUILD SUCCESSFUL; ./init.sh final BUILD SUCCESSFUL; git diff --check limpio; feature_list.json válido. Pendiente smoke test en dispositivo con la TUI remota.
**Completed**: none
---
## 2026-09-21 — Ctrl+0 a Ctrl+9 distinguibles
**Summary**: Implementado el envío explícito de Ctrl+0 hasta Ctrl+9 mediante CSI-u antes de la conversión ASCII heredada del terminal. La fila numérica y keypad ahora producen ESC[48;5u hasta ESC[57;5u, conservando Ctrl como modificador en lugar de colapsar a caracteres ASCII, NUL o Escape. Ctrl con Shift/Alt y teclas no numéricas mantienen su comportamiento heredado.
**Verified**: KeyHandlerControlDigitTest verifica los diez dígitos de fila, tres teclas keypad y casos que deben permanecer sin manejar; test específico BUILD SUCCESSFUL; ./init.sh final BUILD SUCCESSFUL; git diff --check y feature_list.json válidos.
**Completed**: feat-terminal-ctrl-digits; pendiente smoke test con la TUI remota.
---
## 2026-09-21 16:50 — ClientSSH
**Summary**: Investigado Ctrl+1/Ctrl+2/Ctrl+3 en la consola SSH sin modificar código. MainActivity no los consume y el bridge/SSH entrega bytes intactos; el límite es la semántica ASCII heredada del TerminalView de Termux: Ctrl+1 se colapsa a '1', Ctrl+2 es NUL y Ctrl+3 es ESC. Para atajos distinguibles de TUIs modernas hay que acordar e implementar un protocolo extendido opt-in por host.
**Verified**: ./init.sh BUILD SUCCESSFUL antes de investigar. Lectura y traza verificadas en MainActivity.kt, TerminalView.java, KeyHandler.java, TerminalSession.java, TerminalBridge.kt y SSH.kt; no se modificaron archivos de código.
**Completed**: none
---
## 2026-09-04 03:00 — ClientSSH
**Summary**: Implementada feat-sftp-open-with: tocar un archivo remoto ahora lo descarga a una ruta UUID temporal por host y, al completar, abre el chooser Android mediante FileProvider + ACTION_VIEW. Download contextual conserva guardado permanente. Añadidos MIME para Markdown/texto, nombres seguros, eventos lifecycle/tab-aware, limpieza robusta al cancelar, poda de caché a 24h, compatibilidad API 24/25 y seis pruebas unitarias.
**Verified**: Baseline ./init.sh exitoso; test específico SftpOpenFileUtilsTest exitoso; ./gradlew testDebugUnitTest exitoso; ./init.sh final BUILD SUCCESSFUL; git diff --check limpio; feature_list.json válido. lintDebug ejecutado y falla por 42 errores preexistentes, sin hallazgos nuevos atribuibles a esta feature.
**Completed**: none
---
---
## 2026-09-04 02:57 — ClientSSH
**Summary**: Revisión read-only de los cuatro bloqueantes SFTP Open with tras cambios recientes; API24 resuelto, cancelación y lifecycle parciales, caché aún sin limpieza eventual garantizada.
**Verified**: SftpOpenFileUtilsTest PASS; git diff --check PASS; lintDebug ejecutado y confirmó que ya no hay NewApi en createDownloadNotificationChannel, aunque falla por 42 errores preexistentes.
**Completed**: none
---
---
## 2026-09-04 02:51 — ClientSSH
**Summary**: Revisión read-only de SFTP Open with: detectados problemas API 24-25, lifecycle/eventos y limpieza de caché/cancelación; sin modificar fuentes.
**Verified**: Ejecutado :app:testDebugUnitTest --tests com.darkssh.client.util.SftpOpenFileUtilsTest (PASS) y git diff --check sobre archivos revisados (PASS).
**Completed**: none
---
---
## 2026-09-03 — SFTP Open with
**Summary**: Implementada apertura de archivos remotos al estilo file manager. Un toque sobre un archivo lo descarga temporalmente a una ruta UUID única dentro de cacheDir/sftp-open/<hostId>, conserva nombre/extensión, muestra progreso y luego emite un evento one-shot. La UI usa un SftpViewModel separado por host y solo consume el evento con la pestaña activa y lifecycle RESUMED; después crea un content URI con FileProvider y lanza ACTION_VIEW dentro de Intent.createChooser("Open with"). Markdown usa text/markdown; texto/código y formatos estándar reciben MIME por extensión. La acción contextual Download sigue guardando permanentemente en Downloads. Archivos parciales se eliminan incluso bajo cancelación, el cancel tardío no puede abrir el archivo y la caché exitosa mayor a 24h se poda.
**Verified**: 6 tests de SftpOpenFileUtils; testDebugUnitTest BUILD SUCCESSFUL; ./init.sh BUILD SUCCESSFUL; git diff --check y feature_list.json válidos. lintDebug ejecutado tras corregir API 24/25: sigue bloqueado por 42 errores preexistentes, sin hallazgos nuevos atribuibles a esta feature.
**Completed**: feat-sftp-open-with; pendiente smoke test en dispositivo con una app Markdown instalada.
---
## 2026-09-03 04:04 — ClientSSH
**Summary**: Usuario confirmó en dispositivo que el fix real de ConsoleKeyBar funcionó: el viewport del terminal ahora reserva los 44dp de la barra propia cuando el teclado del sistema está cerrado, por lo que la última fila/prompt ya no queda oculta. TODO de validación visual completado.
**Verified**: Confirmación directa del usuario en dispositivo; validación automatizada previa: compileDebugKotlin, testDebugUnitTest e init.sh exitosos.
**Completed**: none
---
---
## 2026-09-03 03:36 — ClientSSH
**Summary**: Revisada la captura real en /pictures. El 'mini teclado' era ConsoleKeyBar (barra propia de 44dp con flechas, Tab e icono de teclado), no Gboard. La barra estaba superpuesta con Alignment.BottomCenter mientras TerminalView ocupaba fillMaxSize, por eso la última fila/prompt quedaba debajo. Revertido el cambio anterior de imeAnimationTarget porque no correspondía. Fix real: constante compartida consoleKeyBarHeight=44.dp y padding inferior de esa altura en el viewport Terminal cuando !imeVisible, permitiendo que TerminalView recalcule sus filas por encima de la barra.
**Verified**: git diff --check limpio; ./gradlew compileDebugKotlin testDebugUnitTest BUILD SUCCESSFUL; ./init.sh BUILD SUCCESSFUL. Pendiente validación visual en dispositivo.
**Completed**: none
---
---
## 2026-09-03 03:27 — ClientSSH
**Summary**: Corregido el padding de la consola SSH cuando Gboard se minimiza y deja una barra/mini teclado. ConsoleScreen mezclaba WindowInsets.ime para detectar visibilidad con WindowInsets.imeAnimationTarget para el layout; el target no representa necesariamente la altura visible del teclado compacto y causaba solapamiento o padding residual. Se reemplazaron ambos usos de imeAnimationTarget por WindowInsets.ime y se eliminó el import. Cambio limitado a ConsoleScreen.kt, sin tocar los cambios pendientes previos.
**Verified**: ./gradlew compileDebugKotlin testDebugUnitTest BUILD SUCCESSFUL; ./init.sh BUILD SUCCESSFUL. Pendiente validación visual en dispositivo con Gboard minimizado.
**Completed**: none
---
---
## 2026-08-31 04:45 — ClientSSH
**Summary**: Validación final exitosa en dispositivo: el upload real de EnemyAITutorial.zip, 1.475.138.004 bytes, cruzó el rekey del servidor en 1 GiB y terminó al 100%. Log: [UL] Complete: 1475138004B in 82439ms = 17474KB/s, retries=0, finalDepth=26; seguido de Upload OK (SFTP). Esto confirma definitivamente que el fallo era el jar 0.3.3-SNAPSHOT antiguo de cbssh sin OutboundPacketController; el jar reconstruido compatible (Kotlin 2.3.21, 0.3.3-SNAPSHOT) incluye el gate y resuelve el problema. No era sshj, WiFi ni VPN. quality-018 queda validado.
**Verified**: Log del dispositivo confirma 100%, retries=0 y Upload OK después de cruzar 1 GiB; cbssh publishToMavenLocal y DarkSSH --refresh-dependencies compileDebugKotlin/testDebugUnitTest/init.sh también pasaron
**Completed**: none
---
---
## 2026-08-31 04:34 — ClientSSH
**Summary**: Causa final identificada: DarkSSH ejecutaba un jar Maven Local 0.3.3-SNAPSHOT antiguo sin SshConnection.OutboundPacketController, mientras yo inspeccionaba/testeaba el source actual que sí tiene el gate RFC 4253 para pausar CHANNEL_DATA durante KEX. Eso explica por qué sshj funcionaba y todos los repros del source actual (incluyendo OpenSSH 10.5p1 + 80ms netem) pasaban, pero la app real seguía muriendo exactamente en rekey. Se creó cbssh branch build/0.3.3-kotlin-2.3-rekey-fix, manteniendo Kotlin 2.3.21 y versión 0.3.3 para compatibilidad Hilt, se publicó a Maven Local y el jar nuevo fue verificado con OutboundPacketController. DarkSSH recompiló contra el artefacto nuevo con --refresh-dependencies. quality-018 registrada, pendiente prueba final del upload de 1.4GB.
**Verified**: cbssh :sshlib:compileKotlin + test específico + publishToMavenLocal BUILD SUCCESSFUL; jar contiene SshConnection$OutboundPacketController.class; DarkSSH --refresh-dependencies compileDebugKotlin + testDebugUnitTest + ./init.sh BUILD SUCCESSFUL
**Completed**: none
---
---
## 2026-08-31 — ClientSSH
**Summary**: Causa final encontrada: DarkSSH no estaba ejecutando el source actual de cbssh que yo inspeccionaba, sino un jar 0.3.3-SNAPSHOT antiguo de Maven Local sin OutboundPacketController. Ese gate (commit cbssh d23e6e6) bloquea SSH_MSG_CHANNEL_DATA durante el intercambio de claves; sin él, el SFTP pipelineado sigue enviando datos durante KEX y OpenSSH corta la conexión exactamente en el umbral de rekey. Esto explica por qué sshj no fallaba y por qué todos los repros contra el source actual (incluyendo OpenSSH 10.5p1 + 80ms netem) pasaban. Se creó build/0.3.3-kotlin-2.3-rekey-fix desde el source actual, manteniendo 0.3.3-SNAPSHOT y Kotlin 2.3.21 para no romper Hilt, y se publicó a Maven Local. El jar nuevo sí contiene SshConnection$OutboundPacketController.class; DarkSSH fue recompilado con --refresh-dependencies.
**Verified**: cbssh publishToMavenLocal BUILD SUCCESSFUL; presencia de OutboundPacketController verificada dentro del jar; DarkSSH compileDebugKotlin + testDebugUnitTest + ./init.sh BUILD SUCCESSFUL
**Completed**: quality-018 queda in-progress hasta validar el upload de 1.4 GB en dispositivo
---
## 2026-08-31 02:41 — ClientSSH
**Summary**: El experimento dio el caso B: con el rekey del cliente en 8 GiB el upload igual murió en 1 GiB exacto (chunk 8184/8192), que es donde rekea el servidor. Rompe cualquier rekey, también el entrante, y el error mutó a ClosedReceiveChannelException, o sea ahora entra por el lado de lectura. Encontré un hueco en mi propio método: los dos repros previos solo ejercitaban rekeys iniciados por el cliente. Escribí el que faltaba (server-initiated, RekeyLimit 4M vía SIGHUP en el contenedor, 64MB pipelineados) con un assert de no-vacuidad que exige ver rekeys reales en los logs del servidor — y también pasa en loopback. Quedan descartadas por test real: pipelining, ML-KEM768, rekey de cliente y rekey de servidor. Única variable sin replicar: latencia/BDP. Commit 1caef483. Desbloqueo inmediato disponible para el usuario: como el servidor es su propia máquina y tiene '#RekeyLimit default none' (o sea el default de 1 GiB), subirlo ahí evitaría el único rekey que queda en juego.
**Verified**: 3 repros del fork verificados en los XML de test-results (0 failures), el del servidor con guard de no-vacuidad que valida que el SIGHUP tomó efecto; ./init.sh BUILD SUCCESSFUL
**Completed**: none
---
---
## 2026-08-31 (5) — ClientSSH
**Summary**: El experimento de quality-017 dio el caso B: con el rekey del cliente en 8 GiB el upload igual murió en offset 1072693248 = chunk 8184/8192 = 1 GiB exacto, que es donde rekea el SERVIDOR ("rekey out after 134217728 blocks"). O sea rompe CUALQUIER rekey, también el entrante, y el fix upstream tiene que cubrir los dos sentidos. El error además mutó a ClosedReceiveChannelException ("Channel was closed"): ahora la caída entra por el lado de LECTURA, no de escritura. Detecté un hueco de método en mis repros: los dos anteriores solo ejercitaban rekeys iniciados por el CLIENTE, así que el camino del KEXINIT entrante nunca estuvo cubierto. Escribí el repro que faltaba ('SFTP pipelined writes survive SERVER-initiated rekey': RekeyLimit 4M en el sshd del contenedor vía SIGHUP, cliente con rekey desactivado, 64MB pipelineados depth 32) y le puse un assert de no-vacuidad que lee los logs del servidor y exige ver rekeys reales — sin eso, si el SIGHUP no tomaba efecto el test pasaba sin probar nada. También PASA en loopback. Variables ya descartadas con test real: pipelining, ML-KEM768, rekey del cliente y rekey del servidor. Única sin replicar: latencia/BDP.
**Verified**: 3 repros del fork verificados en los XML de test-results (0 failures), el del servidor con guard de no-vacuidad; ./init.sh BUILD SUCCESSFUL
**Completed**: quality-017
---
## 2026-08-31 02:35 — ClientSSH
**Summary**: Mi hipótesis del rekey simultáneo quedó refutada por su propia mitigación: con rekeyBytesLimit en 512 MiB el upload no se arregló sino que se movió, muriendo en el chunk 4088/4096 (pegado a los nuevos 512 MiB) igual que antes moría en 8180/8192 (1 GiB). Eso prueba algo más fuerte y más útil: la falla sigue al umbral configurado en el cliente, o sea la rompe el rekey que inicia cbssh, no el choque con el servidor. Revertí ese cambio porque hacía fallar antes (quality-017: umbral a 8 GiB, que además separa rekey propio vs entrante en la próxima prueba). La cadena de causas de quality-015 mostró el fondo: RST del servidor (Connection reset by peer). Agregué un segundo repro en el fork con kex post-cuántico ML-KEM768 y rekey cada 4MB: pasa, así que ML-KEM queda descartado igual que el pipelining. Queda la latencia/BDP como única variable sin replicar. Commit 4439159a.
**Verified**: compileDebugKotlin + testDebugUnitTest + ./init.sh BUILD SUCCESSFUL; los 2 repros del fork verificados en los XML de test-results (0 failures); hipótesis del rekey simultáneo refutada con evidencia del dispositivo
**Completed**: none
---
---
## 2026-08-31 (4) — ClientSSH
**Summary**: El test en dispositivo REFUTÓ mi hipótesis del rekey simultáneo, pero probó algo más fuerte. Con rekeyBytesLimit en 512 MiB el upload no se arregló: se movió. Murió en offset 535822336 = chunk 4088 de 4096, o sea pegado al nuevo umbral (antes: chunk 8180/8192 = 1 GiB, el default). La falla SIGUE al umbral configurado en el cliente ⇒ la rompe el rekey que inicia cbssh, no la simultaneidad con el servidor (si lo fuera, escalonarlo lo habría evitado). Encima esa mitigación empeoraba las cosas (fallaba antes), así que la reverti. Gracias a quality-015 se ve la causa de fondo: "ClosedWriteChannelException: Connection reset by peer" — el servidor manda RST. Intenté sacar el motivo del lado servidor (es esta misma máquina; mapeo de relojes: servidor = teléfono + 4h) pero sshd no deja rastro post-auth de esa sesión. Dos repros nuevos en loopback (rama investigate/rekey-pipelining del fork): pipeline depth 32, y pipeline + kex post-cuántico ML-KEM768 con rekey cada 4 MB — ambos PASAN, o sea ni el pipelining ni ML-KEM bastan; queda la latencia/BDP real como única variable sin replicar. quality-017: rekeyBytesLimit a 8 GiB como workaround, que además sirve de experimento para separar rekey iniciado por el cliente vs. entrante del servidor.
**Verified**: compileDebugKotlin + testDebugUnitTest + ./init.sh BUILD SUCCESSFUL; los 2 repros del fork verificados en los XML de test-results (3.2s y 1.6s, 0 failures)
**Completed**: quality-016 (quality-017 in-progress hasta validar en dispositivo)
---
## 2026-08-31 02:19 — ClientSSH
**Summary**: Causa raíz del corte de uploads en 1 GiB encontrada leyendo los logs de sshd del propio servidor (que resultó ser esta misma máquina): "rekey out/in after 134217728 blocks" = 1 GiB exacto, el mismo byte en que cbssh dispara su rekey (rekeyBytesLimit default). Ambos extremos mandan KEXINIT a la vez → rekey simultáneo → cbssh lanza ProtocolViolationException(responseSent=true), manda DISCONNECT y cierra, y por eso el write al socket falla rápido (TransportException, ~2s) en vez de agotar el timeout de 60s. Quedaron descartadas mis dos hipótesis previas (MAX_REKEY_IN_FLIGHT_PACKETS y el withTimeout propio). Mitigación quality-016: rekeyBytesLimit del cliente a 512 MiB en los 2 sitios de config, que rompe la simultaneidad de forma determinista. Además quality-015: los logs ahora recorren la cadena de causas anidadas. Commit 57729e6d.
**Verified**: compileDebugKotlin + testDebugUnitTest + ./init.sh BUILD SUCCESSFUL; causa raíz corroborada contra los logs reales de sshd (journalctl -u sshd). Falta validar en dispositivo con el upload de 1.4GB
**Completed**: none
---
---
## 2026-08-31 (3) — ClientSSH
**Summary**: CAUSA RAÍZ ENCONTRADA. El log con quality-014 dio "TransportException: Failed to write 32804 bytes" en offset 1072300032 (chunk 8181 de 8192) — un fallo del write al socket TCP, rápido (~2s), no un timeout ni el ProtocolViolationException por MAX_REKEY_IN_FLIGHT_PACKETS que yo suponía (hipótesis descartada). Descubierto que el servidor SFTP del usuario es esta misma máquina (192.168.50.152), así que leí sus logs de sshd: "rekey out/in after 134217728 blocks" = 134217728 × 8 = 1 GiB EXACTO. Es decir OpenSSH pide rekey en el mismo byte que cbssh (rekeyBytesLimit=1 GiB por default) → ambos extremos mandan SSH_MSG_KEXINIT a la vez → rekey simultáneo, y cbssh ante un KEXINIT entrante con kex en progreso lanza ProtocolViolationException(responseSent=true), que manda DISCONNECT y cierra la conexión (por eso el write falla rápido, con el canal ya cerrado localmente). Mitigación (quality-016): rekeyBytesLimit del cliente a 512 MiB en los 2 sitios de SshClientConfig, para romper la simultaneidad de forma determinista (tras cada rekey ambos resetean contadores, así que el servidor nunca llega a su umbral). También quality-015: los helpers de log ahora recorren la cadena de causas, porque cbssh envuelve el error real dentro del TransportException.
**Verified**: compileDebugKotlin + testDebugUnitTest + ./init.sh BUILD SUCCESSFUL. PENDIENTE la verificación decisiva: subir el archivo de 1.4GB en dispositivo real y confirmar que cruza 1 GiB.
**Completed**: quality-015 (quality-016 in-progress hasta validar en dispositivo)
---
## 2026-08-31 02:04 — ClientSSH
**Summary**: El log del dispositivo con quality-013 ubicó el corte con precisión quirúrgica: offset 1072168960 = chunk 8180 de 8192, a 12 chunks (1,5 MiB) exactos de 1 GiB con 24 en vuelo, o sea justo en el borde del rekey de cbssh. Dato nuevo: el kex es mlkem768x25519-sha256 (ML-KEM768 post-cuántico), variable que mi repro de loopback no replicaba y que explica por qué allí pasa. Quedaba un punto ciego: el mensaje lo fabricaba TransferEngine y retryWriteChunk devolvía Boolean, descartando el SftpResult y las excepciones de cbssh; arreglado en quality-014 (devuelve String? con la última causa, incluida en el IOException final). Commit 8f5f9e5d. Falta el próximo log para leer qué devuelve cbssh, o el repro con kex post-cuántico (TODO 291).
**Verified**: compileDebugKotlin + testDebugUnitTest + ./init.sh BUILD SUCCESSFUL; los 3 call sites de retryWriteChunk verificados por grep
**Completed**: none
---
---
## 2026-08-31 (2) — ClientSSH
**Summary**: Llegó el log del dispositivo ya con quality-013: "IoError IOException: Write failed at offset 1072168960 after retries". Ese offset es el chunk 8180 de 8192 (chunks de 128KB), o sea a 12 chunks / 1,5 MiB EXACTOS de 1 GiB con 24 en vuelo → el corte cae en el borde del rekey de cbssh; la ubicación queda confirmada, el mecanismo todavía no. Dato nuevo del log: kex=mlkem768x25519-sha256 [post-quantum] — el rekey es ML-KEM768, con mensajes de KEX bastante más grandes que un DH clásico, variable que mi repro de loopback NO replicaba. Punto ciego que quedaba: ese mensaje lo fabrica el propio TransferEngine (línea 907) y retryWriteChunk devolvía Boolean, tirando a la basura el SftpResult y las excepciones de cbssh. Arreglado en quality-014: retryWriteChunk ahora devuelve String? con la última causa y el IOException final la incluye ("— última causa: ..."), preservando la semántica de totalRetries.
**Verified**: compileDebugKotlin + testDebugUnitTest + ./init.sh BUILD SUCCESSFUL; verificado que los 3 call sites de retryWriteChunk quedaron migrados
**Completed**: quality-014 (investigate-001 sigue in-progress)
