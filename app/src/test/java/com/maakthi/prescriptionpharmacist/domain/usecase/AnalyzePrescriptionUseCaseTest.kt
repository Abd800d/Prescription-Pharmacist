package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.AnalysisResult
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.MedicationCandidate
import com.maakthi.prescriptionpharmacist.domain.repository.AnalysisRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzePrescriptionUseCaseTest {

    private class FakeAnalysisRepository(
        var result: AppResult<AnalysisResult> =
            AppResult.success(AnalysisResult(emptyList()))
    ) : AnalysisRepository {
        var lastImage: ByteArray? = null
        override suspend fun analyzeImage(imageBytes: ByteArray): AppResult<AnalysisResult> {
            lastImage = imageBytes
            return result
        }
    }

    @Test
    fun `empty image is rejected without calling the backend`() = runTest {
        val repository = FakeAnalysisRepository()
        val useCase = AnalyzePrescriptionUseCase(repository)

        val result = useCase(ByteArray(0))

        assertTrue(result is AppResult.Error)
        assertEquals(ErrorCode.IMAGE_UNREADABLE, (result as AppResult.Error).code)
        assertEquals(null, repository.lastImage)
    }

    @Test
    fun `successful analysis is forwarded`() = runTest {
        val analysis = AnalysisResult(
            medications = listOf(
                MedicationCandidate(name = "Augmentin", confidence = 0.92f)
            ),
            language = "ar",
            requestId = "req-1"
        )
        val repository = FakeAnalysisRepository(AppResult.success(analysis))
        val useCase = AnalyzePrescriptionUseCase(repository)

        val result = useCase(byteArrayOf(1, 2, 3))

        assertTrue(result is AppResult.Success)
        assertEquals(analysis, result.getOrNull())
        assertEquals(3, repository.lastImage?.size)
    }

    @Test
    fun `backend errors are propagated`() = runTest {
        val repository = FakeAnalysisRepository(
            AppResult.error(ErrorCode.NOT_CONFIGURED)
        )
        val useCase = AnalyzePrescriptionUseCase(repository)

        val result = useCase(byteArrayOf(1))

        assertTrue(result is AppResult.Error)
        assertEquals(ErrorCode.NOT_CONFIGURED, (result as AppResult.Error).code)
    }
}
