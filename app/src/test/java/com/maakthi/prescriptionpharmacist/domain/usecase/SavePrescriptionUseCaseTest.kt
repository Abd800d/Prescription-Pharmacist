package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.MediaAsset
import com.maakthi.prescriptionpharmacist.domain.model.MediaKind
import com.maakthi.prescriptionpharmacist.domain.model.Medication
import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.model.PrescriptionStatus
import com.maakthi.prescriptionpharmacist.domain.repository.BackupRepository
import com.maakthi.prescriptionpharmacist.domain.repository.MediaRepository
import com.maakthi.prescriptionpharmacist.domain.repository.PrescriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SavePrescriptionUseCaseTest {

    private class FakePrescriptionRepository : PrescriptionRepository {
        val saved = mutableListOf<Prescription>()
        override fun observePrescriptions(): Flow<List<Prescription>> = flowOf(emptyList())
        override fun observePrescription(id: String): Flow<Prescription?> = flowOf(null)
        override suspend fun getPrescription(id: String): Prescription? = null
        override suspend fun savePrescription(prescription: Prescription) {
            saved += prescription
        }
        override suspend fun updateStatus(id: String, status: PrescriptionStatus) = Unit
        override suspend fun deletePrescription(id: String) = Unit
    }

    private class FakeBackupRepository(private val available: Boolean = true) : BackupRepository {
        val backedUp = mutableListOf<Pair<Prescription, ByteArray?>>()
        override fun isAvailable(): Boolean = available
        override suspend fun backupPrescription(
            prescription: Prescription,
            imageBytes: ByteArray?
        ): AppResult<Unit> {
            backedUp += prescription to imageBytes
            return AppResult.success(Unit)
        }
    }

    private class FakeMediaRepository : MediaRepository {
        val savedImages = mutableListOf<ByteArray>()
        override suspend fun savePrescriptionImage(
            imageBytes: ByteArray,
            ownerName: String,
            capturedAt: Long
        ): AppResult<MediaAsset> {
            savedImages += imageBytes
            return AppResult.success(
                MediaAsset(
                    id = "m1", kind = MediaKind.PRESCRIPTION_IMAGE, ownerId = "u1",
                    ownerName = ownerName, capturedAt = capturedAt, localPath = "/tmp/m1"
                )
            )
        }

        override suspend fun saveProductImage(imageBytes: ByteArray, title: String, capturedAt: Long): AppResult<MediaAsset> =
            AppResult.success(
                MediaAsset(
                    id = "m2", kind = MediaKind.PRODUCT_IMAGE, ownerId = "u1",
                    ownerName = "u1", title = title, capturedAt = capturedAt, localPath = "/tmp/m2"
                )
            )

        override fun observeAssets(kind: MediaKind?): Flow<List<MediaAsset>> = flowOf(emptyList())
        override suspend fun getAsset(id: String): MediaAsset? = null
        override suspend fun openReadable(assetId: String): AppResult<File> =
            AppResult.error(com.maakthi.prescriptionpharmacist.domain.model.ErrorCode.NOT_FOUND)

        override suspend fun deleteAsset(assetId: String): AppResult<Unit> = AppResult.success(Unit)
        override suspend fun syncNow(): AppResult<Int> = AppResult.success(0)
        override suspend fun setSyncEnabled(enabled: Boolean) = Unit
    }

    private fun prescription() = Prescription(
        id = "p1",
        imagePath = "/tmp/rx.jpg",
        createdAt = 123L,
        status = PrescriptionStatus.CONFIRMED,
        medications = listOf(
            Medication(id = "m1", prescriptionId = "p1", name = "Panadol")
        )
    )

    @Test
    fun `saves locally and backs up when firebase is available`() = runTest {
        val repository = FakePrescriptionRepository()
        val backup = FakeBackupRepository(available = true)
        val media = FakeMediaRepository()
        val useCase = SavePrescriptionUseCase(repository, backup, media)

        useCase(prescription(), byteArrayOf(9))

        assertEquals(1, repository.saved.size)
        assertEquals(1, backup.backedUp.size)
        assertTrue(backup.backedUp[0].second!!.isNotEmpty())
        assertEquals(1, media.savedImages.size)
    }

    @Test
    fun `saves locally without backup when firebase is unavailable`() = runTest {
        val repository = FakePrescriptionRepository()
        val backup = FakeBackupRepository(available = false)
        val media = FakeMediaRepository()
        val useCase = SavePrescriptionUseCase(repository, backup, media)

        useCase(prescription(), null)

        assertEquals(1, repository.saved.size)
        assertTrue(backup.backedUp.isEmpty())
        assertTrue(media.savedImages.isEmpty())
    }
}
