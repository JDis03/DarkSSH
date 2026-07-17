# Bracketed Paste para el Terminal Emulator de DarkKeyboard

## Estado: Implementado (2026-07-14)

Fix aplicado en `TerminalView.java` (`sendTextToTerminal`) y `TerminalBridge.kt`
(`onPasteTextFromClipboard`). Resumen de lo corregido:

- **IME paste (Gboard, SwiftKey, etc.)**: `commitText()` ahora detecta paste
  real con `Character.codePointCount(text, 0, len) > 1` (NO `text.length() <= 1`
  como sugería la sección "Qué hay que implementar" más abajo — `length()` cuenta
  UTF-16 code units, no codepoints, y rompería con un solo emoji fuera del BMP,
  que son 2 chars/1 codepoint). Si es paste, se enruta por
  `TerminalEmulator#paste()` en vez de character-by-character `inputCodePoint()`.
- **Menú contextual "Paste" (long-press)**: `onPasteTextFromClipboard()` en
  `TerminalBridge.kt` era un stub vacío que solo logueaba — no pegaba nada. Ahora
  lee `clipboardManager.primaryClip` y llama a `terminalEmulator.paste(text)`.
- **Mouse botón central**: ya funcionaba correctamente antes del fix (sin cambios).

El resto de este documento es el diagnóstico original, se mantiene como referencia.

## Problema

La app darkred (TUI Rust, ratatui + crossterm) tiene un sistema de
"paste attachment" que reemplaza pastes largos por chips `[pasted #N]`.
Este sistema **solo funciona** cuando crossterm recibe `Event::Paste`,
que es generado únicamente si el terminal emula **bracketed paste**
(`\e[?2004h` / secuencias `\e[200~` / `\e[201~`).

Tu Terminal Emulator (basado en Termux) **no envía** estas secuencias
de bracketed paste, por lo que crossterm nunca genera `Event::Paste`.
El paste llega como keystrokes individuales (`Key(KeyEvent { Char(c) })`),
y el código de attach (que solo vive en el handler de `Event::Paste`)
nunca se ejecuta.

## Diagnóstico

| Prueba | Resultado |
|---|---|
| ¿Terminal envía `\e[?2004h`? | Lo recibe de darkred |
| ¿Terminal procesa DECSET 2004? | Depende de tu implementación |
| ¿Paste envuelto con `\e[200~` / `\e[201~`? | ❌ No se envuelve |
| ¿Paste enviado como keystrokes individuales? | ✅ Así llega hoy |
| `Event::Paste` en crossterm | ❌ Nunca se genera |

## Qué hay que implementar

### 1. Manejar DECSET 2004 (enable/disable)

Tu emulador de terminal recibe de la app (darkred, vim, etc.)
`\e[?2004h` para activar y `\e[?2004l` para desactivar.
Guardar el estado en un flag.

En el manejador de escape sequences (DECSET/DECRST):

```kotlin
// Manejador de DECSET (SET) / DECRST (RESET)
private var bracketedPasteEnabled = false

fun handleDecSet(decsetBit: Int, set: Boolean) {
    when (decsetBit) {
        2004 -> bracketedPasteEnabled = set
        // otros bits...
    }
}
```

### 2. Envolver el paste con los marcadores

Cuando el usuario pega texto **y** `bracketedPasteEnabled == true`,
el texto debe escribirse al PTY envuelto en marcadores:

```kotlin
fun paste(text: String) {
    val bytes = if (bracketedPasteEnabled) {
        "\u001b[200~${text}\u001b[201~".toByteArray(Charsets.UTF_8)
    } else {
        text.toByteArray(Charsets.UTF_8)
    }
    writeToProcess(bytes)
}
```

### 3. Interceptar el paste desde Android

Android entrega el paste de tres formas. **Todas deben llamar a
`paste()`**, NO enviar caracteres uno por uno.

#### a. Menú contextual (ACTION_PASTE)

