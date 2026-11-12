package com.maakthi.prescriptionpharmacist.presentation.history

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.model.PrescriptionStatus
import com.maakthi.prescriptionpharmacist.domain.usecase.DeletePrescriptionUseCase
import com.maakthi.prescriptionpharmacist.domain.usecase.GetPrescriptionUseCase
import com.maakthi.prescriptionpharmacist.domain.usecase.UpdatePrescriptionStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetailEvent {
    data object Deleted : DetailEvent
}

@HiltViewModel
class PrescriptionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getPrescription: GetPrescriptionUseCase,
    private val updatePrescriptionStatus: UpdatePrescriptionStatusUseCase,
    private val deletePrescription: DeletePrescriptionUseCase
) : ViewModel() {

    private val prescriptionId: String = Uri.decode(
        savedStateHandle.get<String>("prescriptionId").orEmpty()
    )

    val prescription: StateFlow<Prescription?> =
        getPrescription(prescriptionId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val _events = Channel<DetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setStatus(status: PrescriptionStatus) {
        viewModelScope.launch { updatePrescriptionStatus(prescriptionId, status) }
    }

    fun delete() {
        viewModelScope.launch {
            deletePrescription(prescriptionId)
            _events.send(DetailEvent.Deleted)
        }
    }
}
