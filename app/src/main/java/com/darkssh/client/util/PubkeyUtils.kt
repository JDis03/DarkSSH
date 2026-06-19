package com.darkssh.client.util

import com.darkssh.client.data.entity.Pubkey
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

object PubkeyUtils {
    object KeyType {
        const val RSA = "RSA"
        const val DSA = "DSA"
        const val EC = "EC"
        const val ED25519 = "Ed25519"
        const val IMPORTED = "IMPORTED"
    }

    fun getEncodedPrivate(
        pk: PrivateKey,
        secret: String?,
    ): ByteArray {
        val raw = pk.encoded
        return if (secret.isNullOrBlank()) {
            raw
        } else {
            Encryptor.encrypt(raw, secret)
        }
    }

    fun decodePrivate(
        encoded: ByteArray,
        keyType: String,
        secret: String?,
    ): PrivateKey {
        val data =
            if (!secret.isNullOrBlank()) {
                Encryptor.decrypt(encoded, secret)
            } else {
                encoded
            }
        val spec = PKCS8EncodedKeySpec(data)
        val factory = KeyFactory.getInstance(keyType)
        return factory.generatePrivate(spec)
    }

    fun decodePublic(
        encoded: ByteArray,
        keyType: String,
    ): PublicKey {
        val spec = X509EncodedKeySpec(encoded)
        val factory = KeyFactory.getInstance(keyType)
        return factory.generatePublic(spec)
    }

    fun convertToKeyPair(
        pubkey: Pubkey,
        password: String?,
    ): KeyPair? {
        return try {
            val privateKeyBytes = pubkey.privateKey ?: return null
            when (pubkey.type) {
                KeyType.IMPORTED -> {
                    val pemData = String(privateKeyBytes, Charsets.UTF_8)
                    parsePEMKeyPair(pemData, password)
                }

                else -> {
                    val priv = decodePrivate(privateKeyBytes, pubkey.type, password)
                    val pub = decodePublic(pubkey.publicKey, pubkey.type)
                    KeyPair(pub, priv)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePEMKeyPair(
        pemData: String,
        password: String?,
    ): KeyPair? =
        try {
            com.trilead.ssh2.crypto.PEMDecoder.decode(
                pemData.toCharArray(),
                password ?: "",
            )
        } catch (e: Exception) {
            null
        }

    data class KeyTypeInfo(
        val name: String,
        val minBits: Int,
        val maxBits: Int,
        val defaultBits: Int,
    )

    val KEY_TYPES =
        listOf(
            KeyTypeInfo(KeyType.RSA, 1024, 16384, 2048),
            KeyTypeInfo(KeyType.DSA, 1024, 1024, 1024),
            KeyTypeInfo(KeyType.EC, 256, 521, 256),
            KeyTypeInfo(KeyType.ED25519, 255, 255, 255),
        )

    fun getDefaultBits(keyType: String): Int = KEY_TYPES.find { it.name == keyType }?.defaultBits ?: 2048

    fun getMinBits(keyType: String): Int = KEY_TYPES.find { it.name == keyType }?.minBits ?: 1024

    fun getMaxBits(keyType: String): Int = KEY_TYPES.find { it.name == keyType }?.maxBits ?: 16384

    fun isFixedBits(keyType: String): Boolean = keyType == KeyType.DSA || keyType == KeyType.ED25519
}
