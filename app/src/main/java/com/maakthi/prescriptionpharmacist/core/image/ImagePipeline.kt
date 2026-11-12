package com.maakthi.prescriptionpharmacist.core.image

/**
 * Options for rendering a new working image from the current one.
 * All geometry is expressed in normalized (0..1) coordinates of the current
 * upright image.
 */
data class RenderOptions(
    /** Rotation in degrees, multiples of 90. */
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val cropWindow: CropWindow? = null,
    /** 1.0 = neutral. */
    val brightness: Float = 1f,
    /** 1.0 = neutral. */
    val contrast: Float = 1f,
    /** 0 = none. */
    val sharpness: Float = 0f
)

/**
 * Real quality measurements of a prescription photo, used to warn the
 * pharmacist before analysis (blurry / dark / skewed images).
 */
data class ImageQualityReport(
    val width: Int,
    val height: Int,
    /** Variance of the Laplacian (higher = sharper). */
    val blurScore: Double,
    val isLikelyBlurry: Boolean,
    /** Mean luma 0..255. */
    val meanBrightness: Double,
    val isTooDark: Boolean,
    val isTooBright: Boolean,
    /** Luma standard deviation (higher = more contrast). */
    val contrastScore: Double,
    val isLowContrast: Boolean,
    /** Estimated document skew in degrees. */
    val skewDegrees: Int
) {
    val hasWarnings: Boolean
        get() = isLikelyBlurry || isTooDark || isTooBright || isLowContrast || skewDegrees != 0

    companion object {
        const val BLUR_THRESHOLD = 55.0
        const val DARK_THRESHOLD = 55.0
        const val BRIGHT_THRESHOLD = 225.0
        const val LOW_CONTRAST_THRESHOLD = 28.0
    }
}

/**
 * Real image input pipeline: import, quality analysis, rotate/flip/crop/
 * enhance rendering, and compression. Implementations are injectable so the
 * ViewModels are unit-testable.
 */
interface ImagePipeline {

    /** Absolute path for the next camera capture (FileProvider-friendly). */
    fun createCaptureTarget(): String

    /** Copies a content:// or file:// source into the app working area. */
    suspend fun importImage(source: String?): String?

    /** Pixel dimensions of an upright image. */
    suspend fun imageDimensions(path: String): Pair<Int, Int>

    /** Measures blur / brightness / contrast / skew of an image. */
    suspend fun quality(path: String): ImageQualityReport

    /**
     * Applies rotation, flip, crop and enhancements; writes a NEW working
     * file (lossless PNG) and returns its path. Returns null on failure.
     */
    suspend fun render(path: String, options: RenderOptions): String?

    /**
     * Produces the upload JPEG while preserving handwriting legibility
     * (bounded dimension, high quality). Returns null on failure.
     */
    suspend fun compressForUpload(path: String): ByteArray?

    /** Deletes working copies older than 24 hours. */
    suspend fun cleanupOldTemp()
}
