package com.maakthi.prescriptionpharmacist.data.media

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-bound key for media encryption (envelope pattern):
 *
 * A random AES-256 data key is wrapped by a NON-EXTRACTABLE Android Keystore
 * key and stored wrapped in app-private preferences. The raw data key exists
 * only in memory and is handed to the pure
 * [com.maakthi.prescriptionpharmacist.core.media.MediaCrypto] engine.
 */
@Singleton
class DeviceKeyStore @Inject constructor(
    private val context: Context
) {

    @Volatile
    private var cached: ByteArray? = null

    fun keyBytes(): ByteArray {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val wrapped = prefs.getString(KEY_BLOB, null)
            val plain = if (wrapped != null) {
                unwrap(fromHex(wrapped)) ?: regenerate(prefs)
            } else {
                regenerate(prefs)
            }
            cached = plain
            return plain
        }
    }

    private fun regenerate(prefs: android.content.SharedPreferences): ByteArray {
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_BLOB, toHex(wrap(fresh))).apply()
        return fresh
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(WRAP_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAP_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun wrap(dataKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        return cipher.iv + cipher.doFinal(dataKey)
    }

    private fun unwrap(blob: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, blob, 0, 12))
            cipher.doFinal(blob, 12, blob.size - 12)
        } catch (e: Exception) {
            null
        }
    }

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun fromHex(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val WRAP_ALIAS = "pp_media_wrap_key"
        private const val PREFS = "pp_media_keys"
        private const val KEY_BLOB = "wrapped_data_key"
    }
}
