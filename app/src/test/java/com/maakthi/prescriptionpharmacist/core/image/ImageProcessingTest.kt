package com.maakthi.prescriptionpharmacist.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ImageProcessingTest {

    // ---------- luma / statistics ----------

    @Test
    fun `luma of black and white`() {
        val luma = ImageProcessing.luma(
            intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        )
        assertEquals(0, luma[0])
        assertEquals(255, luma[1])
    }

    @Test
    fun `mean and standard deviation`() {
        val values = intArrayOf(0, 10)
        assertEquals(5.0, ImageProcessing.mean(values), 0.001)
        assertEquals(5.0, ImageProcessing.stdDev(values), 0.001)
    }

    // ---------- blur metric ----------

    @Test
    fun `flat image has zero laplacian variance`() {
        val gray = IntArray(16 * 16) { 100 }
        assertEquals(0.0, ImageProcessing.laplacianVariance(gray, 16, 16), 0.001)
    }

    @Test
    fun `checkerboard has high laplacian variance`() {
        val w = 16
        val h = 16
        val gray = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if ((x + y) % 2 == 0) 200 else 0
        }
        assertTrue(ImageProcessing.laplacianVariance(gray, w, h) > 1000.0)
    }

    @Test
    fun `blur metric ranks sharp above flat`() {
        val w = 16
        val h = 16
        val flat = IntArray(w * h) { 128 }
        val sharp = IntArray(w * h) { i -> if ((i / w) % 2 == 0) 220 else 30 }
        assertTrue(
            ImageProcessing.laplacianVariance(sharp, w, h) >
                ImageProcessing.laplacianVariance(flat, w, h)
        )
    }

    // ---------- brightness / contrast ----------

    @Test
    fun `brightness contrast identity returns same pixels`() {
        val pixels = intArrayOf(0xFF123456.toInt(), 0xFFA0B0C0.toInt())
        val out = ImageProcessing.adjustBrightnessContrast(pixels, 1f, 1f)
        assertTrue(pixels.contentEquals(out))
    }

    @Test
    fun `brightness doubles channel values and keeps alpha`() {
        val pixel = 0xFF323232.toInt() // 50,50,50 with full alpha
        val out = ImageProcessing.adjustBrightnessContrast(intArrayOf(pixel), 2f, 1f)[0]
        assertEquals(0xFF, (out ushr 24) and 0xFF)
        assertEquals(100, (out shr 16) and 0xFF)
        assertEquals(100, (out shr 8) and 0xFF)
        assertEquals(100, out and 0xFF)
    }

    @Test
    fun `brightness contrast clamps to legal range`() {
        val out = ImageProcessing.adjustBrightnessContrast(
            intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
            3f,
            3f
        )[0]
        val channels = listOf((out shr 16) and 0xFF, (out shr 8) and 0xFF, out and 0xFF)
        channels.forEach { assertTrue(it in 0..255) }
    }

    // ---------- sharpening ----------

    @Test
    fun `sharpen leaves flat image unchanged`() {
        val pixels = IntArray(5 * 5) { 0xFF808080.toInt() }
        val out = ImageProcessing.sharpen(pixels, 5, 5, 1f)
        assertTrue(pixels.contentEquals(out))
    }

    @Test
    fun `sharpen increases edge contrast`() {
        val w = 5
        val h = 5
        val pixels = IntArray(w * h) { i ->
            val x = i % w
            if (x < 2) 0xFF000000.toInt() else 0xFFC8C8C8.toInt() // 0 | 200 edge
        }
        val out = ImageProcessing.sharpen(pixels, w, h, 1f)
        // Pixel just right of the edge should be pushed brighter.
        val edge = out[2 * w + 2] and 0xFF
        assertTrue("expected > 200, got $edge", edge > 200)
    }

    // ---------- downsampling ----------

    @Test
    fun `in sample size is a power of two`() {
        assertEquals(4, ImageProcessing.calculateInSampleSize(4000, 3000, 1000))
        assertEquals(2, ImageProcessing.calculateInSampleSize(4000, 3000, 1600))
        assertEquals(1, ImageProcessing.calculateInSampleSize(2000, 1000, 2048))
        assertEquals(1, ImageProcessing.calculateInSampleSize(100, 100, 1000))
        assertEquals(1, ImageProcessing.calculateInSampleSize(0, 0, 100))
    }

    // ---------- skew estimation ----------

    @Test
    fun `horizontal lines report zero skew`() {
        val w = 160
        val h = 160
        val gray = IntArray(w * h) { i ->
            val y = i / w
            if (y % 28 < 10) 220 else 30
        }
        assertEquals(0, ImageProcessing.estimateSkewDegrees(gray, w, h))
    }

    @Test
    fun `slanted lines are detected within tolerance`() {
        val w = 160
        val h = 160
        val tan = kotlin.math.tan(Math.toRadians(10.0))
        val gray = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val t = y - x * tan
            val phase = ((t % 28) + 28) % 28
            if (phase < 10) 220 else 30
        }
        val skew = ImageProcessing.estimateSkewDegrees(gray, w, h)
        assertTrue("skew $skew out of range", abs(skew) in 7..13)
    }

    @Test
    fun `mirrored slant gives mirrored skew`() {
        val w = 160
        val h = 160
        val tan = kotlin.math.tan(Math.toRadians(10.0))
        fun pattern(sign: Double): IntArray = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val t = y + sign * x * tan
            val phase = ((t % 28) + 28) % 28
            if (phase < 10) 220 else 30
        }
        val a = ImageProcessing.estimateSkewDegrees(pattern(-1.0), w, h)
        val b = ImageProcessing.estimateSkewDegrees(pattern(1.0), w, h)
        assertTrue("a=$a b=$b not mirrored", abs(a + b) <= 2)
        assertTrue(abs(a) in 7..13)
    }
}
