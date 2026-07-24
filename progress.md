## 2026-07-23 23:30 — ClientSSH
**Summary**: Sesión completa: cerró la migración SFTP sshj→cbssh de punta a punta. Arrancó terminando gaps de auth (host-key TOFU real + key-based auth wiring, con fix de detección Ed25519 en Android/Conscrypt via OID-sniffing). Diagnosticó y arregló bug de UX en copy-paste (progreso falso '0 B/1 B'). Diagnosticó y arregló performance real: upload sin pipelining (100% serial), circuit breaker de RTT en TransferEngine (bug de auto-congestión sin backoff), y tuning de maxPipelineDepth 32→16 — confirmado en dispositivo real (+77% descarga, +377% subida). Con evidencia funcional+performance suficiente, el usuario confirmó explícitamente eliminar sshj por completo: borrado SftpClient.kt, SftpClientFactory.kt, preference useCbsshSftp, dependencia gradle — SftpClient2 (cbssh) es ahora el único backend SFTP. Cerró Fase 7 (housekeeping): archivó migrate-sftp-to-cbssh y refactor-sftp-client-sshj en openspec, actualizó docs/MIGRATION_TO_CBSSH.md y contrib/cbssh-sftp/README.md, agregó entrada cbssh-002 en feature_list.json.
**Verified**: ./init.sh verde en cada commit (92/92 unit tests finales, bajó de 95 tras remover tests de la bifurcación sshj/cbssh que ya no aplica). assembleDebug compila. APK debug: 56.07MB→52.07MB (-4.0MB). Verificado en dispositivo real (Xiaomi 25069PTEBG, Android 16): auth por password y por llave, host-key TOFU, ls, download, upload, copy server-side — todo funcionando. Mejora de throughput confirmada en logs reales de dos rondas de retest (+77% descarga, +377% subida). openspec validate pasó para cbssh-migration-strategy; migrate-sftp-to-cbssh y refactor-sftp-client-sshj archivados con --skip-specs (legacy, sin formato de deltas). 8 commits pusheados a contrib/cbssh-sftp.
**Completed**: none
---
---
## 2026-07-22 15:45 — ClientSSH
**Summary**: Agregué DebugLogger tracing detallado (tags 'SftpHostKey' y 'SftpKeyAuth') a todo el flujo nuevo de host-key-verification y key-based-auth de SftpClient2/SftpViewModel, aprovechando la infraestructura de logging ya existente en la app (FileLoggingTree siempre activo + pantalla Settings→Debug Logs con Copy/Share). No hizo falta construir infra nueva. Pusheado 2 commits nuevos (a98905a1 el feature, 8df90084 el logging) a contrib/cbssh-sftp. Le expliqué al usuario cómo probar manualmente: activar 'Use cbssh SFTP (experimental)' en Settings, conectar a un host, y usar Settings→Debug Logs para copiarme el resultado.
**Verified**: ./init.sh verde (90 tests sin cambios), assembleDebug exitoso, compileDebugKotlin limpio tras agregar los imports/logs.
**Completed**: none
---
---
## 2026-07-22 15:35 — ClientSSH
**Summary**: Apliqué el openspec change 'cbssh-migration-strategy' (creado en el turno anterior): implementé los 2 fixes de auth que había identificado como TODOs. (1) Host key verification real: DarkSshHostKeyVerifier.kt implementa la interfaz suspend HostKeyVerifier de cbssh contra KnownHostRepository con la misma semántica TOFU que SSH.kt (prompt+persist en key desconocida, reject silencioso en mismatch), reemplazando el hardcoded 'return true'. Extraje SshFingerprint.kt compartido entre terminal y SFTP. SftpClient2 ahora recibe knownHostRepository+onUnknownHostKey opcionales con fail-closed si faltan. SftpViewModel expone HostKeyPrompt vía StateFlow+CompletableDeferred y SftpScreen renderiza el dialog de confirmación. (2) Key-based auth wiring: SftpViewModel ahora resuelve Host.pubkeyId → Pubkey → KeyPair (con passphrase prompt propio KeyPassphrasePrompt si está encriptada, cache en memoria tipo TerminalService.loadedKeypairs) y llama connectWithKey en vez de siempre password — wireado en initialize/tryReconnectIfNeeded/dismissAuthError, nunca cae a password para un host con key asignada (igual que SSH.kt). (3) Cleanup: eliminé transport/cbssh/PENDING/ (leftover obsoleto). Agregué DarkSshHostKeyVerifierTest.kt (4 tests, mocks, sin Docker) cubriendo los 4 escenarios TOFU. tasks.md del change actualizado a 15/21 (los 6 pendientes son Docker E2E tests + verificación manual + sync de otro change, registrados como TODOs de proyecto). 1 commit local en contrib/cbssh-sftp (NO pusheado, no se pidió explícitamente).
**Verified**: ./init.sh verde: 90 tests (86 previos + 4 nuevos), 0 failures, sin Docker. ./gradlew integrationTest verde: los 7 Docker tests preexistentes siguen pasando sin regresión. assembleDebug exitoso. openspec validate cbssh-migration-strategy pasa. git status limpio tras commit.
**Completed**: none
---
---
## 2026-07-22 12:01 — ClientSSH
**Summary**: Dos entregables: (1) Integration tests Docker: implementados 7 tests con Testcontainers+linuxserver/openssh-server que ejercen cbssh (SftpClient2/TransferEngine) end-to-end contra un servidor SSH real — connect/auth (correcto y rechazo), listdir, mkdir, upload+download 200KB con integridad byte-a-byte, rename, remove. Aislados vía JUnit4 @Category para no requerir Docker en ./init.sh normal (86 tests siguen igual); corren solo con ./gradlew integrationTest. Resuelto un problema real: docker-java necesita la system property Java 'api.version' (no env var DOCKER_API_VERSION) para negociar con Docker Engine 28+/29+ que rechaza la versión 1.32 default. (2) Investigación de auth: confirmado que 'sshlib' (Trilead, terminal SSH.kt) y 'cbssh' (SftpClient2) son librerías DIFERENTES — el terminal no está migrado a cbssh. Auth de SFTP (sshj legacy Y cbssh nuevo) comparten las mismas limitaciones hoy vs el terminal: host key verification siempre true (sin KnownHostRepository), y connectWithKey es código muerto (SftpViewModel siempre usa password). Conclusión: eliminar sshj es seguro en auth — cbssh ya iguala el comportamiento actual de sshj — pero se registraron 2 TODOs de gaps preexistentes (independientes de la migración) para dar paridad real con el terminal. 1 commit, 86 tests sin cambios + 7 tests Docker nuevos (opt-in), ./init.sh verde, assembleDebug exitoso.
**Verified**: ./init.sh verde, 86 tests, 0 failures (sin Docker). ./gradlew integrationTest: 7/7 passing contra Docker Engine 29.6.1 real, sin contenedores huérfanos tras la corrida. assembleDebug exitoso.
**Completed**: none
---
---
## 2026-07-22 11:43 — ClientSSH
**Summary**: Camino C completado: 22 tests end-to-end nuevos en 1 commit cubriendo TransferEngine.download() y .upload() (los pipelines completos de lectura/escritura, no solo helpers aislados). Testeados vía sus wrappers públicos con SftpClient mockeado — sin necesitar accesores internal porque download/downloadToStream/upload ya son suspend fun públicas (a diferencia de withRetry/retryChunk que eran private en sesiones previas). Cobertura: happy path single/multi-chunk, fallos de stat/open, recovery via retry en chunk individual, agotamiento de retries, cierre de handle, resume-past-EOF short-circuit, flags CREATE+TRUNCATE vs WRITE-only según resumeFrom. Cero cambios a src/main — commit 100% de tests. Aprendizaje documentado sobre mockito-kotlin y parámetros default en funciones suspend (InvalidUseOfMatchersException si falta un matcher por parámetro real, incluso los con default value). Total acumulado en la rama: 86 tests, 0 failures (era 64).
**Verified**: ./init.sh verde. 86 tests, 0 failures (era 64, +22). assembleDebug exitoso. git status confirmó cero cambios en src/main.
**Completed**: none
---
---
## 2026-07-22 11:37 — ClientSSH
**Summary**: Camino B completado: refactor estructural pre-requisito para eliminar sshj. Extraídos SftpEntry y SftpAuthState de SftpClient.kt (sshj legacy) a SftpTypes.kt propio, mismo patrón que TransferProgress.kt. Move verbatim sin cambios de lógica, mismo package = cero cambios de import en los 4 consumidores (ISftpClient, SftpClient2, SftpViewModel, SftpScreen, SftpClipboard). También verificado manualmente que ISftpClient (17 miembros) está 100% implementado por ambos clientes sin gaps, confirmando que no falta portar funcionalidad legacy antes de poder eliminar sshj. No se agregaron tests para los tipos extraídos por ser value types puros sin lógica derivada (mismo criterio que TransferResult). 1 commit, 64 tests sin cambios (0 failures), ./init.sh verde, assembleDebug y assembleRelease compilan.
**Verified**: ./init.sh verde. 64 tests, 0 failures (sin cambios respecto a antes, refactor puro). assembleDebug y assembleRelease ambos exitosos.
**Completed**: none
---
---
## 2026-07-22 08:24 — ClientSSH
**Summary**: Camino A completado: 22 tests nuevos en 2 commits (retryChunk: 7 tests, mapResult: 6 tests, más ajuste de plan que descartó TransferResult por ser sealed class sin lógica). Total acumulado en la rama contrib/cbssh-sftp: 64 tests, 0 failures (era 42 al inicio de la sesión anterior). Aprendizajes clave documentados: SftpFileHandle (constructor internal en cbssh) se puede mockear con Mockito aunque no instanciar directamente; funs top-level internal colisionan por firma entre archivos del mismo package (a diferencia de private que es file-scoped) — encontrado con toException() duplicado, resuelto dejando la copia como private y testeando vía mapResult (misma lógica de ramas). Todos los cambios de producción son mínimos: accesores internal o cambios de visibilidad, cero refactors de lógica.
**Verified**: ./init.sh verde en cada commit. Tests: 42→51→58→64, 0 failures en todo momento. APK debug compila en cada paso.
**Completed**: none
---
---
## 2026-07-22 03:15 — ClientSSH
**Summary**: Tests de TransferEngine.withRetry: 9 tests nuevos cubriendo clasificación de errores (Success/ServerError/ProtocolError/IoError), retry vs no-retry, maxRetries, exponential backoff, CancellationException propagation. Mismo patrón que sesión anterior: accesor internal (withRetryForTest) hace visible la función privada solo para tests del mismo módulo, sin cambiar API pública ni lógica de producción. Verificación: 51 tests total, 0 failures (era 42), ./init.sh verde, APK debug compila sin warnings. Total acumulado en este branch: 4 commits agregando tests, 51 tests, 0 regressions.
**Verified**: ./init.sh pasó completo. 51 tests, 0 failures. Antes: 42 tests. +9 tests nuevos todos verdes.
**Completed**: none
---
---
## 2026-07-22 03:03 — ClientSSH
**Summary**: Tests del pipeline adaptativo de TransferEngine: 9 tests nuevos cubriendo EMA (exponential moving average), adaptación de profundidad basada en RTT buckets (<20, <50, <100, <200, else), bounds clamping (min/max), y comportamiento gradual (±1 por sample). Para hacer state interno observable desde tests, agregué 3 accesores 'internal' (currentPipelineDepthForTest, avgRttMsForTest, updateRttForTest) — internal es invisible a callers externos pero visible desde tests del mismo módulo. Cero cambios de lógica de producción. Verificación: 42 tests, 0 failures (era 33), ./init.sh verde, APK debug compila sin warnings. Pre-requisito cumplido para eliminar sshj legacy en siguientes sesiones.
**Verified**: ./init.sh pasó completo. 42 tests, 0 failures, 2.3s. Antes: 33 tests, 0 failures. +9 tests nuevos todos verdes.
**Completed**: none
---
---
## 2026-07-22 02:56 — ClientSSH
**Summary**: Refactor: extraje TransferProgress de SftpClient.kt (legacy sshj) a su propio archivo TransferProgress.kt en el package com.darkssh.client.transport. Agregué 15 tests unitarios (TransferProgressTest) cubriendo percentage, elapsedSeconds, speed, speedFormatted con edge cases (zero total, backwards clock, etc.). Todos los call sites (7 archivos) ya importaban desde el package correcto, así que el refactor fue transparente sin tocar imports. Verificación: 33 tests total (era 18), 0 failures, ./init.sh verde, compilación limpia sin warnings. Pre-requisito para eventual eliminación de SftpClient.kt sshj legacy.
**Verified**: ./init.sh pasó completo. 33 tests, 0 failures, 1.8s. Antes: 18 tests, 0 failures. +15 tests nuevos todos verdes.
**Completed**: none
---
---
## 2026-07-22 02:51 — ClientSSH
**Summary**: Fix aplicado: TransferState.isComplete retorna false correctamente para archivos vacíos (totalBytes=0). Cambio mínimo 1 línea + 4 comentarios explicativos. Verificado: test rojo→verde, ./init.sh pasa completo, 18 tests 0 failures (1.7s), grep confirma no hay call sites de producción afectados (TransferState es interno, UI usa TransferInfo.isComplete basado en status, no bytes). Bug expost por TransferStateTest que escribí en sesión anterior (test rojo intencional). Commit f8d2b830 pusheado.
**Verified**: ./init.sh pasó completo. 18 tests, 0 failures, 1.7s. Antes: 17 tests, 1 failure.
**Completed**: none
---
---
## 2026-07-22 02:39 — ClientSSH
**Summary**: Reporte formal de migración sshj→cbssh según estándar harness engineering. Resultado: PROBLEMÁTICO. Agregué 8 nuevos tests (TransferConfigTest x2, TransferStateTest x4, SftpClientFactoryTest x3) — 7 pasan, 1 falla porque expone un bug pre-existente en TransferState.isComplete (returns true cuando totalBytes=0). 17 tests totales, 16 passing. Hallazgos críticos: (H1) NO tests para SFTP existente, (H2) NO JaCoCo configurado, (H3) NO benchmark, (H4) bug isComplete para archivos vacíos, (H5-H7) deuda en eliminacion sshj/CbsshTransfer. Cobertura efectiva estimada <1% del módulo SFTP. 8 gaps explícitos listados. Esperando decisión del usuario sobre: fix del bug H4, agregar JaCoCo, y scope de próximos tests.
**Verified**: ./gradlew testDebugUnitTest ejecutado: 17 tests, 1 failing (bug expuesto por test). Antes: 9 tests, 0 failures.
**Completed**: none
---
---
## 2026-07-19 19:17 — ClientSSH
**Summary**: Sesión de INVESTIGACIÓN/VALIDACIÓN antes de migración cbssh + extensions SFTP modernas. SIN código de producción modificado. Validado: (1) cbssh 0.3.2-SNAPSHOT compila, (2) Docker integration tests PASAN contra OpenSSH 9.9p2, (3) OpenSSH 10.3 local anuncia todas las extensions (posix-rename, statvfs, fsync, hardlink, limits, copy-data, etc), (4) APK debug compila, (5) SSH key auth a localhost funciona. BLOQUEADOR identificado: unit test pre-existente SshClientTest.openSftp maps... falla en cbssh main - debe arreglarse ANTES de cualquier PR. Reglas estrictas registradas: ningún PR sin prueba real, ningún adoption sin soak test, toda extension nueva debe tener integration test Docker.
**Verified**: cbssh integration tests passed (Docker), OpenSSH 10.3 extensions confirmed via sftp -vvv, DarkSSH ./init.sh passed
**Completed**: none
---
---
## 2026-07-19 13:00 — ClientSSH
**Summary**: Sesión de INVESTIGACIÓN previa a migración cbssh + extensions modernas. VALIDACIONES COMPLETADAS:
1. ✅ cbssh 0.3.2-SNAPSHOT compila limpio
2. ✅ cbssh Docker integration tests PASAN contra OpenSSH 9.9p2
3. ⚠️ BLOQUEADOR: 1 unit test pre-existente falla en cbssh main (SshClientTest.openSftp maps...) - NO relacionado con mi trabajo
4. ✅ OpenSSH 10.3 local anuncia extensions: posix-rename, statvfs, fstatvfs, hardlink, fsync, lsetstat, limits, expand-path, copy-data, users-groups-by-id
5. ✅ APK debug compila (55MB)
6. ✅ SSH key-based auth a localhost funciona

