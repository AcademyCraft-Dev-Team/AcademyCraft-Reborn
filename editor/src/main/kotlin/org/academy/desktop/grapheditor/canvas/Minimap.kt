package org.academy.desktop.grapheditor.canvas

import imgui.ImGui
import imgui.flag.ImDrawFlags
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiWindowFlags

/**
 * 画布右上角缩略图：整图概览 + 当前视口框 + 点击跳转相机。
 * 作为画布窗口内的 child window 渲染（局部坐标），点击在画布内路由。
 */
class Minimap(
    private val model: GraphEditorModel,
    private val camera: Camera2D,
) {
    /** 画布内容顶部内缩（与 NodeCanvas.topInset 同步，宿主设置）。 */
    var topInset = 0f

    fun render() {
        if (model.nodes.isEmpty() && model.frames.isEmpty()) return
        val canvasX = ImGui.getWindowPosX()
        val canvasY = ImGui.getWindowPosY() + topInset
        val canvasW = ImGui.getWindowSizeX()
        val canvasH = ImGui.getWindowSizeY() - topInset
        val posX = canvasX + canvasW - MAP_W - EDGE_PAD
        val posY = canvasY + EDGE_PAD

        var minX = model.nodes.values.minOfOrNull { it.x } ?: 0f
        var minY = model.nodes.values.minOfOrNull { it.y } ?: 0f
        var maxX = model.nodes.values.maxOfOrNull { it.x + NodeCanvas.NODE_WIDTH } ?: minX
        var maxY = model.nodes.values.maxOfOrNull { it.y + NODE_BOTTOM } ?: minY
        for (frame in model.frames.values) {
            minX = minOf(minX, frame.x)
            minY = minOf(minY, frame.y)
            maxX = maxOf(maxX, frame.x + frame.w)
            maxY = maxOf(maxY, frame.y + frame.h)
        }
        val boundsW = maxOf(maxX - minX, 10f)
        val boundsH = maxOf(maxY - minY, 10f)
        val scale = minOf((MAP_W - 8f) / boundsW, (MAP_H - 8f) / boundsH)
        val mapW = boundsW * scale
        val mapH = boundsH * scale
        val mapLocalX = (MAP_W - mapW) / 2f
        val mapLocalY = (MAP_H - mapH) / 2f

        ImGui.setNextWindowPos(posX, posY)
        ImGui.setNextWindowSize(MAP_W, MAP_H)
        ImGui.beginChild("##minimap", MAP_W, MAP_H, false, CHILD_FLAGS)
        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(0f, 0f, MAP_W, MAP_H, BG_COLOR)

        // frames
        for (frame in model.frames.values) {
            val x = mapLocalX + (frame.x - minX) * scale
            val y = mapLocalY + (frame.y - minY) * scale
            dl.addRect(x, y, x + frame.w * scale, y + frame.h * scale, frame.color)
        }
        // edges
        val edgeColor = col(0.7f, 0.7f, 0.75f, 0.6f)
        for (edge in model.edges.values) {
            val from = model.nodes[edge.fromNode] ?: continue
            val to = model.nodes[edge.toNode] ?: continue
            dl.addLine(
                mapLocalX + (from.x - minX) * scale, mapLocalY + (from.y - minY) * scale,
                mapLocalX + (to.x - minX) * scale, mapLocalY + (to.y - minY) * scale,
                edgeColor
            )
        }
        // nodes
        val nodeColor = col(0.35f, 0.55f, 0.9f, 0.9f)
        for (node in model.nodes.values) {
            val x = mapLocalX + (node.x - minX) * scale
            val y = mapLocalY + (node.y - minY) * scale
            dl.addRectFilled(x, y, x + NodeCanvas.NODE_WIDTH * scale, y + NODE_BOTTOM * scale, nodeColor)
        }
        // 当前视口框
        val vx0 = camera.screenToGraphX(canvasX)
        val vy0 = camera.screenToGraphY(canvasY)
        val vx1 = camera.screenToGraphX(canvasX + canvasW)
        val vy1 = camera.screenToGraphY(canvasY + canvasH)
        val p0x = mapLocalX + (vx0 - minX) * scale
        val p0y = mapLocalY + (vy0 - minY) * scale
        val p1x = mapLocalX + (vx1 - minX) * scale
        val p1y = mapLocalY + (vy1 - minY) * scale
        dl.addRect(p0x, p0y, p1x, p1y, VIEWPORT_COLOR, 0f, ImDrawFlags.RoundCornersAll, 1.5f)

        if (ImGui.isWindowHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            val localX = ImGui.getMousePosX() - ImGui.getWindowPosX()
            val localY = ImGui.getMousePosY() - ImGui.getWindowPosY()
            if (scale > 0f) {
                val gx = minX + (localX - mapLocalX) / scale
                val gy = minY + (localY - mapLocalY) / scale
                camera.panX = (canvasX + canvasW / 2f) - gx * camera.zoom
                camera.panY = (canvasY + canvasH / 2f) - gy * camera.zoom
            }
        }
        ImGui.endChild()
    }

    private fun col(r: Float, g: Float, b: Float, a: Float): Int = ImGui.colorConvertFloat4ToU32(r, g, b, a)

    companion object {
        private const val MAP_W = 180f
        private const val MAP_H = 120f
        private const val EDGE_PAD = 12f
        private const val NODE_BOTTOM = 40f
        private val BG_COLOR = 0xCC16161A.toInt()
        private val VIEWPORT_COLOR = 0xFFFF0000.toInt()
        private val CHILD_FLAGS = ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse
    }
}
