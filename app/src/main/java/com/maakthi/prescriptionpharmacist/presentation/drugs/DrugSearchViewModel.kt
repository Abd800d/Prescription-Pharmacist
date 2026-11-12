package com.maakthi.prescriptionpharmacist.presentation.drugs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugFilterOptions
import com.maakthi.prescriptionpharmacist.domain.model.DrugFilters
import com.maakthi.prescriptionpharmacist.domain.model.VerificationFilter
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.repository.DrugEntryRepository
import com.maakthi.prescriptionpharmacist.domain.usecase.AddDrugUseCase
import com.maakthi.prescriptionpharmacist.domain.usecase.DeleteDrugUseCase
import com.maakthi.prescriptionpharmacist.domain.usecase.GetDrugFilterOptionsUseCase
import com.maakthi.prescriptionpharmacist.domain.usecase.SearchDrugsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DrugSearchUiState {
    data object Idle : DrugSearchUiState
    data object Loading : DrugSearchUiState
    data class Results(
        val local: List<Drug>,
        val remote: List<Drug>,
        val query: String
    ) : DrugSearchUiState

    data object Empty : DrugSearchUiState
    data class Error(val message: String?) : DrugSearchUiState
}

@HiltViewModel
class DrugSearchViewModel @Inject constructor(
    private val searchDrugs: SearchDrugsUseCase,
    private val deleteDrug: DeleteDrugUseCase,
    private val addDrug: AddDrugUseCase,
    private val filterOptions: GetDrugFilterOptionsUseCase,
    private val drugRepository: DrugEntryRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DrugSearchUiState>(DrugSearchUiState.Idle)
    val uiState: StateFlow<DrugSearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filters = MutableStateFlow(DrugFilters())
    val filters: StateFlow<DrugFilters> = _filters.asStateFlow()

    private val _options = MutableStateFlow(DrugFilterOptions())
    val options: StateFlow<DrugFilterOptions> = _options.asStateFlow()

    /** Drug awaiting the delete-confirmation dialog. */
    private val _deleteTarget = MutableStateFlow<Drug?>(null)
    val deleteTarget: StateFlow<Drug?> = _deleteTarget.asStateFlow()

    init {
        viewModelScope.launch {
            _options.value = filterOptions()
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
        if (value.isBlank() && _uiState.value is DrugSearchUiState.Results) {
            _uiState.value = DrugSearchUiState.Idle
        }
    }

    fun onDosageFormFilter(value: String?) {
        _filters.value = _filters.value.copy(dosageForm = value)
        search()
    }

    fun onManufacturerFilter(value: String?) {
        _filters.value = _filters.value.copy(manufacturer = value)
        search()
    }

    fun onVerificationFilter(value: VerificationFilter) {
        _filters.value = _filters.value.copy(verification = value)
        search()
    }

    fun search() {
        val q = _query.value.trim()
        viewModelScope.launch {
            _uiState.value = DrugSearchUiState.Loading
            val local = searchDrugs(q, _filters.value)
            val remote = if (q.isEmpty()) emptyList() else drugRepository.searchRemote(q)
            _uiState.value = when {
                local.isEmpty() && remote.isEmpty() -> DrugSearchUiState.Empty
                else -> DrugSearchUiState.Results(local = local, remote = remote, query = q)
            }
        }
    }

    /** Imports an openFDA record into the local database as a verified entry. */
    fun importRemote(drug: Drug, onImported: (String) -> Unit) {
        viewModelScope.launch {
            val performedBy = currentUserName()
            val result = addDrug(
                drug.copy(
                    id = drug.id,
                    isLocal = true,
                    isVerified = true,
                    sourceName = drug.sourceName ?: "openFDA",
                    sourceUrl = drug.sourceUrl
                ),
                performedBy = performedBy
            )
            result.getOrNull()?.let { saved -> onImported(saved.id) }
        }
    }

    fun requestDelete(drug: Drug) {
        _deleteTarget.value = drug
    }

    fun dismissDelete() {
        _deleteTarget.value = null
    }

    fun confirmDelete() {
        val drug = _deleteTarget.value ?: return
        _deleteTarget.value = null
        viewModelScope.launch {
            deleteDrug(drug.id, currentUserName())
            search()
        }
    }

    private suspend fun currentUserName(): String =
        runCatching { authRepository.currentUser.first() }.getOrNull()
            ?.let { it.displayName ?: it.uid } ?: "pharmacist"
}
