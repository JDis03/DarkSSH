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
---
---
## 2026-07-08 03:31 — ClientSSH
**Summary**: Análisis y fix de 3 bugs estructurales en tabs: (1) collectAsState dentro de forEach en TabBar → key(tab.id){}, (2) isSyncing race condition con 4 efectos → una sola dirección TabManager→pager, (3) DisposableEffect con captures stale eliminado. -44 líneas, código más simple y correcto.
**Verified**: ./init.sh BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-08 02:26 — ClientSSH
**Summary**: Regression check: 2 fixes. (1) connectedHostIds SharingStarted.WhileSubscribed→Eagerly porque el Service gestiona su propio lifecycle. (2) cloneHost firstNotNullOf(2..99)→generateSequence para evitar NoSuchElementException. NavGraph sin connectedHostIds es aceptable (default emptySet).
**Verified**: ./init.sh BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-08 02:22 — ClientSSH
**Summary**: Clone host estilo Termius: duplica la config inmediatamente sin diálogos. Nickname único (Copy of X, Copy of X (2)...), id=0L para nuevo row, lastConnected=null. Aparece en lista via Flow al instante.
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
## 2026-07-07 20:52 — ClientSSH
**Summary**: Indicador online/offline por host con arquitectura escalable. TerminalService.connectedHostIds (flatMapLatest+combine), pasado como Set<Long> a HostListScreen, punto verde/gris de 12dp sobre el icono del host. ViewModel no se acopla al Service.
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
## 2026-07-07 20:29 — ClientSSH
**Summary**: Modernización de HostListScreen: swipe-to-delete con animación, botón SFTP prominente (FilledTonalButton full-width), card elevation mejorada, mejor spacing y jerarquía visual. Edit button discreto en esquina. UX más intuitiva y moderna.
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-07 20:15 — ClientSSH
**Summary**: Fix back button en host editor: ahora cierra el diálogo en lugar de salir de la app. BackHandler verifica showHostEditor primero antes de manejar navegación entre tabs. Orden de prioridad: diálogos → navegación → exit.
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
## 2026-07-07 18:49 — ClientSSH
**Summary**: Fix bug en HostEditor: después de editar un host, al hacer Add aparecían los datos del host anterior. Root cause: ViewModel._host.value no se limpiaba cuando hostId cambiaba de N a -1L. Fix: loadHost() limpia _host = null cuando hostId <= 0, y LaunchedEffect siempre llama loadHost(hostId).
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev
**Completed**: none
---
---
## 2026-07-05 23:24 — ClientSSH
**Summary**: Fix para borrar carpetas no vacías en SFTP al usar rmdir que fallaba silenciosamente. deleteDirectoryViaSsh usa rm -rf vía SSH exec, con fallback al rmdir de SFTP para carpetas vacías.
**Verified**: BUILD SUCCESSFUL, pushed a feature/master/dev, confirmación de compilación únicamente (no tests)
**Completed**: none
---
---
## 2026-07-03 03:21 — ClientSSH
**Summary**: Sesión de fix SFTP copy/move + orden de ramas. Fixes: (1) moveFile usa SSH mv -f con fallback SFTP rename, (2) copyFileViaSsh con overwrite flag y skip same-path, (3) pasteFiles integrado con TransferQueue panel mostrando progreso por archivo. Ramas master/dev/feature sincronizadas con force-push a aaa9d2dc. App estable con todos los features.
**Verified**: BUILD SUCCESSFUL, 4 commits pushed, 3 ramas sincronizadas
**Completed**: none
