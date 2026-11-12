package com.maakthi.prescriptionpharmacist.presentation.history

import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.model.PrescriptionStatus
import com.maakthi.prescriptionpharmacist.domain.repository.PrescriptionRepository
import com.maakthi.prescriptionpharmacist.domain.usecase.DeletePrescriptionUseCase
import com.maakthi.prescriptionpharmacist.domain.usecase.ObservePrescriptionsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ViewModel tests: the history screen streams local prescriptions
 * (offline-first) and deletions reach the repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private class FakePrescriptionRepository : PrescriptionRepository {
        val data = MutableStateFlow<List<Prescription>>(emptyList())
        val deleted = mutableListOf<String>()

        override fun observePrescriptions(): Flow<List<Prescription>> = data
        override fun observePrescription(id: String): Flow<Prescription?> =
            MutableStateFlow(data.value.firstOrNull { it.id == id })

        override suspend fun getPrescription(id: String): Prescription? =
            data.value.firstOrNull { it.id == id }

        override suspend fun savePrescription(prescription: Prescription) {
            data.value = data.value.filterNot { it.id == prescription.id } + prescription
        }

        override suspend fun updateStatus(id: String, status: PrescriptionStatus) {}
        override suspend fun deletePrescription(id: String) {
            deleted += id
            data.value = data.value.filterNot { it.id == id }
        }
    }

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun prescription(id: String) = Prescription(
        id = id,
        imagePath = "/rx/$id.jpg",
        createdAt = 1L,
        status = PrescriptionStatus.PENDING
    )

    @Test
    fun `saved prescriptions are streamed to the screen`() = runTest {
        val repository = FakePrescriptionRepository()
        repository.data.value = listOf(prescription("p1"), prescription("p2"))

        val viewModel = HistoryViewModel(
            ObservePrescriptionsUseCase(repository),
            DeletePrescriptionUseCase(repository)
        )

        val shown = viewModel.prescriptions.first()
        assertEquals(listOf("p1", "p2"), shown.map { it.id })
    }

    @Test
    fun `delete removes the prescription through the repository`() = runTest {
        val repository = FakePrescriptionRepository()
        repository.data.value = listOf(prescription("p1"), prescription("p2"))

        val viewModel = HistoryViewModel(
            ObservePrescriptionsUseCase(repository),
            DeletePrescriptionUseCase(repository)
        )

        viewModel.delete("p1")

        assertTrue(repository.deleted.contains("p1"))
        assertEquals(listOf("p2"), repository.data.value.map { it.id })
    }

    @Test
    fun `offline - the stream works with no network at all`() = runTest {
        // The repository is purely local — this models the offline experience:
        // the screen always renders what is on the device.
        val repository = FakePrescriptionRepository()
        repository.data.value = listOf(prescription("local-1"))

        val viewModel = HistoryViewModel(
            ObservePrescriptionsUseCase(repository),
            DeletePrescriptionUseCase(repository)
        )

        assertEquals(1, viewModel.prescriptions.first().size)
    }
}
