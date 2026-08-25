package org.academy.desktop.uieditor.preview

import imgui.ImGui
import imgui.ImDrawList
import imgui.ImVec2
import imgui.flag.ImGuiStyleVar
import org.academy.api.client.gui.editor.UiEditorDocument
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

interface OverlayCallbacks {
    fun select(path: List<String>)
    fun onZoomIn()
    fun onZoomOut()
    fun onFit()
    fun onZoomReset()
    fun onToggleGrid()
    fun onCycleBackground()
    fun onToggleRulers()
}

data class OverlayModel(
    val win: ViewRect,
    val guiScale: Float,
    val transform: CanvasTransform,
    val snapshot: List<HitEntry>,
    val selectedPath: List<String>,
    val hoveredPath: List<String>?,
    val drag: Drag?,
    val mouseViewX: Float?,
    val mouseViewY: Float?,
    val naturalW: Float,
    val naturalH: Float,
    val grid: Boolean,
    val rulers: Boolean,
    val error: String?,
)

/** All chrome drawn over the blitted preview texture, entirely in window space. */
class CanvasOverlay(private val document: () -> UiEditorDocument) {

    fun render(m: OverlayModel, cb: OverlayCallbacks) {
        val draw = ImGui.getWindowDrawList()
        if (m.rulers) drawRulers(draw, m)
        drawEmptyHint(draw, m)
        drawHoverOutline(draw, m)
        val d = m.drag
        if (d is Drag.Reorder) {
            drawReorderFeedback(draw, m, d)
        } else {
            drawSelection(draw, m)
        }
        if (m.error != null) drawErrorBanner(draw, m, m.error)
        drawStatusBar(draw, m, cb)
    }

    // ---- rulers ----

    private fun drawRulers(draw: ImDrawList, m: OverlayModel) {
        val win = m.win
        val gs = m.guiScale
        val t = m.transform
        draw.addRectFilled(win.x, win.y, win.right, win.y + RULER, RULER_BG)
        draw.addRectFilled(win.x, win.y, win.x + RULER, win.bottom, RULER_BG)
        draw.addRectFilled(win.x, win.y, win.x + RULER, win.y + RULER, CORNER_BG)

        val stepDoc = niceStep(TICK_MIN_PX / (t.scale * gs))
        val minVx = RULER / gs
        val docMinX = t.viewToDocX(minVx)
        val docMaxX = t.viewToDocX(win.w / gs)
        var v = ceil(docMinX / stepDoc) * stepDoc
        var guard = 0
        while (v <= docMaxX && guard++ < 4096) {
            val x = win.x + t.docToViewX(v) * gs
            val atOrigin = abs(v) < stepDoc / 2f
            val col = if (atOrigin) ACCENT else TICK
            draw.addLine(x, win.y + RULER - 6, x, win.y + RULER, col, 1f)
            draw.addText(x + 3f, win.y + 3f, TEXT_DIM, fmtTick(v, stepDoc))
            v += stepDoc
        }
        val docMinY = t.viewToDocY(minVx)
        val docMaxY = t.viewToDocY(win.h / gs)
        var vy = ceil(docMinY / stepDoc) * stepDoc
        guard = 0
        while (vy <= docMaxY && guard++ < 4096) {
            val y = win.y + t.docToViewY(vy) * gs
            val atOrigin = abs(vy) < stepDoc / 2f
            val col = if (atOrigin) ACCENT else TICK
            draw.addLine(win.x + RULER - 6, y, win.x + RULER, y, col, 1f)
            draw.addText(win.x + 3f, y - 7f, TEXT_DIM, fmtTick(vy, stepDoc))
            vy += stepDoc
        }

        val mx = m.mouseViewX
        val my = m.mouseViewY
        if (mx != null && my != null) {
            val gx = win.x + mx * gs
            val gy = win.y + my * gs
            if (mx * gs >= RULER) {
                draw.addLine(gx, win.y + RULER, gx, win.bottom - BAR_H, GUIDE)
                draw.addRectFilled(gx - 2f, win.y, gx + 2f, win.y + RULER, ACCENT)
            }
            if (my * gs >= RULER) {
                draw.addLine(win.x + RULER, gy, win.right, gy, GUIDE)
                draw.addRectFilled(win.x, gy - 2f, win.x + RULER, gy + 2f, ACCENT)
            }
        }
    }

    // ---- element outlines ----

    private fun drawHoverOutline(draw: ImDrawList, m: OverlayModel) {
        val path = m.hoveredPath ?: return
        if (path.isEmpty() || path == m.selectedPath || m.drag != null) return
        val e = m.snapshot.lastOrNull { it.path == path } ?: return
        draw.addRect(dx(m, e.x), dy(m, e.y), dx(m, e.x + e.width), dy(m, e.y + e.height), HOVER_LINE, 0f, 1f)
    }

