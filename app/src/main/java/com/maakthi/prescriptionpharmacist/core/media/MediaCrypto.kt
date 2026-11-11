package com.maakthi.prescriptionpharmacist.core.media

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * At-rest encryption for saved images and sensitive metadata: AES-256-GCM
 * with a device key (32 bytes, provided by the caller — on Android it comes
 * from the Android Keystore). Layout: magic | iv(12) | ciphertext+tag.
 *
 * Pure and unit-tested: wrong key or a tampered file returns null — it never
 * produces garbage.
 */
object MediaCrypto {

    private val MAGIC = "PPIMG1\n".toByteArray(Charsets.US_ASCII)
    private const val IV_SIZE = 12

    fun encrypt(keyBytes: ByteArray, plain: ByteArray): ByteArray {
        require(keyBytes.size == 32) { "key must be 32 bytes" }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plain)
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(iv)
        out.write(ciphertext)
        return out.toByteArray()
    }

    /** Returns the plaintext, or null for a wrong key / corrupted file. */
    fun decrypt(keyBytes: ByteArray, data: ByteArray): ByteArray? {
        return try {
            if (data.size <= MAGIC.size + IV_SIZE + 16) return null
            val input = ByteArrayInputStream(data)
            val magic = ByteArray(MAGIC.size).also { input.read(it) }
            if (!magic.contentEquals(MAGIC)) return null
            val iv = ByteArray(IV_SIZE).also { input.read(it) }
            val ciphertext = input.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            null
        }
    }

    /** Encrypts one short sensitive string (e.g. the owner name) for storage. */
    fun encryptString(keyBytes: ByteArray, value: String): String =
        toHex(encrypt(keyBytes, value.toByteArray(Charsets.UTF_8)))

    /** Decrypts a value stored with [encryptString]; null when unreadable. */
    fun decryptString(keyBytes: ByteArray, stored: String): String? {
        val bytes = fromHex(stored) ?: return null
        return decrypt(keyBytes, bytes)?.let { String(it, Charsets.UTF_8) }
    }

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun fromHex(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
