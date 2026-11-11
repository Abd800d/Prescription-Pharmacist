package com.maakthi.prescriptionpharmacist.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.maakthi.prescriptionpharmacist.core.media.ImageCompression
import java.io.ByteArrayOutputStream

/**
 * Executes the pure [ImageCompression] plan on Android bitmaps: decode with
 * the planned downsample factor, keep EXIF rotation upright, re-encode JPEG
 * at the planned quality — visually lossless, much smaller files.
 */
object ImageCompressor {

    fun compress(imageBytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return imageBytes

        val plan = ImageCompression.planCompression(bounds.outWidth, bounds.outHeight, imageBytes.size.toLong())
        if (plan.skipCompression) return imageBytes

        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageCompression.sampleSize(bounds.outWidth, bounds.outHeight, plan)
        }
        val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
            ?: return imageBytes
        val rotated = upright(decoded, imageBytes)
        val out = ByteArrayOutputStream()
        rotated.compress(Bitmap.CompressFormat.JPEG, plan.quality, out)
        if (rotated !== decoded) rotated.recycle()
        decoded.recycle()
        return out.toByteArray()
    }

    /** EXIF orientation handling for camera JPEGs. */
    private fun upright(bitmap: Bitmap, imageBytes: ByteArray): Bitmap {
        val exif = androidx.exifinterface.media.ExifInterface(imageBytes.inputStream())
        val orientation = exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        )
        val degrees = when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
