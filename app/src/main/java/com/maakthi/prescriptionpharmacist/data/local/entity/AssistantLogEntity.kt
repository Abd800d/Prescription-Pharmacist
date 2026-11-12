package com.maakthi.prescriptionpharmacist.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One Q&A pair in the user's local assistant log. Stored only on-device,
 * never uploaded, and deletable at any time from the chat screen.
 */
@Entity(
    tableName = "assistant_log",
    indices = [Index(value = ["timestamp"])]
)
data class AssistantLogEntity(
    @PrimaryKey val id: String,
    val question: String,
    val answer: String,
    val citationsJson: String,
    val insufficient: Boolean,
    val language: String,
    val timestamp: Long
)
