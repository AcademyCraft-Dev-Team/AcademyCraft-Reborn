package org.academy.desktop.grapheditor.canvas

/**
 * 画布相机：图坐标 ↔ 屏幕坐标换算（平移 + 缩放）+ 取景与网格吸附。
 */
class Camera2D {
    var zoom = 1f
    var panX = 0f
    var panY = 0f

    fun graphToScreenX(gx: Float): Float = gx * zoom + panX

    fun graphToScreenY(gy: Float): Float = gy * zoom + panY

    fun screenToGraphX(sx: Float): Float = (sx - panX) / zoom

    fun screenToGraphY(sy: Float): Float = (sy - panY) / zoom

    /** 将图坐标范围取景到窗口矩形内（含边距）。空/退化范围回退最小尺寸。 */
    fun frameToBounds(
        minX: Float, minY: Float, maxX: Float, maxY: Float,
        windowPosX: Float, windowPosY: Float, windowW: Float, windowH: Float,
        maxZoom: Float = 2f, padding: Float = 0f,
    ) {
        val w = maxOf(maxX - minX + padding * 2, 50f)
        val h = maxOf(maxY - minY + padding * 2, 50f)
        zoom = minOf(windowW / w, windowH / h, maxZoom).coerceIn(0.1f, 4f)
        panX = windowPosX + (windowW - (maxX - minX) * zoom) / 2f - minX * zoom
        panY = windowPosY + (windowH - (maxY - minY) * zoom) / 2f - minY * zoom
    }

    /** 网格吸附：把图坐标取整到 grid 倍数。 */
    fun snap(value: Float, grid: Float = SNAP_GRID): Float =
        if (grid <= 0f) value else Math.round(value / grid) * grid

    companion object {
        const val SNAP_GRID = 10f
    }
}
