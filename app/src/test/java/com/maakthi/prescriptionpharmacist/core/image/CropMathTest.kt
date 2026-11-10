package com.maakthi.prescriptionpharmacist.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropMathTest {

    @Test
    fun `window at zoom 1 with matching aspect is the full image`() {
        val window = CropMath.visibleWindow(
            imageWidth = 1000,
            imageHeight = 800,
            frameWidth = 500f,
            frameHeight = 400f,
            scale = 1f,
            offsetX = 0f,
            offsetY = 0f
        )
        assertEquals(CropWindow.FULL, window)
    }

    @Test
    fun `zoom 2 centers the visible window`() {
        val window = CropMath.visibleWindow(
            imageWidth = 1000,
            imageHeight = 800,
            frameWidth = 500f,
            frameHeight = 400f,
            scale = 2f,
            offsetX = 0f,
            offsetY = 0f
        )
        assertEquals(0.25f, window.left, 0.001f)
        assertEquals(0.25f, window.top, 0.001f)
        assertEquals(0.75f, window.right, 0.001f)
        assertEquals(0.75f, window.bottom, 0.001f)
    }

    @Test
    fun `pan shifts the visible window`() {
        val window = CropMath.visibleWindow(
            imageWidth = 1000,
            imageHeight = 800,
            frameWidth = 500f,
            frameHeight = 400f,
            scale = 2f,
            offsetX = 100f,
            offsetY = 0f
        )
        assertEquals(0.15f, window.left, 0.001f)
        assertEquals(0.25f, window.top, 0.001f)
        assertEquals(0.65f, window.right, 0.001f)
        assertEquals(0.75f, window.bottom, 0.001f)
    }

    @Test
    fun `clamp offset keeps the image covering the frame`() {
        val clamped = CropMath.clampOffset(
            imageWidth = 1000,
            imageHeight = 800,
            frameWidth = 500f,
            frameHeight = 400f,
            scale = 2f,
            offsetX = 500f,
            offsetY = -300f
        )
        assertEquals(250f, clamped.first, 0.001f)
        assertEquals(-200f, clamped.second, 0.001f)
    }

    @Test
    fun `clamp offset is zero at zoom 1`() {
        val clamped = CropMath.clampOffset(
            imageWidth = 1000,
            imageHeight = 800,
            frameWidth = 500f,
            frameHeight = 400f,
            scale = 1f,
            offsetX = 120f,
            offsetY = -90f
        )
        assertEquals(0f, clamped.first, 0.001f)
        assertEquals(0f, clamped.second, 0.001f)
    }

    @Test
    fun `zoom below 1 is coerced to fit`() {
        val window = CropMath.visibleWindow(
            imageWidth = 1000,
            imageHeight = 800,
            frameWidth = 500f,
            frameHeight = 400f,
            scale = 0.1f,
            offsetX = 0f,
            offsetY = 0f
        )
        assertEquals(CropWindow.FULL, window)
    }

    @Test
    fun `constrain to square centers a wide window`() {
        val window = CropWindow(0f, 0f, 1f, 0.5f)
        val constrained = CropMath.constrainToAspect(window, CropAspect.SQUARE)
        assertEquals(0.25f, constrained.left, 0.001f)
        assertEquals(0f, constrained.top, 0.001f)
        assertEquals(0.75f, constrained.right, 0.001f)
        assertEquals(0.5f, constrained.bottom, 0.001f)
    }

    @Test
    fun `free aspect is unchanged by constrain`() {
        val window = CropWindow(0.1f, 0.1f, 0.9f, 0.7f)
        assertEquals(window, CropMath.constrainToAspect(window, CropAspect.FREE))
    }

    @Test
    fun `invalid inputs fall back to full window`() {
        val window = CropMath.visibleWindow(0, 0, 100f, 100f, 1f, 0f, 0f)
        assertEquals(CropWindow.FULL, window)
    }

    @Test
    fun `crop window validity`() {
        assertTrue(CropWindow.FULL.isValid())
        assertFalse(CropWindow(0.5f, 0.5f, 0.4f, 0.9f).isValid())
        assertFalse(CropWindow(-0.1f, 0f, 1f, 1f).isValid())
    }
}
