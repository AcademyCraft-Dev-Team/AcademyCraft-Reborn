package org.academy.desktop.uieditor.preview

import imgui.ImGui
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiMouseButton
import org.academy.api.client.gui.editor.UiEditorDocument
import org.academy.api.client.gui.serialize.PropType
import org.academy.api.client.gui.serialize.setValue
import org.academy.api.client.gui.widget.Widget
import org.academy.desktop.uieditor.UiKeyMods
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/** Active pointer gesture; created on press, committed on release. */
sealed class Drag {
    class Pan(
        val grabViewX: Float,
        val grabViewY: Float,
        val startOriginX: Float,
        val startOriginY: Float,
        val deselectOnRelease: Boolean,
    ) : Drag() {
        var moved: Boolean = false
    }

    class Move(
        val widget: Widget,
        val path: List<String>,
        val startMarginLeft: Float,
        val startMarginTop: Float,
        val grabViewX: Float,
        val grabViewY: Float,
    ) : Drag() {
        var moved: Boolean = false
    }

    class Resize(
        val widget: Widget,
        val path: List<String>,
        val handle: Handle,
        val startWidth: Float,
        val startHeight: Float,
        val startMarginLeft: Float,
        val startMarginTop: Float,
        val grabViewX: Float,
        val grabViewY: Float,
    ) : Drag() {
        var moved: Boolean = false
    }

    class Reorder(
        val widget: Widget,
        val path: List<String>,
        val parentPath: List<String>,
        val grabViewX: Float,
        val grabViewY: Float,
    ) : Drag() {
        var axisHorizontal: Boolean = false
        var dropIndex: Int = 0
        var moved: Boolean = false
    }
}

/**
 * Canvas interaction: converts raw ImGui pointer state into selection changes and document
 * edits. Runs before the offscreen render each frame so texture and overlays stay in sync.
 */
class CanvasInput(private val document: () -> UiEditorDocument) {

    var transform: CanvasTransform = CanvasTransform.IDENTITY
    var snapshot: List<HitEntry> = emptyList()
    var viewportW: Float = 1f
    var viewportH: Float = 1f

    var onSelect: (List<String>) -> Unit = {}
    var hoveredPath: List<String>? = null
        private set
    var drag: Drag? = null
        private set

    private val doc get() = document()

