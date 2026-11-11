package com.maakthi.prescriptionpharmacist.data.remote.dto

import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditAction
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditEntry
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType
import com.maakthi.prescriptionpharmacist.domain.model.medicationTypeOf
import kotlinx.serialization.Serializable

/** Drug database row as transported by the remote CRUD API. */
@Serializable
data class DrugDto(
    val id: String,
    val tradeNameArabic: String? = null,
    val tradeNameEnglish: String? = null,
    val genericNameArabic: String? = null,
    val genericNameEnglish: String? = null,
    val activeIngredient: String? = null,
    val strength: String? = null,
    val dosageForm: String? = null,
    val manufacturer: String? = null,
    val barcode: String? = null,
    val indications: String? = null,
    val contraindications: String? = null,
    val warnings: String? = null,
    val sideEffects: String? = null,
    val interactions: String? = null,
    val pregnancyInformation: String? = null,
    val lactationInformation: String? = null,
    val productImageUrl: String? = null,
    val sourceName: String? = null,
    val sourceUrl: String? = null,
    val sourceDate: String? = null,
    val updatedAt: Long = 0L,
    val createdBy: String? = null,
    val isVerified: Boolean = false
)

/** One change-history entry as transported by the remote API. */
@Serializable
data class DrugAuditDto(
    val id: String,
    val drugId: String,
    val action: String,
    val changes: String,
    val performedBy: String,
    val timestamp: Long
)

fun DrugDto.toDomain(): Drug = Drug(
    id = id,
    name = tradeNameEnglish ?: tradeNameArabic ?: genericNameEnglish ?: genericNameArabic ?: id,
    scientificName = genericNameEnglish ?: genericNameArabic,
    type = when {
        tradeNameEnglish != null || tradeNameArabic != null -> MedicationType.BRAND
        genericNameEnglish != null || genericNameArabic != null -> MedicationType.GENERIC
        else -> MedicationType.UNKNOWN
    },
    strength = strength,
    form = dosageForm,
    manufacturer = manufacturer,
    indications = indications,
    warnings = warnings,
    isLocal = false,
    tradeNameArabic = tradeNameArabic,
    tradeNameEnglish = tradeNameEnglish,
    genericNameArabic = genericNameArabic,
    genericNameEnglish = genericNameEnglish,
    activeIngredient = activeIngredient,
    dosageForm = dosageForm,
    barcode = barcode,
    contraindications = contraindications,
    sideEffects = sideEffects,
    interactions = interactions,
    pregnancyInformation = pregnancyInformation,
    lactationInformation = lactationInformation,
    productImageUrl = productImageUrl,
    sourceName = sourceName,
    sourceUrl = sourceUrl,
    sourceDate = sourceDate,
    updatedAt = updatedAt,
    createdBy = createdBy,
    isVerified = isVerified
)

fun Drug.toDto(): DrugDto = DrugDto(
    id = id,
    tradeNameArabic = tradeNameArabic,
    tradeNameEnglish = tradeNameEnglish,
    genericNameArabic = genericNameArabic,
    genericNameEnglish = genericNameEnglish,
    activeIngredient = activeIngredient,
    strength = strength,
    dosageForm = dosageForm ?: form,
    manufacturer = manufacturer,
    barcode = barcode,
    indications = indications,
    contraindications = contraindications,
    warnings = warnings,
    sideEffects = sideEffects,
    interactions = interactions,
    pregnancyInformation = pregnancyInformation,
    lactationInformation = lactationInformation,
    productImageUrl = productImageUrl,
    sourceName = sourceName,
    sourceUrl = sourceUrl,
    sourceDate = sourceDate,
    updatedAt = updatedAt,
    createdBy = createdBy,
    isVerified = isVerified
)

fun DrugAuditDto.toDomain(): DrugAuditEntry = DrugAuditEntry(
    id = id,
    drugId = drugId,
    action = runCatching { DrugAuditAction.valueOf(action) }.getOrDefault(DrugAuditAction.UPDATE),
    changes = changes,
    performedBy = performedBy,
    timestamp = timestamp
)