    private fun drawSelection(draw: ImDrawList, m: OverlayModel) {
        if (m.selectedPath.isEmpty()) return
        val e = m.snapshot.lastOrNull { it.path == m.selectedPath } ?: return
        val x1 = dx(m, e.x)
        val y1 = dy(m, e.y)
        val x2 = dx(m, e.x + e.width)
        val y2 = dy(m, e.y + e.height)
        draw.addRect(x1, y1, x2, y2, SELECTION_LINE, 0f, 2f)
        for (h in Handle.entries) {
            val hx = dx(m, e.x + (h.nx + 1f) / 2f * e.width)
            val hy = dy(m, e.y + (h.ny + 1f) / 2f * e.height)
            draw.addRectFilled(hx - HANDLE_HALF, hy - HANDLE_HALF, hx + HANDLE_HALF, hy + HANDLE_HALF, ACCENT)
            draw.addRect(hx - HANDLE_HALF, hy - HANDLE_HALF, hx + HANDLE_HALF, hy + HANDLE_HALF, OUTLINE_DARK, 0f, 1f)
        }
        val label = "${e.width.roundToInt()} × ${e.height.roundToInt()}"
        val tw = ImGui.calcTextSize(label).x
        val lx = x1.coerceAtMost(x2 - tw - 10f)
        val ly = max(y1 - 17f, m.win.y + (if (m.rulers) RULER + 2f else 2f))
        draw.addRectFilled(lx, ly, lx + tw + 10f, ly + 15f, CHIP_BG, 3f)
        draw.addText(lx + 5f, ly + 2f, TEXT, label)
    }

    private fun drawReorderFeedback(draw: ImDrawList, m: OverlayModel, d: Drag.Reorder) {
        val mx = m.mouseViewX ?: return
        val my = m.mouseViewY ?: return
        val parent = m.snapshot.lastOrNull { it.path == d.parentPath } ?: return
        val px1 = dx(m, parent.x)
        val py1 = dy(m, parent.y)
        val px2 = dx(m, parent.x + parent.width)
        val py2 = dy(m, parent.y + parent.height)
        draw.addRect(px1, py1, px2, py2, PARENT_LINE, 0f, 1f)
        val wx = m.win.x + mx * m.guiScale
        val wy = m.win.y + my * m.guiScale
        if (d.axisHorizontal) {
            draw.addLine(wx, py1, wx, py2, ACCENT, 2f)
        } else {
            draw.addLine(px1, wy, px2, wy, ACCENT, 2f)
        }
        val label = if (d.axisHorizontal) "↔ ${d.dropIndex}" else "↕ ${d.dropIndex}"
        badge(draw, label, wx + 12f, wy + 12f)
    }

    // ---- banners ----

    private fun drawErrorBanner(draw: ImDrawList, m: OverlayModel, message: String) {
        val y = m.win.y + (if (m.rulers) RULER else 0f) + 4f
        val text = "⚠ ${message.take(160)}"
        val tw = minOf(ImGui.calcTextSize(text).x, m.win.w - 40f)
        draw.addRectFilled(m.win.x + 12f, y, m.win.x + 20f + tw, y + 19f, ERROR_BG, 3f)
        draw.addText(m.win.x + 16f, y + 3f, ERROR_TEXT, trunc(text, tw))
    }

    private fun drawEmptyHint(draw: ImDrawList, m: OverlayModel) {
        if (m.snapshot.size > 1 || m.naturalW > 0f || m.naturalH > 0f) return
        val hint = "Empty document — Insert ▸ Add …"
        val ts = ImGui.calcTextSize(hint)
        draw.addText(
            m.win.x + (m.win.w - ts.x) / 2f,
            m.win.y + (m.win.h - ts.y) / 2f,
            TEXT_DIM,
            hint
        )
    }

    // ---- bottom bar ----

    private fun drawStatusBar(draw: ImDrawList, m: OverlayModel, cb: OverlayCallbacks) {
        val win = m.win
        val barY = win.bottom - BAR_H
        draw.addRectFilled(win.x, barY, win.right, win.bottom, BAR_BG)
        draw.addLine(win.x, barY, win.right, barY, HAIRLINE)

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 5f, 2f)

        ImGui.setCursorScreenPos(ImVec2(win.x + 6f, barY + 3f))
        val path = m.selectedPath
        if (path.isEmpty()) {
            ImGui.textDisabled("no selection")
        } else {
            if (ImGui.button(ROOT_LABEL)) cb.select(emptyList())
            for (i in path.indices) {
                ImGui.sameLine()
                ImGui.textDisabled("›")
                ImGui.sameLine()
                val node = UiEditorDocument.findNodeByPath(document().root, path.subList(0, i + 1))
                val label = (node?.name?.ifBlank { node.type } ?: path[i]).let { trunc(it, 120f) }
                if (ImGui.button(label)) cb.select(path.subList(0, i + 1))
            }
        }

