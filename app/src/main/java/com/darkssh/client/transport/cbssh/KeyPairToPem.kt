/*
 * DarkSSH SFTP Client - cbssh Migration
 * Copyright 2026 DarkSSH
 *
 * Helper to convert java.security.KeyPair to PEM format for cbssh authentication.
 * Based on cbssh's OpenSshKeyWriter implementation (which is internal).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.darkssh.client.transport.cbssh

import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.EdECPrivateKey
import java.security.interfaces.RSAPrivateCrtKey

/**
 * Convert a [KeyPair] to OpenSSH PEM format that cbssh can authenticate with.
 *
 * Supports RSA, ECDSA (P-256/P-384/P-521), and Ed25519 keys.
 * Encrypted keys (with passphrase) are not supported yet.
 *
 * This is a simplified implementation based on cbssh's internal OpenSshKeyWriter.
 */
internal object KeyPairToPem {

    private val OPENSSH_V1_MAGIC = "openssh-key-v1\u0000".toByteArray(Charsets.US_ASCII)
    private const val LINE_LENGTH = 70

    /**
     * Convert keypair to unencrypted OpenSSH PEM string.
     *
     * @return PEM-formatted private key (OpenSSH format)
     * @throws IllegalArgumentException if key type is not supported
     */
    fun toPem(keyPair: KeyPair): String {
        val keyType = inferKeyType(keyPair)
        val publicKeyBlob = encodePublicKeyBlob(keyPair, keyType)
        val privateSection = buildPrivateSection(keyPair, keyType)

        val binaryData = buildBinaryData(
            cipherName = "none",
            kdfName = "none",
            kdfOptions = ByteArray(0),
            publicKeyBlob = publicKeyBlob,
            encryptedSection = privateSection,
        )

        return formatOutput(binaryData)
    }

    /**
     * Infer the SSH key type string from the keypair.
     */
    private fun inferKeyType(keyPair: KeyPair): String {
        return when {
            keyPair.private is EdECPrivateKey -> "ssh-ed25519"
            keyPair.private is ECPrivateKey -> {
                val ecPub = keyPair.public as ECPublicKey
                val fieldSize = (ecPub.params.order.bitLength() + 7) / 8
                when (fieldSize) {
                    32 -> "ecdsa-sha2-nistp256"
                    48 -> "ecdsa-sha2-nistp384"
                    66 -> "ecdsa-sha2-nistp521"
                    else -> throw IllegalArgumentException(
                        "Unsupported ECDSA field size: $fieldSize",
                    )
                }
            }
            keyPair.private is RSAPrivateCrtKey -> "ssh-rsa"
            else -> throw IllegalArgumentException(
                "Unsupported key type: ${keyPair.private.javaClass.name}",
            )
        }
    }

    /**
     * Encode the public key in SSH wire format.
     */
    private fun encodePublicKeyBlob(keyPair: KeyPair, keyType: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(encodeSshString(keyType.toByteArray(Charsets.US_ASCII)))

        when {
            keyType == "ssh-ed25519" -> {
                val pubEncoded = keyPair.public.encoded
                val pubKey32 = pubEncoded.copyOfRange(pubEncoded.size - 32, pubEncoded.size)
                out.write(encodeSshString(pubKey32))
            }
            keyType.startsWith("ecdsa-sha2-") -> {
                val ecPub = keyPair.public as ECPublicKey
                val curveName = keyType.removePrefix("ecdsa-sha2-")
                out.write(encodeSshString(curveName.toByteArray(Charsets.US_ASCII)))

                val fieldSize = (ecPub.params.order.bitLength() + 7) / 8
                val qBytes = encodeEcPoint(ecPub, fieldSize)
                out.write(encodeSshString(qBytes))
            }
            keyType == "ssh-rsa" -> {
                val rsaPub = keyPair.public as java.security.interfaces.RSAPublicKey
                out.write(encodeMpint(rsaPub.publicExponent.toByteArray()))
                out.write(encodeMpint(rsaPub.modulus.toByteArray()))
            }
        }

        return out.toByteArray()
    }

    /**
     * Build the private section of the OpenSSH key format.
     */
    private fun buildPrivateSection(keyPair: KeyPair, keyType: String): ByteArray {
        // Random checkint for verification
        val checkInt = (Math.random() * Int.MAX_VALUE).toInt()
        val out = ByteArrayOutputStream()
        out.write(encodeUint32(checkInt))
        out.write(encodeUint32(checkInt))

        when {
            keyType == "ssh-ed25519" -> writeEd25519Private(out, keyPair)
            keyType.startsWith("ecdsa-sha2-") -> writeEcdsaPrivate(out, keyPair, keyType)
            keyType == "ssh-rsa" -> writeRsaPrivate(out, keyPair)
            else -> throw IllegalArgumentException("Unsupported key type: $keyType")
        }

        // Empty comment
        out.write(encodeSshString(ByteArray(0)))

        // Padding to block size (8 for unencrypted)
        val blockSize = 8
        val currentSize = out.size()
        val paddingNeeded = (blockSize - (currentSize % blockSize)) % blockSize
        for (i in 1..paddingNeeded) {
            out.write(i and 0xFF)
        }

        return out.toByteArray()
    }

