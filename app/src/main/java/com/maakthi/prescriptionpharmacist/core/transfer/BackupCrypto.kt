package com.maakthi.prescriptionpharmacist.core.transfer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted backup format: passphrase-derived key (PBKDF2-HMAC-SHA256)
 * + AES-256-GCM (authenticated encryption).
 *
 * Layout: magic | iterations(Int, BE) | salt(16) | iv(12) | ciphertext+tag.
 * A wrong passphrase or a tampered file fails the GCM tag check and
 * [decrypt] returns null — it never produces garbage.
 */
object BackupCrypto {

    private val MAGIC = "PPBAK1\n".toByteArray(Charsets.US_ASCII)
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val DEFAULT_ITERATIONS = 120_000

    fun encrypt(
        passphrase: String,
        plain: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS
    ): ByteArray {
        require(passphrase.isNotEmpty()) { "passphrase required" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { random.nextBytes(it) }
        val key = deriveKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plain)

        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(intToBytes(iterations))
        out.write(salt)
        out.write(iv)
        out.write(ciphertext)
        return out.toByteArray()
    }

    /** Returns the plaintext, or null for a wrong passphrase / corrupted file. */
    fun decrypt(passphrase: String, data: ByteArray): ByteArray? {
        return try {
            val input = ByteArrayInputStream(data)
            val magic = ByteArray(MAGIC.size).also { input.read(it) }
            if (!magic.contentEquals(MAGIC)) return null
            val iterationsBytes = ByteArray(4).also { input.read(it) }
            val iterations = bytesToInt(iterationsBytes)
            if (iterations <= 0 || iterations > 5_000_000) return null
            val salt = ByteArray(SALT_SIZE).also { input.read(it) }
            val iv = ByteArray(IV_SIZE).also { input.read(it) }
            val ciphertext = input.readBytes()
            if (ciphertext.isEmpty()) return null

            val key = deriveKey(passphrase, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            // Bad passphrase, truncated file, tampered content — all fail here.
            null
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()
    )

    private fun bytesToInt(bytes: ByteArray): Int =
        ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
}
