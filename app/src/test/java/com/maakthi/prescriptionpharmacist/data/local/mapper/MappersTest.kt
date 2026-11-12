package com.maakthi.prescriptionpharmacist.data.local.mapper

import com.maakthi.prescriptionpharmacist.data.local.entity.DrugEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.MedicationEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.PrescriptionEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.PrescriptionWithMedications
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.Medication
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType
import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.model.PrescriptionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Database mappers: entity ↔ domain round-trips must preserve every stored
 * field (drugs, prescriptions with updatedAt, medications).
 */
class MappersTest {

    @Test
    fun `drug round-trip preserves every field`() {
        val drug = Drug(
            id = "d1",
            name = "Panadol",
            scientificName = "Paracetamol",
            type = MedicationType.BRAND,
            strength = "500 mg",
            form = "tablet",
            manufacturer = "GSK",
            indications = "pain",
            dosage = "1-2 tablets",
            warnings = "hepatic",
            notes = "note",
            isLocal = true,
            tradeNameArabic = "بنادول",
            tradeNameEnglish = "Panadol",
            genericNameArabic = "باراسيتامول",
            genericNameEnglish = "Paracetamol",
            activeIngredient = "Paracetamol",
            dosageForm = "tablet",
            barcode = "6281000000000",
            contraindications = "allergy",
            sideEffects = "rash",
            interactions = "warfarin",
            pregnancyInformation = "safe",
            lactationInformation = "safe",
            productImageUrl = "https://img.example/1.jpg",
            sourceName = "SFDA",
            sourceUrl = "https://sfda.example/1",
            sourceDate = "2026-01-01",
            updatedAt = 123L,
            createdBy = "admin",
            isVerified = true
        )
        assertEquals(drug, drug.toEntity().toDomain())
    }

    @Test
    fun `prescription round-trip keeps updatedAt and status`() {
        val prescription = Prescription(
            id = "p1",
            imagePath = "/data/rx.jpg",
            createdAt = 100L,
            updatedAt = 250L,
            status = PrescriptionStatus.CONFIRMED,
            notes = "notes",
            analysisLanguage = "ar",
            remoteRequestId = "r1"
        )
        val restored = PrescriptionWithMedications(
            prescription = prescription.toEntity(),
            medications = emptyList()
        ).toDomain()
        assertEquals(prescription, restored)
        assertEquals(250L, restored.updatedAt)
    }

    @Test
    fun `medication round-trip keeps the stored fields`() {
        val medication = Medication(
            id = "m1",
            prescriptionId = "p1",
            name = "Panadol",
            scientificName = "Paracetamol",
            type = MedicationType.BRAND,
            strength = "500 mg",
            form = "tablet",
            confidence = 0.9f,
            rawText = "Panadol 500 mg",
            confirmedByPharmacist = true
        )
        val entity = medication.toEntity()
        val restored = entity.toDomain()
        assertEquals(medication.id, restored.id)
        assertEquals(medication.name, restored.name)
        assertEquals(medication.strength, restored.strength)
        assertEquals(medication.confidence, restored.confidence, 0.0001f)
        assertEquals(medication.confirmedByPharmacist, restored.confirmedByPharmacist)
    }

    @Test
    fun `prescription medications map with their parent id`() {
        val medication = Medication(
            id = "m1",
            prescriptionId = "p1",
            name = "Augmentin",
            type = MedicationType.UNKNOWN,
            confidence = 0.4f,
            rawText = "Augmentin",
            isUncertain = true
        )
        val entity: MedicationEntity = medication.toEntity()
        assertEquals("p1", entity.prescriptionId)
        val restored = entity.toDomain()
        assertTrue(restored.name == "Augmentin")
    }

    @Test
    fun `entity defaults stay consistent with the domain defaults`() {
        val entity = PrescriptionEntity(
            id = "p2",
            imagePath = "/x",
            createdAt = 7L,
            updatedAt = 7L,
            status = "PENDING",
            notes = null,
            analysisLanguage = null,
            remoteRequestId = null
        )
        val domain = PrescriptionWithMedications(entity, emptyList()).toDomain()
        assertEquals(PrescriptionStatus.PENDING, domain.status)
        assertEquals(emptyList<Medication>(), domain.medications)
    }

    @Test
    fun `drug entity carries verification and provenance columns`() {
        val entity = Drug(id = "d2", name = "X", sourceName = "WHO", sourceUrl = "https://who", updatedAt = 1L, isVerified = true)
            .toEntity()
        assertEquals("WHO", entity.sourceName)
        assertTrue(entity.isVerified)
        assertEquals(1L, entity.updatedAt)
    }
}
