package org.academy.desktop.uieditor.preview

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.command.RoundedRectDrawCommand
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.render.UiContext
import org.academy.api.client.gui.widget.AbstractWidgetContainer
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.desktop.platform.DesktopEnvironment
import org.joml.Vector2f
import org.joml.Vector4f
import java.util.*

/** One laid-out widget flattened into document space, used for picking and overlays. */
class HitEntry(
    val path: List<String>,
    val widget: Widget,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f
    fun contains(px: Float, py: Float): Boolean =
        px >= x && py >= y && px < x + width && py < y + height
}

/**
 * Hosts the decoded document at its natural size: children are measured UNSPECIFIED so the
 * viewport never stretches them, and the whole host is later drawn scaled by the view transform.
 */
private class DocumentHost : AbstractWidgetContainer() {
    var contentWidth: Float = 0f
        private set
    var contentHeight: Float = 0f
        private set

    override fun generateDefaultLayoutParams(): WidgetContainer.LayoutParams = FrameLayoutWidget.LayoutParams()
    override fun generateLayoutParams(p: WidgetContainer.LayoutParams): WidgetContainer.LayoutParams =
        FrameLayoutWidget.LayoutParams(p)

    override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean = p is FrameLayoutWidget.LayoutParams

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val child = children.values.firstOrNull { it.isVisible() }
        if (child == null) {
            contentWidth = 0f
            contentHeight = 0f
            setMeasuredDimension(0f, 0f)
            return
        }
        child.measure(MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f), MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f))
        contentWidth = child.measuredWidth
        contentHeight = child.measuredHeight
        setMeasuredDimension(contentWidth, contentHeight)
    }

    override fun onLayout() {
        val child = children.values.firstOrNull() ?: return
        if (child.isVisible()) child.layout(0f, 0f, child.measuredWidth, child.measuredHeight)
    }

    /** perform() lays the root out at viewport size; the host must stay at its natural size. */
    override fun layout(left: Float, top: Float, right: Float, bottom: Float) {
        super.layout(left, top, left + contentWidth, top + contentHeight)
        onLayout()
        isLayoutDirty = false
        invalidate()
    }
}

/**
 * Offscreen renderer for the preview canvas. Draws workspace backdrop, artboard card and an
 * optional dot grid as procedural draw commands around the transformed document, then flattens
 * the laid-out tree into [HitEntry] snapshots.
 */
internal class PreviewSurface(private val environment: DesktopEnvironment) {

    private val ui = PreviewUiContext()
    private val host = DocumentHost().apply { name = "document_host" }
    private var target: TextureTarget? = null
    private var sampler: GpuSampler? = null
    private var textureId = 0L
    private var registeredView: GpuTextureView? = null

    var document: Widget? = null
        private set

    val naturalWidth: Float get() = host.contentWidth
    val naturalHeight: Float get() = host.contentHeight

    fun setDocument(root: Widget?) {
        document = root
        host.clearChildren()
        if (root != null) host.addChild("document", root)
    }

    private var lastSubmitted: CanvasTransform? = null

    fun renderFrame(win: ViewRect, transform: CanvasTransform, artboardColor: Int, grid: Boolean): List<HitEntry> {
        val scale = environment.guiScale.coerceAtLeast(0.01f)
        val lw = (win.w / scale).coerceAtLeast(1f)
        val lh = (win.h / scale).coerceAtLeast(1f)
        ensureTarget(win.w.toInt().coerceAtLeast(64), win.h.toInt().coerceAtLeast(64))
        // Widget command caches bake the submit-time pose; the view transform lives outside
        // the widget tree, so cached subtrees would render at a stale transform.
        if (transform != lastSubmitted) host.invalidate()
        lastSubmitted = transform
        ui.viewWidth = lw
        ui.viewHeight = lh
        ui.transform = transform
        ui.artboardColor = artboardColor
        ui.gridEnabled = grid
        ui.perform(host, -10000.0, -10000.0, environment.frameDeltaTicks(), lw, lh)
        target?.let { ui.upload(it, true, lw, lh) }
        return snapshot()
    }

