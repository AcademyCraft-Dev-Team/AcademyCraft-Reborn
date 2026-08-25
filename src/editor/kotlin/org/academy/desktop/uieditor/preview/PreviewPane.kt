package org.academy.desktop.uieditor.preview

import imgui.ImGui
import org.academy.api.client.gui.editor.UiEditorDocument
import org.academy.api.client.gui.serialize.WidgetSerializer
import org.academy.desktop.platform.DesktopEnvironment
import kotlin.math.max

/**
 * Facade between [org.academy.desktop.uieditor.UiEditorApp] and the preview internals.
 * Owns the view transform (via [CanvasInput]) and orchestrates per-frame order:
 * pointer processing → offscreen render + snapshot → texture blit + overlays.
 */
class PreviewPane(
    private val environment: DesktopEnvironment,
    private val document: () -> UiEditorDocument,
) : OverlayCallbacks {

    private val surface = PreviewSurface(environment)
    private val input = CanvasInput(document)
    private val overlay = CanvasOverlay(document)

    private var win = ViewRect(0f, 0f, 480f, 320f)
    private var snapshot: List<HitEntry> = emptyList()
    private var needsRebuild = true
    private var pendingFit = true

    var showOverlays: Boolean = true
    var showRulers: Boolean = true

    var gridShown: Boolean = false
        private set
    var artboardColor: Int = ARTBOARD_DARK
        private set

    val zoom: Float get() = input.transform.scale

    init {
        input.onSelect = { path -> document().setSelection(path) }
    }

    fun rebuild() {
        needsRebuild = true
    }

    fun onDocumentReplaced() {
        needsRebuild = true
        pendingFit = true
    }

    fun setGrid(enabled: Boolean) {
        gridShown = enabled
    }

    fun setArtboard(color: Int) {
        artboardColor = color
    }

    fun zoomStep(delta: Float) {
        zoomBy(if (delta >= 0f) 1.25f else 1f / 1.25f)
    }

    fun setZoomScale(scale: Float) {
        val target = CanvasTransform.clamp(scale)
        val c = viewportCenter()
        input.transform = input.transform.zoomAt(c.first, c.second, target / input.transform.scale)
    }

    fun zoomToFit() {
        fitNow()
    }

    fun centerOnSelection() {
        val e = snapshot.lastOrNull { it.path == document().selectedPath } ?: return
        input.transform = input.transform.centeredOn(e.centerX, e.centerY, input.viewportW, input.viewportH)
    }

    override fun select(path: List<String>) {
        document().setSelection(path)
    }

    override fun onZoomIn() = zoomBy(1.25f)

    override fun onZoomOut() = zoomBy(1f / 1.25f)

    override fun onFit() = fitNow()

    override fun onZoomReset() {
        val c = viewportCenter()
        input.transform = input.transform.zoomAt(c.first, c.second, 1f / input.transform.scale)
    }

    override fun onToggleGrid() {
        gridShown = !gridShown
    }

    override fun onCycleBackground() {
        artboardColor = when (artboardColor) {
            ARTBOARD_DARK -> ARTBOARD_GRAY
            ARTBOARD_GRAY -> ARTBOARD_WHITE
            else -> ARTBOARD_DARK
        }
    }

    override fun onToggleRulers() {
        showRulers = !showRulers
    }

    /** Runs before the ImGui frame: input first, then render, so texture and chrome agree. */
    fun renderBackground() {
        input.snapshot = snapshot
        input.process(win, environment.guiScale)
        if (needsRebuild) {
            decodeIntoSurface()
            needsRebuild = false
        }
        snapshot = surface.renderFrame(win, input.transform, artboardColor, gridShown)
        if (pendingFit && surface.naturalWidth > 0f && surface.naturalHeight > 0f) {
            pendingFit = false
            fitNow()
        }
    }

    /** Blits the offscreen texture into the docked canvas window and draws chrome. */
    fun render(winX: Float, winY: Float, winW: Float, winH: Float) {
        win = ViewRect(winX, winY, max(winW, 32f), max(winH, 32f))
        val texId = surface.ensureTextureId()
        if (texId != 0L) {
            ImGui.image(texId, win.w, win.h, 0f, 1f, 1f, 0f)
        }
        if (showOverlays) overlay.render(buildModel(), this)
    }

    fun close() {
        surface.close()
    }

    private fun buildModel(): OverlayModel {
        val io = ImGui.getIO()
        val mx = io.getMousePosX()
        val my = io.getMousePosY()
        val inside = win.contains(mx, my)
        val gs = environment.guiScale
        return OverlayModel(
            win = win,
            guiScale = gs,
            transform = input.transform,
            snapshot = snapshot,
            selectedPath = document().selectedPath,
            hoveredPath = input.hoveredPath,
            drag = input.drag,
            mouseViewX = if (inside) (mx - win.x) / gs else null,
            mouseViewY = if (inside) (my - win.y) / gs else null,
            naturalW = surface.naturalWidth,
            naturalH = surface.naturalHeight,
            grid = gridShown,
            rulers = showRulers,
            error = document().error,
        )
    }

    private fun decodeIntoSurface() {
        try {
            surface.setDocument(WidgetSerializer.decode(document().documentJson()))
            document().reportError(null)
        } catch (e: Exception) {
            surface.setDocument(null)
            document().reportError("Invalid layout: ${e.message}")
        }
    }

    private fun fitNow() {
        val nw = surface.naturalWidth
        val nh = surface.naturalHeight
        if (nw <= 0f || nh <= 0f) {
            pendingFit = true
            return
        }
        pendingFit = false
        input.transform = input.transform.fitted(nw, nh, input.viewportW, input.viewportH, FIT_PAD)
    }

    private fun zoomBy(factor: Float) {
        val c = viewportCenter()
        input.transform = input.transform.zoomAt(c.first, c.second, factor)
    }

    private fun viewportCenter(): Pair<Float, Float> =
        input.viewportW / 2f to input.viewportH / 2f

    companion object {
        const val FIT_PAD = 24f
        const val ARTBOARD_DARK = 0xFF151515.toInt()
        const val ARTBOARD_GRAY = 0xFF808080.toInt()
        const val ARTBOARD_WHITE = 0xFFFFFFFF.toInt()
    }
}
