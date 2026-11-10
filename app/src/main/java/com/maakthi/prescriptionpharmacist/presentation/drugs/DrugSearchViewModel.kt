package com.maakthi.prescriptionpharmacist.presentation.drugs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.repository.DrugRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val drugRepository: DrugRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DrugSearchUiState>(DrugSearchUiState.Idle)
    val uiState: StateFlow<DrugSearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun search() {
        val q = _query.value.trim()
        if (q.isEmpty()) {
            _uiState.value = DrugSearchUiState.Idle
            return
        }
        viewModelScope.launch {
            _uiState.value = DrugSearchUiState.Loading
            val local = drugRepository.searchLocal(q)
            val remote = drugRepository.searchRemote(q)
            _uiState.value = when {
                local.isEmpty() && remote.isEmpty() -> DrugSearchUiState.Empty
                else -> DrugSearchUiState.Results(local = local, remote = remote, query = q)
            }
        }
    }

    fun deleteLocalDrug(id: String) {
        viewModelScope.launch {
            drugRepository.deleteDrug(id)
            search()
        }
    }
}
