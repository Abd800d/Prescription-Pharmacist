package com.maakthi.prescriptionpharmacist.presentation.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.core.util.ImageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import javax.inject.Inject

/** Crop aspect presets. */
enum class CropAspect(val labelKey: String) {
    FREE("crop_aspect_free"),
    SQUARE("crop_aspect_1_1"),
    LANDSCAPE("crop_aspect_4_3"),
    PORTRAIT("crop_aspect_3_4")
}

sealed interface CropEvent {
    data class Saved(val imagePath: String) : CropEvent
}

@HiltViewModel
class CropEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val imagePath: String = Uri.decode(savedStateHandle.get<String>("imagePath").orEmpty())

    private val _rotationDegrees = MutableStateFlow(0)
    val rotationDegrees: StateFlow<Int> = _rotationDegrees.asStateFlow()

    private val _flipHorizontal = MutableStateFlow(false)
    val flipHorizontal: StateFlow<Boolean> = _flipHorizontal.asStateFlow()

    private val _aspect = MutableStateFlow(CropAspect.FREE)
    val aspect: StateFlow<CropAspect> = _aspect.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _events = Channel<CropEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun rotateLeft() {
        _rotationDegrees.value = (_rotationDegrees.value + 270) % 360
    }

    fun rotateRight() {
        _rotationDegrees.value = (_rotationDegrees.value + 90) % 360
    }

    fun toggleFlip() {
        _flipHorizontal.value = !_flipHorizontal.value
    }

    fun reset() {
        _rotationDegrees.value = 0
        _flipHorizontal.value = false
        _aspect.value = CropAspect.FREE
    }

    fun setAspect(newAspect: CropAspect) {
        _aspect.value = newAspect
    }

    /** Applies rotation, flip and center crop, then saves a new JPEG. */
    fun applyEdits() {
        viewModelScope.launch {
            _saving.value = true
            val resultPath = withContext(Dispatchers.IO) { renderEditedImage() }
            _saving.value = false
            if (resultPath != null) {
                _events.send(CropEvent.Saved(resultPath))
            }
        }
    }

    private fun renderEditedImage(): String? {
        return try {
            val original = BitmapFactory.decodeFile(imagePath) ?: return null

            val matrix = Matrix().apply {
                postRotate(_rotationDegrees.value.toFloat())
                if (_flipHorizontal.value) postScale(-1f, 1f)
            }
            val rotated = Bitmap.createBitmap(
                original, 0, 0, original.width, original.height, matrix, true
            )

            val cropped = centerCrop(rotated, _aspect.value) ?: rotated

            val outFile = ImageUtils.createCaptureFile(context)
            FileOutputStream(outFile).use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (cropped !== rotated) cropped.recycle()
            if (rotated !== original) rotated.recycle()
            original.recycle()
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun centerCrop(bitmap: Bitmap, aspect: CropAspect): Bitmap? {
        if (aspect == CropAspect.FREE) return null
        val targetRatio = when (aspect) {
            CropAspect.SQUARE -> 1f
            CropAspect.LANDSCAPE -> 4f / 3f
            CropAspect.PORTRAIT -> 3f / 4f
            CropAspect.FREE -> return null
        }
        val w = bitmap.width
        val h = bitmap.height
        val currentRatio = w.toFloat() / h
        val (cropW, cropH) = if (currentRatio > targetRatio) {
            ((h * targetRatio).toInt()) to h
        } else {
            w to ((w / targetRatio).toInt())
        }
        val x = (w - cropW) / 2
        val y = (h - cropH) / 2
        return Bitmap.createBitmap(bitmap, x, y, cropW, cropH)
    }
}
