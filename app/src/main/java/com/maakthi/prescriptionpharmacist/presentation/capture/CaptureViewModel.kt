package com.maakthi.prescriptionpharmacist.presentation.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.core.image.ImagePipeline
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CaptureEvent {
    /** A working copy is ready — navigate to crop & edit. */
    data class ImageReady(val imagePath: String) : CaptureEvent
}

/**
 * Handles camera and gallery intake: creates capture targets, imports
 * gallery images into the temporary working area, and reports errors.
 * All image work goes through [ImagePipeline] (injectable → testable).
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val imagePipeline: ImagePipeline
) : ViewModel() {

    private val _events = Channel<CaptureEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _error = MutableStateFlow<ErrorCode?>(null)
    val error: StateFlow<ErrorCode?> = _error.asStateFlow()

    init {
        viewModelScope.launch { imagePipeline.cleanupOldTemp() }
    }

    /** Absolute path for the next camera capture. */
    fun createCaptureTarget(): String = imagePipeline.createCaptureTarget()

    /**
     * Imports a gallery image (as a uri string) into a temporary working
     * copy and emits navigation to crop & edit.
     */
    fun onGalleryImagePicked(uriString: String?) {
        viewModelScope.launch {
            val path = imagePipeline.importImage(uriString)
            if (path == null) {
                _error.value = ErrorCode.IMAGE_UNREADABLE
            } else {
                _error.value = null
                _events.send(CaptureEvent.ImageReady(path))
            }
        }
    }

    /** Accepts a freshly captured photo file and emits navigation to crop & edit. */
    fun onPhotoCaptured(path: String?) {
        viewModelScope.launch {
            if (path == null) {
                _error.value = ErrorCode.IMAGE_UNREADABLE
                return@launch
            }
            // Verify the captured file is a readable image.
            val dimensions = imagePipeline.imageDimensions(path)
            if (dimensions.first <= 0) {
                _error.value = ErrorCode.IMAGE_UNREADABLE
            } else {
                _error.value = null
                _events.send(CaptureEvent.ImageReady(path))
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
