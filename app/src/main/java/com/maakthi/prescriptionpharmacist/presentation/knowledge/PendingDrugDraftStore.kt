package com.maakthi.prescriptionpharmacist.presentation.knowledge

import com.maakthi.prescriptionpharmacist.domain.model.Drug
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory hand-off of a knowledge draft to the add-drug form.
 *
 * Deliberately NOT persisted anywhere: external information reaches the
 * approved drug database only after the pharmacist reviews it in the form
 * and presses save. One-shot — [consume] clears the draft.
 */
@Singleton
class PendingDrugDraftStore @Inject constructor() {

    private val draft = MutableStateFlow<Drug?>(null)

    fun set(value: Drug) {
        draft.value = value
    }

    fun consume(): Drug? = draft.value.also { draft.value = null }
}
