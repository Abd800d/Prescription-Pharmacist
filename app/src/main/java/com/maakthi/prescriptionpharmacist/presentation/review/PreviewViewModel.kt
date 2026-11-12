package com.maakthi.prescriptionpharmacist.presentation.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.core.image.ImagePipeline
import com.maakthi.prescriptionpharmacist.core.image.ImageQualityReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreviewUiState(
    val isLoading: Boolean = true,
    val imagePath: String? = null,
    val quality: ImageQualityReport? = null,
    /** Size of the upload-ready JPEG in kilobytes. */
    val uploadSizeKb: Int = 0,
    val consentGiven: Boolean = false,
    val showError: Boolean = false
)

sealed interface PreviewEvent {
    /** User consented; navigate to the review graph to start analysis. */
    data class StartAnalysis(val imagePath: String) : PreviewEvent
}

/**
 * Preview & consent step: shows real quality measurements and the upload
 * size, and only emits [PreviewEvent.StartAnalysis] after the pharmacist
 * explicitly consents — nothing is ever sent to the server before that.
 */
@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val imagePipeline: ImagePipeline
) : ViewModel() {

    private val _state = MutableStateFlow(PreviewUiState())
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    private val _events = Channel<PreviewEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun load(path: String) {
        if (_state.value.imagePath != null) return
        viewModelScope.launch {
            val quality = runCatching { imagePipeline.quality(path) }.getOrNull()
            val upload = runCatching { imagePipeline.compressForUpload(path) }.getOrNull()
            _state.update {
                it.copy(
                    isLoading = false,
                    imagePath = path,
                    quality = quality,
                    uploadSizeKb = ((upload?.size ?: 0) / 1024).coerceAtLeast(1),
                    showError = quality == null
                )
            }
        }
    }

    fun setConsent(consented: Boolean) {
        _state.update { it.copy(consentGiven = consented) }
    }

    /** Only callable after explicit user consent; starts the review flow. */
    fun startAnalysis() {
        val current = _state.value
        val path = current.imagePath ?: return
        if (!current.consentGiven) return
        viewModelScope.launch { _events.send(PreviewEvent.StartAnalysis(path)) }
    }

    fun dismissError() {
        _state.update { it.copy(showError = false) }
    }
}
