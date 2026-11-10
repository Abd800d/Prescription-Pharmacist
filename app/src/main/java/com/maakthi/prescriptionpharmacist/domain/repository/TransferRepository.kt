package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.Prescription

/** JSON import/export of the pharmacist's local data. */
interface TransferRepository {
    suspend fun exportToJson(): AppResult<String>
    suspend fun importFromJson(json: String): AppResult<TransferSummary>
}

data class TransferSummary(
    val prescriptions: Int,
    val drugs: Int
)
