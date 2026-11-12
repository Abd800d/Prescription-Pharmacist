package com.maakthi.prescriptionpharmacist.presentation.media

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.MediaAsset
import com.maakthi.prescriptionpharmacist.domain.model.MediaKind
import com.maakthi.prescriptionpharmacist.domain.repository.MediaRepository
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface MediaEvent {
    data class Failed(val message: String) : MediaEvent
    data object Saved : MediaEvent
    data class Synced(val count: Int) : MediaEvent
    data object Deleted : MediaEvent
}

data class MediaLibraryState(
    val assets: List<MediaAsset> = emptyList(),
    val filter: MediaKind? = null,
    val readablePaths: Map<String, String> = emptyMap(),
    val syncEnabled: Boolean = false,
    val busy: Boolean = false
)

@HiltViewModel
class MediaManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MediaLibraryState())
    val state: StateFlow<MediaLibraryState> = _state.asStateFlow()

    private val _events = Channel<MediaEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            mediaRepository.observeAssets().collect { assets ->
                _state.value = _state.value.copy(assets = assets)
                refreshReadable(assets)
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.value = _state.value.copy(syncEnabled = settings.mediaSyncEnabled)
            }
        }
        viewModelScope.launch { mediaRepository.syncNow() }
    }

    fun setFilter(kind: MediaKind?) {
        _state.value = _state.value.copy(filter = kind)
    }

    fun filtered(): List<MediaAsset> {
        val filter = _state.value.filter ?: return _state.value.assets
        return _state.value.assets.filter { it.kind == filter }
    }

    /** Imports a picked image as a prescription image (date + user recorded). */
    fun addPrescriptionImage(uri: Uri) = addImage(uri, productTitle = null)

    /** Imports a picked image as a product image with a title. */
    fun addProductImage(uri: Uri, title: String) = addImage(uri, productTitle = title)

    private fun addImage(uri: Uri, productTitle: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            val result = if (bytes == null) {
                AppResult.error(com.maakthi.prescriptionpharmacist.domain.model.ErrorCode.IMAGE_UNREADABLE)
            } else if (productTitle == null) {
                mediaRepository.savePrescriptionImage(bytes, ownerName = "")
            } else {
                mediaRepository.saveProductImage(bytes, title = productTitle)
            }
            _state.value = _state.value.copy(busy = false)
            when (result) {
                is AppResult.Success -> _events.send(MediaEvent.Saved)
                is AppResult.Error -> _events.send(MediaEvent.Failed(result.message ?: "save_failed"))
            }
        }
    }

    fun deleteAsset(id: String) {
        viewModelScope.launch {
            when (val result = mediaRepository.deleteAsset(id)) {
                is AppResult.Success -> _events.send(MediaEvent.Deleted)
                is AppResult.Error -> _events.send(MediaEvent.Failed(result.message ?: "delete_failed"))
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val result = mediaRepository.syncNow()
            _state.value = _state.value.copy(busy = false)
            when (result) {
                is AppResult.Success -> _events.send(MediaEvent.Synced(result.data))
                is AppResult.Error -> _events.send(MediaEvent.Failed(result.message ?: "sync_failed"))
            }
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            mediaRepository.setSyncEnabled(enabled)
        }
    }

    /** Decrypts encrypted assets into the display cache (permission-checked). */
    private suspend fun refreshReadable(assets: List<MediaAsset>) {
        val paths = mutableMapOf<String, String>()
        assets.forEach { asset ->
            when (val opened = mediaRepository.openReadable(asset.id)) {
                is AppResult.Success -> paths[asset.id] = opened.data.absolutePath
                is AppResult.Error -> Unit
            }
        }
        _state.value = _state.value.copy(readablePaths = paths)
    }
}
