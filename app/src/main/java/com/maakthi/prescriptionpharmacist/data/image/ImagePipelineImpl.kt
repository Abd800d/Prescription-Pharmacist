package com.maakthi.prescriptionpharmacist.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.maakthi.prescriptionpharmacist.core.image.CropWindow
import com.maakthi.prescriptionpharmacist.core.image.ImageProcessing
import com.maakthi.prescriptionpharmacist.core.image.ImagePipeline
import com.maakthi.prescriptionpharmacist.core.image.ImageQualityReport
import com.maakthi.prescriptionpharmacist.core.image.RenderOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real implementation of [ImagePipeline] on top of android.graphics.
 *
 * - Working copies live in `filesDir/work_images` (temporary, auto-cleaned).
 * - Camera captures live in `filesDir/images` (FileProvider path).
 * - Rendered intermediates are lossless PNG to avoid generation loss.
 * - Uploads are JPEG with bounded size to preserve handwriting legibility.
 */
@Singleton
class ImagePipelineImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ImagePipeline {

    private val workDir: File get() = File(context.filesDir, "work_images").apply { mkdirs() }
    private val captureDir: File get() = File(context.filesDir, "images").apply { mkdirs() }

    override fun createCaptureTarget(): String =
        File(captureDir, "rx_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg").absolutePath

    override suspend fun importImage(source: String?): String? = withContext(Dispatchers.IO) {
        if (source.isNullOrBlank()) return@withContext null
        try {
            val uri = Uri.parse(source)
            val target = File(workDir, "wk_${System.currentTimeMillis()}_${UUID.randomUUID()}.img")
            when (uri.scheme) {
                null, "file" -> {
                    val src = File(uri.path ?: return@withContext null)
                    if (!src.exists()) return@withContext null
                    src.copyTo(target, overwrite = true)
                }
                else -> {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: return@withContext null
                    input.use { s -> FileOutputStream(target).use { out -> s.copyTo(out) } }
                }
            }
            // Validate the copy is a decodable image.
            val bounds = decodeBounds(target.absolutePath) ?: return@withContext null
            if (bounds.first <= 0 || bounds.second <= 0) return@withContext null
            target.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun imageDimensions(path: String): Pair<Int, Int> =
        withContext(Dispatchers.IO) {
            decodeBounds(path) ?: (0 to 0)
        }

    override suspend fun quality(path: String): ImageQualityReport =
        withContext(Dispatchers.IO) {
            try {
                val sampled = decodeSampled(path, QUALITY_ANALYSIS_DIM) ?: return@withContext emptyReport()
                val w = sampled.width
                val h = sampled.height
                val pixels = IntArray(w * h)
                sampled.getPixels(pixels, 0, w, 0, 0, w, h)
                sampled.recycle()

                val gray = ImageProcessing.luma(pixels)
                val blur = ImageProcessing.laplacianVariance(gray, w, h)
                val mean = ImageProcessing.mean(gray)
                val contrast = ImageProcessing.stdDev(gray)
                val skew = ImageProcessing.estimateSkewDegrees(gray, w, h)

                ImageQualityReport(
                    width = decodeBounds(path)?.first ?: w,
                    height = decodeBounds(path)?.second ?: h,
                    blurScore = blur,
                    isLikelyBlurry = blur < ImageQualityReport.BLUR_THRESHOLD,
                    meanBrightness = mean,
                    isTooDark = mean < ImageQualityReport.DARK_THRESHOLD,
                    isTooBright = mean > ImageQualityReport.BRIGHT_THRESHOLD,
                    contrastScore = contrast,
                    isLowContrast = contrast < ImageQualityReport.LOW_CONTRAST_THRESHOLD,
                    skewDegrees = skew
                )
            } catch (e: Exception) {
                emptyReport()
            }
        }

    override suspend fun render(path: String, options: RenderOptions): String? =
        withContext(Dispatchers.IO) {
            try {
                var bitmap = decodeSampled(path, MAX_RENDER_DIM)
                    ?: return@withContext null

                // 1) Rotate & flip (exact, on the upright working image).
                val normalizedRotation = ((options.rotationDegrees % 360) + 360) % 360
                if (normalizedRotation != 0 || options.flipHorizontal) {
                    val matrix = Matrix().apply {
                        postRotate(normalizedRotation.toFloat())
                        if (options.flipHorizontal) postScale(-1f, 1f)
                    }
                    val transformed = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                    if (transformed !== bitmap) bitmap.recycle()
                    bitmap = transformed
                }

                // 2) Crop (normalized window on the upright image).
                val window = options.cropWindow?.clamped()
                if (window != null && window.isValid() && window != CropWindow.FULL) {
                    val x = (window.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                    val y = (window.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                    val w = (window.width * bitmap.width).toInt()
                        .coerceIn(1, bitmap.width - x)
                    val h = (window.height * bitmap.height).toInt()
                        .coerceIn(1, bitmap.height - y)
                    val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
                    if (cropped !== bitmap) bitmap.recycle()
                    bitmap = cropped
                }

                // 3) Enhancements (brightness / contrast / sharpening).
                val needsColor = options.brightness != 1f || options.contrast != 1f
                val needsSharpen = options.sharpness > 0.01f
                if (needsColor || needsSharpen) {
                    val w = bitmap.width
                    val h = bitmap.height
                    val pixels = IntArray(w * h)
                    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                    var processed = pixels
                    if (needsColor) {
                        processed = ImageProcessing.adjustBrightnessContrast(
                            processed, options.brightness, options.contrast
                        )
                    }
                    if (needsSharpen) {
                        processed = ImageProcessing.sharpen(processed, w, h, options.sharpness)
                    }
                    bitmap.setPixels(processed, 0, w, 0, 0, w, h)
                }

                // 4) Write lossless PNG working copy.
                val out = File(workDir, "wk_${System.currentTimeMillis()}_${UUID.randomUUID()}.png")
                FileOutputStream(out).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                bitmap.recycle()
                out.absolutePath
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: Exception) {
                null
            }
        }

    override suspend fun compressForUpload(path: String): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeSampled(path, UPLOAD_MAX_DIM) ?: return@withContext null
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, UPLOAD_JPEG_QUALITY, output)
                bitmap.recycle()
                output.toByteArray()
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: Exception) {
                null
            }
        }

    override suspend fun cleanupOldTemp() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - TEMP_MAX_AGE_MS
        runCatching {
            workDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
        }
        Unit
    }

    // ---------- internals ----------

    private fun decodeBounds(path: String): Pair<Int, Int>? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    } catch (e: Exception) {
        null
    }

    private fun decodeSampled(path: String, maxDimension: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = ImageProcessing.calculateInSampleSize(
            bounds.outWidth, bounds.outHeight, maxDimension
        )
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)
    } catch (e: OutOfMemoryError) {
        null
    } catch (e: Exception) {
        null
    }

    private fun emptyReport() = ImageQualityReport(
        width = 0,
        height = 0,
        blurScore = 0.0,
        isLikelyBlurry = true,
        meanBrightness = 0.0,
        isTooDark = true,
        isTooBright = false,
        contrastScore = 0.0,
        isLowContrast = true,
        skewDegrees = 0
    )

    private companion object {
        const val MAX_RENDER_DIM = 2560
        const val UPLOAD_MAX_DIM = 2048
        const val UPLOAD_JPEG_QUALITY = 92
        const val QUALITY_ANALYSIS_DIM = 320
        const val TEMP_MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}
