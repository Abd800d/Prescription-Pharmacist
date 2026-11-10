package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.AppLanguage
import com.maakthi.prescriptionpharmacist.domain.model.AppSettings
import com.maakthi.prescriptionpharmacist.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Persistent user settings (DataStore).
 */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(themeMode: ThemeMode)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setBackendUrl(url: String)
}
