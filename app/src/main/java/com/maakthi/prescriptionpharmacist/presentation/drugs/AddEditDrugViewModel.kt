package com.maakthi.prescriptionpharmacist.presentation.drugs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType
import com.maakthi.prescriptionpharmacist.domain.repository.DrugRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DrugFormState(
    val id: String? = null,
    val name: String = "",
    val scientificName: String = "",
    val type: MedicationType = MedicationType.UNKNOWN,
    val strength: String = "",
    val form: String = "",
    val manufacturer: String = "",
    val indications: String = "",
    val dosage: String = "",
    val warnings: String = "",
    val notes: String = ""
)

sealed interface DrugFormEvent {
    data object Saved : DrugFormEvent
}

@HiltViewModel
class AddEditDrugViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val drugRepository: DrugRepository
) : ViewModel() {

    private val drugId: String? = savedStateHandle.get<String>("drugId")

    private val _form = MutableStateFlow(DrugFormState())
    val form: StateFlow<DrugFormState> = _form.asStateFlow()

    private val _loading = MutableStateFlow(drugId != null)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _events = Channel<DrugFormEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        if (drugId != null) {
            viewModelScope.launch {
                val drug = drugRepository.getDrug(drugId)
                if (drug != null) {
                    _form.value = DrugFormState(
                        id = drug.id,
                        name = drug.name,
                        scientificName = drug.scientificName.orEmpty(),
                        type = drug.type,
                        strength = drug.strength.orEmpty(),
                        form = drug.form.orEmpty(),
                        manufacturer = drug.manufacturer.orEmpty(),
                        indications = drug.indications.orEmpty(),
                        dosage = drug.dosage.orEmpty(),
                        warnings = drug.warnings.orEmpty(),
                        notes = drug.notes.orEmpty()
                    )
                }
                _loading.value = false
            }
        }
    }

    fun update(transform: (DrugFormState) -> DrugFormState) {
        _form.value = transform(_form.value)
    }

    fun save() {
        val state = _form.value
        if (state.name.isBlank()) return
        viewModelScope.launch {
            drugRepository.upsertDrug(
                Drug(
                    id = state.id ?: UUID.randomUUID().toString(),
                    name = state.name.trim(),
                    scientificName = state.scientificName.trim().takeIf { it.isNotEmpty() },
                    type = state.type,
                    strength = state.strength.trim().takeIf { it.isNotEmpty() },
                    form = state.form.trim().takeIf { it.isNotEmpty() },
                    manufacturer = state.manufacturer.trim().takeIf { it.isNotEmpty() },
                    indications = state.indications.trim().takeIf { it.isNotEmpty() },
                    dosage = state.dosage.trim().takeIf { it.isNotEmpty() },
                    warnings = state.warnings.trim().takeIf { it.isNotEmpty() },
                    notes = state.notes.trim().takeIf { it.isNotEmpty() },
                    isLocal = true
                )
            )
            _events.send(DrugFormEvent.Saved)
        }
    }
}
