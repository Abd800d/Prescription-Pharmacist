package com.maakthi.prescriptionpharmacist.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class PrescriptionWithMedications(
    @Embedded val prescription: PrescriptionEntity,
    @Relation(parentColumn = "id", entityColumn = "prescriptionId")
    val medications: List<MedicationEntity>
)
