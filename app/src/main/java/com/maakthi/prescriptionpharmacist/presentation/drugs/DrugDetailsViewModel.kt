package com.maakthi.prescriptionpharmacist.presentation.drugs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditEntry
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.usecase.DeleteDrugUseCase
import com.maakthi.prescriptionpharmacist.domain.usecase.GetDrugAuditUseCase
import com.maakthi.prescriptionpharmacist.domain.usecase.GetDrugUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DrugDetailsUiState {
    data object Loading : DrugDetailsUiState
    data class Loaded(val drug: Drug, val audit: List<DrugAuditEntry>) : DrugDetailsUiState
    data object NotFound : DrugDetailsUiState
}

@HiltViewModel
class DrugDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDrug: GetDrugUseCase,
    private val getDrugAudit: GetDrugAuditUseCase,
    private val deleteDrug: DeleteDrugUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val drugId: String = savedStateHandle.get<String>("drugId").orEmpty()

    private val _uiState = MutableStateFlow<DrugDetailsUiState>(DrugDetailsUiState.Loading)
    val uiState: StateFlow<DrugDetailsUiState> = _uiState.asStateFlow()

    private val _confirmDelete = MutableStateFlow(false)
    val confirmDelete: StateFlow<Boolean> = _confirmDelete.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = DrugDetailsUiState.Loading
            val drug = getDrug(drugId)
            _uiState.value = if (drug == null) {
                DrugDetailsUiState.NotFound
            } else {
                DrugDetailsUiState.Loaded(drug, getDrugAudit(drugId))
            }
        }
    }

    fun requestDelete() {
        _confirmDelete.value = true
    }

    fun dismissDelete() {
        _confirmDelete.value = false
    }

    fun confirmDelete() {
        _confirmDelete.value = false
        viewModelScope.launch {
            val performedBy = runCatching { authRepository.currentUser.first() }.getOrNull()
                ?.let { it.displayName ?: it.uid } ?: "pharmacist"
            deleteDrug(drugId, performedBy)
            _deleted.value = true
        }
    }
}
