package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.core.analysis.AiExtractionEngine
import com.maakthi.prescriptionpharmacist.core.analysis.AiMedicationHint
import com.maakthi.prescriptionpharmacist.core.analysis.OcrEngine
import com.maakthi.prescriptionpharmacist.core.analysis.OcrLine
import com.maakthi.prescriptionpharmacist.core.analysis.OcrResult
import com.maakthi.prescriptionpharmacist.domain.model.AnalysisResult
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.repository.DrugRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzePrescriptionUseCaseTest {

    private class FakeOcrEngine(
        var result: AppResult<OcrResult> = AppResult.success(
            OcrResult(fullText = "Panadol 500 mg", lines = listOf(OcrLine("Panadol 500 mg")))
        )
    ) : OcrEngine {
        var calls = 0
        override suspend fun recognize(imageBytes: ByteArray): AppResult<OcrResult> {
            calls++
            return result
        }
    }

    private class FakeExtractionEngine(
        var result: AppResult<List<AiMedicationHint>> = AppResult.success(
            listOf(AiMedicationHint(name = "Panadol", strength = "500 mg", rawText = "Panadol 500 mg"))
        )
    ) : AiExtractionEngine {
        var calls = 0
        override suspend fun extract(ocrText: String): AppResult<List<AiMedicationHint>> {
            calls++
            return result
        }
    }

    private class FakeDrugRepository(
        var catalog: List<Drug> = listOf(
            Drug(id = "1", name = "Panadol", scientificName = "Paracetamol")
        ),
        var remote: List<Drug> = emptyList()
    ) : DrugRepository {
        var remoteSearches = 0
        override fun observeDrugs(): Flow<List<Drug>> = flowOf(catalog)
        override suspend fun getDrug(id: String): Drug? = catalog.firstOrNull { it.id == id }
        override suspend fun searchLocal(query: String): List<Drug> = emptyList()
        override suspend fun upsertDrug(drug: Drug) {}
        override suspend fun deleteDrug(drug: String) {}
        override suspend fun searchRemote(query: String): List<Drug> {
            remoteSearches++
            return remote
        }
    }

    @Test
    fun `empty image is rejected without calling any engine`() = runTest {
        val ocr = FakeOcrEngine()
        val extract = FakeExtractionEngine()
        val repo = FakeDrugRepository()
        val useCase = AnalyzePrescriptionUseCase(ocr, extract, repo)

        val result = useCase(ByteArray(0))

        assertTrue(result is AppResult.Error)
        assertEquals(ErrorCode.IMAGE_UNREADABLE, (result as AppResult.Error).code)
        assertEquals(0, ocr.calls)
        assertEquals(0, extract.calls)
    }

    @Test
    fun `ocr errors are propagated (eg backend not configured)`() = runTest {
        val ocr = FakeOcrEngine(result = AppResult.error(ErrorCode.NOT_CONFIGURED))
        val useCase = AnalyzePrescriptionUseCase(ocr, FakeExtractionEngine(), FakeDrugRepository())

        val result = useCase(byteArrayOf(1, 2))

        assertEquals(ErrorCode.NOT_CONFIGURED, (result as AppResult.Error).code)
    }

    @Test
    fun `ai provider failure falls back to the local rule engine`() = runTest {
        val ocr = FakeOcrEngine(
            result = AppResult.success(
                OcrResult(
                    fullText = "Augmentin 1g",
                    lines = listOf(OcrLine("Augmentin 1g")),
                    requestId = "req-7"
                )
            )
        )
        val extract = FakeExtractionEngine(result = AppResult.error(ErrorCode.NETWORK))
        val repo = FakeDrugRepository(
            catalog = listOf(Drug(id = "2", name = "Augmentin"))
        )
        val useCase = AnalyzePrescriptionUseCase(ocr, extract, repo)

        val result = useCase(byteArrayOf(1))

        val analysis = result.getOrNull()!!
        assertEquals("req-7", analysis.requestId)
        assertTrue(analysis.medications.isNotEmpty())
        assertEquals("Augmentin", analysis.medications[0].name)
    }

    @Test
    fun `hints and catalog merge into grounded results`() = runTest {
        val useCase = AnalyzePrescriptionUseCase(
            FakeOcrEngine(), FakeExtractionEngine(), FakeDrugRepository()
        )

        val analysis = useCase(byteArrayOf(9)).getOrNull()!!

        val med = analysis.medications[0]
        assertEquals("Panadol", med.name)
        assertEquals("500 mg", med.strength)
        assertTrue(analysis.requiresPharmacistConfirmation)
    }

    @Test
    fun `uncertain unmatched names are enriched from the remote catalog`() = runTest {
        val ocr = FakeOcrEngine(
            result = AppResult.success(
                OcrResult(fullText = "Zyxtrazine 100 mg", lines = listOf(OcrLine("Zyxtrazine 100 mg")))
            )
        )
        val extract = FakeExtractionEngine(result = AppResult.success(emptyList()))
        val repo = FakeDrugRepository(
            catalog = emptyList(),
            remote = listOf(Drug(id = "9", name = "Zyxtrazine", isLocal = false))
        )
        val useCase = AnalyzePrescriptionUseCase(ocr, extract, repo)

        val analysis = useCase(byteArrayOf(1)).getOrNull()!!
        val med = analysis.medications[0]
        assertTrue(med.isUncertain)
        assertEquals(1, repo.remoteSearches)
        assertTrue("alternatives=${med.alternatives}", med.alternatives.any { it.name == "Zyxtrazine" })
    }
}
