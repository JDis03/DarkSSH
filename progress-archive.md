---
## 2026-07-03 03:21 — ClientSSH
**Summary**: Sesión de fix SFTP copy/move + orden de ramas. Fixes: (1) moveFile usa SSH mv -f con fallback SFTP rename, (2) copyFileViaSsh con overwrite flag y skip same-path, (3) pasteFiles integrado con TransferQueue panel mostrando progreso por archivo. Ramas master/dev/feature sincronizadas con force-push a aaa9d2dc. App estable con todos los features.
**Verified**: BUILD SUCCESSFUL, 4 commits pushed, 3 ramas sincronizadas
**Completed**: none
---
---
## 2026-07-05 23:24 — ClientSSH
**Summary**: Fix para borrar carpetas no vacías en SFTP al usar rmdir que fallaba silenciosamente. deleteDirectoryViaSsh usa rm -rf vía SSH exec, con fallback al rmdir de SFTP para carpetas vacías.
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev, confirmación de compilación únicamente (no tests)
**Completed**: none
---
---
## 2026-07-07 18:49 — ClientSSH
**Summary**: Fix bug en HostEditor: después de editar un host, al hacer Add aparecían los datos del host anterior. Root cause: ViewModel._host.value no se limpiaba cuando hostId cambiaba de N a -1L. Fix: loadHost() limpia _host = null cuando hostId <= 0, y LaunchedEffect siempre llama loadHost(hostId).
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-07 19:43 — ClientSSH
**Summary**: Regression check después de fix host-editor. Detectada y corregida regresión crítica: NavGraph no pasaba hostId al HostEditorScreen, causando que LaunchedEffect limpiara el host cargado por el ViewModel. Fix: extraer hostId de backStackEntry.arguments. Verificados otros cambios recientes (SFTP delete, copy/move progress) - sin regresiones.
**Verified**: ./init.sh BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-07 20:15 — ClientSSH
**Summary**: Fix back button en host editor: ahora cierra el diálogo en lugar de salir de la app. BackHandler verifica showHostEditor primero antes de manejar navegación entre tabs. Orden de prioridad: diálogos → navegación → exit.
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-07 20:29 — ClientSSH
**Summary**: Modernización de HostListScreen: swipe-to-delete con animación, botón SFTP prominente (FilledTonalButton full-width), card elevation mejorada, mejor spacing y jerarquía visual. Edit button discreto en esquina. UX más intuitiva y moderna.
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-07 20:40 — ClientSSH
**Summary**: Rediseño completo de HostCard: swipe derecha para edit (azul), swipe izquierda para delete (rojo), menú desplegable (⋮) con SFTP y Clone. Icono más grande, menos clutter, mejor jerarquía visual. Clone abre editor vacío (pendiente pre-llenar datos).
**Verified**: ./init.sh BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-07 20:52 — ClientSSH
**Summary**: Indicador online/offline por host con arquitectura escalable. TerminalService.connectedHostIds (flatMapLatest+combine), pasado como Set<Long> a HostListScreen, punto verde/gris de 12dp sobre el icono del host. ViewModel no se acopla al Service.
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-07 21:01 — ClientSSH
**Summary**: Fix swipe-delete: confirmValueChange devuelve false (snap back) + llama onDeleteClick para mostrar diálogo. Item solo desaparece cuando se confirma y la DB lo borra, animado por LazyColumn.animateItem().
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-08 02:22 — ClientSSH
**Summary**: Clone host estilo Termius: duplica la config inmediatamente sin diálogos. Nickname único (Copy of X, Copy of X (2)...), id=0L para nuevo row, lastConnected=null. Aparece en lista via Flow al instante.
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-08 02:26 — ClientSSH
**Summary**: Regression check: 2 fixes. (1) connectedHostIds SharingStarted.WhileSubscribed→Eagerly porque el Service gestiona su propio lifecycle. (2) cloneHost firstNotNullOf(2..99)→generateSequence para evitar NoSuchElementException. NavGraph sin connectedHostIds es aceptable (default emptySet).
**Verified**: ./init.sh BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-08 03:31 — ClientSSH
**Summary**: Análisis y fix de 3 bugs estructurales en tabs: (1) collectAsState dentro de forEach en TabBar → key(tab.id){}, (2) isSyncing race condition con 4 efectos → una sola dirección TabManager→pager, (3) DisposableEffect con captures stale eliminado. -44 líneas, código más simple y correcto.
**Verified**: ./init.sh BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-08 05:37 — ClientSSH
**Summary**: Mejoras UX en tabs: (1) ConnectionDot por tab SSH con verde/rojo/pulsando según estado de conexión, (2) Long-press menu con Close/Close others/Close all, (3) closeOtherTabs y closeAllTabs en TabManager, (4) Empty state con botón New connection, (5) Tab height 52dp.
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
## 2026-07-08 06:01 — ClientSSH
**Summary**: Removed X close button from tabs - close via long-press menu only. Tab expandida: icono + texto limpio. Tab colapsada: solo icono + dot. Long-press → Close / Close others / Close all.
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
## 2026-07-17 04:24 — ClientSSH
**Summary**: Diagnosticado y corregido bug-012: conexión SSH a IP válida (192.168.50.45) fallaba con error de DNS en Xiaomi Redmi Note 8 Pro. Causa raíz identificada por análisis del log darkssh_20260716_233139.log: InetAddress.getAllByName() solo evita DNS si el string matchea EXACTAMENTE su parser estricto de IPv4 literal; ningún caracter extra invisible (espacio, NBSP, zero-width space) rompía ese match y forzaba un lookup DNS real que fallaba. Ningún .trim()/sanitización existía en el flujo de guardado de Host ni en SSH.kt. Fix: nuevo util/TextSanitizer.kt (trim + strip de caracteres Unicode invisibles, escaneando todo el string ya que Character.isWhitespace() excluye NBSP deliberadamente) aplicado en HostEditorViewModel.saveHost() (save-time, limpia la DB a futuro) y en SSH.kt connect()/authenticate() (use-time, desbloquea inmediatamente el host ya roto del usuario sin que tenga que reescribirlo). También se commiteó el trabajo acumulado de sesiones previas sin commitear (bug-010 bracketed paste, bug-011 key-based auth E2E, fixes de teclado/mouse-tracking en TUIs) en un solo commit combinado (6b3b3621) dado que SSH.Settings → Debug Logs, etc. No usé NavController en MainActivity.
**Verified**: ./gradlew clean assembleDebug BUILD SUCCESSFUL, push a feature/master/dev
**Completed**: none
---
---
## 2026-07-14 23:23 — ClientSSH
**Summary**: Implementado bug-010: bracketed paste fix en TerminalView.java (commitText detecta paste real via codePointCount>1, no length<=1, evitando falso positivo con emoji) y arreglado onPasteTextFromClipboard() en TerminalBridge.kt que era stub vacío (long-press Paste no funcionaba). BUILD SUCCESSFUL, sin tests unitarios en el proyecto (verificación = compilación).
**Verified**: ./init.sh -> BUILD SUCCESSFUL (compilación Kotlin+Java OK, no hay unit tests en el proyecto)
**Completed**: none
---
---
## 2026-07-17 04:24 — ClientSSH
**Summary**: Diagnosticado y corregido bug-012: conexión SSH a IP válida (192.168.50.45) fallaba con error de DNS en Xiaomi Redmi Note 8 Pro. Causa raíz identificada por análisis del log darkssh_20260716_233139.log: InetAddress.getAllByName() solo evita DNS si el string matchea EXACTAMENTE su parser estricto de IPv4 literal; ningún caracter extra invisible (espacio, NBSP, zero-width space) rompía ese match y forzaba un lookup DNS real que fallaba. Ningún .trim()/sanitización existía en el flujo de guardado de Host ni en SSH.kt. Fix: nuevo util/TextSanitizer.kt (trim + strip de caracteres Unicode invisibles, escaneando todo el string ya que Character.isWhitespace() excluye NBSP deliberadamente) aplicado en HostEditorViewModel.saveHost() (save-time, limpia la DB a futuro) y en SSH.kt connect()/authenticate() (use-time, desbloquea inmediatamente el host ya roto del usuario sin que tenga que reescribirlo). También se commiteó el trabajo acumulado de sesiones previas sin commitear (bug-010 bracketed paste, bug-011 key-based auth E2E, fixes de teclado/mouse-tracking en TUIs) en un solo commit combinado (6b3b3621) dado que SSH.kt mezclaba cambios de bug-011 y bug-012 no separables limpiamente por hunks.
**Verified**: ./gradlew clean assembleDebug BUILD SUCCESSFUL (44/44 fresh) + ./init.sh BUILD SUCCESSFUL, dos veces (antes y después del commit). Compilación limpia sin warnings nuevos relevantes. NO verificado en el dispositivo Xiaomi real del usuario (no había adb access en esta sesión, solo se proveyó el archivo de log) - pendiente que el usuario reinstale el APK y reintente conectar a 'arch' sin reeditar el host.
**Completed**: none
---
---
## 2026-07-16 00:59 — ClientSSH
**Summary**: Implementado bug-011: key-based SSH login gestionado end-to-end desde la app. 4 fixes usando connectbot/ como referencia: (1) dropdown roto de SSH Key en Host Editor (ExposedDropdownMenuBox + PrimaryNotEditable), (2) host.pubkeyId ahora se usa realmente en SSH.kt::authenticate() en vez de ignorarse, (3) export de clave pública a clipboard vía PublicKeyUtils.toAuthorizedKeysFormat (sshlib), (4) agregada entrada real 'SSH Keys' en Settings ya que PubkeyListScreen/GeneratePubkeyScreen eran inalcanzables (NavGraph.kt es código muerto, MainScreen.kt). Clean build 44/44 + ./init.sh OK.
**Verified**: ./gradlew clean assembleDebug BUILD SUCCESSFUL (44/44 fresh) + ./init.sh BUILD SUCCESSFUL; verificado en device real conectado vía adb (127.0.0.1:15555), DB inspeccionada en cada paso
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
## 2026-07-22 03:15 — ClientSSH
**Summary**: Tests de TransferEngine.withRetry: 9 tests nuevos cubriendo clasificación de errores (Success/ServerError/ProtocolError/IoError), retry vs no-retry, maxRetries, exponential backoff, CancellationException propagation. Mismo patrón que sesión anterior: accesor internal (withRetryForTest) hace visible la función privada solo para tests del mismo módulo, sin cambiar API pública ni lógica de producción. Verificación: 51 tests total, 0 failures (era 42), ./init.sh verde, APK debug compila sin warnings. Total acumulado en este branch: 4 commits agregando tests, 51 tests, 0 regressions.
**Verified**: ./init.sh pasó completo. 51 tests, 0 failures. Antes: 42 tests. +9 tests nuevos todos verdes.
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
## 2026-07-24 01:56 — ClientSSH
**Summary**: El usuario corrigió mi diagnóstico anterior de la sesión pasada: el problema real no es que el botón Paste no aparezca, sino que al tocar Paste, la operación se queda colgada indefinidamente mostrando 'Copiando'/'Moviendo' sin terminar nunca ni dar error. Root cause encontrado en SftpClient2.kt: copyFileViaSsh/deleteDirectoryViaSsh ejecutan cp/rm -rf server-side vía SSH exec y drenan stdout con un loop sin timeout (`while (true) { s.read() ?: break }`); si el comando remoto nunca manda CHANNEL_EOF (alias de shell interactivo, proceso trabado, etc.), ese read() queda suspendido para siempre. Coincide con un TODO ya existente (#220) que predecía exactamente este failure mode. Descarté la teoría de deadlock por backpressure de stderr revisando LocalChannelWindow en cbssh-fork (rellena el window local sin esperar consumo de la app). Fix: agregado `internal var execCommandTimeoutMs` (120s default) envolviendo el drain loop con withTimeout() en copyFileViaSsh/deleteDirectoryViaSsh/executeCommand, convirtiendo el cuelgue silencioso en un Result.failure claro y recuperable. También agregado catch(CancellationException)+rethrow antes del catch(Exception) genérico en las 3 funciones (mismo patrón de bug-013), porque el catch genérico existente también tragaba cancelaciones reales del usuario. Agregada entrada bug-015 en feature_list.json, decisión y aprendizaje en memoria, y TODO de seguimiento para test de integración Docker del timeout (requiere mock de KnownHostRepository + probablemente Robolectric por el uso de android.util.Base64 en DarkSshHostKeyVerifier).
**Verified**: ./init.sh BUILD SUCCESSFUL (unit tests verdes), ./gradlew assembleDebug BUILD SUCCESSFUL. NO verificado aún en dispositivo físico contra un cuelgue real reproducido (no había log de dispositivo disponible para este reporte específico) — es una mitigación defensiva correcta y de bajo riesgo, pero recomendado testear en el dispositivo real para confirmar que ahora el Paste falla con mensaje claro en vez de colgarse para siempre. 1 commit (36877c76) pusheado a contrib/cbssh-sftp.
**Completed**: none
---
---
## 2026-07-24 02:24 — ClientSSH
**Summary**: Feature cross-repo: implementé copia server-side SFTP nativa vía la extensión 'copy-data' de OpenSSH (SSH_FXP_EXTENDED), reemplazando el `cp` por exec como camino primario (mantenido como fallback con su timeout de bug-015). En cbssh-fork (sshlib 0.3.2→0.3.3-SNAPSHOT): parseo de los pares extension-name/data del handshake SSH_FXP_VERSION (antes ignorados por completo), nueva propiedad SftpClient.extensions, nuevo método SftpClient.copyData(), api.txt regenerado vía metalava, 4 tests unitarios nuevos (parsing + payload wire + error mapping) y 2 tests de integración Docker reales contra OpenSSH 9.9p2 confirmando que el servidor anuncia copy-data y que copia bytes correctamente — toda la suite :sshlib:test sigue verde. Publicado a mavenLocal. En clientssh: SftpClient2.copyFileViaSsh ahora chequea client.extensions primero — copyDataSingleFile (archivo individual vía open+copyData+close) y copyDataRecursive (walk recursivo client-side ya que copy-data no tiene operación de copia de directorio completo) — con fallback al exec `cp` renombrado a copyFileViaExec si el servidor no anuncia la extensión o el path nativo falla inesperadamente. Confirmé que el contenedor Docker que usan los tests de clientssh (linuxserver/openssh-server:latest) corre OpenSSH 10.3p1, por encima del mínimo 9.0 requerido. Agregada entrada feat-cbssh-copydata en feature_list.json, decisión y 2 aprendizajes en memoria, y TODOs de seguimiento (test Docker a nivel SftpClient2, y preparar PR a connectbot/cbssh recién después de testing manual en dispositivo real).
**Verified**: cbssh-fork: ./gradlew :sshlib:test BUILD SUCCESSFUL (toda la suite Docker-backed, incluyendo los 2 tests nuevos de copy-data contra OpenSSH 9.9p2 real). clientssh: ./init.sh BUILD SUCCESSFUL, ./gradlew assembleDebug BUILD SUCCESSFUL con cbssh 0.3.3-SNAPSHOT. Verificado por fuera de test automatizado que el contenedor OpenSSH usado en los Docker tests de clientssh (10.3p1) soporta copy-data. NO verificado aún: (1) test de integración Docker a nivel SftpClient2 (bloqueado por mock de KnownHostRepository + Robolectric, mismo gap que bug-015), (2) testing manual en dispositivo real del flujo completo Copy→Paste con el nuevo path nativo. Commit 98b15ca en cbssh-fork (local, sin pushear — sin credenciales de git configuradas para ese remote, igual que sesiones anteriores). Commit 646aa879 en clientssh pusheado a contrib/cbssh-sftp.
**Completed**: none
---
---
## 2026-07-24 02:38 — ClientSSH
**Summary**: Fix bug-016: usuario confirmó que Copy+Paste ya funciona (fix de copy-data de la sesión anterior), pero reportó que Mover (Cut+Paste) todavía se cuelga y tuvo que cancelar. Sin log a mano, encontré la causa por análisis de código: moveFile() en SftpClient2.kt usaba una ruta completamente separada de copyFileViaSsh — intentaba rename() primero (SSH_FXP_RENAME plano, que falla si el destino ya existe, caso muy común al cortar-pegar para reemplazar un archivo), y ante ese fallo caía al viejo CbsshTransfer.copy(): copia serial byte a byte sin progreso real ni timeout global, exactamente el patrón que ya se había arreglado en copyFileViaSsh en la sesión anterior pero nunca se replicó acá. Fix: moveFile() ahora recibe isDirectory y, si rename() falla, reusa copyFileViaSsh (que ya prioriza copy-data nativo con fallback exec acotado por timeout) + borra el origen (deleteDirectoryViaSsh o rm según corresponda). De paso corrige que el fallback de mover directorios ya estaba roto (CbsshTransfer.copy() abría todo como archivo regular). Agregada entrada bug-016 en feature_list.json, decisión y aprendizaje en memoria (sobre buscar TODOS los call sites de una función pública antes de dar un fix por completo), y TODO de test de seguimiento.
**Verified**: ./init.sh BUILD SUCCESSFUL (unit tests verdes), ./gradlew assembleDebug BUILD SUCCESSFUL. No existe cobertura de test previa para moveFile() (gap preexistente, mismo bloqueo de infra Docker/mock que TODOs anteriores) — no se agregó test nuevo en esta sesión, solo se registró el TODO. No verificado aún en dispositivo físico — recomendado que el usuario reintente Cortar+Pegar, especialmente reemplazando un archivo ya existente en destino. 1 commit (befc430d) pusheado a contrib/cbssh-sftp.
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
## 2026-08-18 11:52 — ClientSSH
**Summary**: Implementado quality-004 (resume real): download resume ya no trunca el local — openResumableOutputStream() con RandomAccessFile (setLength recorta cola basura + seek a resumeFrom, fallo claro si el local no existe). Upload resume: skipFully() salta exactamente N bytes (EOFException si el local es más corto) + validación stat del remoto cuando resumeFrom>0 (Failed sin abrir handle si el remoto es más chico que el punto de reanudación — evita gap de ceros). 6 tests nuevos/actualizados: 3 de download resume (prefix preservado, cola recortada, local inexistente → handle cerrado) y 3 de upload (flags WRITE-only, write desde offset 20, remoto más chico → Failed sin open). El test existente de flags WRITE-only se actualizó con stub de stat (contrato nuevo del fix).
**Verified**: testDebugUnitTest BUILD SUCCESSFUL + ./init.sh BUILD SUCCESSFUL + assembleDebug OK
**Completed**: quality-004
---
---
## 2026-08-18 11:52 — ClientSSH
**Summary**: Implementado quality-004 (resume de transfers): download resume con RandomAccessFile (setLength recorta cola + seek), upload con skipFully (skip exacto, EOFException si local corto) + validación stat del remoto para evitar gaps. 6 tests nuevos/actualizados cubriendo los casos borde, todos verdes. Commit 0daae371 pusheado a contrib/cbssh-sftp.
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
## 2026-08-18 12:02 — ClientSSH
**Summary**: Completado el plan de robustez cbssh: quality-005 Flow APIs arregladas (channel siempre cerrado + resultado propagado al collector, 3 tests nuevos) y quality-006 uploadParallel eliminada (dead code con off-by-one, -110 líneas + constantes muertas). Commits bb107f70 y 9773f02e pusheados. Plan quality-001..006 completo, todo verificado verde.
**Verified**: testDebugUnitTest BUILD SUCCESSFUL + ./init.sh BUILD SUCCESSFUL + assembleDebug OK
**Completed**: none
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
## 2026-08-18 12:50 — ClientSSH
**Summary**: quality-007 iteración 2 — el retest con VPN reveló el límite restante: RTT real ~200-300ms (VPN, no LAN) → BDP de 7MB/s×250ms = 1.75MB en vuelo; 32×32KB = 1MB no alcanzaba → throughput = depth×chunk/RTT = 4.2MB/s teórico (descarga real 5.1MB/s, upload 3.07MB/s). Fix: chunkSize 32KB→128KB (32×128KB=4MB en vuelo = ~16MB/s teórico a 250ms) y panicRttMs 2000→4000 (picos VPN de 2-3s son transitorios; el breaker colapsaba el pipeline innecesariamente). La medición de RTT corregida de la iteración 1 ya estabilizó el depth en 32 (3.4→5.1MB/s, 1 solo panic vs sawtooth permanente).
**Verified**: testDebugUnitTest BUILD SUCCESSFUL + ./init.sh BUILD SUCCESSFUL + assembleDebug OK. PENDIENTE retest en dispositivo con VPN.
**Completed**: quality-007 (iteración 2)
---
---
## 2026-08-18 13:10 — ClientSSH
**Summary**: quality-008 — el log LAN (RTT 400-3000ms contra IP local 192.168.50.45) reveló que el cuello NO era la red: openSessionChannel() de cbssh abre el canal SFTP con initialWindowSize=64KB → el servidor envía 64KB y pausa esperando WINDOW_ADJUST → un round-trip por cada 64KB. Por eso DL=6.4MB/s y UL=3.2MB/s idénticos en LAN y VPN (el RTT 'medido' era stall de ventana). Fix en el fork: ventana 16MB, packet 32KB (256KB rompía SftpPacketIO — lección del fork local 49af22a/ea1f04b de junio que el jar publicado NO incluía). Commit a3a7cb0 en JDis03/cbssh, publicado a mavenLocal 0.3.3-SNAPSHOT, DarkSSH recompilado OK.
**Verified**: testDebugUnitTest + ./init.sh BUILD SUCCESSFUL en DarkSSH; publishToMavenLocal BUILD SUCCESSFUL en fork. PENDIENTE retest en dispositivo (LAN + VPN).
**Completed**: quality-008
---
---
## 2026-08-18 14:00 — ClientSSH
**Summary**: quality-009 — benchmark decisivo de cbssh en PC (mismo pipeline depth 32 chunk 128KB): DL 130MB/s, UL 170MB/s contra localhost. El código de cbssh NO es el cuello — el throughput del móvil (DL 6.4MB/s = 91% del WiFi ~7, UL 3.2MB/s) es el techo del WiFi/CPU del teléfono. El 'RTT medido' de 400-3000ms en LAN era bufferbloat del radio saturado (efecto, no causa). quality-008 fue falso positivo para la app: el jar 0.3.3 YA tenía ventana 16MB desde junio — el fix sigue siendo válido para el PR upstream (lib default 64KB). keepAlive cherry-picked a feat/copy-data-extension (76ad956). DarkSSH sigue en cbssh 0.3.3-SNAPSHOT (0.4.3 rompe Hilt por metadatos Kotlin 2.4 vs 2.3 — no subir). Benchmark temporal eliminado; fork limpio con 3 commits para el PR.
**Verified**: benchmark en PC (DL 130 / UL 170 MB/s) + fork limpio + DarkSSH assembleDebug OK con 0.3.3.
**Completed**: quality-009
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
## 2026-08-18 14:13 — ClientSSH
**Summary**: quality-008: el log LAN con RTT de 400-3000ms contra IP local delató el cuello interno — openSessionChannel() de cbssh abre el canal SFTP con ventana de 64KB (round-trip stall por cada 64KB). Fix en el fork: ventana 16MB + packet 32KB (a3a7cb0), publicado a mavenLocal, DarkSSH recompilado. El jar publicado antes no incluía los fixes de ventana de junio (49af22a/ea1f04b). Pendiente retest del usuario en LAN/VPN.
**Verified**: publishToMavenLocal OK + testDebugUnitTest OK + ./init.sh BUILD SUCCESSFUL en DarkSSH; pendiente retest en dispositivo
**Completed**: none
---
---
## 2026-08-18 14:48 — ClientSSH
**Summary**: Benchmark decisivo de cbssh en PC (mismo pipeline depth 32 chunk 128KB contra localhost): DL 130MB/s, UL 170MB/s. El código de cbssh NO es el cuello — el throughput del móvil (DL 6.4MB/s = 91% del WiFi, UL 3.2MB/s) es el techo físico del teléfono. El RTT medido alto era bufferbloat del radio saturado. quality-008 fue falso positivo para la app (el jar 0.3.3 ya tenía la ventana 16MB). keepAlive cherry-picked al branch del PR. DarkSSH sigue en 0.3.3 (0.4.3 rompe Hilt por metadatos Kotlin). Benchmark temporal eliminado. Fork limpio con 3 commits para el PR: copy-data + ventana 16MB + keepAlive.
**Verified**: benchmark PC (DL 130 / UL 170 MB/s) + DarkSSH assembleDebug OK con cbssh 0.3.3-SNAPSHOT + fork pusheado limpio
**Completed**: none
---
---
## 2026-08-18 19:58 — ClientSSH
**Summary**: Validación final del plan de throughput con log real: 28-32 MB/s (DL y UL) vs 3.4 original — el engine optimizado entrega ~30MB/s estables cuando la red lo permite. El límite anterior era el WiFi 2.4GHz del router (5GHz muerto: RT-AX54, diagnosticado por SSH: driver carga pero no transmite, descartados smart-connect/SSID oculto/canal fijo/reboot — probable hardware/firmware). Bug nuevo registrado: cancelar descarga mata la conexión SFTP ('Failed to write 68 bytes'). Commits 0fd03477.
**Verified**: Log real dispositivo: 3 corridas (28.0/29.9/31.7 MB/s); diagnóstico router por SSH completo; tests verdes previos
**Completed**: none
---
---
## 2026-08-19 04:15 — ClientSSH
**Summary**: PR copy-data verificado con calidad completa: ./gradlew build en cbssh-fork pasa 1215 tests (0 fallos) + spotless + metalava + kover. Fixes aplicados: spotless espaciado en SshClient.kt, api.txt con keepAliveIntervalMs regenerado, openSftp no rompe el test de upstream (ventana movida a default de openSessionChannel), .memory-project removido del PR, rebase autosquash limpio. Branch feat/copy-data-extension (3 commits) force-pusheado a JDis03/cbssh. Pendiente: testing en dispositivo real (TODO 204) antes de abrir el PR (TODO 227).
**Verified**: ./gradlew build en cbssh-fork: BUILD SUCCESSFUL (1215 tests, 0 fallos; spotless, metalava, kover OK); branch pusheado
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
## 2026-08-31 01:55 — ClientSSH
**Summary**: Analizado el log del corte de upload al 72%: cae EXACTO en 1 GiB, que es el rekeyBytesLimit default de cbssh que DarkSSH nunca sobreescribe — el usuario tenía razón en que es código, no red. Hipótesis del rekey rompiendo el pipeline NO confirmada: mi repro pipelineado (depth 32, rekeyBytesLimit 256KB) contra OpenSSH real PASA en loopback; quedó aislado en la rama investigate/rekey-pipelining para no contaminar el PR #273. Descubierto que el test de rekey upstream es secuencial, o sea el path pipelineado no tenía cobertura. Arreglado el bloqueante del diagnóstico (quality-013): uploadFile descartaba el motivo del fallo, por eso el log solo decía "falling back to SCP"; ahora describeSftpFailure() loguea ServerError/ProtocolError/IoError. Commit 5df14621.
**Verified**: compileDebugKotlin + testDebugUnitTest + ./init.sh BUILD SUCCESSFUL; repro del fork verificado en test-results XML (3.199s, 0 failures)
**Completed**: none
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