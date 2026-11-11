package com.maakthi.prescriptionpharmacist.data.sync

import com.maakthi.prescriptionpharmacist.core.sync.SyncLocalStore
import com.maakthi.prescriptionpharmacist.data.local.dao.DrugDao
import com.maakthi.prescriptionpharmacist.data.local.dao.PrescriptionDao
import com.maakthi.prescriptionpharmacist.data.local.dao.SyncConflictDao
import com.maakthi.prescriptionpharmacist.data.local.entity.SyncConflictEntity
import com.maakthi.prescriptionpharmacist.data.local.mapper.toDomain
import com.maakthi.prescriptionpharmacist.data.local.mapper.toEntity
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.model.SyncEntityType
import com.maakthi.prescriptionpharmacist.domain.model.SyncRecord
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed local store: drugs and prescriptions are the offline-first
 * source of truth; every write stamps `updatedAt` so the engine can detect
 * changes. applyRemote upserts only — it never deletes local data.
 */
@Singleton
class RoomSyncLocalStore @Inject constructor(
    private val drugDao: DrugDao,
    private val prescriptionDao: PrescriptionDao,
    private val syncConflictDao: SyncConflictDao,
    private val settingsRepository: SettingsRepository
) : SyncLocalStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun lastSyncAt(type: SyncEntityType): Long =
        settingsRepository.settings.first().lastSyncAt

    override suspend fun setLastSyncAt(type: SyncEntityType, at: Long) {
        settingsRepository.setLastSyncAt(at)
    }

    override suspend fun localRecord(type: SyncEntityType, id: String): SyncRecord? = when (type) {
        SyncEntityType.DRUG -> drugDao.getById(id)?.toDomain()?.toRecord()
        SyncEntityType.PRESCRIPTION ->
            prescriptionDao.observeAll().first().firstOrNull { it.prescription.id == id }
                ?.toDomain()?.toRecord()
        SyncEntityType.IMAGE -> null // images use the media pipeline
    }

    override suspend fun dirtyRecords(type: SyncEntityType, since: Long): List<SyncRecord> = when (type) {
        SyncEntityType.DRUG -> drugDao.observeAll().first()
            .map { it.toDomain() }
            .filter { it.updatedAt > since }
            .map { it.toRecord() }
        SyncEntityType.PRESCRIPTION -> prescriptionDao.observeAll().first()
            .map { it.toDomain() }
            .filter { it.updatedAt > since }
            .map { it.toRecord() }
        SyncEntityType.IMAGE -> emptyList()
    }

    override suspend fun applyRemote(type: SyncEntityType, record: SyncRecord) {
        when (type) {
            SyncEntityType.DRUG -> {
                val drug = decodeDrug(record.payloadJson) ?: return
                drugDao.upsert(drug.copy(id = record.id, updatedAt = record.updatedAt).toEntity())
            }
            SyncEntityType.PRESCRIPTION -> {
                val prescription = decodePrescription(record.payloadJson) ?: return
                val updated = prescription.copy(id = record.id, updatedAt = record.updatedAt)
                prescriptionDao.upsert(updated.toEntity(), updated.medications.map { it.toEntity() })
            }
            SyncEntityType.IMAGE -> Unit
        }
    }

    override suspend fun recordConflict(type: SyncEntityType, id: String, local: SyncRecord, remote: SyncRecord) {
        syncConflictDao.upsert(
            SyncConflictEntity(
                id = "${type.name}:$id",
                entityType = type.name,
                entityId = id,
                label = local.payloadJson.take(80),
                localUpdatedAt = local.updatedAt,
                remoteUpdatedAt = remote.updatedAt,
                localJson = local.payloadJson,
                remoteJson = remote.payloadJson,
                detectedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun applyResolution(type: SyncEntityType, id: String, winning: SyncRecord) {
        applyRemote(type, winning)
        syncConflictDao.delete("${type.name}:$id")
    }

    // ---------- payload codec ----------

    private fun Drug.toRecord(): SyncRecord =
        SyncRecord(id = id, updatedAt = updatedAt, payloadJson = json.encodeToString(Drug.serializer(), this))

    private fun Prescription.toRecord(): SyncRecord =
        SyncRecord(id = id, updatedAt = updatedAt, payloadJson = json.encodeToString(Prescription.serializer(), this))

    private fun decodeDrug(payload: String): Drug? =
        runCatching { json.decodeFromString(Drug.serializer(), payload) }.getOrNull()

    private fun decodePrescription(payload: String): Prescription? =
        runCatching { json.decodeFromString(Prescription.serializer(), payload) }.getOrNull()
}
