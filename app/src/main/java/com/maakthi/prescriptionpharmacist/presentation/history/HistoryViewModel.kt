package com.maakthi.prescriptionpharmacist.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.usecase.DeletePrescriptionUseCase
import com.maakthi.prescriptionpharmacist.domain.usecase.ObservePrescriptionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observePrescriptions: ObservePrescriptionsUseCase,
    private val deletePrescription: DeletePrescriptionUseCase
) : ViewModel() {

    val prescriptions: StateFlow<List<Prescription>> =
        observePrescriptions().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun delete(id: String) {
        viewModelScope.launch { deletePrescription(id) }
    }
}
