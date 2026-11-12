package com.maakthi.prescriptionpharmacist.data.firebase

import com.google.firebase.storage.FirebaseStorage
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.repository.BackupRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs up confirmed prescriptions to Firebase Storage as JSON metadata
 * (plus the source image when available). Disabled when Firebase is not
 * configured.
 */
@Singleton
class FirebaseBackupRepository @Inject constructor(
    private val firebaseSupport: FirebaseSupport,
    private val authRepository: AuthRepository
) : BackupRepository {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override fun isAvailable(): Boolean = firebaseSupport.isAvailable

    override suspend fun backupPrescription(
        prescription: Prescription,
        imageBytes: ByteArray?
    ): AppResult<Unit> {
        if (!isAvailable()) {
            return AppResult.error(ErrorCode.NOT_CONFIGURED, "Firebase is not configured")
        }
        val uid = authRepository.currentUser.first()?.uid
            ?: return AppResult.error(ErrorCode.UNAUTHORIZED, "Sign in before backing up")

        return try {
            val storage = FirebaseStorage.getInstance()
            val baseRef = storage.reference
                .child("users/$uid/prescriptions/${prescription.id}")

            val metadata = BackupPayload.from(prescription)
            baseRef.child("prescription.json")
                .putBytes(json.encodeToString(metadata).toByteArray(Charsets.UTF_8))
                .await()

            if (imageBytes != null && imageBytes.isNotEmpty()) {
                baseRef.child("image.jpg").putBytes(imageBytes).await()
            }
            AppResult.success(Unit)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.NETWORK, cause = e)
        }
    }
}

@Serializable
private data class BackupPayload(
    val id: String,
    val createdAt: Long,
    val status: String,
    val notes: String?,
    val analysisLanguage: String?,
    val remoteRequestId: String?,
    val medications: List<BackupMedication>
) {
    companion object {
        fun from(p: Prescription) = BackupPayload(
            id = p.id,
            createdAt = p.createdAt,
            status = p.status.name,
            notes = p.notes,
            analysisLanguage = p.analysisLanguage,
            remoteRequestId = p.remoteRequestId,
            medications = p.medications.map {
                BackupMedication(
                    name = it.name,
                    scientificName = it.scientificName,
                    type = it.type.name,
                    strength = it.strength,
                    form = it.form,
                    confidence = it.confidence,
                    confirmedByPharmacist = it.confirmedByPharmacist
                )
            }
        )
    }
}

@Serializable
private data class BackupMedication(
    val name: String,
    val scientificName: String?,
    val type: String,
    val strength: String?,
    val form: String?,
    val confidence: Float,
    val confirmedByPharmacist: Boolean
)