**Verified**: ./init.sh passed (DarkSSH tests), cbssh integration tests passed (Docker), OpenSSH 10.3 extensions confirmed via sftp -vvv
**Next**: REGLAS ESTRICTAS: (a) Ningún PR a cbssh sin antes arreglar test unit roto, (b) Toda extension nueva debe tener test integración con Docker, (c) Toda adoption en DarkSSH debe probarse con app real contra localhost antes de commit, (d) Soak test 24h antes de cualquier cambio default useCbsshSftp=true

**Bloqueador para arrancar**: Arreglar SshClientTest línea 386 (test unit cbssh)

## 2026-07-19 07:03 — ClientSSH
**Summary**: Sesión productiva con 3 fixes/features completados: (1) Integración de TransferEngine en SftpClient2 - ahora cbssh usa el nuevo motor de transferencia con adaptive pipeline, retry, timeouts. (2) Fix bug Ctrl+Shift+V que enviaba paste a todos los tabs en vez del activo. (3) Fix bug scroll vertical en terminal que cambiaba tabs accidentalmente - agregado requestDisallowInterceptTouchEvent en TerminalView.onScroll. (4) Pinch zoom ahora cambia font size en vez de no hacer nada - conectado onScale callback con increaseFontSize/decreaseFontSize del bridge.
**Verified**: ./init.sh passed - all tests green
**Completed**: none
---
---
## 2026-07-19 06:27 — ClientSSH
**Summary**: Sesión cbssh SFTP: (1) Arreglé errores de compilación post-rebase (ISftpClient interface, SftpClient/SftpClient2 signatures, ConsoleScreen duplicate param). (2) Fix bug-013: cancellation en cbssh - agregué CancellationException handling y ensureActive() en todos los loops de CbsshTransfer. (3) Creé TransferEngine.kt - nuevo motor Kotlin-first con adaptive pipeline (2-32 basado en RTT), retry con exponential backoff, timeouts por operación, resume support, y Flow-based progress. NO integrado aún en SftpClient2. Próximos pasos: integrar TransferEngine, testear cbssh con switch ON, investigar bug de paste yendo a tab incorrecto.
**Verified**: ./init.sh BUILD SUCCESSFUL
**Completed**: none
---
---
## 2026-07-18 20:29 — ClientSSH
**Summary**: Refactor completo del código de tabs (refactor-002). Bugs corregidos: (1) Race condition en TabManager.closeTab() - ahora usa snapshot de tabs antes de modificaciones, (2) Flag shouldSwitchToNewTab no era thread-safe - ahora protegido por Mutex, (3) closeTab en TabbedMainScreen no esperaba cleanup - ahora usa try-finally y await. Mejoras: (1) Consolidé dos LaunchedEffects de bridge management en uno solo, (2) Extraje magic numbers a TabBarDefaults object, (3) Removí código muerto (widthFraction animation nunca usada), (4) Limpié imports FQN. También identifiqué bugs pendientes para futuro: SFTP activeClients usa hostId en vez de tabId (refactor-001 existente), y reorderTabs no tenía bounds checking (corregido).
**Verified**: ./init.sh BUILD SUCCESSFUL (29 tasks)
**Completed**: none
---
---
## 2026-07-18 06:34 — ClientSSH
**Summary**: Implementado fix para conflicto de gestos scroll/swipe en terminal con múltiples tabs (ux-001). Problema: al hacer scroll vertical en el historial de la terminal, el movimiento natural del dedo tiene un componente horizontal mínimo que HorizontalPager interceptaba, causando cambios de tab accidentales durante el scroll. Solución: NestedScrollConnection en Terminal.kt que intercepta gestos en onPreScroll() y consume el componente horizontal cuando el gesto es predominantemente vertical (Y > X * 1.2). El scroll vertical ahora funciona limpio; swipes horizontales intencionales siguen cambiando tabs. También documenté mejoras futuras identificadas durante el análisis: drag-and-drop para reordenar tabs, restauración de sesiones al reiniciar app, y modernización del manejo de teclado (SHOW_FORCED deprecado).
**Verified**: ./init.sh BUILD SUCCESSFUL (29 tasks)
**Completed**: none
---
---
## 2026-07-17 04:48 — ClientSSH
**Summary**: Corregido bug-012 en su segunda iteración: el usuario confirmó que el fix anterior (blacklist de 6 caracteres invisibles) NO funcionó en su dispositivo real - mismo error DNS persistió incluso con el código nuevo corriendo (confirmado por line-number shift en el stacktrace). Solo funcionó al borrar y recrear el host. Root-caused sin acceso al dispositivo mediante un test Java standalone (javac/java) contra la lógica real de InetAddress.getAllByName(): confirmé que 17 caracteres invisibles distintos (no solo los 6 originales) producen el mismo fallo, probando que cualquier blacklist manual es insuficiente por diseño. Reescribí TextSanitizer.kt con un enfoque de categoría Unicode + whitelist de charset: sanitize() (liviano, preserva espacios legítimos, usado en nickname/username) y sanitizeStrict() (whitelist estricta [A-Za-z0-9.-:_[]], usado solo en hostname). Verificado con el mismo harness de test: sanitizeStrict() corrige el 100% de los 17 casos (vs 6/17 antes). Compilación limpia + ./init.sh OK. Commit 08404c85.
**Verified**: ./gradlew clean assembleDebug BUILD SUCCESSFUL (44/44 fresh) + ./init.sh BUILD SUCCESSFUL. Verificación adicional rigurosa fuera del build normal: test standalone javac/java (17 casos de caracteres invisibles distintos insertados alrededor de una IP válida) confirmando 0 fallos con sanitizeStrict() vs 11 fallos con el enfoque de blacklist anterior. NO reconfirmado en el dispositivo Xiaomi real del usuario porque ya borró el único host corrupto que tenía disponible para reproducir - la confianza en este fix viene de testing exhaustivo local del modo de fallo real, no de un solo reintento anecdótico en el device.
**Completed**: none
---
---
## 2026-07-17 04:24 — ClientSSH
**Summary**: Diagnosticado y corregido bug-012: conexión SSH a IP válida (192.168.50.45) fallaba con error de DNS en Xiaomi Redmi Note 8 Pro. Causa raíz identificada por análisis del log darkssh_20260716_233139.log: InetAddress.getAllByName() solo evita DNS si el string matchea EXACTAMENTE su parser estricto de IPv4 literal; ningún caracter extra invisible (espacio, NBSP, zero-width space) rompía ese match y forzaba un lookup DNS real que fallaba. Ningún .trim()/sanitización existía en el flujo de guardado de Host ni en SSH.kt. Fix: nuevo util/TextSanitizer.kt (trim + strip de caracteres Unicode invisibles, escaneando todo el string ya que Character.isWhitespace() excluye NBSP deliberadamente) aplicado en HostEditorViewModel.saveHost() (save-time, limpia la DB a futuro) y en SSH.kt connect()/authenticate() (use-time, desbloquea inmediatamente el host ya roto del usuario sin que tenga que reescribirlo). También se commiteó el trabajo acumulado de sesiones previas sin commitear (bug-010 bracketed paste, bug-011 key-based auth E2E, fixes de teclado/mouse-tracking en TUIs) en un solo commit combinado (6b3b3621) dado que SSH.kt mezclaba cambios de bug-011 y bug-012 no separables limpiamente por hunks.
**Verified**: ./gradlew clean assembleDebug BUILD SUCCESSFUL (44/44 tareas frescas) + ./init.sh BUILD SUCCESSFUL, dos veces (antes y después del commit). Compilación limpia sin warnings nuevos relevantes. NO verificado en el dispositivo Xiaomi real del usuario (no había adb access en esta sesión, solo se proveyó el archivo de log) - pendiente que el usuario reinstale el APK y reintente conectar a 'arch' sin reeditar el host.
**Completed**: none
---
---
## 2026-07-16 00:59 — ClientSSH
**Summary**: Implementado bug-011: key-based SSH login gestionado end-to-end desde la app. 4 fixes usando connectbot/ como referencia: (1) dropdown roto de SSH Key en Host Editor (ExposedDropdownMenuBox + PrimaryNotEditable), (2) host.pubkeyId ahora se usa realmente en SSH.kt::authenticate() en vez de ignorarse, (3) export de clave pública a clipboard vía PublicKeyUtils.toAuthorizedKeysFormat (sshlib), (4) agregada entrada real 'SSH Keys' en Settings ya que PubkeyListScreen/GeneratePubkeyScreen eran inalcanzables (NavGraph.kt es código muerto, MainActivity usa MainScreen.kt). Clean build 44/44 + ./init.sh OK.
**Verified**: ./gradlew clean assembleDebug BUILD SUCCESSFUL (44/44 fresh) + ./init.sh BUILD SUCCESSFUL; verificado en device real conectado vía adb (127.0.0.1:15555), DB inspeccionada en cada paso
**Completed**: none
---
---
## 2026-07-14 23:23 — ClientSSH
**Summary**: Implementado bug-010: bracketed paste fix en TerminalView.java (commitText detecta paste real via codePointCount>1, no length<=1, evitando falso positivo con emoji) y arreglado onPasteTextFromClipboard() en TerminalBridge.kt que era stub vacío (long-press Paste no funcionaba). BUILD SUCCESSFUL, sin tests unitarios en el proyecto (verificación = compilación).
**Verified**: ./init.sh -> BUILD SUCCESSFUL (compilación Kotlin+Java OK, no hay unit tests en el proyecto)
**Completed**: none
---
---
## 2026-07-10 05:02 — ClientSSH
**Summary**: Análisis puro cbssh vs sshj: 8 gaps identificados (~20h trabajo). Lo implementado en cbssh irá como PR a connectbot/cbssh. De vuelta en feature/ui-modernization-vivaldi-tabs.
**Verified**: git checkout feature/ui-modernization-vivaldi-tabs OK
**Completed**: none
---
---
## 2026-07-10 04:45 — ClientSSH
**Summary**: Revisión rama contrib/cbssh-sftp: Fases 1-4 completadas (CbsshTransfer, SftpClient2, SCP fallback, feature flag). Gap principal: rmdir solo borra dirs vacíos, falta deleteDirectoryViaSsh en ISftpClient+SftpClient2. Pipeline depth: 32 → 8 revertido por timeouts en VPN. tasks.md desactualizado.
**Verified**: BUILD SUCCESSFUL en cbssh-sftp
**Completed**: none
---
---
## 2026-07-10 04:30 — ClientSSH
**Summary**: Fix triple bug SSH: (1) crash al cerrar tab durante conexión: isClosed guard en TerminalBridge, Relay e SSH.connect() chequean bridge.isClosed antes de dispatchDisconnect, onBridgeDisconnected siempre en serviceScope(Main); (2) icono OS tarda: TabBar usa tab.osType como fallback inmediato; (3) perf: onConnected salta OS detection si osType ya cached.
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-08 21:21 — ClientSSH
**Summary**: Fix crash Hilt NoClassDefFoundError SingletonCImpl: causa era clases stale en transformDebugClassesWithAsm del 5 julio, hiltJavaCompileDebug ya era del 8 julio. Gradle no re-transformó al fallar/corregir la compilación Hilt. gradlew clean + assembleDebug resolvió. APK 52MB listo en outputs/apk/debug.
**Verified**: BUILD SUCCESSFUL, SingletonCImpl en DEX verificado con unzip+strings, timestamps consistentes post-clean
**Completed**: none
---
---
## 2026-07-08 21:12 — ClientSSH
**Summary**: Tres bugs corregidos: (1) rename host actualiza tabs abiertos via SharedFlow savedHost + TabManager.updateTabsForHost, (2) icono SFTP reemplazado por candy-icons/folder-remote (era un SVG complejo que no se veía), (3) prefijo 'SFTP:' redundante eliminado. Hilt prohíbe inyectar @HiltViewModel en otro — solucionado con callback via UI layer.
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-08 06:09 — ClientSSH
**Summary**: Fix crítico: close-others/close-all dejaban conexiones SSH/SFTP huérfanas. Extraído closeTab() como función privada con cleanup completo. TabManager.closeOtherTabs/closeAllTabs eliminados — cleanup no pertenece al ViewModel.
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-08 06:01 — ClientSSH
**Summary**: Removed X close button from tabs - close via long-press menu only. Tab expandida: icono + texto limpio. Tab colapsada: solo icono + dot. Long-press → Close / Close others / Close all.
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-08 05:52 — ClientSSH
**Summary**: Fix long-press (Tab() consume gestos, reemplazado por Box+combinedClickable) + Vivaldi collapse: >3 tabs, selected ancha (120-180dp icono+texto+close), demás colapsadas (44dp solo icono+dot). animateFloatAsState 200ms, auto-scroll al seleccionado, dividers, secondaryContainer background.
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-08 05:37 — ClientSSH
**Summary**: Mejoras UX en tabs: (1) ConnectionDot por tab SSH con verde/rojo/pulsando según estado de conexión, (2) Long-press menu con Close/Close others/Close all, (3) closeOtherTabs y closeAllTabs en TabManager, (4) Empty state con botón New connection, (5) Tab height 52dp.
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
