package com.maakthi.prescriptionpharmacist.presentation.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.core.image.CropAspect
import com.maakthi.prescriptionpharmacist.core.image.CropMath
import com.maakthi.prescriptionpharmacist.core.image.CropWindow
import com.maakthi.prescriptionpharmacist.core.image.ImagePipeline
import com.maakthi.prescriptionpharmacist.core.image.RenderOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CropEditUiState(
    val isLoading: Boolean = true,
    /** The imported source copy; every render starts from here. */
    val originalPath: String? = null,
    /** Current preview working copy (transforms applied). */
    val renderedPath: String? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    // Transform state (baked into renderedPath).
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val sharpness: Float = 0f,
    // Crop state (applied at save time).
    val aspect: CropAspect = CropAspect.FREE,
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    // Quality info (of the source image).
    val skewDegrees: Int = 0,
    val showError: Boolean = false
)

sealed interface CropEditEvent {
    data class Saved(val imagePath: String) : CropEditEvent
}

/**
 * Drives the crop & edit screen against the real [ImagePipeline].
 *
 * Every render starts from the ORIGINAL copy with absolute options, so
 * repeated edits never accumulate decode/generation loss. Rotation, flip and
 * enhancements re-render the preview immediately; the crop window
 * (zoom/pan/aspect) is computed with [CropMath] and applied at save time.
 */
