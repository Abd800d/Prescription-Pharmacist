package com.maakthi.prescriptionpharmacist.domain.model

import kotlinx.serialization.Serializable

/**
 * A medication/drug record. Can be added locally by the pharmacist
 * or imported from the public openFDA drug label database.
 */
@Serializable
data class Drug(
    val id: String,
    val name: String,
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
    val isLocal: Boolean = true
)

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
