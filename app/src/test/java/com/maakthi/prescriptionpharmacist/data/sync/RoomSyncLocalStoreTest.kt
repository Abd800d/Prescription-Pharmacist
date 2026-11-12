package com.maakthi.prescriptionpharmacist.data.sync

import com.maakthi.prescriptionpharmacist.data.local.dao.DrugDao
import com.maakthi.prescriptionpharmacist.data.local.dao.PrescriptionDao
import com.maakthi.prescriptionpharmacist.data.local.dao.SyncConflictDao
import com.maakthi.prescriptionpharmacist.data.local.entity.DrugEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.MedicationEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.PrescriptionEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.PrescriptionWithMedications
import com.maakthi.prescriptionpharmacist.data.local.entity.SyncConflictEntity
import com.maakthi.prescriptionpharmacist.data.local.mapper.toDomain
import com.maakthi.prescriptionpharmacist.data.local.mapper.toEntity
import com.maakthi.prescriptionpharmacist.domain.model.AppLanguage
import com.maakthi.prescriptionpharmacist.domain.model.AppSettings
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.model.SyncEntityType
import com.maakthi.prescriptionpharmacist.domain.model.SyncRecord
import com.maakthi.prescriptionpharmacist.domain.model.ThemeMode
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Repository-level sync store tests (offline-first): local data is the
 * source of truth, applyRemote UPSERTS only and never deletes, and the dirty
 * clock drives what gets pushed.
 */
class RoomSyncLocalStoreTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ---------- fakes ----------

    private class FakeDrugDao : DrugDao {
        val rows = mutableMapOf<String, DrugEntity>()
        override fun observeAll(): Flow<List<DrugEntity>> = MutableStateFlow(rows.values.toList())
        override suspend fun getById(id: String): DrugEntity? = rows[id]
        override suspend fun getByBarcode(barcode: String): DrugEntity? = rows.values.firstOrNull { it.barcode == barcode }
        override suspend fun search(query: String): List<DrugEntity> = rows.values.toList()
        override suspend fun searchFiltered(query: String, dosageForm: String, manufacturer: String, verified: Int?): List<DrugEntity> = rows.values.toList()
        override suspend fun distinctDosageForms(): List<String> = emptyList()
        override suspend fun distinctManufacturers(): List<String> = emptyList()
        override suspend fun insertStrict(drug: DrugEntity) { rows[drug.id] = drug }
        override suspend fun upsert(drug: DrugEntity) { rows[drug.id] = drug }
        override suspend fun delete(id: String) { rows.remove(id) }
    }

    private class FakePrescriptionDao : PrescriptionDao {
        val rows = mutableMapOf<String, PrescriptionWithMedications>()
        override fun observeAll(): Flow<List<PrescriptionWithMedications>> = MutableStateFlow(rows.values.toList())
        override fun observeById(id: String): Flow<PrescriptionWithMedications?> = MutableStateFlow(rows[id])
        override suspend fun getById(id: String): PrescriptionWithMedications? = rows[id]
        override suspend fun insertPrescription(prescription: PrescriptionEntity) {
            rows[prescription.id] = PrescriptionWithMedications(prescription, rows[prescription.id]?.medications.orEmpty())
        }
        override suspend fun insertMedications(medications: List<MedicationEntity>) {
            medications.forEach { med ->
                val owner = rows[med.prescriptionId] ?: return@forEach
                rows[med.prescriptionId] = owner.copy(medications = owner.medications + med)
            }
        }
        override suspend fun updateStatus(id: String, status: String, updatedAt: Long) {}
        override suspend fun deleteMedications(prescriptionId: String) {}
        override suspend fun deletePrescription(id: String) { rows.remove(id) }
    }

    private class FakeSyncConflictDao : SyncConflictDao {
        val rows = mutableMapOf<String, SyncConflictEntity>()
        override fun observeAll(): Flow<List<SyncConflictEntity>> = MutableStateFlow(rows.values.toList())
        override suspend fun getById(id: String): SyncConflictEntity? = rows[id]
        override suspend fun count(): Int = rows.size
        override suspend fun upsert(entity: SyncConflictEntity) { rows[entity.id] = entity }
        override suspend fun delete(id: String) { rows.remove(id) }
    }

    private class FakeSettings : SettingsRepository {
        var lastSync = 0L
        override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())
        override suspend fun setThemeMode(themeMode: ThemeMode) {}
        override suspend fun setLanguage(language: AppLanguage) {}
        override suspend fun setBackendUrl(url: String) {}
        override suspend fun setOnboardingDone(done: Boolean) {}
        override suspend fun setMediaSyncEnabled(enabled: Boolean) {}
        override suspend fun setAutoBackupEnabled(enabled: Boolean) {}
        override suspend fun setLastSyncAt(at: Long) { lastSync = at }
        override suspend fun setLastBackupAt(at: Long) {}
        override suspend fun clearAll() {}
    }

    private lateinit var drugDao: FakeDrugDao
    private lateinit var prescriptionDao: FakePrescriptionDao
    private lateinit var conflicts: FakeSyncConflictDao
    private lateinit var settings: FakeSettings
    private lateinit var store: RoomSyncLocalStore

    @Before
    fun setUp() {
        drugDao = FakeDrugDao()
        prescriptionDao = FakePrescriptionDao()
        conflicts = FakeSyncConflictDao()
        settings = FakeSettings()
        store = RoomSyncLocalStore(drugDao, prescriptionDao, conflicts, settings)
    }

    private fun drug(id: String, updatedAt: Long) =
        Drug(id = id, name = "Drug $id", updatedAt = updatedAt)

    private fun payload(d: Drug): String = json.encodeToString(Drug.serializer(), d)

    // ---------- dirty detection (offline-first clock) ----------

    @Test
    fun `only records newer than the last sync clock are dirty`() = runTest {
        drugDao.upsert(drug("old", updatedAt = 50).toEntity())
        drugDao.upsert(drug("new", updatedAt = 150).toEntity())

        val dirty = store.dirtyRecords(SyncEntityType.DRUG, since = 100)

        assertEquals(listOf("new"), dirty.map { it.id })
    }

    @Test
    fun `everything is dirty before the first sync`() = runTest {
        drugDao.upsert(drug("a", 10).toEntity())
        drugDao.upsert(drug("b", 20).toEntity())
        assertEquals(2, store.dirtyRecords(SyncEntityType.DRUG, since = 0).size)
    }

    // ---------- applyRemote upserts only — never deletes ----------

    @Test
    fun `applying a remote record upserts and never deletes local siblings`() = runTest {
        drugDao.upsert(drug("local-keep", 10).toEntity())
        val remote = drug("remote-1", 200)

        store.applyRemote(SyncEntityType.DRUG, SyncRecord("remote-1", 200, payload(remote)))

        assertEquals(2, drugDao.rows.size)
        assertTrue(drugDao.rows.containsKey("local-keep"))
        assertEquals("Drug remote-1", drugDao.rows["remote-1"]!!.toDomain().name)
    }

    @Test
    fun `applying the same remote record twice is idempotent`() = runTest {
        val remote = drug("r", 200)
        val record = SyncRecord("r", 200, payload(remote))
        store.applyRemote(SyncEntityType.DRUG, record)
        store.applyRemote(SyncEntityType.DRUG, record)
        assertEquals(1, drugDao.rows.size)
    }

    @Test
    fun `malformed remote payloads are ignored, local data untouched`() = runTest {
        drugDao.upsert(drug("safe", 10).toEntity())
        store.applyRemote(SyncEntityType.DRUG, SyncRecord("bad", 300, "{not json"))
        assertEquals(1, drugDao.rows.size)
    }

    @Test
    fun `prescription records round-trip with medications`() = runTest {
        val prescription = Prescription(
            id = "p1",
            imagePath = "/rx.jpg",
            createdAt = 100,
            updatedAt = 300
        )
        val record = SyncRecord(
            id = "p1",
            updatedAt = 300,
            payloadJson = json.encodeToString(Prescription.serializer(), prescription)
        )
        store.applyRemote(SyncEntityType.PRESCRIPTION, record)

        val restored = store.localRecord(SyncEntityType.PRESCRIPTION, "p1")
        assertEquals(300L, restored?.updatedAt)
    }

    // ---------- conflicts stay until the user decides ----------

    @Test
    fun `recorded conflicts are persisted and resolved only explicitly`() = runTest {
        val local = SyncRecord("d1", 100, "local version")
        val remote = SyncRecord("d1", 200, "remote version")
        store.recordConflict(SyncEntityType.DRUG, "d1", local, remote)

        assertEquals(1, conflicts.rows.size)

        store.applyResolution(SyncEntityType.DRUG, "d1", SyncRecord("d1", 400, payload(drug("d1", 400))))
        assertEquals(0, conflicts.rows.size)
    }

    // ---------- clocks ----------

    @Test
    fun `last sync clock round-trips through settings`() = runTest {
        assertEquals(0L, store.lastSyncAt(SyncEntityType.DRUG))
        store.setLastSyncAt(SyncEntityType.DRUG, 999L)
        assertEquals(999L, store.lastSyncAt(SyncEntityType.DRUG))
        assertEquals(999L, settings.lastSync)
    }

    @Test
    fun `unknown remote drug id without payload is not created`() = runTest {
        store.applyRemote(SyncEntityType.DRUG, SyncRecord("ghost", 300, ""))
        assertNull(drugDao.rows["ghost"])
    }
}