@HiltViewModel
class CropEditViewModel @Inject constructor(
    private val imagePipeline: ImagePipeline
) : ViewModel() {

    private val _state = MutableStateFlow(CropEditUiState())
    val state: StateFlow<CropEditUiState> = _state.asStateFlow()

    private val _events = Channel<CropEditEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun load(path: String) {
        if (_state.value.originalPath != null) return
        viewModelScope.launch {
            val dimensions = imagePipeline.imageDimensions(path)
            val quality = runCatching { imagePipeline.quality(path) }.getOrNull()
            _state.update {
                it.copy(
                    originalPath = path,
                    renderedPath = path,
                    imageWidth = dimensions.first,
                    imageHeight = dimensions.second,
                    skewDegrees = quality?.skewDegrees ?: 0,
                    isLoading = false
                )
            }
        }
    }

    // ---------- transform operations (re-render from the original) ----------

    fun rotate90() = render { copy(rotationDegrees = (rotationDegrees + 90) % 360) }

    fun flip() = render { copy(flipHorizontal = !flipHorizontal) }

    fun setBrightness(value: Float) {
        _state.update { it.copy(brightness = value.coerceIn(0.2f, 2f)) }
    }

    fun setContrast(value: Float) {
        _state.update { it.copy(contrast = value.coerceIn(0.2f, 2f)) }
    }

    fun setSharpness(value: Float) {
        _state.update { it.copy(sharpness = value.coerceIn(0f, 1f)) }
    }

    /** Re-renders the preview after enhancement sliders settle. */
    fun commitEnhancements() = render { this }

    /** Suggests brightness/contrast/sharpness from real quality metrics. */
    fun autoEnhance() {
        viewModelScope.launch {
            val quality = _state.value.originalPath
                ?.let { runCatching { imagePipeline.quality(it) }.getOrNull() }
            val targetLuma = 140.0
            val suggestedBrightness = if (quality != null && quality.meanBrightness > 0) {
                (targetLuma / quality.meanBrightness).toFloat().coerceIn(0.6f, 1.8f)
            } else {
                1.1f
            }
            val suggestedContrast = if (quality != null && quality.contrastScore > 0) {
                (55.0 / quality.contrastScore).toFloat().coerceIn(0.8f, 1.8f)
            } else {
                1.15f
            }
            render {
                copy(
                    brightness = suggestedBrightness,
                    contrast = suggestedContrast,
                    sharpness = 0.6f
                )
            }
        }
    }

    /** Rotates by the estimated skew to straighten the prescription. */
    fun autoDeskew() {
        val skew = _state.value.skewDegrees
        if (skew == 0) return
        render {
            copy(
                rotationDegrees = (((rotationDegrees - skew) % 360) + 360) % 360,
                skewDegrees = 0
            )
        }
    }

    // ---------- crop state ----------

    fun setAspect(aspect: CropAspect) {
        _state.update { it.copy(aspect = aspect) }
    }

    fun setZoom(zoom: Float) {
        _state.update { it.copy(zoom = zoom.coerceIn(1f, 5f)) }
    }

    fun setPan(dx: Float, dy: Float) {
        _state.update { current ->
            // Pan limits depend on zoom and image size; clamp conservatively
            // against the image extent (exact clamp happens in save()).
            val maxX = (current.imageWidth * (current.zoom - 1) / 2f).coerceAtLeast(0f)
            val maxY = (current.imageHeight * (current.zoom - 1) / 2f).coerceAtLeast(0f)
            current.copy(
                offsetX = (current.offsetX + dx).coerceIn(-maxX, maxX),
                offsetY = (current.offsetY + dy).coerceIn(-maxY, maxY)
            )
        }
    }

    fun reset() = render {
        copy(
            rotationDegrees = 0,
            flipHorizontal = false,
            brightness = 1f,
            contrast = 1f,
            sharpness = 0f,
            zoom = 1f,
            offsetX = 0f,
            offsetY = 0f
        )
    }

    /**
     * Applies the crop window computed from zoom/pan/aspect and emits the
     * saved path for the preview step.
     *
     * @param frameSizePx size of the crop frame on screen (px).
     */
    fun save(frameSizePx: Pair<Int, Int>) {
        viewModelScope.launch {
            val current = _state.value
            val original = current.originalPath ?: return@launch
            _state.update { it.copy(isLoading = true) }

            // Crop math runs on the TRANSFORMED image (what the user sees).
            val clamped = CropMath.clampOffset(
                imageWidth = current.imageWidth,
                imageHeight = current.imageHeight,
                frameWidth = frameSizePx.first.toFloat(),
                frameHeight = frameSizePx.second.toFloat(),
                scale = current.zoom,
                offsetX = current.offsetX,
                offsetY = current.offsetY
            )
            val window: CropWindow = CropMath.visibleWindow(
                imageWidth = current.imageWidth,
                imageHeight = current.imageHeight,
                frameWidth = frameSizePx.first.toFloat(),
                frameHeight = frameSizePx.second.toFloat(),
                scale = current.zoom,
                offsetX = clamped.first,
                offsetY = clamped.second
            )

            val saved = imagePipeline.render(
                original,
                RenderOptions(
                    rotationDegrees = current.rotationDegrees,
                    flipHorizontal = current.flipHorizontal,
                    cropWindow = window,
                    brightness = current.brightness,
                    contrast = current.contrast,
                    sharpness = current.sharpness
                )
            )
            _state.update { it.copy(isLoading = false) }
            if (saved == null) {
                _state.update { it.copy(showError = true) }
            } else {
                _events.send(CropEditEvent.Saved(saved))
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(showError = false) }
    }

    // ---------- internals ----------

    private fun render(mutation: CropEditUiState.() -> CropEditUiState) {
        val next = _state.value.mutation()
        render(next)
    }

    private fun render(next: CropEditUiState) {
        viewModelScope.launch {
            val original = next.originalPath ?: return@launch
            val rendered = imagePipeline.render(
                original,
                RenderOptions(
                    rotationDegrees = next.rotationDegrees,
                    flipHorizontal = next.flipHorizontal,
                    brightness = next.brightness,
                    contrast = next.contrast,
                    sharpness = next.sharpness
                )
            )
            if (rendered == null) {
                _state.update { it.copy(showError = true) }
            } else {
                val dimensions = imagePipeline.imageDimensions(rendered)
                _state.update {
                    next.copy(
                        renderedPath = rendered,
                        imageWidth = dimensions.first,
                        imageHeight = dimensions.second
                    )
                }
            }
        }
    }
}