        val pctLabel = "${(m.transform.scale * 100).roundToInt()}%"
        val controls: List<Pair<String, () -> Unit>> = listOf(
            "−" to { cb.onZoomOut() },
            pctLabel to {},
            "+" to { cb.onZoomIn() },
            "Fit" to { cb.onFit() },
            "1:1" to { cb.onZoomReset() },
            (if (m.grid) "Grid ✓" else "Grid") to { cb.onToggleGrid() },
            "BG" to { cb.onCycleBackground() },
            (if (m.rulers) "Rulers ✓" else "Rulers") to { cb.onToggleRulers() },
        )
        var total = 0f
        for ((label, _) in controls) total += ImGui.calcTextSize(label).x + 14f
        total += (controls.size - 1) * 4f

        val node = m.selectedPath.lastOrNull()?.let { UiEditorDocument.findNodeByPath(document().root, m.selectedPath) }
        val selEntry = m.snapshot.lastOrNull { it.path == m.selectedPath }
        val readout = if (node != null && selEntry != null) {
            "${node.name.ifBlank { node.type }} [${node.type}]  ${selEntry.width.roundToInt()}×${selEntry.height.roundToInt()}  @${selEntry.x.roundToInt()},${selEntry.y.roundToInt()}"
        } else {
            "${m.naturalW.roundToInt()} × ${m.naturalH.roundToInt()}"
        }
        val rw = ImGui.calcTextSize(readout).x
        val readoutX = max(win.x + 260f, win.right - total - rw - 24f)
        if (readoutX < win.right - total - 8f) {
            draw.addText(readoutX, barY + 5f, TEXT_DIM, trunc(readout, win.right - total - readoutX - 12f))
        }

        val cx = win.right - total - 8f
        ImGui.setCursorScreenPos(ImVec2(cx, barY + 2f))
        for ((i, action) in controls.withIndex()) {
            if (i > 0) ImGui.sameLine()
            if (ImGui.button(action.first, 0f, BAR_H - 6f)) action.second()
        }
        ImGui.popStyleVar()
    }

    // ---- helpers ----

    private fun sx(m: OverlayModel, viewX: Float): Float = m.win.x + viewX * m.guiScale
    private fun sy(m: OverlayModel, viewY: Float): Float = m.win.y + viewY * m.guiScale

    /** doc-space coordinate → window screen pixel, applying the view transform. */
    private fun dx(m: OverlayModel, docX: Float): Float = sx(m, m.transform.docToViewX(docX))
    private fun dy(m: OverlayModel, docY: Float): Float = sy(m, m.transform.docToViewY(docY))

    private fun badge(draw: ImDrawList, text: String, x: Float, y: Float) {
        val tw = ImGui.calcTextSize(text).x
        draw.addRectFilled(x, y, x + tw + 12f, y + 17f, ACCENT_BG, 3f)
        draw.addText(x + 6f, y + 2f, TEXT, text)
    }

    private fun trunc(text: String, maxWidth: Float): String {
        if (ImGui.calcTextSize(text).x <= maxWidth) return text
        var s = text
        while (s.isNotEmpty() && ImGui.calcTextSize("$s…").x > maxWidth) s = s.dropLast(1)
        return "$s…"
    }

    private fun fmtTick(v: Float, step: Float): String =
        if (step >= 1f) v.roundToInt().toString() else "%.1f".format(v)

    companion object {
        const val RULER = 18f
        const val BAR_H = 22f
        const val TICK_MIN_PX = 64f
        const val HANDLE_HALF = 3.5f
        const val ROOT_LABEL = "root"

        val ACCENT = u32(0.29f, 0.62f, 0.94f, 1f)
        val ACCENT_BG = u32(0.20f, 0.46f, 0.74f, 0.95f)
        val SELECTION_LINE = u32(0.29f, 0.62f, 0.94f, 0.95f)
        val HOVER_LINE = u32(1f, 1f, 1f, 0.42f)
        val PARENT_LINE = u32(1f, 1f, 1f, 0.22f)
        val GUIDE = u32(0.29f, 0.62f, 0.94f, 0.28f)
        val TICK = u32(1f, 1f, 1f, 0.38f)
        val RULER_BG = u32(0.086f, 0.086f, 0.106f, 0.94f)
        val CORNER_BG = u32(0.13f, 0.13f, 0.16f, 0.97f)
        val BAR_BG = u32(0.055f, 0.055f, 0.07f, 0.88f)
        val HAIRLINE = u32(1f, 1f, 1f, 0.14f)
        val CHIP_BG = u32(0f, 0f, 0f, 0.68f)
        val OUTLINE_DARK = u32(0f, 0f, 0f, 0.85f)
        val ERROR_BG = u32(0.55f, 0.12f, 0.10f, 0.55f)
        val ERROR_TEXT = u32(1f, 0.62f, 0.58f, 1f)
        val TEXT = u32(1f, 1f, 1f, 0.94f)
        val TEXT_DIM = u32(1f, 1f, 1f, 0.52f)

        private fun u32(r: Float, g: Float, b: Float, a: Float): Int =
            ImGui.colorConvertFloat4ToU32(r, g, b, a)
    }
}
