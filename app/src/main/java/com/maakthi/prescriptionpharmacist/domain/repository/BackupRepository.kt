package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.Prescription

/**
 * Optional cloud backup of confirmed prescriptions (Firebase Storage).
 * Real uploads only — no dummy data.
 */
interface BackupRepository {
    fun isAvailable(): Boolean
    suspend fun backupPrescription(
        prescription: Prescription,
        imageBytes: ByteArray?
    ): AppResult<Unit>
}
