package com.maakthi.prescriptionpharmacist.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The drug database table. Indexed on every search column:
 * barcode (unique — prevents duplication), trade/generic names in both
 * languages, active ingredient, manufacturer and dosage form.
 */
@Entity(
    tableName = "drugs",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["tradeNameEnglish"]),
        Index(value = ["tradeNameArabic"]),
        Index(value = ["genericNameEnglish"]),
        Index(value = ["genericNameArabic"]),
        Index(value = ["activeIngredient"]),
        Index(value = ["manufacturer"]),
        Index(value = ["dosageForm"])
    ]
)
data class DrugEntity(
    @PrimaryKey val id: String,
    val tradeNameArabic: String?,
    val tradeNameEnglish: String?,
    val genericNameArabic: String?,
    val genericNameEnglish: String?,
    val activeIngredient: String?,
    val strength: String?,
    val dosageForm: String?,
    val manufacturer: String?,
    val barcode: String?,
    val indications: String?,
    val contraindications: String?,
    val warnings: String?,
    val sideEffects: String?,
    val interactions: String?,
    val pregnancyInformation: String?,
    val lactationInformation: String?,
    val productImageUrl: String?,
    val sourceName: String?,
    val sourceUrl: String?,
    val sourceDate: String?,
    val updatedAt: Long,
    val createdBy: String?,
    val isVerified: Boolean,
    // --- legacy columns used by the analysis catalog ---
    val name: String,
    val scientificName: String?,
    val type: String,
    val dosage: String?,
    val notes: String?,
    val isLocal: Boolean
)
