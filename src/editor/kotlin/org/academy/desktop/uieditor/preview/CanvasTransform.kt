package org.academy.desktop.uieditor.preview

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/** Rectangle in window (screen) pixels. */
data class ViewRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h
    fun contains(px: Float, py: Float): Boolean = px >= x && py >= y && px < right && py < bottom
}

/**
 * Immutable document-to-viewport transform: doc point p renders at (origin + p*scale),
 * everything in logical pixels (window pixels divided by GUI scale).
 */
data class CanvasTransform(val scale: Float, val originX: Float, val originY: Float) {

    fun docToViewX(x: Float): Float = originX + x * scale
    fun docToViewY(y: Float): Float = originY + y * scale
    fun viewToDocX(vx: Float): Float = (vx - originX) / scale
    fun viewToDocY(vy: Float): Float = (vy - originY) / scale

    /** Zoom keeping the document point under [viewX,viewY] pinned. */
    fun zoomAt(viewX: Float, viewY: Float, factor: Float): CanvasTransform {
        val s = clamp(scale * factor)
        if (s == scale) return this
        val k = s / scale
        return CanvasTransform(s, viewX - (viewX - originX) * k, viewY - (viewY - originY) * k)
    }

    fun panned(dx: Float, dy: Float): CanvasTransform = CanvasTransform(scale, originX + dx, originY + dy)

    fun fitted(docW: Float, docH: Float, viewW: Float, viewH: Float, pad: Float): CanvasTransform {
        if (docW <= 0f || docH <= 0f || viewW <= pad * 2 || viewH <= pad * 2) return this
        val s = clamp(minOf((viewW - pad * 2) / docW, (viewH - pad * 2) / docH))
        return CanvasTransform(s, (viewW - docW * s) / 2f, (viewH - docH * s) / 2f)
    }

    fun centeredOn(docCx: Float, docCy: Float, viewW: Float, viewH: Float): CanvasTransform =
        CanvasTransform(scale, viewW / 2f - docCx * scale, viewH / 2f - docCy * scale)

    companion object {
        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 8f
        val IDENTITY = CanvasTransform(1f, 0f, 0f)
        fun clamp(s: Float): Float = s.coerceIn(MIN_SCALE, MAX_SCALE)
    }
}

/** Resize handle anchors as normalized offsets (-1..1) from the selection rect. */
enum class Handle(val nx: Float, val ny: Float) {
    TOP_LEFT(-1f, -1f), TOP(0f, -1f), TOP_RIGHT(1f, -1f),
    LEFT(-1f, 0f), RIGHT(1f, 0f),
    BOTTOM_LEFT(-1f, 1f), BOTTOM(0f, 1f), BOTTOM_RIGHT(1f, 1f);

    val movesLeft: Boolean get() = nx < 0f
    val movesRight: Boolean get() = nx > 0f
    val movesTop: Boolean get() = ny < 0f
    val movesBottom: Boolean get() = ny > 0f
}

/** Smallest value from the 1-2-5 ladder that is >= [minStep] in magnitude. */
fun niceStep(minStep: Float): Float {
    if (minStep <= 0f) return 1f
    val exp = floor((ln(minStep.toDouble()) / ln(10.0)))
    val base = minStep / 10.0.pow(exp).toFloat()
    val mantissa = when {
        base <= 1f -> 1f
        base <= 2f -> 2f
        base <= 5f -> 5f
        else -> 10f
    }
    return mantissa * 10.0.pow(exp).toFloat()
}
