package com.maakthi.prescriptionpharmacist.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Image helpers: copy picked/captured photos into app storage and compress
 * them for secure upload to the analysis backend.
 */
object ImageUtils {

    private const val MAX_UPLOAD_DIMENSION = 2048
    private const val JPEG_QUALITY = 90

    /** Copies a content:// or file:// URI into app files and returns the local path. */
    fun copyToAppStorage(context: Context, uri: Uri): String? {
        return try {
            val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
            val target = File(imagesDir, "rx_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { source ->
                FileOutputStream(target).use { out -> source.copyTo(out) }
            }
            target.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** Creates a new capture file inside app storage and returns it. */
    fun createCaptureFile(context: Context): File {
        val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
        return File(imagesDir, "rx_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
    }

    /**
     * Decodes and downscales a stored image to a reasonable upload size.
     * Returns null when the file cannot be decoded.
     */
    fun compressForUpload(path: String): ByteArray? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_UPLOAD_DIMENSION) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(path, options) ?: return null

            val scale = MAX_UPLOAD_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            output.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /** Deletes a stored image file (best effort). */
    fun deleteImage(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
