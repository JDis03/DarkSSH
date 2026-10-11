package com.darkssh.client.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardInteractivePasswordTest {
    @Test
    fun `detects OpenSSH PAM password prompts`() {
        assertTrue(KeyboardInteractivePassword.isPasswordPrompt(1, "Password: ", false))
        assertTrue(KeyboardInteractivePassword.isPasswordPrompt(1, "(dark@host) Password:", false))
        assertTrue(KeyboardInteractivePassword.isPasswordPrompt(1, "Contraseña: ", false))
    }

    @Test
    fun `ignores OTP, visible and multi-prompt challenges`() {
        assertFalse(KeyboardInteractivePassword.isPasswordPrompt(1, "Verification code: ", false))
        assertFalse(KeyboardInteractivePassword.isPasswordPrompt(1, "Password: ", true))
        assertFalse(KeyboardInteractivePassword.isPasswordPrompt(2, "Password: ", false))
        assertFalse(KeyboardInteractivePassword.isPasswordPrompt(1, null, false))
    }

    @Test
    fun `password change prompts never receive the saved password`() {
        assertFalse(KeyboardInteractivePassword.isPasswordPrompt(1, "New password: ", false))
        assertFalse(KeyboardInteractivePassword.isPasswordPrompt(1, "Retype new password: ", false))
        assertFalse(KeyboardInteractivePassword.isPasswordPrompt(1, "Nueva contraseña: ", false))
        assertTrue(KeyboardInteractivePassword.isPasswordPrompt(1, "Current password: ", false))
    }
}
