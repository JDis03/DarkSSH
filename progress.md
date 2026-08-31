## 2026-08-31 (2) — ClientSSH
**Summary**: Llegó el log del dispositivo ya con quality-013: "IoError IOException: Write failed at offset 1072168960 after retries". Ese offset es el chunk 8180 de 8192 (chunks de 128KB), o sea a 12 chunks / 1,5 MiB EXACTOS de 1 GiB con 24 en vuelo → el corte cae en el borde del rekey de cbssh; la ubicación queda confirmada, el mecanismo todavía no. Dato nuevo del log: kex=mlkem768x25519-sha256 [post-quantum] — el rekey es ML-KEM768, con mensajes de KEX bastante más grandes que un DH clásico, variable que mi repro de loopback NO replicaba. Punto ciego que quedaba: ese mensaje lo fabrica el propio TransferEngine (línea 907) y retryWriteChunk devolvía Boolean, tirando a la basura el SftpResult y las excepciones de cbssh. Arreglado en quality-014: retryWriteChunk ahora devuelve String? con la última causa y el IOException final la incluye ("— última causa: ..."), preservando la semántica de totalRetries.
**Verified**: compileDebugKotlin + testDebugUnitTest + ./init.sh BUILD SUCCESSFUL; verificado que los 3 call sites de retryWriteChunk quedaron migrados
**Completed**: quality-014 (investigate-001 sigue in-progress)
---
## 2026-08-31 01:55 — ClientSSH
**Summary**: Analizado el log del corte de upload al 72%: cae EXACTO en 1 GiB, que es el rekeyBytesLimit default de cbssh que DarkSSH nunca sobreescribe — el usuario tenía razón en que es código, no red. Hipótesis del rekey rompiendo el pipeline NO confirmada: mi repro pipelineado (depth 32, rekeyBytesLimit 256KB) contra OpenSSH real PASA en loopback; quedó aislado en la rama investigate/rekey-pipelining para no contaminar el PR #273. Descubierto que el test de rekey upstream es secuencial, o sea el path pipelineado no tenía cobertura. Arreglado el bloqueante del diagnóstico (quality-013): uploadFile descartaba el motivo del fallo, por eso el log solo decía "falling back to SCP"; ahora describeSftpFailure() loguea ServerError/ProtocolError/IoError. Commit 5df14621.
**Verified**: compileDebugKotlin + testDebugUnitTest + ./init.sh BUILD SUCCESSFUL; repro del fork verificado en test-results XML (3.199s, 0 failures)
**Completed**: none
---
---
## 2026-08-31 — ClientSSH
**Summary**: Analizado el log del usuario (log/darkssh_20260830_115606.log) del upload que muere al 72%. Dato duro: archivo de 1.475.138.004 bytes, muere a ~36s a 29MB/s ≈ 1.06GB; 1 GiB (1.073.741.824) = 72,8% del archivo, que coincide EXACTO con SshClientConfig.rekeyBytesLimit (default de cbssh, que DarkSSH nunca sobreescribe) → el rekey lo dispara cbssh, no la red, tal como sospechaba el usuario ("es código"). Hipótesis: el rekey colisiona con el pipeline profundo (MAX_REKEY_IN_FLIGHT_PACKETS=1024 en SshConnection.kt:2386). NO CONFIRMADA: escribí un repro pipelineado (depth 32, chunk 128KB, rekeyBytesLimit 256KB) contra OpenSSH 9.9p2 real y PASA en loopback en 3.2s — quedó en la rama investigate/rekey-pipelining del fork, fuera de la rama del PR #273. Descubrimiento colateral: el test de rekey que ya existía upstream es secuencial (1 request en vuelo), así que el path pipelineado no tenía cobertura. Bloqueante real del diagnóstico: uploadFile descartaba el motivo del fallo (el log solo decía "falling back to SCP") → arreglado en quality-013 con describeSftpFailure() cubriendo las 3 variantes de SftpResult. El log también mostró el fallback SCP colgado 44s inútilmente, que es justo lo que corta quality-012.
**Verified**: compileDebugKotlin + testDebugUnitTest + ./init.sh BUILD SUCCESSFUL; repro en fork corrió de verdad (verificado en el XML de test-results: 3.199s, 0 failures)
**Completed**: quality-013 (investigate-001 queda in-progress)
---
## 2026-08-30 15:53 — ClientSSH
**Summary**: Investigado el reporte de uploads que "cierran el canal": el error 'Failed to write 68 bytes' es del transporte TCP (socket cerrado por red inestable), no del canal SFTP. Bug real arreglado en SftpClient2.uploadFile (quality-012): ante IoError de conexión seguía con exists() + fallback SCP sobre el socket muerto (re-subiendo 712MB) y nunca llamaba markDead, dejando la UI sin saber que debía reconectar. Ahora corta rápido con markDead + mensaje claro. Rama de trabajo confirmada: contrib/cbssh-sftp. Commit a153553c.
**Verified**: compileDebugKotlin + testDebugUnitTest + ./init.sh BUILD SUCCESSFUL; pendiente log del usuario del momento exacto del fallo
**Completed**: none
---
---
## 2026-08-19 — ClientSSH
**Summary**: quality-012 — investigado el reporte "el SFTP cierra el canal en uploads, varias veces". El error 'Failed to write 68 bytes' viene de KtorTcpTransport.write(): el socket TCP se cierra a mitad del upload (red inestable), no es un cierre de canal SFTP. Bug real encontrado en SftpClient2.uploadFile: ante cualquier fallo (incluido IoError de conexión) llamaba exists() y caía al fallback SCP, que intenta abrir sesión sobre el socket muerto y re-subiría el archivo completo (712MB); además ese path devuelve el SftpResult crudo del engine (sin pasar por mapResult), así que markDead() nunca se llamaba y la UI no sabía que debía reconectar. Fix: cortar rápido ante IoError (markDead + log claro + Result.failure), dejando exists()+SCP solo para errores de servidor/protocolo con la conexión viva. Rama de trabajo confirmada: contrib/cbssh-sftp (PR #273 ya abierto en connectbot/cbssh).
**Verified**: compileDebugKotlin + testDebugUnitTest + ./init.sh BUILD SUCCESSFUL
**Completed**: quality-012
---
---
## 2026-08-19 04:19 — ClientSSH
**Summary**: PR #273 abierto a connectbot/cbssh con los 3 commits de cbssh (copy-data extension, ventana SFTP 64KB→16MB, keepAliveIntervalMs config) tras confirmación del usuario de testing en dispositivo real. Calidad verificada: 1215 tests 0 fallos, spotless/metalava/kover OK, rebase limpio. CI de upstream en action_required (espera aprobación del maintainer para PRs externos). URL: https://github.com/connectbot/cbssh/pull/273
**Verified**: PR abierto y visible; gh pr view confirma OPEN con 3 commits; gh run list muestra CI/Dependency Submission/CodeQL en action_required
**Completed**: none
---
---
## 2026-08-19 04:15 — ClientSSH
**Summary**: PR copy-data verificado con calidad completa: ./gradlew build en cbssh-fork pasa 1215 tests (0 fallos) + spotless + metalava + kover. Fixes aplicados: spotless espaciado en SshClient.kt, api.txt con keepAliveIntervalMs regenerado, openSftp no rompe el test de upstream (ventana movida a default de openSessionChannel), .memory-project removido del PR, rebase autosquash limpio. Branch feat/copy-data-extension (3 commits) force-pusheado a JDis03/cbssh. Pendiente: testing en dispositivo real (TODO 204) antes de abrir el PR (TODO 227).
**Verified**: ./gradlew build en cbssh-fork: BUILD SUCCESSFUL (1215 tests, 0 fallos; spotless, metalava, kover OK); branch pusheado
**Completed**: none
---
---
## 2026-08-18 19:58 — ClientSSH
**Summary**: Validación final del plan de throughput con log real: 28-32 MB/s (DL y UL) vs 3.4 original — el engine optimizado entrega ~30MB/s estables cuando la red lo permite. El límite anterior era el WiFi 2.4GHz del router (5GHz muerto: RT-AX54, diagnosticado por SSH: driver carga pero no transmite, descartados smart-connect/SSID oculto/canal fijo/reboot — probable hardware/firmware). Bug nuevo registrado: cancelar descarga mata la conexión SFTP ('Failed to write 68 bytes'). Commits 0fd03477.
**Verified**: Log real dispositivo: 3 corridas (28.0/29.9/31.7 MB/s); diagnóstico router por SSH completo; tests verdes previos
**Completed**: none
---
---
## 2026-08-18 14:48 — ClientSSH
**Summary**: Benchmark decisivo de cbssh en PC (mismo pipeline depth 32 chunk 128KB contra localhost): DL 130MB/s, UL 170MB/s. El código de cbssh NO es el cuello — el throughput del móvil (DL 6.4MB/s = 91% del WiFi, UL 3.2MB/s) es el techo físico del teléfono. El RTT medido alto era bufferbloat del radio saturado. quality-008 fue falso positivo para la app (el jar 0.3.3 ya tenía la ventana 16MB). keepAlive cherry-picked al branch del PR. DarkSSH sigue en 0.3.3 (0.4.3 rompe Hilt por metadatos Kotlin). Benchmark temporal eliminado. Fork limpio con 3 commits para el PR: copy-data + ventana 16MB + keepAlive.
**Verified**: benchmark PC (DL 130 / UL 170 MB/s) + DarkSSH assembleDebug OK con cbssh 0.3.3-SNAPSHOT + fork pusheado limpio
**Completed**: none
---
---
## 2026-08-18 14:13 — ClientSSH
**Summary**: quality-008: el log LAN con RTT de 400-3000ms contra IP local delató el cuello interno — openSessionChannel() de cbssh abre el canal SFTP con ventana de 64KB (round-trip stall por cada 64KB). Fix en el fork: ventana 16MB + packet 32KB (a3a7cb0), publicado a mavenLocal, DarkSSH recompilado. El jar publicado antes no incluía los fixes de ventana de junio (49af22a/ea1f04b). Pendiente retest del usuario en LAN/VPN.
**Verified**: publishToMavenLocal OK + testDebugUnitTest OK + ./init.sh BUILD SUCCESSFUL en DarkSSH; pendiente retest en dispositivo
**Completed**: none
---
---
## 2026-08-18 12:46 — ClientSSH
**Summary**: quality-007 iteración 2 con el dato clave "VPN": el RTT real de 200-300ms explicaba el límite de 4-5MB/s (BDP 1.75MB vs 1MB en vuelo con 32×32KB). Fix: chunkSize 128KB (4MB en vuelo = 16MB/s teórico) + panicRttMs 4000 (picos VPN transitorios). Commit e45a15a9. Pendiente retest del usuario con VPN — si da ~7MB/s, queda listo el device testing para el PR de copy-data.
**Verified**: testDebugUnitTest BUILD SUCCESSFUL + ./init.sh BUILD SUCCESSFUL + assembleDebug OK; pendiente retest en dispositivo con VPN
**Completed**: none
---
---
## 2026-08-18 12:24 — ClientSSH
**Summary**: Diagnosticado con el log real del dispositivo y arreglado quality-007: el throughput de 3.4MB/s (link de ~7MB/s) era causado por medir el RTT desde el ENCOLADO del async — la espera de thread del pool inflaba el 'RTT' → sawtooth depth 12↔16 + panic + auto-congestión medida. Fix: timestamp dentro del async, buckets recalibrados, maxPipelineDepth 32. Commit 47a9b00c. Pendiente: retest en dispositivo.
**Verified**: testDebugUnitTest BUILD SUCCESSFUL (100 tests) + ./init.sh BUILD SUCCESSFUL; pendiente retest en dispositivo real
**Completed**: none
---
---
## 2026-08-18 14:00 — ClientSSH
**Summary**: quality-009 — benchmark decisivo de cbssh en PC (mismo pipeline depth 32 chunk 128KB): DL 130MB/s, UL 170MB/s contra localhost. El código de cbssh NO es el cuello — el throughput del móvil (DL 6.4MB/s = 91% del WiFi ~7, UL 3.2MB/s) es el techo del WiFi/CPU del teléfono. El 'RTT medido' de 400-3000ms en LAN era bufferbloat del radio saturado (efecto, no causa). quality-008 fue falso positivo para la app: el jar 0.3.3 YA tenía ventana 16MB desde junio — el fix sigue siendo válido para el PR upstream (lib default 64KB). keepAlive cherry-picked a feat/copy-data-extension (76ad956). DarkSSH sigue en cbssh 0.3.3-SNAPSHOT (0.4.3 rompe Hilt por metadatos Kotlin 2.4 vs 2.3 — no subir). Benchmark temporal eliminado; fork limpio con 3 commits para el PR.
**Verified**: benchmark en PC (DL 130 / UL 170 MB/s) + fork limpio + DarkSSH assembleDebug OK con 0.3.3.
**Completed**: quality-009
---
---
## 2026-08-18 13:10 — ClientSSH
**Summary**: quality-008 — el log LAN (RTT 400-3000ms contra IP local 192.168.50.45) reveló que el cuello NO era la red: openSessionChannel() de cbssh abre el canal SFTP con initialWindowSize=64KB → el servidor envía 64KB y pausa esperando WINDOW_ADJUST → un round-trip por cada 64KB. Por eso DL=6.4MB/s y UL=3.2MB/s idénticos en LAN y VPN (el RTT 'medido' era stall de ventana). Fix en el fork: ventana 16MB, packet 32KB (256KB rompía SftpPacketIO — lección del fork local 49af22a/ea1f04b de junio que el jar publicado NO incluía). Commit a3a7cb0 en JDis03/cbssh, publicado a mavenLocal 0.3.3-SNAPSHOT, DarkSSH recompilado OK.
**Verified**: testDebugUnitTest + ./init.sh BUILD SUCCESSFUL en DarkSSH; publishToMavenLocal BUILD SUCCESSFUL en fork. PENDIENTE retest en dispositivo (LAN + VPN).
**Completed**: quality-008
---
---
## 2026-08-18 12:50 — ClientSSH
**Summary**: quality-007 iteración 2 — el retest con VPN reveló el límite restante: RTT real ~200-300ms (VPN, no LAN) → BDP de 7MB/s×250ms = 1.75MB en vuelo; 32×32KB = 1MB no alcanzaba → throughput = depth×chunk/RTT = 4.2MB/s teórico (descarga real 5.1MB/s, upload 3.07MB/s). Fix: chunkSize 32KB→128KB (32×128KB=4MB en vuelo = ~16MB/s teórico a 250ms) y panicRttMs 2000→4000 (picos VPN de 2-3s son transitorios; el breaker colapsaba el pipeline innecesariamente). La medición de RTT corregida de la iteración 1 ya estabilizó el depth en 32 (3.4→5.1MB/s, 1 solo panic vs sawtooth permanente).
**Verified**: testDebugUnitTest BUILD SUCCESSFUL + ./init.sh BUILD SUCCESSFUL + assembleDebug OK. PENDIENTE retest en dispositivo con VPN.
**Completed**: quality-007 (iteración 2)
---
---
## 2026-08-18 12:25 — ClientSSH
**Summary**: quality-007 — el log real del dispositivo reveló la causa del throughput bajo (3.4MB/s en un link de ~7MB/s): la medición de RTT usaba el timestamp de ENCOLADO del async, incluyendo la espera de thread del pool de coroutines → el adaptativo se auto-congestionaba por medición (sawtooth depth 12↔16 toda la descarga, panic 2056ms→6, avgRtt 50-400ms). Fix: timestamp movido DENTRO del async (justo antes del sftp.read/write), buckets recalibrados (<30→12, <60→16, <120→24, else→max) y maxPipelineDepth restaurado a 32 (el downgrade a 16 del 23-jul era artefacto de la misma medición). Tests actualizados (AdaptiveTest, TransferConfigTest). PENDIENTE retest en dispositivo: la descarga de 712MB debería ir a ~7MB/s estable.
**Verified**: testDebugUnitTest BUILD SUCCESSFUL + ./init.sh BUILD SUCCESSFUL
**Completed**: quality-007
---
---
## 2026-08-18 12:14 — ClientSSH
**Summary**: Fork cbssh verificado y listo para el PR de copy-data: rama feat/copy-data-extension rebasada sobre upstream/main (8 commits de renovate sin conflicto), api.txt descontaminado (5 líneas de keepAlive de la main local rompían metalavaCheckCompatibility), spotless aplicado (solo header copyright), build completo verde (test+spotless+metalava+kover), 1215 tests 0 fallos incluyendo los 3 archivos de test de copy-data (SftpClientIntegrationTest con Docker real). Rama pusheada a JDis03/cbssh. Falta solo el testing en dispositivo real (TODO 204) antes de abrir el PR (TODO 227).
**Verified**: ./gradlew build en cbssh-fork: BUILD SUCCESSFUL (1215 tests, 0 fallos; spotless, metalava, kover OK) en Java 21 con Docker disponible
**Completed**: none
---
---
## 2026-08-18 12:02 — ClientSSH
**Summary**: Completado el plan de robustez cbssh: quality-005 Flow APIs arregladas (channel siempre cerrado + resultado propagado al collector, 3 tests nuevos) y quality-006 uploadParallel eliminada (dead code con off-by-one, -110 líneas + constantes muertas). Commits bb107f70 y 9773f02e pusheados. Plan quality-001..006 completo, todo verificado verde.
**Verified**: testDebugUnitTest BUILD SUCCESSFUL + ./init.sh BUILD SUCCESSFUL + assembleDebug OK
**Completed**: none
---
---
## 2026-08-18 12:03 — ClientSSH
**Summary**: Implementados quality-005 y quality-006 (ambos dead code, decisiones opuestas con ojo crítico). quality-005 — Flow APIs ARREGLADAS (son API pública útil): el job cierra el channel SIEMPRE (try/finally cubre éxito/fallo/cancelación incl. rethrow de quality-003), el resultado se propaga al collector (Failed/Cancelled lanzados vía job.await()) y job.cancel() si el collector cancela. 3 tests nuevos (completa sin colgarse, propaga fallo, upload completa). quality-006 — uploadParallel ELIMINADA (110 líneas + constantes muertas DEFAULT_CHUNK_SIZE/DEFAULT_PARALLEL_CHUNKS): dead code con off-by-one (perdía el último byte de cada chunk) y sin límite de concurrencia; arreglar código sin callers solo agrega superficie de bugs. Verificado todo verde.
**Verified**: testDebugUnitTest BUILD SUCCESSFUL + ./init.sh BUILD SUCCESSFUL + assembleDebug OK
**Completed**: quality-005, quality-006
---
---
## 2026-08-18 11:52 — ClientSSH
**Summary**: Implementado quality-004 (resume de transfers): download resume con RandomAccessFile (setLength recorta cola + seek), upload con skipFully (skip exacto, EOFException si local corto) + validación stat del remoto para evitar gaps. 6 tests nuevos/actualizados cubriendo los casos borde, todos verdes. Commit 0daae371 pusheado a contrib/cbssh-sftp.
**Verified**: testDebugUnitTest BUILD SUCCESSFUL + ./init.sh BUILD SUCCESSFUL + assembleDebug OK
**Completed**: none
---
---
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
