package com.maakthi.prescriptionpharmacist.data.repository

import com.maakthi.prescriptionpharmacist.data.local.dao.DrugDao
import com.maakthi.prescriptionpharmacist.data.local.dao.PrescriptionDao
import com.maakthi.prescriptionpharmacist.data.local.mapper.toDomain
import com.maakthi.prescriptionpharmacist.data.local.mapper.toEntity
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.repository.TransferRepository
import com.maakthi.prescriptionpharmacist.domain.repository.TransferSummary
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class TransferPayload(
    val formatVersion: Int = 1,
    val exportedAt: Long,
    val prescriptions: List<Prescription> = emptyList(),
    val drugs: List<Drug> = emptyList()
)

@Singleton
class TransferRepositoryImpl @Inject constructor(
    private val prescriptionDao: PrescriptionDao,
    private val drugDao: DrugDao
) : TransferRepository {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override suspend fun exportToJson(): AppResult<String> {
        return try {
            val prescriptions = prescriptionDao.observeAll().first().map { it.toDomain() }
            val drugs = drugDao.observeAll().first().map { it.toDomain() }
            val payload = TransferPayload(
                exportedAt = System.currentTimeMillis(),
                prescriptions = prescriptions,
                drugs = drugs
            )
            AppResult.success(json.encodeToString(payload))
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, cause = e)
        }
    }

    override suspend fun importFromJson(jsonText: String): AppResult<TransferSummary> {
        return try {
            val payload = json.decodeFromString<TransferPayload>(jsonText)
            payload.prescriptions.forEach { prescription ->
                prescriptionDao.upsert(
                    prescription.toEntity(),
                    prescription.medications.map { it.toEntity() }
                )
            }
            payload.drugs.forEach { drugDao.upsert(it.toEntity()) }
            AppResult.success(
                TransferSummary(
                    prescriptions = payload.prescriptions.size,
                    drugs = payload.drugs.size
                )
            )
        } catch (e: SerializationException) {
            AppResult.error(ErrorCode.ANALYSIS_FAILED, "Invalid file format", e)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, cause = e)
        }
    }
}
