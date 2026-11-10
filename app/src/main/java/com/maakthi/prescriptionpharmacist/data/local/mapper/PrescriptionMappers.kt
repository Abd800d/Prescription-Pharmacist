package com.maakthi.prescriptionpharmacist.data.local.mapper

import com.maakthi.prescriptionpharmacist.data.local.entity.MedicationEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.PrescriptionEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.PrescriptionWithMedications
import com.maakthi.prescriptionpharmacist.domain.model.Medication
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType
import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.model.PrescriptionStatus
import com.maakthi.prescriptionpharmacist.domain.model.medicationTypeOf

fun PrescriptionWithMedications.toDomain(): Prescription = Prescription(
    id = prescription.id,
    imagePath = prescription.imagePath,
    createdAt = prescription.createdAt,
    status = prescriptionStatusOf(prescription.status),
    notes = prescription.notes,
    analysisLanguage = prescription.analysisLanguage,
    remoteRequestId = prescription.remoteRequestId,
    medications = medications.map { it.toDomain() }
)

fun MedicationEntity.toDomain(): Medication = Medication(
    id = id,
    prescriptionId = prescriptionId,
    name = name,
    scientificName = scientificName,
    type = medicationTypeOf(type),
    strength = strength,
    form = form,
    confidence = confidence,
    rawText = rawText,
    confirmedByPharmacist = confirmedByPharmacist
)

fun Prescription.toEntity(): PrescriptionEntity = PrescriptionEntity(
    id = id,
    imagePath = imagePath,
    createdAt = createdAt,
    status = status.name,
    notes = notes,
    analysisLanguage = analysisLanguage,
    remoteRequestId = remoteRequestId
)

fun Medication.toEntity(): MedicationEntity = MedicationEntity(
    id = id,
    prescriptionId = prescriptionId,
    name = name,
    scientificName = scientificName,
    type = type.name,
    strength = strength,
    form = form,
    confidence = confidence,
    rawText = rawText,
    confirmedByPharmacist = confirmedByPharmacist
)

fun prescriptionStatusOf(raw: String): PrescriptionStatus =
    runCatching { PrescriptionStatus.valueOf(raw) }.getOrDefault(PrescriptionStatus.PENDING)

fun medicationTypeRaw(type: MedicationType): String = type.name
