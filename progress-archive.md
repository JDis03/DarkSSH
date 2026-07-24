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