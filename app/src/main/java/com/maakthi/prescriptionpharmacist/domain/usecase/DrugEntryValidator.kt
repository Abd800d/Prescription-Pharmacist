package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode

/**
 * Shared validation rules for the drug database. Pure JVM — unit testable.
 */
object DrugEntryValidator {

    /** `field key` → error. At least one name must be present. */
    fun validate(drug: Drug): Pair<ErrorCode, String>? {
        val hasName = listOf(
            drug.tradeNameArabic, drug.tradeNameEnglish,
            drug.genericNameArabic, drug.genericNameEnglish
        ).any { !it.isNullOrBlank() }
        if (!hasName) return ErrorCode.VALIDATION to "name_required"
        val barcode = drug.barcode?.trim().orEmpty()
        if (barcode.isNotEmpty() && !barcode.all { it.isLetterOrDigit() }) {
            return ErrorCode.VALIDATION to "barcode_invalid"
        }
        return null
    }

    /** The display/matching name: English trade preferred, then Arabic, then generic. */
    fun primaryName(drug: Drug): String =
        drug.tradeNameEnglish?.takeIf { it.isNotBlank() }
            ?: drug.tradeNameArabic?.takeIf { it.isNotBlank() }
            ?: drug.genericNameEnglish?.takeIf { it.isNotBlank() }
            ?: drug.genericNameArabic?.takeIf { it.isNotBlank() }
            ?: drug.name

    /** Generic (scientific) name in English when available, else Arabic. */
    fun primaryGeneric(drug: Drug): String? =
        drug.genericNameEnglish?.takeIf { it.isNotBlank() }
            ?: drug.genericNameArabic?.takeIf { it.isNotBlank() }

    /** Normalized key used for duplicate detection. */
    fun duplicateKey(name: String, strength: String?, manufacturer: String?): String =
        listOf(name, strength.orEmpty(), manufacturer.orEmpty())
            .joinToString("|") { normalizeKey(it) }

    private fun normalizeKey(value: String): String =
        value.lowercase().replace(Regex("[\\s\\-_.,]+"), "").trim()
}
