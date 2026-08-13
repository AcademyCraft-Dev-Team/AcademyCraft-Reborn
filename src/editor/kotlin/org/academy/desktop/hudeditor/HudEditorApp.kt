package org.academy.desktop.hudeditor

import net.minecraft.util.Mth
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.widget.AbstractWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.desktop.platform.DesktopEnvironment
import org.academy.desktop.platform.EditorApp
import org.academy.internal.client.hud.HudLayout
import org.academy.internal.client.hud.HudLayoutDefaults
import java.nio.file.Files

/**
 * HUD region layout editor: drag/resize the four ability HUD regions on a
 * canvas sized like the game's GUI and persist changes back to
 * `hud_layout_defaults.json` in the project source.
 */
class HudEditorApp(
    private val environment: DesktopEnvironment,
) : EditorApp {
    private var config = HudLayoutDefaults.defaults()
    private val canvas = HudCanvas()

    private var dirty = false
    private var needsLabelLayout = true
    private var lastGuiWidth = 0
    private var lastGuiHeight = 0

    override var title = "AcademyCraft HUD Layout Editor"

    override fun createRoot(): WidgetContainer {
        config = loadConfig()
        val root = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            background = ColorDrawable(0xFF1E1E1E.toInt())
        }
        canvas.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        root.addChild("canvas", canvas)
        for (region in HudRegion.entries) {
            root.addChild(
                "label_${region.key}",
                LabelWidget(region.label).apply {
                    baseFontSize = 13f
                    layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.FIXED, SizeMode.FIXED)
                }
            )
        }
        return root
    }

    override fun onResize(guiScaledWidth: Int, guiScaledHeight: Int) {
        needsLabelLayout = true
    }

    override fun onFrame(partialTick: Float) {
        val root = canvas.parent ?: return
        val guiWidth = environment.guiScaledWidth
        val guiHeight = environment.guiScaledHeight
        if (guiWidth != lastGuiWidth || guiHeight != lastGuiHeight) {
            lastGuiWidth = guiWidth
            lastGuiHeight = guiHeight
            needsLabelLayout = true
        }
        if (needsLabelLayout) {
            needsLabelLayout = false
            for (region in HudRegion.entries) {
                val value = config.regions[region.key] ?: continue
                val rect = rectOf(region, value)
                val label = root.children["label_${region.key}"] as? LabelWidget ?: continue
                label.layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.WRAP_CONTENT)
                    .marginLeft(rect.x + 2f)
                    .marginTop(rect.y + 2f)
                label.requestLayout()
            }
        }
        canvas.invalidate()
    }

    // ============ interaction (delegated to canvas) ============

    private inner class HudCanvas : AbstractWidget() {
        init {
            isClickable = true
        }

        private var grabbed: HudRegion? = null
        private var mode = Mode.NONE
        private var pressX = 0.0
        private var pressY = 0.0
        private var grabOffsetX = 0f
        private var grabOffsetY = 0f
        private var initialScale = 1f

        override fun renderInternal(context: RenderContext) {
            val width = environment.guiScaledWidth.toFloat()
            val height = environment.guiScaledHeight.toFloat()
            // Dotted guide frame at the GUI bounds
            context.pose().pushPose()
            border(context, 0f, 0f, width, height, 0xFF333333.toInt())
            context.pose().popPose()

            for (region in HudRegion.entries) {
                val value = config.regions[region.key] ?: continue
                val rect = rectOf(region, value)
                val active = grabbed == region && mode != Mode.NONE
                context.pose().pushPose()
                context.pose().translate(rect.x, rect.y)
                fillRect(context, rect.w, rect.h, if (active) 0x3066FFCC else 0x20FFFFFF)
                border(context, 0f, 0f, rect.w, rect.h, if (active) BOX_ACTIVE else BOX)
                val handleX = if (region.usesLeftHandle()) 0f else rect.w - HANDLE_SIZE
                val handleY = rect.h - HANDLE_SIZE
                fillRect(context, HANDLE_SIZE, HANDLE_SIZE, HANDLE, handleX, handleY)
                border(context, handleX, handleY, HANDLE_SIZE, HANDLE_SIZE, 0xFF000000.toInt())
                context.pose().popPose()
            }
        }

        override fun onMousePressed(event: MouseEvent) {
            if (event.button != 0) return
            val x = event.x.toFloat()
            val y = event.y.toFloat()
            for (region in HudRegion.entries) {
                val value = config.regions[region.key] ?: continue
                val rect = rectOf(region, value)
                if (overHandle(region, rect, x, y)) {
                    beginGrab(region, Mode.RESIZE, rect, x, y)
                    return
                }
            }
            for (region in HudRegion.entries) {
                val value = config.regions[region.key] ?: continue
                val rect = rectOf(region, value)
                if (contains(rect, x, y)) {
                    beginGrab(region, Mode.MOVE, rect, x, y)
                    return
                }
            }
        }

        override fun onMouseDragged(event: MouseEvent) {
            val region = grabbed ?: return
            val value = config.regions[region.key] ?: return
            val x = event.x.toFloat()
            val y = event.y.toFloat()
            if (mode == Mode.MOVE) {
                setTopLeft(region, value, x - grabOffsetX, y - grabOffsetY)
            } else if (mode == Mode.RESIZE) {
                val horizontalDelta = if (region.usesLeftHandle()) pressX - x else x - pressX
                val delta = (horizontalDelta + (y - pressY)) / 2.0
                value.scale = Mth.clamp(
                    (initialScale + delta / RESIZE_PIXELS_PER_UNIT).toFloat(),
                    HudLayout.MIN_SCALE, HudLayout.MAX_SCALE
                )
            }
            dirty = true
            needsLabelLayout = true
            canvas.invalidate()
        }

        override fun onMouseReleased(event: MouseEvent) {
            if (event.button == 0 && grabbed != null) {
                grabbed = null
                mode = Mode.NONE
                if (dirty) save()
            }
        }

        private fun beginGrab(region: HudRegion, grabMode: Mode, rect: Rect, x: Float, y: Float) {
            grabbed = region
            mode = grabMode
            pressX = x.toDouble()
            pressY = y.toDouble()
            grabOffsetX = x - rect.x
            grabOffsetY = y - rect.y
            initialScale = config.regions[region.key]?.scale ?: 1f
            dirty = false
        }
    }

    // ============ geometry ============

    private fun rectOf(region: HudRegion, value: HudLayoutDefaults.RegionValue): Rect {
        val screenWidth = environment.guiScaledWidth.toFloat()
        val screenHeight = environment.guiScaledHeight.toFloat()
        val width = region.width * value.scale
        val height = region.height * value.scale
        val x = when (value.anchor) {
            HudLayoutDefaults.Anchor.TOP_LEFT, HudLayoutDefaults.Anchor.CENTER_LEFT -> value.offsetX
            HudLayoutDefaults.Anchor.TOP_RIGHT, HudLayoutDefaults.Anchor.CENTER_RIGHT -> screenWidth - width + value.offsetX
        }
        val y = when (value.anchor) {
            HudLayoutDefaults.Anchor.TOP_LEFT, HudLayoutDefaults.Anchor.TOP_RIGHT -> value.offsetY
            HudLayoutDefaults.Anchor.CENTER_LEFT, HudLayoutDefaults.Anchor.CENTER_RIGHT -> (screenHeight - height) / 2.0f + value.offsetY
        }
        return Rect(x, y, width, height)
    }

    private fun setTopLeft(region: HudRegion, value: HudLayoutDefaults.RegionValue, left: Float, top: Float) {
        val screenWidth = environment.guiScaledWidth.toFloat()
        val screenHeight = environment.guiScaledHeight.toFloat()
        val rect = rectOf(region, value)
        val baseX = when (value.anchor) {
            HudLayoutDefaults.Anchor.TOP_LEFT, HudLayoutDefaults.Anchor.CENTER_LEFT -> value.offsetX
            HudLayoutDefaults.Anchor.TOP_RIGHT, HudLayoutDefaults.Anchor.CENTER_RIGHT -> screenWidth - rect.w + value.offsetX
        }
        val baseY = when (value.anchor) {
            HudLayoutDefaults.Anchor.TOP_LEFT, HudLayoutDefaults.Anchor.TOP_RIGHT -> value.offsetY
            HudLayoutDefaults.Anchor.CENTER_LEFT, HudLayoutDefaults.Anchor.CENTER_RIGHT -> (screenHeight - rect.h) / 2.0f + value.offsetY
        }
        value.offsetX = left - baseX
        value.offsetY = top - baseY
    }

    private fun overHandle(region: HudRegion, rect: Rect, x: Float, y: Float): Boolean {
        val left = if (region.usesLeftHandle()) rect.x else rect.x + rect.w - HANDLE_SIZE
        return x >= left && x <= left + HANDLE_SIZE && y >= rect.y + rect.h - HANDLE_SIZE && y <= rect.y + rect.h
    }

    private fun contains(rect: Rect, x: Float, y: Float): Boolean =
        x >= rect.x && x <= rect.x + rect.w && y >= rect.y && y <= rect.y + rect.h

    // ============ drawing helpers ============

    private fun fillRect(context: RenderContext, w: Float, h: Float, argb: Int, dx: Float = 0f, dy: Float = 0f) {
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        context.pose().pushPose()
        if (dx != 0f || dy != 0f) context.pose().translate(dx, dy)
        context.submit(FillRectDrawCommand(w, h, r, g, b, a))
        context.pose().popPose()
    }

    private fun border(context: RenderContext, x: Float, y: Float, w: Float, h: Float, argb: Int) {
        context.pose().pushPose()
        context.pose().translate(x, y)
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        context.submit(FillRectDrawCommand(w, 1f, r, g, b, a))
        context.pose().pushPose()
        context.pose().translate(0f, h - 1f)
        context.submit(FillRectDrawCommand(w, 1f, r, g, b, a))
        context.pose().popPose()
        context.submit(FillRectDrawCommand(1f, h, r, g, b, a))
        context.pose().pushPose()
        context.pose().translate(w - 1f, 0f)
        context.submit(FillRectDrawCommand(1f, h, r, g, b, a))
        context.pose().popPose()
        context.pose().popPose()
    }

    // ============ persistence ============

    private fun loadConfig(): HudLayoutDefaults.Config {
        val file = environment.layoutDir().resolve(HudLayoutDefaults.FILE_NAME)
        return try {
            HudLayoutDefaults.loadJson(
                org.academy.api.client.gui.serialize.UiJson.GSON.fromJson(
                    Files.readString(file), com.google.gson.JsonObject::class.java
                )
            )
        } catch (e: Exception) {
            HudLayoutDefaults.defaults()
        }
    }

    private fun save() {
        try {
            val file = environment.layoutDir().resolve(HudLayoutDefaults.FILE_NAME)
            Files.createDirectories(file.parent)
            Files.writeString(file, org.academy.api.client.gui.serialize.UiJson.GSON.toJson(HudLayoutDefaults.toJson(config)))
            dirty = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private data class Rect(val x: Float, val y: Float, val w: Float, val h: Float)

    private enum class Mode { NONE, MOVE, RESIZE }

    private companion object {
        const val HANDLE_SIZE = 9f
        const val RESIZE_PIXELS_PER_UNIT = 110f
        const val BOX = 0xFFFFFFFF.toInt()
        const val BOX_ACTIVE = 0xFF66FFCC.toInt()
        const val HANDLE = 0xC0FFE34D.toInt()
    }
}

enum class HudRegion(val key: String, val label: String, val width: Float, val height: Float) {
    TOGGLE_STATUS("toggle_status", "Ability Status", 140f, 75f),
    MENTAL_CONTROL("mental_control", "Mental Control", 168f, 184f),
    CP("cp", "CP", 240f, 27f),
    SKILL_WHEEL("skill_wheel", "Skill Wheel", 104f, 119f);

    fun usesLeftHandle(): Boolean = this == SKILL_WHEEL
}
