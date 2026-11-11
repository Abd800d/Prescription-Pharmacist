package com.maakthi.prescriptionpharmacist.data.sync

import android.content.Context
import com.maakthi.prescriptionpharmacist.core.media.MediaCrypto
import com.maakthi.prescriptionpharmacist.data.media.DeviceKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the user's backup passphrase, encrypted with the device key
 * (Android Keystore envelope). The plaintext never touches disk; auto-backup
 * decrypts it only in memory. Restoring on another device asks the user for
 * the passphrase directly.
 */
@Singleton
class BackupVault @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceKeyStore: DeviceKeyStore
) {

    private val prefs = context.getSharedPreferences("pp_backup_vault", Context.MODE_PRIVATE)

    fun hasPassphrase(): Boolean = prefs.contains(KEY)

    fun setPassphrase(passphrase: String) {
        val stored = MediaCrypto.encryptString(deviceKeyStore.keyBytes(), passphrase)
        prefs.edit().putString(KEY, stored).apply()
    }

    fun passphrase(): String? {
        val stored = prefs.getString(KEY, null) ?: return null
        return MediaCrypto.decryptString(deviceKeyStore.keyBytes(), stored)
    }

    companion object {
        private const val KEY = "backup_passphrase_enc"
    }
}
