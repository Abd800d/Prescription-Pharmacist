package com.maakthi.prescriptionpharmacist.core.image

/**
 * Pure geometry for the crop window (unit-testable on the JVM).
 *
 * [CropWindow] uses normalized coordinates (0..1) relative to the current,
 * upright working image.
 */
data class CropWindow(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun clamped(): CropWindow = CropWindow(
        left = left.coerceIn(0f, 1f),
        top = top.coerceIn(0f, 1f),
        right = right.coerceIn(0f, 1f),
        bottom = bottom.coerceIn(0f, 1f)
    )

    fun isValid(): Boolean = width > 0.001f && height > 0.001f &&
        left >= 0f && top >= 0f && right <= 1f && bottom <= 1f

    companion object {
        val FULL = CropWindow(0f, 0f, 1f, 1f)
    }
}

enum class CropAspect(val ratio: Float?) {
    FREE(null),
    SQUARE(1f),
    LANDSCAPE(4f / 3f),
    PORTRAIT(3f / 4f)
}

/**
 * Crop geometry helpers.
 *
 * The crop UI shows the image fitted inside a fixed frame; the user zooms
 * (scale ≥ 1) and pans the image under the frame. The visible frame region
 * mapped back into image coordinates is the resulting [CropWindow].
 */
object CropMath {

    /**
     * Computes the normalized crop window for the visible part of the frame.
     *
     * @param imageWidth/imageHeight upright image size in pixels.
     * @param frameWidth/frameHeight frame size in pixels.
     * @param scale user zoom factor (1 = fit).
     * @param offsetX/offsetY pan translation in frame pixels (already clamped).
     */
    fun visibleWindow(
        imageWidth: Int,
        imageHeight: Int,
        frameWidth: Float,
        frameHeight: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): CropWindow {
        if (imageWidth <= 0 || imageHeight <= 0 || frameWidth <= 0f || frameHeight <= 0f) {
            return CropWindow.FULL
        }
        val safeScale = scale.coerceAtLeast(1f)
        val baseScale = minOf(frameWidth / imageWidth, frameHeight / imageHeight)
        val displayedW = imageWidth * baseScale * safeScale
        val displayedH = imageHeight * baseScale * safeScale

        val displayedLeft = (frameWidth - displayedW) / 2f + offsetX
        val displayedTop = (frameHeight - displayedH) / 2f + offsetY

        // Intersect frame with the displayed image rect.
        val visibleLeft = 0f.coerceAtLeast(displayedLeft)
        val visibleTop = 0f.coerceAtLeast(displayedTop)
        val visibleRight = frameWidth.coerceAtMost(displayedLeft + displayedW)
        val visibleBottom = frameHeight.coerceAtMost(displayedTop + displayedH)

        if (visibleRight <= visibleLeft || visibleBottom <= visibleTop) {
            return CropWindow.FULL
        }

        val normLeft = (visibleLeft - displayedLeft) / displayedW
        val normTop = (visibleTop - displayedTop) / displayedH
        val normRight = (visibleRight - displayedLeft) / displayedW
        val normBottom = (visibleBottom - displayedTop) / displayedH

        return CropWindow(normLeft, normTop, normRight, normBottom).clamped()
    }

    /** Clamps pan offsets so the image always covers the frame. */
    fun clampOffset(
        imageWidth: Int,
        imageHeight: Int,
        frameWidth: Float,
        frameHeight: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): Pair<Float, Float> {
        if (imageWidth <= 0 || imageHeight <= 0) return 0f to 0f
        val safeScale = scale.coerceAtLeast(1f)
        val baseScale = minOf(frameWidth / imageWidth, frameHeight / imageHeight)
        val displayedW = imageWidth * baseScale * safeScale
        val displayedH = imageHeight * baseScale * safeScale
        val maxX = ((displayedW - frameWidth) / 2f).coerceAtLeast(0f)
        val maxY = ((displayedH - frameHeight) / 2f).coerceAtLeast(0f)
        return offsetX.coerceIn(-maxX, maxX) to offsetY.coerceIn(-maxY, maxY)
    }

    /** Constrain a window to an aspect ratio (centered, clipped to the image). */
    fun constrainToAspect(window: CropWindow, aspect: CropAspect): CropWindow {
        val ratio = aspect.ratio ?: return window
        val currentRatio = window.width / window.height
        if (currentRatio <= 0f) return window

        var newW = window.width
        var newH = window.height
        if (currentRatio > ratio) {
            newW = window.height * ratio
        } else {
            newH = window.width / ratio
        }

        var cx = (window.left + window.right) / 2f
        var cy = (window.top + window.bottom) / 2f

        var left = cx - newW / 2f
        var top = cy - newH / 2f
        var right = left + newW
        var bottom = top + newH

        // Shift back inside the unit square if needed.
        if (left < 0f) { right -= left; left = 0f }
        if (top < 0f) { bottom -= top; top = 0f }
        if (right > 1f) { left -= (right - 1f); right = 1f }
        if (bottom > 1f) { top -= (bottom - 1f); bottom = 1f }

        return CropWindow(
            left.coerceIn(0f, 1f),
            top.coerceIn(0f, 1f),
            right.coerceIn(0f, 1f),
            bottom.coerceIn(0f, 1f)
        )
    }
}
