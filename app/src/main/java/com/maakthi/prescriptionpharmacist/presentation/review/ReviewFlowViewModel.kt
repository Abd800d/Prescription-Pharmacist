package com.maakthi.prescriptionpharmacist.presentation.review

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.core.util.ImageUtils
import com.maakthi.prescriptionpharmacist.domain.model.AnalysisResult
import com.maakthi.prescriptionpharmacist.domain.model.AnalysisWarning
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ConfidenceLevel
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.Medication
import com.maakthi.prescriptionpharmacist.domain.model.MedicationAlternative
import com.maakthi.prescriptionpharmacist.domain.model.MedicationCandidate
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType
import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.model.PrescriptionStatus
import com.maakthi.prescriptionpharmacist.domain.model.confidenceLevelOf
import com.maakthi.prescriptionpharmacist.domain.usecase.AnalyzePrescriptionUseCase
import com.maakthi.prescriptionpharmacist.domain.usecase.SavePrescriptionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/** A medication row being reviewed/edited before the pharmacist confirms it. */
data class EditableMedication(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val scientificName: String? = null,
    val type: MedicationType = MedicationType.UNKNOWN,
    val strength: String? = null,
    val form: String? = null,
    val dose: String? = null,
    val route: String? = null,
    val duration: String? = null,
    val confidence: Float = 1f,
    val rawText: String? = null,
    val isUncertain: Boolean = false,
    val alternatives: List<MedicationAlternative> = emptyList()
) {
    val confidenceLevel: ConfidenceLevel
        get() = confidenceLevelOf(confidence)
}

sealed interface ReviewUiState {
    data object Analyzing : ReviewUiState
    data class Result(
        val medications: List<EditableMedication>,
        val warnings: List<AnalysisWarning> = emptyList()
    ) : ReviewUiState
    data class Failed(val code: ErrorCode) : ReviewUiState
}

sealed interface ReviewEvent {
    data object Saved : ReviewEvent
}

@HiltViewModel
class ReviewFlowViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val analyzePrescription: AnalyzePrescriptionUseCase,
    private val savePrescription: SavePrescriptionUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val imagePath: String = Uri.decode(
        savedStateHandle.get<String>("imagePath").orEmpty()
    )

    /** Path of the prescription photo under review (shown in the UI). */
    val imagePathState: String get() = imagePath

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Analyzing)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _events = Channel<ReviewEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Pharmacist attestation gate: CONFIRMED saves are blocked until true. */
    private val _attested = MutableStateFlow(false)
    val attested: StateFlow<Boolean> = _attested.asStateFlow()

    private var lastAnalysis: AnalysisResult? = null
    private var cachedUploadBytes: ByteArray? = null

    init {
        analyze()
    }

    fun analyze() {
        viewModelScope.launch {
            _uiState.value = ReviewUiState.Analyzing
            val bytes = withContext(Dispatchers.IO) {
                ImageUtils.compressForUpload(imagePath).also { cachedUploadBytes = it }
            }
            if (bytes == null) {
                _uiState.value = ReviewUiState.Failed(ErrorCode.IMAGE_UNREADABLE)
                return@launch
            }
            when (val result = analyzePrescription(bytes)) {
                is AppResult.Success -> {
                    lastAnalysis = result.data
                    _uiState.value = ReviewUiState.Result(
                        medications = result.data.medications.map { it.toEditable() },
                        warnings = result.data.warnings
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = ReviewUiState.Failed(result.code)
                }
            }
        }
    }

    fun onNotesChanged(value: String) {
        _notes.value = value
    }

    /** Records the pharmacist's explicit attestation (stage 12 gate). */
    fun setAttested(value: Boolean) {
        _attested.value = value
    }

    fun addMedication(medication: EditableMedication) {
        _uiState.update { state ->
            if (state is ReviewUiState.Result) {
                state.copy(medications = state.medications + medication)
            } else {
                state
            }
        }
    }

    fun updateMedication(medication: EditableMedication) {
        _uiState.update { state ->
            if (state is ReviewUiState.Result) {
                state.copy(
                    medications = state.medications.map {
                        if (it.id == medication.id) medication else it
                    }
                )
            } else {
                state
            }
        }
    }

    fun removeMedication(id: String) {
        _uiState.update { state ->
            if (state is ReviewUiState.Result) {
                state.copy(medications = state.medications.filterNot { it.id == id })
            } else {
                state
            }
        }
    }

    /** Saves the prescription with the pharmacist decision status. */
    fun save(status: PrescriptionStatus) {
        val state = _uiState.value as? ReviewUiState.Result ?: return
        // Stage 12: a CONFIRMED result is impossible without explicit
        // pharmacist attestation. Drafts may be saved without it.
        if (status == PrescriptionStatus.CONFIRMED && !_attested.value) return
        viewModelScope.launch {
            val prescriptionId = UUID.randomUUID().toString()
            val prescription = Prescription(
                id = prescriptionId,
                imagePath = imagePath,
                createdAt = System.currentTimeMillis(),
                status = status,
                notes = _notes.value.trim().takeIf { it.isNotEmpty() },
                analysisLanguage = lastAnalysis?.language,
                remoteRequestId = lastAnalysis?.requestId,
                medications = state.medications.map { it.toDomain(prescriptionId) }
            )
            savePrescription(prescription, cachedUploadBytes)
            _events.send(ReviewEvent.Saved)
        }
    }

    /** Discards the review and deletes the copied image. */
    fun discard() {
        viewModelScope.launch(Dispatchers.IO) {
            ImageUtils.deleteImage(imagePath)
        }
    }

    private fun MedicationCandidate.toEditable() = EditableMedication(
        id = UUID.randomUUID().toString(),
        name = name,
        scientificName = scientificName,
        type = type,
        strength = strength,
        form = form,
        dose = dose,
        route = route,
        duration = duration,
        confidence = confidence,
        rawText = rawText,
        isUncertain = isUncertain,
        alternatives = alternatives
    )

    private fun EditableMedication.toDomain(prescriptionId: String) = Medication(
        id = id,
        prescriptionId = prescriptionId,
        name = name,
        scientificName = scientificName,
        type = type,
        strength = strength,
        form = form,
        dose = dose,
        route = route,
        duration = duration,
        confidence = confidence,
        rawText = rawText,
        isUncertain = isUncertain,
        alternatives = alternatives,
        confirmedByPharmacist = true
    )
}