    fun ensureTextureId(): Long {
        val backend = environment.imguiBackend ?: return 0L
        val t = target ?: return 0L
        val view = t.getColorTextureView() ?: return 0L
        if (sampler == null) {
            sampler = RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty()
            )
        }
        if (view !== registeredView) {
            if (textureId != 0L) backend.unregisterTexture(textureId)
            textureId = backend.registerTexture(view, sampler!!)
            registeredView = view
        }
        return textureId
    }

    fun close() {
        val backend = environment.imguiBackend
        if (backend != null && textureId != 0L) backend.unregisterTexture(textureId)
        textureId = 0L
        registeredView = null
        target?.destroyBuffers()
        target = null
        ui.close()
    }

    private fun ensureTarget(w: Int, h: Int) {
        val t = target ?: TextureTarget("ui-editor-preview", w, h, true, GpuFormat.RGBA8_UNORM).also { target = it }
        if (t.width != w || t.height != h) t.resize(w, h)
    }

    private fun snapshot(): List<HitEntry> {
        val root = document ?: return emptyList()
        if (!root.isVisible()) return emptyList()
        val ox = root.getAbsoluteX()
        val oy = root.getAbsoluteY()
        val out = ArrayList<HitEntry>()
        fun visit(w: Widget, prefix: List<String>) {
            out += HitEntry(
                prefix, w,
                w.getAbsoluteX() + w.getAbsoluteTranslationX() - ox,
                w.getAbsoluteY() + w.getAbsoluteTranslationY() - oy,
                w.width, w.height
            )
            if (w is WidgetContainer) {
                for (c in w.children.values) if (c.isVisible()) visit(c, prefix + c.name)
            }
        }
        visit(root, emptyList())
        return out
    }
}

private class PreviewUiContext : UiContext() {
    var viewWidth: Float = 1f
    var viewHeight: Float = 1f
    var transform: CanvasTransform = CanvasTransform.IDENTITY
    var artboardColor: Int = 0xFF151515.toInt()
    var gridEnabled: Boolean = false

    // ponytail: always regenerate commands; editor mutates widgets every frame during drags
    override fun shouldUseCacheCommands(rootWidget: WidgetContainer): Boolean = false

    override fun generateCommands(
        context: RenderContext,
        rootWidget: WidgetContainer,
        mouseX: Double,
        mouseY: Double,
        partialTick: Float
    ) {
        fill(context, 0f, 0f, viewWidth, viewHeight, WORKSPACE, 1f)
        context.pose().pushPose()
        context.pose().translate(transform.originX, transform.originY)
        context.pose().scale(transform.scale, transform.scale)
        val docW = (rootWidget as? DocumentHost)?.contentWidth ?: 0f
        val docH = (rootWidget as? DocumentHost)?.contentHeight ?: 0f
        if (docW > 0f && docH > 0f) {
            drawArtboard(context, docW, docH)
            if (gridEnabled) drawGrid(context, docW, docH)
        }
        super.generateCommands(context, rootWidget, mouseX, mouseY, partialTick)
        context.pose().popPose()
    }

    private fun drawArtboard(context: RenderContext, w: Float, h: Float) {
        context.submit(
            RoundedRectDrawCommand(
                w, h,
                Vector4f(0f),
                BORDER_WIDTH / transform.scale,
                Vector4f(chR, chG, chB, 1f),
                Vector4f(1f, 1f, 1f, 0.22f),
                Vector4f(0f, 0f, 0f, 0f),
                0f,
                Vector2f(0f),
                0,
                Vector4f(0f), Vector4f(0f)
            )
        )
    }

    private val chR: Float get() = ((artboardColor shr 16) and 0xFF) / 255f
    private val chG: Float get() = ((artboardColor shr 8) and 0xFF) / 255f
    private val chB: Float get() = (artboardColor and 0xFF) / 255f

    private fun drawGrid(context: RenderContext, w: Float, h: Float) {
        var step = niceStep(GRID_MIN_PX / transform.scale)
        while (w / step * (h / step) > MAX_DOTS) step *= 2f
        val dot = (DOT_PX / transform.scale).coerceAtLeast(step / 40f)
        val r = ((GRID_COLOR shr 16) and 0xFF) / 255f
        val g = ((GRID_COLOR shr 8) and 0xFF) / 255f
        val b = (GRID_COLOR and 0xFF) / 255f
        var gx = step
        while (gx < w) {
            var gy = step
            while (gy < h) {
                context.pose().pushPose()
                context.pose().translate(gx, gy)
                context.submit(FillRectDrawCommand(dot, dot, r, g, b, GRID_ALPHA))
                context.pose().popPose()
                gy += step
            }
            gx += step
        }
    }

    private fun fill(context: RenderContext, x: Float, y: Float, w: Float, h: Float, color: Int, alpha: Float) {
        context.pose().pushPose()
        context.pose().translate(x, y)
        context.submit(
            FillRectDrawCommand(
                w, h,
                ((color shr 16) and 0xFF) / 255f,
                ((color shr 8) and 0xFF) / 255f,
                (color and 0xFF) / 255f,
                alpha
            )
        )
        context.pose().popPose()
    }

    companion object {
        const val WORKSPACE = 0x101014
        const val GRID_COLOR = 0x5A7BA8
        const val GRID_ALPHA = 0.30f
        const val GRID_MIN_PX = 14f
        const val DOT_PX = 2f
        const val MAX_DOTS = 4000f
        const val BORDER_WIDTH = 1f
    }
}
