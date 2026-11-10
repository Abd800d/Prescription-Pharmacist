package com.maakthi.prescriptionpharmacist.presentation.capture

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.core.util.ImageUtils
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface CaptureEvent {
    data class ImageReady(val imagePath: String) : CaptureEvent
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _events = Channel<CaptureEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _error = MutableStateFlow<ErrorCode?>(null)
    val error: StateFlow<ErrorCode?> = _error.asStateFlow()

    /** Copies a gallery image into app storage and emits navigation to review. */
    fun onGalleryImagePicked(uri: Uri?) {
        viewModelScope.launch {
            if (uri == null) {
                _error.value = ErrorCode.IMAGE_UNREADABLE
                return@launch
            }
            val path = withContext(Dispatchers.IO) {
                ImageUtils.copyToAppStorage(context, uri)
            }
            if (path == null) {
                _error.value = ErrorCode.IMAGE_UNREADABLE
            } else {
                _error.value = null
                _events.send(CaptureEvent.ImageReady(path))
            }
        }
    }

    /** Accepts a freshly captured photo file and emits navigation to review. */
    fun onPhotoCaptured(path: String?) {
        viewModelScope.launch {
            if (path == null) {
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
