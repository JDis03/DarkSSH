package com.darkssh.client.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Encryptor {
    private const val CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val KEY_SIZE = 16
    private const val SALT_SIZE = 8
    private const val ITERATIONS = 1000

    fun encrypt(
        data: ByteArray,
        secret: String,
    ): ByteArray {
        val salt = ByteArray(SALT_SIZE)
        SecureRandom.getInstance("SHA1PRNG").nextBytes(salt)

        val keyAndIv = deriveKeyAndIv(secret, salt)
        val keySpec = SecretKeySpec(keyAndIv, 0, KEY_SIZE, "AES")
        val ivSpec = IvParameterSpec(keyAndIv, KEY_SIZE, KEY_SIZE)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(data)

        return salt + encrypted
    }

    fun decrypt(
        data: ByteArray,
        secret: String,
    ): ByteArray {
        val salt = data.copyOfRange(0, SALT_SIZE)
        val encrypted = data.copyOfRange(SALT_SIZE, data.size)

        val keyAndIv = deriveKeyAndIv(secret, salt)
        val keySpec = SecretKeySpec(keyAndIv, 0, KEY_SIZE, "AES")
        val ivSpec = IvParameterSpec(keyAndIv, KEY_SIZE, KEY_SIZE)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(encrypted)
    }

    private fun deriveKeyAndIv(
        secret: String,
        salt: ByteArray,
    ): ByteArray {
        var hash = sha256(salt + secret.toByteArray())
        repeat(ITERATIONS - 1) {
            hash = sha256(salt + hash)
        }
        return hash
    }

    private fun sha256(data: ByteArray): ByteArray =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(data)
}
