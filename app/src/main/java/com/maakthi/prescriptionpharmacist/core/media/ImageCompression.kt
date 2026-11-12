package com.maakthi.prescriptionpharmacist.core.media

/**
 * Compression policy: shrinks photos before saving WITHOUT visible quality
 * loss. Decisions are pure and unit-tested; the Android layer only executes
 * them (downscale with [com.maakthi.prescriptionpharmacist.core.image.ImageProcessing.calculateInSampleSize],
 * then re-encode JPEG at the planned quality).
 */
object ImageCompression {

    /** Longest edge kept — Full HD covers on-screen viewing of prescriptions. */
    const val MAX_DIMENSION = 1920

    /** JPEG quality: visually lossless for photos and documents. */
    const val JPEG_QUALITY = 85

    /** Files below this size are already small enough. */
    const val MIN_USEFUL_BYTES = 250 * 1024

    data class Plan(
        val maxDimension: Int,
        val quality: Int,
        val skipCompression: Boolean
    )

    /**
     * Plans the compression of one image. Everything is kept at or below
     * [MAX_DIMENSION] with quality [JPEG_QUALITY]; very small files are
     * stored as-is to avoid a pointless re-encode.
     */
    fun planCompression(width: Int, height: Int, byteSize: Long): Plan {
        val largestSide = maxOf(width, height)
        val skip = largestSide in 1..MAX_DIMENSION && byteSize <= MIN_USEFUL_BYTES
        return Plan(
            maxDimension = MAX_DIMENSION,
            quality = JPEG_QUALITY,
            skipCompression = skip
        )
    }

    /** Downsample factor for the plan (power of two, at least 1). */
    fun sampleSize(width: Int, height: Int, plan: Plan): Int {
        val largestSide = maxOf(width, height)
        var size = 1
        while (largestSide / size > plan.maxDimension && size < 64) {
            size *= 2
        }
        return size
    }

    /** Rough output size estimate used for sync quotas and UI. */
    fun estimatedBytes(byteSize: Long, plan: Plan): Long =
        if (plan.skipCompression) byteSize else (byteSize * plan.quality) / 100 / 2
}
