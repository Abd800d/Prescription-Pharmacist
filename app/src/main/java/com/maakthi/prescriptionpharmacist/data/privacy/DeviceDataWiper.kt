package com.maakthi.prescriptionpharmacist.data.privacy

import android.content.Context
import com.maakthi.prescriptionpharmacist.data.local.AppDatabase
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Delete my data on this device" — removes every local category from
 * core/privacy/DataDeletionPolicy: database (drugs, prescriptions, audit),
 * encrypted media, settings, the device key (including the Keystore alias),
 * the backup vault and caches. Irreversible on the device.
 */
@Singleton
class DeviceDataWiper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository
) {

    suspend fun wipeAll() {
        database.clearAllTables()
        settingsRepository.clearAll()

        listOf("pp_media_keys", "pp_backup_vault").forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().clear().apply()
        }

        // drop the Keystore wrapping key so old ciphertext is unreadable
        runCatching {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry("pp_media_wrap_key")
        }

        File(context.filesDir, "media").deleteRecursively()
        File(context.cacheDir, "support").deleteRecursively()
        File(context.cacheDir, "images").deleteRecursively()
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}
