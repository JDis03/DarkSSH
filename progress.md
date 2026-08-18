## 2026-08-18 11:52 — ClientSSH
**Summary**: Implementado quality-004 (resume real): download resume ya no trunca el local — openResumableOutputStream() con RandomAccessFile (setLength recorta cola basura + seek a resumeFrom, fallo claro si el local no existe). Upload resume: skipFully() salta exactamente N bytes (EOFException si el local es más corto) + validación stat del remoto cuando resumeFrom>0 (Failed sin abrir handle si el remoto es más chico que el punto de reanudación — evita gap de ceros). 6 tests nuevos/actualizados: 3 de download resume (prefix preservado, cola recortada, local inexistente → handle cerrado) y 3 de upload (flags WRITE-only, write desde offset 20, remoto más chico → Failed sin open). El test existente de flags WRITE-only se actualizó con stub de stat (contrato nuevo del fix).
**Verified**: testDebugUnitTest BUILD SUCCESSFUL + ./init.sh BUILD SUCCESSFUL + assembleDebug OK
**Completed**: quality-004
---
---
## 2026-08-18 11:43 — ClientSSH
**Summary**: Implementado quality-003: TransferEngine ya no traga la cancelación (downloadChunksToStream/uploadInternal re-lanzan CancellationException en vez de devolver TransferResult.Cancelled), respetando structured concurrency. Verificado antes que SftpViewModel captura la cancelación en su frontera en downloads (759, limpia notificación) y uploads (1166) — sin regresiones, y de bonus arregla la notificación fantasma al cancelar descargas. Commit 13a561d3 pusheado.
**Verified**: ./init.sh BUILD SUCCESSFUL + testDebugUnitTest OK (tests de propagación de cancelación verdes) + assembleDebug OK
**Completed**: none
---
---
## 2026-08-18 08:39 — ClientSSH
**Summary**: Implementado quality-002 con verificación previa de la causa real: las descargas ya no destruyen la copia local si el remoto falla. TransferEngine valida stat+open antes de truncar (helpers statRemoteForDownload/openRemoteForDownload, loop extraído a downloadChunksToStream), check resume>=total antes del open (contrato de tests), caso 0-byte preservado. CbsshTransfer: preflight stat+open+close. Los 2 tests rotos se diagnosticaron con evidencia (NoWhenBranchMatchedException + verify never open), no por asunción. Commit f1e7ba6f pusheado.
**Verified**: TransferEngineDownloadTest 11/11 + ./init.sh BUILD SUCCESSFUL + assembleDebug OK
**Completed**: none
---
---
## 2026-08-18 09:30 — ClientSSH
**Summary**: Implementado quality-003 (structured concurrency): downloadChunksToStream y uploadInternal ya NO convierten la cancelación en TransferResult.Cancelled — ahora re-lanzan CancellationException (los finally cierran handle/stream). Verificado previamente que SftpViewModel captura CancellationException en su frontera en AMBOS flujos (download:759 limpia la notificación; upload:1166), así que el rethrow no rompe la UI y de paso arregla la notificación fantasma que quedaba colgada al cancelar una descarga. SftpClient2 ya re-lanzaba bien (828). Los 3 compat mantienen el branch Cancelled como defensivo (when exhaustivo, inalcanzable). Tests de propagación existentes (TransferEngineRetryTest:159, RetryChunkTest:144) siguen verdes.
**Verified**: ./init.sh BUILD SUCCESSFUL + testDebugUnitTest OK + assembleDebug OK
**Completed**: quality-003
---
---
## 2026-08-18 09:00 — ClientSSH
**Summary**: Implementado quality-002 (protección de datos): las descargas ya NO destruyen la copia local cuando el remoto falla. TransferEngine: downloadInternal/downloadToStreamInternal ahora validan el remoto (statRemoteForDownload + openRemoteForDownload) ANTES de abrir/truncar el archivo local; el loop de chunks se extrajo a downloadChunksToStream sin cambios de lógica. El check resumeFrom>=totalBytes quedó ANTES del open (contrato de TransferEngineDownloadTest: verify never open). Caso 0-byte preservado (crea archivo local vacío). CbsshTransfer.download: preflight stat+open+close antes de truncar. Ojo crítico: los 2 tests rotos se verificaron con evidencia real (NoWhenBranchMatchedException por open sin stub + verify(never).open) antes de corregir — no se asumió.
**Verified**: TransferEngineDownloadTest 11/11 + ./init.sh BUILD SUCCESSFUL + assembleDebug OK
**Completed**: quality-002
---
---
## 2026-08-18 08:25 — ClientSSH
**Summary**: Review de robustez del transporte cbssh (TransferEngine/CbsshTransfer/SftpClient2): 24 hallazgos, 6 críticos. Implementado quality-001: TimeoutCancellationException ahora se captura antes de CancellationException en los 5 catch sites de TransferEngine, reactivando el retry-on-timeout (antes los stalls de red se reportaban como cancelación). Commit e807dc6a pusheado. Registrados TODOs quality-002..006 para el resto (truncar-antes-de-validar, cancellation swallow, resume roto, Flow hang, off-by-one uploadParallel).
**Verified**: ./init.sh BUILD SUCCESSFUL + compileDebugKotlin OK; sin tests de timeout (bloqueo infra Docker, TODOs 225/226)
**Completed**: none
---
---
## 2026-08-18 — ClientSSH
**Summary**: quality-001 (review de robustez del transporte cbssh): review completo de TransferEngine.kt/CbsshTransfer.kt/SftpClient2.kt identificó 24 hallazgos (6 críticos). El más impactante del path activo: en TransferEngine.kt los 5 catch sites tenían catch(CancellationException){throw e} ANTES de manejar timeouts, así que un TimeoutCancellationException de withTimeout (que extiende CancellationException) escapaba como 'cancelación de usuario' — el retry-on-timeout nunca corría y los stalls de red se reportaban como cancelados. Fix: catch(TimeoutCancellationException) explícito antes en download loop, upload loop, withRetry, retryChunk y retryWriteChunk; la cancelación real sigue re-lanzándose. Se registraron TODOs para los demás hallazgos: quality-002 (truncar local antes de validar remoto), quality-003 (CancellationException convertida a Cancelled), quality-004 (resume roto), quality-005 (Flow con channel nunca cerrado), quality-006 (off-by-one en CbsshTransfer.uploadParallel).
**Verified**: ./init.sh BUILD SUCCESSFUL + compileDebugKotlin OK. Sin tests dedicados para timeouts (mismo bloqueo de infra Docker de TODOs 225/226).
**Completed**: quality-001
---
---
## 2026-08-18 03:28 — ClientSSH
**Summary**: Fix bug-019: copy+paste en la misma carpeta fallaba (dest==src, `cp 'f' 'f'` rechazado). Ahora COPY auto-renombra a 'nombre (copia).ext'/'(copia N)' verificando colisiones con client.exists(), y CUT en la misma ubicación es no-op con mensaje claro. Commit d535bcdf pusheado a contrib/cbssh-sftp. También se reportó el estado de los PRs: el PR de copy-data a connectbot/cbssh sigue bloqueado por testing en dispositivo real (TODO 204/227).
**Verified**: ./init.sh BUILD SUCCESSFUL (unit tests) + ./gradlew assembleDebug exit 0; no verificado en dispositivo físico
**Completed**: none
---
---
## 2026-08-18 — ClientSSH
**Summary**: Fix bug-019: copiar un archivo y pegarlo en la misma carpeta fallaba porque pasteFiles() construía destPath idéntico al sourcePath, ejecutando `cp 'f' 'f'` (o copy-data src==dst) que el servidor rechaza. En SftpViewModel.pasteFiles(): COPY con destino==origen ahora genera un nombre único con el helper copyName() ('archivo (copia).ext', '(copia 2)', ...) verificando colisiones con client.exists() antes de copiar; CUT con mismo origen/destino se salta como no-op y si todo fue skip muestra 'Origen y destino son el mismo — nada que mover'. Dotfiles no se parten por extensión ('.env' -> '.env (copia)'). Agregada entrada bug-019 en feature_list.json, decisión y aprendizaje en memoria.
**Verified**: ./init.sh BUILD SUCCESSFUL (unit tests verdes) y ./gradlew assembleDebug exit 0. NO verificado aún en dispositivo físico — recomendado reintentar copiar+pegar en la misma carpeta (debería aparecer 'archivo (copia)') y cortar+pegar en la misma carpeta (no-op).
**Completed**: bug-019 Copy+Paste Into Same Folder Fails
---
---
## 2026-07-31 10:35 — ClientSSH
**Summary**: bug-018 second iteration: user's retry still failed; a second device log showed the previous session's mutex fix was actively counterproductive (blocked independent deletes 28+ seconds with no completion, while unrelated ls calls proved the connection was healthy). Corrected the diagnosis: the real problem is fully-sequential recursive delete taking 20-40+ seconds for nested folders, indistinguishable from a hang. Removed the mutex, pipelined deleteDirectoryRecursive with a bounded Semaphore (depth 8), matching TransferEngine's existing pattern. Verified with ./init.sh and assembleDebug, committed 7dba994e to contrib/cbssh-sftp.
**Verified**: ran init.sh BUILD SUCCESSFUL and gradlew assembleDebug BUILD SUCCESSFUL
**Completed**: none
---
---
## 2026-07-31 10:34 — ClientSSH
**Summary**: bug-018 segunda iteración: el usuario reportó que seguía sin funcionar y compartió un SEGUNDO log de dispositivo real que reveló que mi diagnóstico anterior (bug de concurrencia entre deletes independientes) era INCORRECTO. El log mostró: delete de DarkDM (carpeta grande con varias subcarpetas anidadas) adquirió el managementOpMutex, y un segundo delete (MediaFire) quedó esperando el lock por 28+ segundos sin completarse nunca — mientras que llamadas `ls` a OTRAS rutas seguían funcionando con total normalidad en paralelo, lo que descarta un cuelgue de la conexión/dispatcher. Conclusión correcta: deleteDirectoryRecursive borraba una entrada a la vez, 100% secuencial — para un árbol con varias docenas de archivos anidados a ~300-700ms de RTT (observado en el mismo log vía otros `ls`), eso son 20-40+ segundos de espera pura, sin ningún feedback, indistinguible de un cuelgue para un usuario impaciente. El Mutex de la sesión anterior EMPEORABA esto al bloquear deletes independientes entre sí en vez de dejarlos compartir la conexión (cbssh's SftpDispatcher ya correlaciona requests concurrentes correctamente por ID único, re-confirmado leyendo su código). Fix: eliminado managementOpMutex por completo; deleteDirectoryRecursive reescrito para borrar todas las entradas de un nivel de directorio EN PARALELO, acotado por un Semaphore compartido (DELETE_PIPELINE_DEPTH=8) a través de todo el walk recursivo (no solo un nivel) — mismo patrón de pipelining que ya usan TransferEngine y CbsshTransfer para uploads. Actualizada la entrada bug-018 en feature_list.json con la Parte 3 (corrige la Parte 2), decisión y aprendizaje en memoria sobre distinguir 'lento sin feedback' de 'realmente colgado' antes de aplicar un fix de concurrencia.
**Verified**: ./init.sh BUILD SUCCESSFUL y ./gradlew assembleDebug BUILD SUCCESSFUL. NO verificado aún en el dispositivo físico del usuario contra el escenario real de borrar carpetas grandes anidadas — recomendado reintentar y confirmar que ahora es notablemente más rápido y que las carpetas efectivamente desaparecen.
**Completed**: none
---
---
## 2026-07-31 05:31 — ClientSSH
**Summary**: Fixed bug-018 (SFTP delete folders): replaced SSH-exec 'rm -rf' with pure SFTP recursive delete (modern standard, no exit-status blind spot), and fixed a real concurrency bug found via device log — concurrent delete taps ran unserialized against the shared SftpClient2, causing intermittent no-op deletes. Added managementOpMutex + real success/failure logging. Verified with ./init.sh, committed dcdfbd4c to contrib/cbssh-sftp.
**Verified**: ran init.sh BUILD SUCCESSFUL after each change
**Completed**: none
---
---
## 2026-07-31 05:31 — ClientSSH
**Summary**: Fix bug-018: usuario reportó 'eliminar carpetas no funciona' en SFTP. Dos partes. (1) SftpClient2.deleteDirectoryViaSsh usaba SSH exec `rm -rf` sin chequear exit status ni stderr — si fallaba, devolvía success igual con la carpeta intacta. Reemplazado por recursión pura SFTP (deleteDirectoryRecursive: listdir + recurse + remove + rmdir), el estándar moderno, funciona incluso en cuentas internal-sftp sin shell. (2) El usuario compartió un debug log real que reveló la causa de fondo: al tocar delete en varias carpetas casi seguidas (~4-9s entre cada una), los deletes corrían en paralelo como coroutines independientes sobre el mismo SftpClient2 compartido (activeClients por hostId) — uno de tres quedó sin efecto por 9+ segundos sin ningún error visible, porque deleteEntry() solo escribe a _uiState.error, nunca a DebugLogger. Fix: agregado managementOpMutex (Mutex) en SftpClient2 que serializa deletes recursivos concurrentes, más logging real de éxito/fallo (antes solo logueaba el inicio). Agregada entrada bug-018 en feature_list.json, decisión+aprendizaje en memoria, y TODO de seguimiento (loguear rename/mkdir también, y revisar botón muerto 'Delete selected' en SftpScreen.kt).
**Verified**: ./init.sh BUILD SUCCESSFUL después de cada cambio. NO reverificado aún en el dispositivo físico del usuario contra el escenario exacto de deletes rápidos en paralelo del log — recomendado reintentar y confirmar que todas las carpetas desaparecen, compartiendo un log nuevo si alguna sigue fallando.
**Completed**: none
---
---
## 2026-07-27 18:24 — ClientSSH
**Summary**: Fixed bug-017: MainScreen BackHandler now resets showServerSettings and showDebugLogs when pressing back from those sub-screens, matching the existing showSshKeys/showGenerateKey pattern. Also discarded refactor-001 (activeClients by tabId) as not useful. Verified with ./init.sh. Committed to contrib/cbssh-sftp.
**Verified**: ran init.sh BUILD SUCCESSFUL
**Completed**: none
---
---
## 2026-07-27 18:09 — ClientSSH
**Summary**: Fix bug-017: en MainScreen.kt, el BackHandler no reseteaba `showServerSettings` ni `showDebugLogs` al apretar back desde esos sub-screens dentro de la pestaña Settings. Eso hacía que `selectedTab` volviera a 0 pero los flags quedaran `true`, entonces al volver a tocar Settings se re-abría el sub-screen en lugar de mostrar la lista de opciones. Fix: agregados los dos casos en el `BackHandler` siguiendo el mismo patrón que `showSshKeys` y `showGenerateKey`. También se descartó `refactor-001` (activeClients keyed by tabId) por falta de utilidad real para el usuario — se actualizó su estado a `resolved` en feature_list.json.
**Verified**: ./init.sh BUILD SUCCESSFUL.
**Completed**: Bug: showServerSettings/showDebugLogs no se resetean en BackHandler de MainScreen.kt
---
---
## 2026-07-24 02:38 — ClientSSH
**Summary**: Fix bug-016: usuario confirmó que Copy+Paste ya funciona (fix de copy-data de la sesión anterior), pero reportó que Mover (Cut+Paste) todavía se cuelga y tuvo que cancelar. Sin log a mano, encontré la causa por análisis de código: moveFile() en SftpClient2.kt usaba una ruta completamente separada de copyFileViaSsh — intentaba rename() primero (SSH_FXP_RENAME plano, que falla si el destino ya existe, caso muy común al cortar-pegar para reemplazar un archivo), y ante ese fallo caía al viejo CbsshTransfer.copy(): copia serial byte a byte sin progreso real ni timeout global, exactamente el patrón que ya se había arreglado en copyFileViaSsh en la sesión anterior pero nunca se replicó acá. Fix: moveFile() ahora recibe isDirectory y, si rename() falla, reusa copyFileViaSsh (que ya prioriza copy-data nativo con fallback exec acotado por timeout) + borra el origen (deleteDirectoryViaSsh o rm según corresponda). De paso corrige que el fallback de mover directorios ya estaba roto (CbsshTransfer.copy() abría todo como archivo regular). Agregada entrada bug-016 en feature_list.json, decisión y aprendizaje en memoria (sobre buscar TODOS los call sites de una función pública antes de dar un fix por completo), y TODO de test de seguimiento.
**Verified**: ./init.sh BUILD SUCCESSFUL (unit tests verdes), ./gradlew assembleDebug BUILD SUCCESSFUL. No existe cobertura de test previa para moveFile() (gap preexistente, mismo bloqueo de infra Docker/mock que TODOs anteriores) — no se agregó test nuevo en esta sesión, solo se registró el TODO. No verificado aún en dispositivo físico — recomendado que el usuario reintente Cortar+Pegar, especialmente reemplazando un archivo ya existente en destino. 1 commit (befc430d) pusheado a contrib/cbssh-sftp.
**Completed**: none
---
---
## 2026-07-24 02:24 — ClientSSH
**Summary**: Feature cross-repo: implementé copia server-side SFTP nativa vía la extensión 'copy-data' de OpenSSH (SSH_FXP_EXTENDED), reemplazando el `cp` por exec como camino primario (mantenido como fallback con su timeout de bug-015). En cbssh-fork (sshlib 0.3.2→0.3.3-SNAPSHOT): parseo de los pares extension-name/data del handshake SSH_FXP_VERSION (antes ignorados por completo), nueva propiedad SftpClient.extensions, nuevo método SftpClient.copyData(), api.txt regenerado vía metalava, 4 tests unitarios nuevos (parsing + payload wire + error mapping) y 2 tests de integración Docker reales contra OpenSSH 9.9p2 confirmando que el servidor anuncia copy-data y que copia bytes correctamente — toda la suite :sshlib:test sigue verde. Publicado a mavenLocal. En clientssh: SftpClient2.copyFileViaSsh ahora chequea client.extensions primero — copyDataSingleFile (archivo individual vía open+copyData+close) y copyDataRecursive (walk recursivo client-side ya que copy-data no tiene operación de copia de directorio completo) — con fallback al exec `cp` renombrado a copyFileViaExec si el servidor no anuncia la extensión o el path nativo falla inesperadamente. Confirmé que el contenedor Docker que usan los tests de clientssh (linuxserver/openssh-server:latest) corre OpenSSH 10.3p1, por encima del mínimo 9.0 requerido. Agregada entrada feat-cbssh-copydata en feature_list.json, decisión y 2 aprendizajes en memoria, y TODOs de seguimiento (test Docker a nivel SftpClient2, y preparar PR a connectbot/cbssh recién después de testing manual en dispositivo real).
**Verified**: cbssh-fork: ./gradlew :sshlib:test BUILD SUCCESSFUL (toda la suite Docker-backed, incluyendo los 2 tests nuevos de copy-data contra OpenSSH 9.9p2 real). clientssh: ./init.sh BUILD SUCCESSFUL, ./gradlew assembleDebug BUILD SUCCESSFUL con cbssh 0.3.3-SNAPSHOT. Verificado por fuera de test automatizado que el contenedor OpenSSH usado en los Docker tests de clientssh (10.3p1) soporta copy-data. NO verificado aún: (1) test de integración Docker a nivel SftpClient2 (bloqueado por mock de KnownHostRepository + Robolectric, mismo gap que bug-015), (2) testing manual en dispositivo real del flujo completo Copy→Paste con el nuevo path nativo. Commit 98b15ca en cbssh-fork (local, sin pushear — sin credenciales de git configuradas para ese remote, igual que sesiones anteriores). Commit 646aa879 en clientssh pusheado a contrib/cbssh-sftp.
**Completed**: none
---
---
## 2026-07-24 01:56 — ClientSSH
**Summary**: El usuario corrigió mi diagnóstico anterior de la sesión pasada: el problema real no es que el botón Paste no aparezca, sino que al tocar Paste, la operación se queda colgada indefinidamente mostrando 'Copiando'/'Moviendo' sin terminar nunca ni dar error. Root cause encontrado en SftpClient2.kt: copyFileViaSsh/deleteDirectoryViaSsh ejecutan cp/rm -rf server-side vía SSH exec y drenan stdout con un loop sin timeout (`while (true) { s.read() ?: break }`); si el comando remoto nunca manda CHANNEL_EOF (alias de shell interactivo, proceso trabado, etc.), ese read() queda suspendido para siempre. Coincide con un TODO ya existente (#220) que predecía exactamente este failure mode. Descarté la teoría de deadlock por backpressure de stderr revisando LocalChannelWindow en cbssh-fork (rellena el window local sin esperar consumo de la app). Fix: agregado `internal var execCommandTimeoutMs` (120s default) envolviendo el drain loop con withTimeout() en copyFileViaSsh/deleteDirectoryViaSsh/executeCommand, convirtiendo el cuelgue silencioso en un Result.failure claro y recuperable. También agregado catch(CancellationException)+rethrow antes del catch(Exception) genérico en las 3 funciones (mismo patrón de bug-013), porque el catch genérico existente también tragaba cancelaciones reales del usuario. Agregada entrada bug-015 en feature_list.json, decisión y aprendizaje en memoria, y TODO de seguimiento para test de integración Docker del timeout (requiere mock de KnownHostRepository + probablemente Robolectric por el uso de android.util.Base64 en DarkSshHostKeyVerifier).
**Verified**: ./init.sh BUILD SUCCESSFUL (unit tests verdes), ./gradlew assembleDebug BUILD SUCCESSFUL. NO verificado aún en dispositivo físico contra un cuelgue real reproducido (no había log de dispositivo disponible para este reporte específico) — es una mitigación defensiva correcta y de bajo riesgo, pero recomendado testear en el dispositivo real para confirmar que ahora el Paste falla con mensaje claro en vez de colgarse para siempre. 1 commit (36877c76) pusheado a contrib/cbssh-sftp.
**Completed**: none
---
---
## 2026-07-24 01:40 — ClientSSH
**Summary**: Analicé y arreglé el bug reportado 'copiar y cortar no funcionan' en SftpScreen. Root cause encontrado por análisis de código puro (sin logs): SftpClipboard.data era un plain var en un singleton object, no un Compose State observable — copyFiles()/cutFiles() SÍ guardaban los datos correctamente y el snackbar de confirmación sí aparecía (vía coroutine/SnackbarHostState, independiente de Compose), pero el botón Paste (gateado en hasClipboardData()) nunca recomponía para mostrarse, dando la impresión de que la acción no hacía nada. Fix: convertir data a `by mutableStateOf(null)` en SftpClipboard.kt (androidx.compose.runtime), cambio mínimo y localizado sin tocar el resto del flujo. Agregada entrada bug-014 en feature_list.json y decisión/aprendizaje en memoria sobre el patrón general (objects planos + Compose state).
**Verified**: ./init.sh BUILD SUCCESSFUL (unit tests verdes), ./gradlew assembleDebug BUILD SUCCESSFUL. No verificado aún en dispositivo físico por el usuario (no se proporcionó log de reproducción para este reporte, el fix es 100% basado en análisis estático del código). 1 commit (6a4c8518) pusheado a contrib/cbssh-sftp.
**Completed**: none
---
---
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
**Summary**: Camino B completado: refactor estructural pre-requisito para eliminar sshj. Extraídos SftpEntry y SftpAuthState de SftpClient.kt (legacy sshj) a SftpTypes.kt propio, mismo patrón que TransferProgress.kt. Move verbatim sin cambios de lógica, mismo package = cero cambios de import en los 4 consumidores (ISftpClient, SftpClient2, SftpViewModel, SftpScreen, SftpClipboard). También verificado manualmente que ISftpClient (17 miembros) está 100% implementado por ambos clientes sin gaps, confirmando que no falta portar funcionalidad legacy antes de poder eliminar sshj. No se agregaron tests para los tipos extraídos por ser value types puros sin lógica derivada (mismo criterio que TransferResult). 1 commit, 64 tests sin cambios (0 failures), ./init.sh verde, assembleDebug y assembleRelease compilan.
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
**Verified**: ./init.sh pasó completo. 18 tests, 1 rojo→verde, 0 failures. Compilación limpia sin warnings nuevos relevantes.
**Completed**: none
---
---
## 2026-07-22 02:39 — ClientSSH
**Summary**: Reporte formal de migración sshj→cbssh según estándar harness engineering. Resultado: PROBLEMÁTICO. Agregué 8 nuevos tests (TransferConfigTest x2, TransferStateTest x4, SftpClientFactoryTest x3) — 7 pasan, 1 falla porque expone un bug pre-existente en TransferState.isComplete (returns true cuando totalBytes=0). 17 tests totales, 16 passing. Hallazgos críticos: (H1) NO tests para SFTP existente, (H2) NO JaCoCo configurado, (H3) NO benchmark, (H4) bug isComplete para archivos vacíos, (H5-H7) deuda en eliminacion sshj/CbsshTransfer. Cobertura efectiva estimada <1% del módulo SFTP. 8 gaps explícitos listados. Esperando decisión del usuario sobre: fix del bug H4, agregar JaCoCo, y scope de próximos tests.
**Verified**: ./gradlew testDebugUnitTest ejecutado: 17 tests, 1 failing (bug expuesto por test). Antes: 9 tests, 0 failures.
**Completed**: none
