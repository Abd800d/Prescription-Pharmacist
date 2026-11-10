package com.maakthi.prescriptionpharmacist.data.local.mapper

import com.maakthi.prescriptionpharmacist.data.local.entity.DrugEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.ProductImageEntity
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.ProductImage
import com.maakthi.prescriptionpharmacist.domain.model.medicationTypeOf

fun DrugEntity.toDomain(): Drug = Drug(
    id = id,
    name = name,
    scientificName = scientificName,
    type = medicationTypeOf(type),
    strength = strength,
    form = form,
    manufacturer = manufacturer,
    indications = indications,
    dosage = dosage,
    warnings = warnings,
    notes = notes,
    isLocal = true
)

fun Drug.toEntity(): DrugEntity = DrugEntity(
    id = id,
    name = name,
    scientificName = scientificName,
    type = type.name,
    strength = strength,
    form = form,
    manufacturer = manufacturer,
    indications = indications,
    dosage = dosage,
    warnings = warnings,
    notes = notes
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
