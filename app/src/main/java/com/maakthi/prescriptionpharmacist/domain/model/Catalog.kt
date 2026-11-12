package com.maakthi.prescriptionpharmacist.domain.model

import kotlinx.serialization.Serializable

/**
 * A medication/drug record — the editable drug database row.
 *
 * The record can be added locally by the pharmacist or imported from a
 * trusted source (openFDA or the backend API). Source-backed records set
 * [isVerified] together with [sourceName]/[sourceUrl]; manually added ones
 * stay unverified.
 */
@Serializable
data class Drug(
    val id: String,
    // --- identity / matching (used by the analysis catalog) ---
    /** Primary display / matching name (English trade name when available). */
    val name: String,
    /** Primary generic name in English (used by the analysis matcher). */
    val scientificName: String? = null,
    val type: MedicationType = MedicationType.UNKNOWN,
    val strength: String? = null,
    val form: String? = null,
    val manufacturer: String? = null,
    val indications: String? = null,
    val dosage: String? = null,
    val warnings: String? = null,
    val notes: String? = null,
    /** true when the record lives in the local database. */
    val isLocal: Boolean = true,
    // --- drug database fields ---
    val tradeNameArabic: String? = null,
    val tradeNameEnglish: String? = null,
    val genericNameArabic: String? = null,
    val genericNameEnglish: String? = null,
    val activeIngredient: String? = null,
    val dosageForm: String? = null,
    val barcode: String? = null,
    val contraindications: String? = null,
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
    /** True for records imported from a trusted source and reviewed. */
    val isVerified: Boolean = false
)

/** Change-history action recorded in the drug audit log. */
@Serializable
enum class DrugAuditAction { ADD, UPDATE, DELETE }

/** One entry of the drug change history (audit log). */
@Serializable
data class DrugAuditEntry(
    val id: String,
    val drugId: String,
    val action: DrugAuditAction,
    /** Human-readable summary of the changed fields. */
    val changes: String,
    val performedBy: String,
    val timestamp: Long
)

/** Result-list filters for the drug database search. */
data class DrugFilters(
    val dosageForm: String? = null,
    val manufacturer: String? = null,
    val verification: VerificationFilter = VerificationFilter.ALL
)

/** Whether a record is source-verified or manually added. */
enum class VerificationFilter { ALL, VERIFIED, MANUAL }

/** Distinct filter values offered by the drug database. */
data class DrugFilterOptions(
    val dosageForms: List<String> = emptyList(),
    val manufacturers: List<String> = emptyList()
)

/**
 * Builds the human-readable "field: old → new" summary stored in the audit
 * log. Empty string when nothing changed.
 */
fun drugChanges(old: Drug, new: Drug): String {
    val labels = linkedMapOf<String, Pair<String?, String?>>(
        "tradeNameArabic" to (old.tradeNameArabic to new.tradeNameArabic),
        "tradeNameEnglish" to (old.tradeNameEnglish to new.tradeNameEnglish),
        "genericNameArabic" to (old.genericNameArabic to new.genericNameArabic),
        "genericNameEnglish" to (old.genericNameEnglish to new.genericNameEnglish),
        "activeIngredient" to (old.activeIngredient to new.activeIngredient),
        "strength" to (old.strength to new.strength),
        "dosageForm" to (old.dosageForm to new.dosageForm),
        "manufacturer" to (old.manufacturer to new.manufacturer),
        "barcode" to (old.barcode to new.barcode),
        "indications" to (old.indications to new.indications),
        "contraindications" to (old.contraindications to new.contraindications),
        "warnings" to (old.warnings to new.warnings),
        "sideEffects" to (old.sideEffects to new.sideEffects),
        "interactions" to (old.interactions to new.interactions),
        "pregnancyInformation" to (old.pregnancyInformation to new.pregnancyInformation),
        "lactationInformation" to (old.lactationInformation to new.lactationInformation),
        "productImageUrl" to (old.productImageUrl to new.productImageUrl),
        "sourceName" to (old.sourceName to new.sourceName),
        "sourceUrl" to (old.sourceUrl to new.sourceUrl),
        "sourceDate" to (old.sourceDate to new.sourceDate),
        "isVerified" to (old.isVerified.toString() to new.isVerified.toString())
    )
    return labels.entries
        .filter { (_, pair) -> pair.first.orEmpty() != pair.second.orEmpty() }
        .joinToString("; ") { (label, pair) -> "$label: ${pair.first.orEmpty()} → ${pair.second.orEmpty()}" }
}

/** A product photo (medicine box, shelf, etc.) stored on the device. */
@Serializable
data class ProductImage(
    val id: String,
    val imagePath: String,
    val title: String,
    val createdAt: Long
)

/** A single chat message with the AI pharmacist assistant. */
@Serializable
data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val timestamp: Long
)

@Serializable
enum class ChatRole { USER, ASSISTANT }
