package com.maakthi.prescriptionpharmacist.core.image

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure pixel-array image algorithms (unit-testable on the JVM).
 * All functions operate on ARGB [IntArray]s or 0..255 luma [IntArray]s.
 */
object ImageProcessing {

    /** Extracts luma (0..255) from ARGB pixels. */
    fun luma(pixels: IntArray): IntArray {
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            out[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
        }
        return out
    }

    fun mean(values: IntArray): Double =
        if (values.isEmpty()) 0.0 else values.sumOf { it }.toDouble() / values.size

    /** Standard deviation — a simple contrast metric. */
    fun stdDev(values: IntArray): Double {
        if (values.isEmpty()) return 0.0
        val m = mean(values)
        var acc = 0.0
        for (v in values) {
            val d = v - m
            acc += d * d
        }
        return sqrt(acc / values.size)
    }

    /**
     * Variance of the Laplacian — the standard blur metric.
     * Uniform/blurry images score near 0; sharp edges score high.
     */
    fun laplacianVariance(gray: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3 || gray.size < width * height) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val lap = gray[i - 1] + gray[i + 1] + gray[i - width] + gray[i + width] - 4 * gray[i]
                sum += lap
                sumSq += lap.toDouble() * lap
                n++
            }
        }
        if (n == 0) return 0.0
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    /**
     * Estimates document skew in degrees (−[maxAngle]..[maxAngle]) using the
     * projection-profile method on a downscaled grayscale buffer: the
     * compensating angle that maximizes row-sum variance is the skew.
     */
    fun estimateSkewDegrees(
        gray: IntArray,
        width: Int,
        height: Int,
        maxAngle: Int = 12
    ): Int {
        if (width < 16 || height < 16) return 0
        var bestAngle = 0
        var bestVariance = -1.0
        for (angle in -maxAngle..maxAngle) {
            val variance = projectionVariance(gray, width, height, angle)
            if (variance > bestVariance) {
                bestVariance = variance
                bestAngle = angle
            }
        }
        return bestAngle
    }

    private fun projectionVariance(
        gray: IntArray,
        width: Int,
        height: Int,
        angleDegrees: Int
    ): Double {
        val rad = Math.toRadians(angleDegrees.toDouble())
        val cos = kotlin.math.cos(rad)
        val sin = kotlin.math.sin(rad)
        val rows = LongArray(height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Sample the source at the rotated position (nearest neighbor).
                val sx = ((x - width / 2.0) * cos - (y - height / 2.0) * sin + width / 2.0).toInt()
                val sy = ((x - width / 2.0) * sin + (y - height / 2.0) * cos + height / 2.0).toInt()
                if (sx in 0 until width && sy in 0 until height) {
                    rows[y] += gray[sy * width + sx]
                }
            }
        }
        // Variance of the row-sum first differences.
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until height) {
            val d = (rows[y] - rows[y - 1]).toDouble()
            sum += d
            sumSq += d * d
            n++
        }
        if (n == 0) return 0.0
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    /**
     * Unsharp mask: `out = in + amount * (in − blur3x3(in))`.
     * [amount] 0 = no change, 1 = strong sharpening.
     */
    fun sharpen(pixels: IntArray, width: Int, height: Int, amount: Float): IntArray {
        if (amount <= 0.01f || width < 3 || height < 3) return pixels.copyOf()
        val out = pixels.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                for (shift in intArrayOf(16, 8, 0)) {
                    val c = (pixels[i] shr shift) and 0xFF
                    val blur = (
                        ((pixels[i - 1] shr shift) and 0xFF) +
                            ((pixels[i + 1] shr shift) and 0xFF) +
                            ((pixels[i - width] shr shift) and 0xFF) +
                            ((pixels[i + width] shr shift) and 0xFF)
                        ) / 4
                    val v = (c + amount * (c - blur)).toInt().coerceIn(0, 255)
                    out[i] = (out[i] and (0xFF shl shift).inv()) or (v shl shift)
                }
            }
        }
        return out
    }

    /**
     * Brightness/contrast per channel:
     * `v' = clamp((v * brightness − 128) * contrast + 128)`
     * 1.0/1.0 is identity.
     */
    fun adjustBrightnessContrast(
        pixels: IntArray,
        brightness: Float,
        contrast: Float
    ): IntArray {
        if (abs(brightness - 1f) < 0.005f && abs(contrast - 1f) < 0.005f) {
            return pixels.copyOf()
        }
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p and 0xFF000000.toInt() // keep alpha
            val r = (((p shr 16) and 0xFF) * brightness - 128f) * contrast + 128f
            val g = (((p shr 8) and 0xFF) * brightness - 128f) * contrast + 128f
            val b = ((p and 0xFF) * brightness - 128f) * contrast + 128f
            out[i] = a or
                (r.toInt().coerceIn(0, 255) shl 16) or
                (g.toInt().coerceIn(0, 255) shl 8) or
                b.toInt().coerceIn(0, 255)
        }
        return out
    }

    /** Downsampling step size for a bounds-decoded image (power of two). */
    fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxDimension) {
            sample *= 2
        }
        return sample
    }
}
