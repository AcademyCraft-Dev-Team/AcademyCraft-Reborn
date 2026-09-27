package org.academy.desktop.grapheditor.editorcurve

import imgui.ImGui
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImInt
import org.academy.api.client.render.graph.type.Curve
import org.academy.api.client.render.graph.type.Curve.Keyframe

/**
 * bezier 曲线编辑器（M12-03）：拖关键帧/切线、双击加点、右键删点、逐帧插值模式。
 * 编辑经 [onApply] 回写参数（宿主经 replaceParameter 入命令栈，可撤销）。
 */
class CurveEditor {
    private var curve: MutableList<Keyframe> = mutableListOf()
    private var onApply: ((Curve) -> Unit)? = null
    private var selected = -1
    private var draggingKey = -1
    private var draggingTangent = 0 // 1=in, 2=out
    private var hoverTime = 0f
    private var hoverValue = 0f

    private var plotX = 0f
    private var plotY = 0f
    private var plotW = 0f
    private var plotH = 0f

    fun open(curve: Curve, onApply: (Curve) -> Unit) {
        this.curve = curve.keyframes().toMutableList()
        this.onApply = onApply
        this.selected = -1
    }

    fun render() {
        ImGui.text("Curve")
        ImGui.separator()
        if (ImGui.button("Add Key")) addKey(0.5f, 0.5f)
        ImGui.sameLine()
        if (ImGui.button("Reset")) {
            curve = mutableListOf(Keyframe.linear(0f, 0f), Keyframe.linear(1f, 1f))
            apply()
        }

        val childH = 260f
        ImGui.beginChild("##curve_plot", 0f, childH, false, CHILD_FLAGS)
        val draw = ImGui.getWindowDrawList()
        val wx = ImGui.getWindowPosX()
        val wy = ImGui.getWindowPosY()
        val ww = ImGui.getWindowSizeX()
        val wh = ImGui.getWindowSizeY()
        plotX = wx + PAD
        plotY = wy + PAD
        plotW = ww - PAD * 2
        plotH = wh - PAD * 2

        drawGrid(draw)
        drawCurveLine(draw)
        drawKeyframes(draw)
        handleInteraction(draw)

        ImGui.endChild()

        renderSelectedInspector()
    }