```kotlin
override fun onTextContextMenuItem(id: Int): Boolean {
    when (id) {
        android.R.id.paste -> {
            val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip ?: return true
            val text = clip.getItemAt(0).coerceToText(context).toString()
            paste(text)    // ← envía wrapper si bracketed paste activo
            return true
        }
        else -> return super.onTextContextMenuItem(id)
    }
}
```

#### b. IME (teclado virtual) — commitText con texto largo

Cuando el IME hace paste (ej: Gboard pega desde su propio clipboard),
entra por `commitText`. El tipeo normal es 1 caracter a la vez;
los pastes del IME suelen mandar todo el texto en un solo commit.

```kotlin
override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
    val asString = text.toString()
    // Si es un solo caracter, es tipeo normal →
    if (asString.length <= 1) {
        return sendChar(asString.first())
    }
    // Si son múltiples caracteres, es probablemente un paste →
    paste(asString)
    return true
}
```

**⚠️ Cuidado**: Algunos IMEs mandan autocompletado/sugerencias como
`commitText` largo. Para distinguir, podés usar un threshold más
alto (ej: > 50 chars) o trackear si hubo un `startBatchEdit()`
sin `endBatchEdit()` previo (los IMEs que hacen paste suelen
envolverlo en batch edits). Pero en la práctica, texto > 10 chars
en un solo commitText es casi siempre un paste.

#### c. KeyEvents individuales (NO hacer)

El error clásico: cuando el código del emulador recibe caracteres del
clipboard y los reescribe como `KeyEvent` individuales:

```kotlin
// ❌ MAL — rompe bracketed paste
for (c in text) {
    writeToProcess(c.code.toByte())
}

// ✅ BIEN — llamar paste() que envuelve si corresponde
paste(text)
```

### 4. Casos borde

| Situación | Qué pasa si NO se maneja |
|---|---|
| Paste sin DECSET 2004 activo | `paste()` manda texto sin wrapper — la app lo recibe como keystrokes, funciona, pero no crea attach chips |
| Paste parcial (interrumpido) | Si el usuario cierra el teclado antes de que termine el paste, el `\e[201~` nunca se envía → crossterm se cuelga (issue #1060). **Siempre enviar `\e[201~`** incluso si el paste se cancela |
| Múltiples pastes rápidos | Cada `paste()` envía un par completo `\e[200~...\e[201~`. No anidar |
| OSC 52 (clipboard remoto) | Si el terminal recibe OSC 52 del host (copy desde el TUI), no tiene nada que ver con bracketed paste — es el flujo inverso |

### 5. Resumen de secuencias

| Secuencia | Quién envía | Qué significa |
|---|---|---|
| `\e[?2004h` | App (darkred) → Terminal | Activá bracketed paste mode |
| `\e[?2004l` | App (darkred) → Terminal | Desactivá bracketed paste mode |
| `\e[200~` | Terminal → App | Empieza paste — todo lo que sigue es pasted text |
| `\e[201~` | Terminal → App | Termina paste — los caracteres que siguen son tipeo normal |

## Cómo probar

1. Habilitar debug log en darkred (`~/.darkred-workspace/logs/darkred_debug.log`)
2. Abrir darkred dentro del terminal de DarkKeyboard
3. Pegar texto
4. Revisar log: debe aparecer `PASTE received: N bytes, M chars`
5. Si aparece, el attach funciona
6. Si no aparece, el paste sigue yendo como keystrokes

## Referencias

- [Wikipedia: Bracketed-paste](https://en.wikipedia.org/wiki/Bracketed-paste)
- [xterm control sequences (DECSET 2004)](https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h2-Bracketed-Paste-Mode)
- [crossterm Event::Paste (feature-gated)](https://github.com/crossterm-rs/crossterm/blob/master/src/event.rs)
- [crossterm Issue #1060 — parser cuelga si `\e[201~` nunca llega](https://github.com/crossterm-rs/crossterm/issues/1060)
- [Termux TerminalEmulator.java — DECSET_BIT_BRACKETED_PASTE_MODE](https://github.com/termux/termux-app/blob/master/terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java)
