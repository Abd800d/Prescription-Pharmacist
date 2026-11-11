package com.maakthi.prescriptionpharmacist.data.local.mapper

import com.maakthi.prescriptionpharmacist.data.local.entity.DrugAuditEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.DrugEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.ProductImageEntity
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditAction
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditEntry
import com.maakthi.prescriptionpharmacist.domain.model.ProductImage
import com.maakthi.prescriptionpharmacist.domain.model.medicationTypeOf

fun DrugEntity.toDomain(): Drug = Drug(
    id = id,
    name = name,
    scientificName = scientificName,
    type = medicationTypeOf(type),
    strength = strength,
    form = dosageForm,
    manufacturer = manufacturer,
    indications = indications,
    dosage = dosage,
    warnings = warnings,
    notes = notes,
    isLocal = isLocal,
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

fun Drug.toEntity(): DrugEntity = DrugEntity(
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
    isVerified = isVerified,
    name = name,
    scientificName = scientificName,
    type = type.name,
    dosage = dosage,
    notes = notes,
    isLocal = isLocal
)

fun DrugAuditEntity.toDomain(): DrugAuditEntry = DrugAuditEntry(
    id = id,
    drugId = drugId,
    action = runCatching { DrugAuditAction.valueOf(action) }.getOrDefault(DrugAuditAction.UPDATE),
    changes = changes,
    performedBy = performedBy,
    timestamp = timestamp
)

fun DrugAuditEntry.toEntity(): DrugAuditEntity = DrugAuditEntity(
    id = id,
    drugId = drugId,
    action = action.name,
    changes = changes,
    performedBy = performedBy,
    timestamp = timestamp
)

fun ProductImageEntity.toDomain(): ProductImage = ProductImage(
    id = id,
    imagePath = imagePath,
    title = title,
    createdAt = createdAt
)

fun ProductImage.toEntity(): ProductImageEntity = ProductImageEntity(
    id = id,
    imagePath = imagePath,
    title = title,
    createdAt = createdAt
)