    private fun drawGrid(draw: imgui.ImDrawList) {
        val lineCol = ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.45f, 0.35f)
        for (i in 0..4) {
            val t = i / 4f
            val x = mapX(t)
            draw.addLine(x, plotY, x, plotY + plotH, lineCol)
        }
        for (i in 0..4) {
            val v = i / 4f
            val y = mapY(v)
            draw.addLine(plotX, y, plotX + plotW, y, lineCol)
        }
        draw.addRect(plotX, plotY, plotX + plotW, plotY + plotH, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.6f))
    }

    private fun drawCurveLine(draw: imgui.ImDrawList) {
        if (curve.isEmpty()) return
        val col = ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 0.6f, 1f)
        val sorted = curve.sortedBy { it.time }
        var px = mapX(sorted[0].time)
        var py = mapY(sorted[0].value)
        for (i in 1 until sorted.size) {
            val kf = sorted[i]
            val nx = mapX(kf.time)
            val ny = mapY(kf.value)
            draw.addLine(px, py, nx, ny, col, 2f)
            px = nx
            py = ny
        }
    }

    private fun drawKeyframes(draw: imgui.ImDrawList) {
        val white = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f)
        for (i in curve.indices) {
            val kf = curve[i]
            val x = mapX(kf.time)
            val y = mapY(kf.value)
            val isSel = i == selected
            draw.addCircleFilled(x, y, if (isSel) 6f else 4f, if (isSel) col(0.3f, 0.7f, 1f) else col(0.9f, 0.9f, 0.4f))
            draw.addCircle(x, y, if (isSel) 6f else 4f, white)
            if (isSel && kf.interpolation() == Curve.Interpolation.BEZIER) {
                drawTangentHandle(draw, kf, true)
                drawTangentHandle(draw, kf, false)
            }
        }
    }

    private fun drawTangentHandle(draw: imgui.ImDrawList, kf: Keyframe, isIn: Boolean) {
        val slope = if (isIn) kf.inTangent() else kf.outTangent()
        val x = mapX(kf.time)
        val y = mapY(kf.value)
        val hx = if (isIn) x - HANDLE_PX else x + HANDLE_PX
        val hy = y - slope * HANDLE_PX
        draw.addLine(x, y, hx, hy, col(0.8f, 0.5f, 0.2f), 1.5f)
        draw.addCircleFilled(hx, hy, 4f, col(0.9f, 0.6f, 0.3f))
    }

    private fun handleInteraction(draw: imgui.ImDrawList) {
        val mouseX = ImGui.getMousePosX()
        val mouseY = ImGui.getMousePosY()
        val hovered = ImGui.isWindowHovered()

        if (!hovered) {
            draggingKey = -1
            draggingTangent = 0
            return
        }

        // 双击空白处加点
        if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
            val t = inverseX(mouseX)
            val v = inverseY(mouseY)
            if (t in 0f..1f && v in 0f..1f) addKey(t, v)
        }

        // 左键按下：命中关键帧或切线
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            val tangent = hitTangent(mouseX, mouseY)
            if (tangent != null) {
                selected = tangent.first
                draggingTangent = tangent.second
                draggingKey = -1
            } else {
                val idx = hitKey(mouseX, mouseY)
                if (idx >= 0) {
                    selected = idx
                    draggingKey = idx
                    draggingTangent = 0
                } else {
                    selected = -1
                }
            }
        }

        if (draggingKey >= 0 && draggingKey < curve.size && ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
            val t = inverseX(mouseX).coerceIn(0f, 1f)
            val v = inverseY(mouseY).coerceIn(0f, 1f)
            updateKey(draggingKey, time = t, value = v)
        } else if (draggingTangent != 0 && selected in curve.indices) {
            val kf = curve[selected]
            val slope = (mapY(kf.value) - mouseY) / HANDLE_PX
            if (draggingTangent == 1) updateKey(selected, inTangent = slope)
            else updateKey(selected, outTangent = slope)
        }

        // 释放左键：应用
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            if (draggingKey >= 0 || draggingTangent != 0) apply()
            draggingKey = -1
            draggingTangent = 0
        }

        // 删除选中
        if (ImGui.isKeyPressed(ImGuiKey.Delete) && selected in curve.indices) {
            curve.removeAt(selected)
            selected = -1
            apply()
        }
    }

    private fun renderSelectedInspector() {
        ImGui.separator()
        if (selected !in curve.indices) {
            ImGui.textDisabled("No key selected")
            return
        }
        val kf = curve[selected]
        val t = floatArrayOf(kf.time)
        val v = floatArrayOf(kf.value)
        if (ImGui.dragFloat("Time", t, 0.001f)) {
            updateKey(selected, time = t[0].coerceIn(0f, 1f))
            apply()
        }
        if (ImGui.dragFloat("Value", v, 0.001f)) {
            updateKey(selected, value = v[0].coerceIn(0f, 1f))
            apply()
        }
        val modes = arrayOf("LINEAR", "STEP", "SMOOTH", "BEZIER")
        val idx = ImInt(modeIndex(kf.interpolation()))
        if (ImGui.combo("Interpolation", idx, modes)) {
            updateKey(selected, interpolation = Curve.Interpolation.values()[idx.get()])
            apply()
        }
        if (kf.interpolation() == Curve.Interpolation.BEZIER) {
            val it = floatArrayOf(kf.inTangent)
            val ot = floatArrayOf(kf.outTangent)
            if (ImGui.dragFloat("In Tangent", it, 0.01f)) {
                updateKey(selected, inTangent = it[0])
                apply()
            }
            if (ImGui.dragFloat("Out Tangent", ot, 0.01f)) {
                updateKey(selected, outTangent = ot[0])
                apply()
            }
        }
    }

    private fun updateKey(
        index: Int,
        time: Float? = null,
        value: Float? = null,
        inTangent: Float? = null,
        outTangent: Float? = null,
        interpolation: Curve.Interpolation? = null,
    ) {
        val kf = curve[index]
        curve[index] = Keyframe(
            time ?: kf.time,
            value ?: kf.value,
            inTangent ?: kf.inTangent,
            outTangent ?: kf.outTangent,
            interpolation ?: kf.interpolation,
        )
    }

    private fun hitKey(mouseX: Float, mouseY: Float): Int {
        for (i in curve.indices) {
            val kf = curve[i]
            val dx = mouseX - mapX(kf.time)
            val dy = mouseY - mapY(kf.value)
            if (dx * dx + dy * dy <= 49f) return i
        }
        return -1
    }

    private fun hitTangent(mouseX: Float, mouseY: Float): Pair<Int, Int>? {
        for (i in curve.indices) {
            val kf = curve[i]
            if (kf.interpolation() != Curve.Interpolation.BEZIER) continue
            val x = mapX(kf.time)
            val y = mapY(kf.value)
            for ((tangent, dir) in listOf(1 to -1, 2 to 1)) {
                val hx = x + dir * HANDLE_PX
                val hy = y - (if (tangent == 1) kf.inTangent() else kf.outTangent()) * HANDLE_PX
                val dx = mouseX - hx
                val dy = mouseY - hy
                if (dx * dx + dy * dy <= 36f) return Pair(i, tangent)
            }
        }
        return null
    }

    private fun addKey(t: Float, v: Float) {
        curve.add(Keyframe.linear(t, v))
        selected = curve.lastIndex
        apply()
    }

    private fun apply() {
        onApply?.invoke(Curve(curve.sortedBy { it.time }))
    }

    private fun modeIndex(mode: Curve.Interpolation): Int = when (mode) {
        Curve.Interpolation.LINEAR -> 0
        Curve.Interpolation.STEP -> 1
        Curve.Interpolation.SMOOTH -> 2
        Curve.Interpolation.BEZIER -> 3
    }

    private fun mapX(t: Float): Float = plotX + t * plotW

    private fun mapY(v: Float): Float = plotY + (1f - v) * plotH

    private fun inverseX(x: Float): Float = (x - plotX) / plotW

    private fun inverseY(y: Float): Float = 1f - (y - plotY) / plotH

    private fun col(r: Float, g: Float, b: Float, a: Float = 1f): Int = ImGui.colorConvertFloat4ToU32(r, g, b, a)

    companion object {
        private const val PAD = 20f
        private const val HANDLE_PX = 40f
        private val CHILD_FLAGS = ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse
    }
}
