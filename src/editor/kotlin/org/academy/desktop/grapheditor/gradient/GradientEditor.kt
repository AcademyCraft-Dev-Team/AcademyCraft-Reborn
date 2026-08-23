package org.academy.desktop.grapheditor.gradient

import imgui.ImGui
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiWindowFlags
import org.academy.api.client.render.graph.type.Gradient
import org.academy.api.client.render.graph.type.Gradient.ColorStop
import org.academy.api.client.render.graph.type.GradientSampler

/**
 * 渐变 ramp 编辑器（M12-04）：色带 + 停靠点拖拽、双击加点、Delete 删点、颜色编辑。
 * 编辑经 [onApply] 回写参数（宿主经 replaceParameter 入命令栈）。
 */
class GradientEditor {
    private var stops: MutableList<ColorStop> = mutableListOf()
    private var onApply: ((Gradient) -> Unit)? = null
    private var selected = -1
    private var dragging = -1

    private var barX = 0f
    private var barY = 0f
    private var barW = 0f
    private var barH = 0f

    fun open(gradient: Gradient, onApply: (Gradient) -> Unit) {
        this.stops = gradient.stops().toMutableList()
        this.onApply = onApply
        this.selected = -1
    }

    fun render() {
        ImGui.text("Gradient")
        ImGui.separator()

        val childH = 140f
        ImGui.beginChild("##gradient_bar", 0f, childH, false, CHILD_FLAGS)
        val draw = ImGui.getWindowDrawList()
        val wx = ImGui.getWindowPosX()
        val wy = ImGui.getWindowPosY()
        val ww = ImGui.getWindowSizeX()
        val wh = ImGui.getWindowSizeY()
        barX = wx + PAD
        barY = wy + PAD
        barW = ww - PAD * 2
        barH = 60f

        drawBar(draw)
        drawStops(draw)
        handleInteraction()

        ImGui.endChild()

        ImGui.separator()
        if (ImGui.button("Add Stop")) addStop(0.5f)
        ImGui.sameLine()
        if (ImGui.button("Reset")) {
            stops = mutableListOf(ColorStop(0f, 0f, 0f, 0f, 1f), ColorStop(1f, 1f, 1f, 1f, 1f))
            apply()
        }
        renderSelectedInspector()
    }

    private fun drawBar(draw: imgui.ImDrawList) {
        val gradient = Gradient(stops)
        val stepPx = 4f
        var x = barX
        while (x < barX + barW) {
            val t0 = ((x - barX) / barW)
            val t1 = (((x + stepPx) - barX) / barW).coerceAtMost(1f)
            val c0 = GradientSampler.sample(gradient, t0)
            val c1 = GradientSampler.sample(gradient, t1)
            val col0 = colorToU32(c0)
            val col1 = colorToU32(c1)
            draw.addRectFilledMultiColor(x, barY, x + stepPx, barY + barH, col0, col1, col1, col0)
            x += stepPx
        }
        draw.addRect(barX, barY, barX + barW, barY + barH, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.6f))
    }

    private fun drawStops(draw: imgui.ImDrawList) {
        for (i in stops.indices) {
            val stop = stops[i]
            val x = mapPos(stop.position())
            val isSel = i == selected
            val markerCol = ImGui.colorConvertFloat4ToU32(stop.r(), stop.g(), stop.b(), 1f)
            draw.addTriangleFilled(x, barY + barH, x - 7f, barY + barH + 12f, x + 7f, barY + barH + 12f, markerCol)
            draw.addTriangle(x, barY + barH, x - 7f, barY + barH + 12f, x + 7f, barY + barH + 12f,
                if (isSel) ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f) else ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f))
        }
    }

    private fun handleInteraction() {
        val mouseX = ImGui.getMousePosX()
        val mouseY = ImGui.getMousePosY()
        val hovered = ImGui.isWindowHovered()
        if (!hovered) {
            dragging = -1
            return
        }

        if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left) && mouseY in barY..(barY + barH + 14f)) {
            val t = inversePos(mouseX)
            if (t in 0f..1f) addStop(t)
        }

        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            val idx = hitStop(mouseX, mouseY)
            if (idx >= 0) {
                selected = idx
                dragging = idx
            } else {
                selected = -1
            }
        }

        if (dragging in stops.indices && ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
            updateStop(dragging, position = inversePos(mouseX).coerceIn(0f, 1f))
        }

        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            if (dragging >= 0) apply()
            dragging = -1
        }

        if (ImGui.isKeyPressed(ImGuiKey.Delete) && selected in stops.indices) {
            stops.removeAt(selected)
            selected = -1
            apply()
        }
    }

    private fun renderSelectedInspector() {
        ImGui.separator()
        if (selected !in stops.indices) {
            ImGui.textDisabled("No stop selected")
            return
        }
        val stop = stops[selected]
        val pos = floatArrayOf(stop.position())
        if (ImGui.dragFloat("Position", pos, 0.001f)) {
            updateStop(selected, position = pos[0].coerceIn(0f, 1f))
            apply()
        }
        val col = floatArrayOf(stop.r(), stop.g(), stop.b(), stop.a())
        if (ImGui.colorEdit4("Color", col)) {
            updateStop(selected, r = col[0], g = col[1], b = col[2], a = col[3])
            apply()
        }
    }

    private fun updateStop(
        index: Int,
        position: Float? = null,
        r: Float? = null,
        g: Float? = null,
        b: Float? = null,
        a: Float? = null,
    ) {
        val stop = stops[index]
        stops[index] = ColorStop(
            position ?: stop.position(),
            r ?: stop.r(), g ?: stop.g(), b ?: stop.b(), a ?: stop.a(),
        )
    }

    private fun hitStop(mouseX: Float, mouseY: Float): Int {
        for (i in stops.indices) {
            val x = mapPos(stops[i].position())
            if (mouseX in (x - 10f)..(x + 10f) && mouseY in (barY + barH - 4f)..(barY + barH + 16f)) return i
        }
        return -1
    }

    private fun addStop(t: Float) {
        val color = GradientSampler.sample(Gradient(stops), t)
        stops.add(ColorStop(t, color.x, color.y, color.z, color.w))
        stops.sortBy { it.position() }
        selected = stops.indexOfFirst { kotlin.math.abs(it.position() - t) < 0.001f }
        apply()
    }

    private fun apply() {
        onApply?.invoke(Gradient(stops.sortedBy { it.position() }))
    }

    private fun mapPos(t: Float): Float = barX + t * barW

    private fun inversePos(x: Float): Float = (x - barX) / barW

    private fun colorToU32(c: org.joml.Vector4f): Int {
        val r = (c.x * 255f).toInt().coerceIn(0, 255)
        val g = (c.y * 255f).toInt().coerceIn(0, 255)
        val b = (c.z * 255f).toInt().coerceIn(0, 255)
        val a = (c.w * 255f).toInt().coerceIn(0, 255)
        return (a shl 24) or (b shl 16) or (g shl 8) or r
    }

    companion object {
        private const val PAD = 20f
        private val CHILD_FLAGS = ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse
    }
}