    private fun writeEd25519Private(out: ByteArrayOutputStream, keyPair: KeyPair) {
        out.write(encodeSshString("ssh-ed25519".toByteArray(Charsets.US_ASCII)))

        // Public key (last 32 bytes of X.509 encoding)
        val pubEncoded = keyPair.public.encoded
        val pubKey32 = pubEncoded.copyOfRange(pubEncoded.size - 32, pubEncoded.size)
        out.write(encodeSshString(pubKey32))

        // Private key seed
        val seed = extractEd25519Seed(keyPair)

        // Private section: seed || public (64 bytes)
        val privBytes = ByteArray(64)
        System.arraycopy(seed, 0, privBytes, 0, 32)
        System.arraycopy(pubKey32, 0, privBytes, 32, 32)
        out.write(encodeSshString(privBytes))
    }

    private fun extractEd25519Seed(keyPair: KeyPair): ByteArray {
        val privKey = keyPair.private
        return when {
            privKey is EdECPrivateKey -> {
                privKey.bytes.orElseThrow {
                    IllegalArgumentException("Cannot extract Ed25519 seed")
                }
            }
            else -> throw IllegalArgumentException(
                "Cannot extract Ed25519 seed from ${privKey.javaClass.name}",
            )
        }
    }

    private fun writeEcdsaPrivate(out: ByteArrayOutputStream, keyPair: KeyPair, keyType: String) {
        out.write(encodeSshString(keyType.toByteArray(Charsets.US_ASCII)))

        val ecPub = keyPair.public as ECPublicKey
        val ecPriv = keyPair.private as ECPrivateKey

        val curveName = keyType.removePrefix("ecdsa-sha2-")
        val fieldSize = (ecPub.params.order.bitLength() + 7) / 8

        out.write(encodeSshString(curveName.toByteArray(Charsets.US_ASCII)))
        out.write(encodeSshString(encodeEcPoint(ecPub, fieldSize)))
        out.write(encodeMpint(ecPriv.s.toByteArray()))
    }

    private fun writeRsaPrivate(out: ByteArrayOutputStream, keyPair: KeyPair) {
        val rsaPriv = keyPair.private as RSAPrivateCrtKey

        out.write(encodeMpint(rsaPriv.modulus.toByteArray()))
        out.write(encodeMpint(rsaPriv.publicExponent.toByteArray()))
        out.write(encodeMpint(rsaPriv.privateExponent.toByteArray()))
        out.write(encodeMpint(rsaPriv.crtCoefficient.toByteArray()))
        out.write(encodeMpint(rsaPriv.primeP.toByteArray()))
        out.write(encodeMpint(rsaPriv.primeQ.toByteArray()))
    }

    private fun encodeEcPoint(ecPub: ECPublicKey, fieldSize: Int): ByteArray {
        val w = ecPub.w
        val x = w.affineX.toByteArray()
        val y = w.affineY.toByteArray()

        // Pad to fieldSize
        val xPadded = ByteArray(fieldSize)
        val yPadded = ByteArray(fieldSize)
        val xOffset = fieldSize - x.size
        val yOffset = fieldSize - y.size
        if (xOffset >= 0) System.arraycopy(x, 0, xPadded, xOffset, x.size)
        if (yOffset >= 0) System.arraycopy(y, 0, yPadded, yOffset, y.size)

        // Uncompressed point format: 0x04 || X || Y
        val result = ByteArray(1 + 2 * fieldSize)
        result[0] = 0x04
        System.arraycopy(xPadded, 0, result, 1, fieldSize)
        System.arraycopy(yPadded, 0, result, 1 + fieldSize, fieldSize)
        return result
    }

    private fun buildBinaryData(
        cipherName: String,
        kdfName: String,
        kdfOptions: ByteArray,
        publicKeyBlob: ByteArray,
        encryptedSection: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(OPENSSH_V1_MAGIC)
        out.write(encodeSshString(cipherName.toByteArray(Charsets.US_ASCII)))
        out.write(encodeSshString(kdfName.toByteArray(Charsets.US_ASCII)))
        out.write(encodeSshString(kdfOptions))
        out.write(encodeUint32(1)) // number of keys
        out.write(encodeSshString(publicKeyBlob))
        out.write(encodeSshString(encryptedSection))
        return out.toByteArray()
    }

    private fun encodeSshString(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(encodeUint32(data.size))
        out.write(data)
        return out.toByteArray()
    }

    private fun encodeUint32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun encodeMpint(value: ByteArray): ByteArray {
        // SSH mpint format: 4-byte length || big-endian integer
        // If high bit is set, prepend 0x00 to indicate positive number
        val needsPad = value.isNotEmpty() && (value[0].toInt() and 0x80) != 0
        val data = if (needsPad) {
            byteArrayOf(0) + value
        } else {
            value
        }
        return encodeSshString(data)
    }

    private fun formatOutput(data: ByteArray): String {
        val sb = StringBuilder()
        sb.appendLine("-----BEGIN OPENSSH PRIVATE KEY-----")
        val base64 = java.util.Base64.getEncoder().encodeToString(data)
        sb.appendLine(base64.chunked(LINE_LENGTH).joinToString("\n"))
        sb.appendLine("-----END OPENSSH PRIVATE KEY-----")
        return sb.toString()
    }
}