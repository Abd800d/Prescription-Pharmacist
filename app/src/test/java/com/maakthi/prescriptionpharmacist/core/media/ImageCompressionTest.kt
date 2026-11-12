package com.maakthi.prescriptionpharmacist.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Compression policy: no visible quality loss, bounded dimensions. */
class ImageCompressionTest {

    @Test
    fun `small images are stored as-is`() {
        val plan = ImageCompression.planCompression(800, 600, byteSize = 100 * 1024)
        assertTrue(plan.skipCompression)
    }

    @Test
    fun `large photos are recompressed at visual-lossless quality`() {
        val plan = ImageCompression.planCompression(4000, 3000, byteSize = 5_000_000)
        assertFalse(plan.skipCompression)
        assertEquals(1920, plan.maxDimension)
        assertEquals(85, plan.quality)
    }

    @Test
    fun `big files are recompressed even when dimensions fit`() {
        val plan = ImageCompression.planCompression(1600, 1200, byteSize = 3_000_000)
        assertFalse(plan.skipCompression)
    }

    @Test
    fun `sample size keeps the result within bounds`() {
        val plan = ImageCompression.planCompression(4000, 3000, 5_000_000)
        assertEquals(4, ImageCompression.sampleSize(4000, 3000, plan))
        assertEquals(1, ImageCompression.sampleSize(1920, 1080, plan))
        assertEquals(1, ImageCompression.sampleSize(800, 600, plan))
        assertEquals(16, ImageCompression.sampleSize(16000, 12000, plan))
    }

    @Test
    fun `estimate shrinks with the plan`() {
        val plan = ImageCompression.planCompression(4000, 3000, 4_000_000)
        val estimate = ImageCompression.estimatedBytes(4_000_000, plan)
        assertTrue(estimate < 4_000_000)
        assertEquals(4_000_000L, ImageCompression.estimatedBytes(4_000_000, ImageCompression.planCompression(640, 480, 50_000)))
    }
}
