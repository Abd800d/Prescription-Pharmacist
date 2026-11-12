package com.maakthi.prescriptionpharmacist.presentation.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugFact
import com.maakthi.prescriptionpharmacist.domain.model.DrugKnowledgeResult
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType
import com.maakthi.prescriptionpharmacist.domain.usecase.SearchDrugKnowledgeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface DrugKnowledgeUiState {
    data object Idle : DrugKnowledgeUiState
    data object Loading : DrugKnowledgeUiState
    data class Loaded(val result: DrugKnowledgeResult) : DrugKnowledgeUiState
    data object Empty : DrugKnowledgeUiState
    data class Error(val message: String) : DrugKnowledgeUiState
}

/**
 * Search over official sources. Conflicts are shown for the pharmacist to
 * resolve manually — the ViewModel never selects a value automatically.
 */
@HiltViewModel
class DrugKnowledgeViewModel @Inject constructor(
    private val searchKnowledge: SearchDrugKnowledgeUseCase,
    private val draftStore: PendingDrugDraftStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<DrugKnowledgeUiState>(DrugKnowledgeUiState.Idle)
    val uiState: StateFlow<DrugKnowledgeUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Manual pick per conflicting field (fact index inside the conflict). */
    private val _conflictPicks = MutableStateFlow<Map<String, DrugFact>>(emptyMap())
    val conflictPicks: StateFlow<Map<String, DrugFact>> = _conflictPicks.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun search() {
        val q = _query.value.trim()
        viewModelScope.launch {
            _uiState.value = DrugKnowledgeUiState.Loading
            _conflictPicks.value = emptyMap()
            when (val result = searchKnowledge(q)) {
                is AppResult.Error -> _uiState.value =
                    DrugKnowledgeUiState.Error(result.message ?: "error")
                is AppResult.Success -> _uiState.value =
                    if (result.data.facts.isEmpty() && result.data.failedSources.isEmpty()) {
                        DrugKnowledgeUiState.Empty
                    } else {
                        DrugKnowledgeUiState.Loaded(result.data)
                    }
            }
        }
    }

    /** The pharmacist explicitly picks one of the conflicting values. */
    fun pickConflict(field: String, fact: DrugFact) {
        _conflictPicks.value = _conflictPicks.value + (field to fact)
    }

    /**
     * Builds a review draft from the non-conflicted facts plus the
     * explicitly picked conflict values, and hands it to the add form.
     * Conflicts without a manual pick are simply omitted — never guessed.
     */
    fun importForReview(onReady: () -> Unit) {
        val state = _uiState.value as? DrugKnowledgeUiState.Loaded ?: return
        val result = state.result
        val resolved = result.facts.filter { fact ->
            result.conflicts.none { it.field == fact.field } ||
                _conflictPicks.value[fact.field] == fact
        }
        if (resolved.isEmpty()) return
        draftStore.set(resolved.toDraft())
        onReady()
    }

    private fun List<DrugFact>.toDraft(): Drug {
        fun value(field: String): String? =
            firstOrNull { it.field == field }?.value?.takeIf { it.isNotBlank() }

        val tradeEn = value("tradeName")
        val genericEn = value("genericName")
        val source = firstOrNull()?.source
        return Drug(
            id = UUID.randomUUID().toString(),
            name = tradeEn ?: genericEn ?: "",
            scientificName = genericEn,
            type = when {
                tradeEn != null -> MedicationType.BRAND
                genericEn != null -> MedicationType.GENERIC
                else -> MedicationType.UNKNOWN
            },
            tradeNameEnglish = tradeEn,
            genericNameEnglish = genericEn,
            activeIngredient = value("activeIngredient"),
            strength = value("strength"),
            dosageForm = value("dosageForm"),
            manufacturer = value("manufacturer"),
            indications = value("indications"),
            dosage = value("dosage"),
            warnings = value("warnings"),
            contraindications = value("contraindications"),
            sideEffects = value("sideEffects"),
            interactions = value("interactions"),
            pregnancyInformation = value("pregnancyInformation"),
            lactationInformation = value("lactationInformation"),
            notes = value("description"),
            isLocal = true,
            // External information stays unverified until the pharmacist approves it.
            isVerified = false,
            sourceName = source?.name,
            sourceUrl = source?.url,
            sourceDate = source?.retrievedAt?.let { java.text.DateFormat.getDateInstance().format(java.util.Date(it)) }
        )
    }
}
