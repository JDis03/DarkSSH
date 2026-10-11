package com.darkssh.client.transport

import java.util.Locale

/**
 * OpenSSH with PAM usually asks for the account password through
 * keyboard-interactive instead of the plain "password" auth method.
 * Detect that single hidden password prompt so a remembered password can be
 * reused and a newly typed one can be saved.
 */
internal object KeyboardInteractivePassword {
    private val passwordWords = listOf("password", "contraseña", "contrasena", "passwort", "mot de passe", "senha")

    /** Password-change prompts (expired accounts) must never receive the saved password. */
    private val passwordChangeWords = listOf("new ", "retype", "again", "nueva", "repita", "repetir")

    fun isPasswordPrompt(
        numPrompts: Int,
        prompt: String?,
        echo: Boolean,
    ): Boolean {
        if (numPrompts != 1 || echo || prompt == null) return false
        val normalized = prompt.lowercase(Locale.ROOT)
        if (passwordChangeWords.any { it in normalized }) return false
        return passwordWords.any { it in normalized }
    }
}