    fun process(win: ViewRect, guiScale: Float) {
        val io = ImGui.getIO()
        viewportW = win.w / guiScale
        viewportH = win.h / guiScale
        val mx = io.mousePosX
        val my = io.mousePosY
        val inside = win.contains(mx, my)
        val lx = (mx - win.x) / guiScale
        val ly = (my - win.y) / guiScale

        if (drag == null && inside && io.mouseWheel != 0f && !io.wantCaptureKeyboard) {
            val factor = 1.15.pow(io.mouseWheel.toDouble()).toFloat()
            transform = transform.zoomAt(lx, ly, factor)
        }

        val current = drag
        if (current == null) {
            hoveredPath = if (inside && !ImGui.isAnyItemHovered()) pick(lx, ly)?.path else null
            if (inside && !ImGui.isAnyItemHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                onPress(lx, ly, io.keyMods)
            }
        } else {
            if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
                onRelease(current)
            } else if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                onDrag(current, lx, ly)
            }
        }
    }

    fun entryFor(path: List<String>): HitEntry? =
        snapshot.lastOrNull { it.path == path }

    private fun pick(viewX: Float, viewY: Float): HitEntry? {
        val dx = transform.viewToDocX(viewX)
        val dy = transform.viewToDocY(viewY)
        return snapshot.lastOrNull { it.contains(dx, dy) }
    }

    private fun hitHandle(viewX: Float, viewY: Float, selected: HitEntry): Handle? {
        var best: Handle? = null
        var bestDist = Float.MAX_VALUE
        for (h in Handle.entries) {
            val hx = transform.docToViewX(selected.x + (h.nx + 1f) / 2f * selected.width)
            val hy = transform.docToViewY(selected.y + (h.ny + 1f) / 2f * selected.height)
            val dist = max(abs(viewX - hx), abs(viewY - hy))
            if (dist <= HANDLE_RADIUS && dist < bestDist) {
                bestDist = dist
                best = h
            }
        }
        return best
    }

    private fun onPress(lx: Float, ly: Float, mods: Int) {
        if (ImGui.isKeyDown(ImGuiKey.Space)) {
            drag = Drag.Pan(lx, ly, transform.originX, transform.originY, false)
            return
        }
        val selectedPath = doc.selectedPath
        val selected = if (selectedPath.isEmpty()) null else entryFor(selectedPath)
        if (selected != null) {
            val h = hitHandle(lx, ly, selected)
            if (h != null) {
                val w = selected.widget
                drag = Drag.Resize(
                    w, selected.path, h,
                    w.width, w.height,
                    w.layoutParams.marginLeft, w.layoutParams.marginTop,
                    lx, ly
                )
                return
            }
        }
        val hit = pick(lx, ly)
        if (hit == null || hit.path.isEmpty()) {
            drag = Drag.Pan(lx, ly, transform.originX, transform.originY, true)
            return
        }
        if (mods and UiKeyMods.CTRL != 0) {
            onSelect(hit.path.dropLast(1))
            return
        }
        if (mods and UiKeyMods.ALT != 0 && tryStartReorder(hit, lx, ly)) return
        onSelect(hit.path)
        val w = hit.widget
        drag = Drag.Move(
            w, hit.path,
            w.layoutParams.marginLeft, w.layoutParams.marginTop,
            lx, ly
        )
    }

    private fun tryStartReorder(hit: HitEntry, lx: Float, ly: Float): Boolean {
        if (hit.path.isEmpty()) return false
        val parent = hit.widget.parent ?: return false
        var siblings = 0
        for (s in parent.children.values) if (s.isVisible()) siblings++
        if (siblings < 2) return false
        drag = Drag.Reorder(hit.widget, hit.path, hit.path.dropLast(1), lx, ly)
        return true
    }

    private fun onDrag(d: Drag, lx: Float, ly: Float) {
        when (d) {
            is Drag.Pan -> {
                transform = CanvasTransform(
                    transform.scale,
                    d.startOriginX + lx - d.grabViewX,
                    d.startOriginY + ly - d.grabViewY
                )
                d.moved = d.moved || abs(lx - d.grabViewX) + abs(ly - d.grabViewY) > MOVE_EPSILON_PX
            }

            is Drag.Move -> applyMove(d, lx, ly)
            is Drag.Resize -> applyResize(d, lx, ly)
            is Drag.Reorder -> applyReorder(d, lx, ly)
        }
    }

    private fun applyMove(d: Drag.Move, lx: Float, ly: Float) {
        val dx = (lx - d.grabViewX) / transform.scale
        val dy = (ly - d.grabViewY) / transform.scale
        if (!d.moved && abs(dx) * transform.scale + abs(dy) * transform.scale <= MOVE_EPSILON_PX) return
        d.moved = true
        d.widget.layoutParams.marginLeft = d.startMarginLeft + dx
        d.widget.layoutParams.marginTop = d.startMarginTop + dy
        d.widget.requestLayout()
    }

    private fun applyResize(d: Drag.Resize, lx: Float, ly: Float) {
        val dx = (lx - d.grabViewX) / transform.scale
        val dy = (ly - d.grabViewY) / transform.scale
        if (!d.moved && abs(dx) * transform.scale + abs(dy) * transform.scale <= MOVE_EPSILON_PX) return
        d.moved = true
        var newW = d.startWidth
        var newH = d.startHeight
        var newMl = d.startMarginLeft
        var newMt = d.startMarginTop
        if (d.handle.movesLeft) {
            newW = d.startWidth - dx
            newMl = d.startMarginLeft + dx
        } else if (d.handle.movesRight) {
            newW = d.startWidth + dx
        }
        if (d.handle.movesTop) {
            newH = d.startHeight - dy
            newMt = d.startMarginTop + dy
        } else if (d.handle.movesBottom) {
            newH = d.startHeight + dy
        }
        d.widget.width = max(MIN_SIZE, newW)
        d.widget.height = max(MIN_SIZE, newH)
        d.widget.layoutParams.marginLeft = newMl
        d.widget.layoutParams.marginTop = newMt
        d.widget.requestLayout()
    }

    private fun applyReorder(d: Drag.Reorder, lx: Float, ly: Float) {
        if (!d.moved && abs(lx - d.grabViewX) + abs(ly - d.grabViewY) > MOVE_EPSILON_PX) d.moved = true
        d.axisHorizontal = abs(lx - d.grabViewX) >= abs(ly - d.grabViewY)
        val pointer = if (d.axisHorizontal) transform.viewToDocX(lx) else transform.viewToDocY(ly)
        var index = 0
        for (e in snapshot) {
            if (e.path.size != d.parentPath.size + 1) continue
            if (e.path.subList(0, d.parentPath.size) != d.parentPath) continue
            val center = if (d.axisHorizontal) e.centerX else e.centerY
            if (center < pointer) index++
        }
        d.dropIndex = index
    }

    private fun onRelease(d: Drag) {
        when (d) {
            is Drag.Pan -> if (!d.moved && d.deselectOnRelease) onSelect(emptyList())

            is Drag.Move -> if (d.moved) commitMargins(d)

            is Drag.Resize -> if (d.moved) commitSize(d)

            is Drag.Reorder -> if (d.moved) doc.moveChildTo(d.parentPath, d.path.last(), d.dropIndex)
        }
        drag = null
    }

    private fun commitMargins(d: Drag.Move) {
        val lp = d.widget.layoutParams
        doc.editNode(d.path) { n ->
            n.setValue("margin_left", PropType.FLOAT, fmt(lp.marginLeft))
            n.setValue("margin_top", PropType.FLOAT, fmt(lp.marginTop))
        }
    }

    private fun commitSize(d: Drag.Resize) {
        val w = d.widget
        val lp = w.layoutParams
        doc.editNode(d.path) { n ->
            n.setValue("width_mode", PropType.TEXT, "FIXED")
            n.setValue("width", PropType.FLOAT, fmt(w.width))
            n.setValue("height_mode", PropType.TEXT, "FIXED")
            n.setValue("height", PropType.FLOAT, fmt(w.height))
            n.setValue("margin_left", PropType.FLOAT, fmt(lp.marginLeft))
            n.setValue("margin_top", PropType.FLOAT, fmt(lp.marginTop))
        }
    }

    companion object {
        const val HANDLE_RADIUS = 7f
        const val MOVE_EPSILON_PX = 3f
        const val MIN_SIZE = 1f

        fun fmt(v: Float): String = "%.2f".format(v)
    }
}
