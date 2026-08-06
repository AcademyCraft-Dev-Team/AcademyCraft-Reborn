package org.academy.internal.client.hud

import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.serialize.WidgetSerializer
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.internal.client.gui.SerializedUiLayout
import org.academy.internal.client.gui.debug.SerializedUiDebugHost
import org.academy.internal.client.gui.debug.UiDebugLayoutRegistry
import org.academy.internal.client.gui.debug.UiDebugSession
import kotlin.math.roundToInt

class HudDebugScreen(private val previousScreen: Screen?) :
    UiScreen(Component.translatable("screen.academy.ui_debug.hud.title")),
    SerializedUiDebugHost {
    private val layoutRoots = LinkedHashMap<String, FrameLayoutWidget>()
    private val mounts = LinkedHashMap<HudLayout.Region, Widget>()
    private var selected = HudLayout.Region.CP
    private var grabbed: HudLayout.Region? = null
    private var resizing = false
    private var grabOffsetX = 0.0
    private var grabOffsetY = 0.0
    private var pressX = 0.0
    private var pressY = 0.0
    private var initialScale = 1f

    override fun isPauseScreen(): Boolean = false

    override fun onInit() {
        UiDebugSession.hudEditorOpen = true
        for (definition in UiDebugLayoutRegistry.hud()) {
            val layout = WidgetSerializer.decode(UiDebugSession.documentJson(definition.id)) as FrameLayoutWidget
            layoutRoots[definition.id] = layout
            root.addChild(definition.id, layout)
        }
        mounts[HudLayout.Region.CP] = SerializedUiLayout.require(layoutRoots.getValue("ability_cp_hud"), "cp")
        mounts[HudLayout.Region.SKILL_WHEEL] = SerializedUiLayout.require(
            layoutRoots.getValue("ability_skill_wheel_hud"), "skill_wheel"
        )
        mounts[HudLayout.Region.TOGGLE_STATUS] = SerializedUiLayout.require(
            layoutRoots.getValue("toggle_status_hud"), "toggle_statuses"
        )
        mounts[HudLayout.Region.MENTAL_CONTROL] = SerializedUiLayout.require(
            layoutRoots.getValue("mental_control_hud"), "mental_control"
        )
        mounts.values.forEach { it.visibility = Widget.Visibility.VISIBLE }
        select(HudLayout.Region.CP)
        applyDefaults()
    }

    override fun tick() {
        applyDefaults()
        super.tick()
    }

    private fun applyDefaults() {
        val defaults = UiDebugSession.hudDefaults()
        val minecraft = Minecraft.getInstance()
        for ((region, mount) in mounts) {
            val rect = region.rect(minecraft, defaults, false)
            mount.translationX = rect.x()
            mount.translationY = rect.y()
            mount.scale = defaults.regions.getValue(region.configKey()).scale
        }
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0x88000000.toInt())
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val defaults = UiDebugSession.hudDefaults()
        drawSamples(graphics, defaults)
        drawRegions(graphics, defaults)
        graphics.centeredText(font, title, width / 2, 6, 0xFFFFFFFF.toInt())
        graphics.centeredText(font, Component.translatable("screen.academy.ui_debug.hud.hint"),
            width / 2, 18, 0xFFB8C8CE.toInt())
        val buttonY = height - 24
        drawButton(graphics, width / 2 - 156, buttonY, 96,
            Component.translatable("screen.academy.ui_debug.action.revert"), mouseX, mouseY)
        drawButton(graphics, width / 2 - 48, buttonY, 96,
            Component.translatable("screen.academy.ui_debug.action.publish"), mouseX, mouseY)
        drawButton(graphics, width / 2 + 60, buttonY, 96,
            Component.translatable("screen.academy.ui_debug.action.done"), mouseX, mouseY)
    }

    private fun drawSamples(graphics: GuiGraphicsExtractor, defaults: HudLayoutDefaults.Config) {
        val mc = Minecraft.getInstance()
        run {
            val rect = HudLayout.Region.CP.rect(mc, defaults, false)
            val x = rect.x().roundToInt()
            val y = rect.y().roundToInt()
            val w = rect.width().roundToInt()
            val h = rect.height().roundToInt()
            graphics.fill(x + 3, y + 7, x + w - 3, y + h - 4, 0xCC17242B.toInt())
            graphics.fill(x + 5, y + 9, x + 5 + ((w - 10) * 0.65f).roundToInt(), y + h - 6, 0xDD43C8E8.toInt())
            graphics.text(font, Component.translatable("screen.academy.ui_debug.hud.sample.cp", 65),
                x + 8, y + 10, 0xFFFFFFFF.toInt(), false)
        }
        run {
            val rect = HudLayout.Region.SKILL_WHEEL.rect(mc, defaults, false)
            val x = rect.x().roundToInt()
            val y = rect.y().roundToInt()
            val w = rect.width().roundToInt()
            for (index in 0 until 5) {
                val size = if (index == 2) 18 else 14
                val rowY = y + 8 + index * 21
                val left = x + w - size - 7
                graphics.fill(left, rowY, left + size, rowY + size,
                    if (index == 2) 0xFF55D5F2.toInt() else 0xAA30434D.toInt())
                graphics.text(font, Component.translatable("screen.academy.ui_debug.hud.sample.skill", index + 1),
                    left - 18, rowY + 3, 0xFFE4F2F5.toInt(), false)
            }
        }
        run {
            val rect = HudLayout.Region.TOGGLE_STATUS.rect(mc, defaults, false)
            val x = rect.x().roundToInt()
            val y = rect.y().roundToInt()
            val w = rect.width().roundToInt()
            listOf("skill.academy.vector_reflection", "skill.academy.tailwind_field")
                .forEachIndexed { index, skillKey ->
                val rowY = y + 5 + index * 19
                graphics.fill(x + 3, rowY, x + w - 3, rowY + 16, 0xA0151D22.toInt())
                graphics.text(
                    font,
                    Component.translatable(
                        "screen.academy.ui_debug.hud.sample.toggle",
                        Component.translatable(skillKey),
                        Component.translatable("hud.academy.toggle_status.on")
                    ),
                    x + 7,
                    rowY + 4,
                    0xFFBFF8D0.toInt(),
                    false
                )
            }
        }
        run {
            val rect = HudLayout.Region.MENTAL_CONTROL.rect(mc, defaults, false)
            val x = rect.x().roundToInt()
            val y = rect.y().roundToInt()
            val w = rect.width().roundToInt()
            graphics.text(font, Component.translatable("screen.academy.ui_debug.hud.sample.mental_control", 3),
                x + 7, y + 6, 0xFF18302A.toInt(), false)
            listOf(
                Triple("entity.minecraft.zombie", 18, 20),
                Triple("screen.academy.ui_debug.hud.sample.player", 20, 20),
                Triple("entity.minecraft.warden", 420, 500)
            ).forEachIndexed { index, target ->
                val rowY = y + 22 + index * 20
                graphics.fill(x + 5, rowY, x + w - 5, rowY + 17,
                    if (index % 2 == 0) 0x88E5ECE9.toInt() else 0x88D8E4DF.toInt())
                graphics.text(
                    font,
                    Component.translatable(
                        "screen.academy.ui_debug.hud.sample.target",
                        Component.translatable(target.first),
                        target.second,
                        target.third
                    ),
                    x + 9,
                    rowY + 5,
                    0xFF24322E.toInt(),
                    false
                )
            }
        }
    }

    private fun drawRegions(graphics: GuiGraphicsExtractor, defaults: HudLayoutDefaults.Config) {
        val minecraft = Minecraft.getInstance()
        for (region in HudLayout.Region.values()) {
            val rect = region.rect(minecraft, defaults, false)
            val x = rect.x().roundToInt()
            val y = rect.y().roundToInt()
            val w = rect.width().roundToInt()
            val h = rect.height().roundToInt()
            val active = region == selected
            border(graphics, x, y, w, h, if (active) 0xFF66FFCC.toInt() else 0xFFFFFFFF.toInt())
            val handleX = if (usesLeftHandle(region)) x else x + w - 9
            graphics.fill(handleX, y + h - 9, handleX + 9, y + h, 0xD0FFE34D.toInt())
            graphics.text(font, Component.translatable(
                "screen.academy.ui_debug.hud.region_scale",
                Component.translatable(region.nameKey()),
                defaults.regions.getValue(region.configKey()).scale.times(100).roundToInt()
            ),
                x + 2, y + 2, if (active) 0xFF66FFCC.toInt() else 0xFFFFFFFF.toInt(), true)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick)
        val buttonY = height - 24
        if (inside(event.x(), event.y(), width / 2 - 156, buttonY, 96, 18)) {
            UiDebugSession.revertHudDefaults()
            return true
        }
        if (inside(event.x(), event.y(), width / 2 - 48, buttonY, 96, 18)) {
            org.academy.internal.client.gui.debug.UiDebugBrowserScreen.notifyPublish(UiDebugSession.publish())
            return true
        }
        if (inside(event.x(), event.y(), width / 2 + 60, buttonY, 96, 18)) {
            onClose()
            return true
        }
        val defaults = UiDebugSession.hudDefaults()
        val mc = Minecraft.getInstance()
        for (region in HudLayout.Region.values()) {
            val rect = region.rect(mc, defaults, false)
            if (overHandle(region, rect, event.x(), event.y())) {
                select(region)
                beginGrab(region, rect, event, true)
                return true
            }
        }
        for (region in HudLayout.Region.values()) {
            val rect = region.rect(mc, defaults, false)
            if (rect.contains(event.x(), event.y())) {
                select(region)
                beginGrab(region, rect, event, false)
                return true
            }
        }
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val region = grabbed ?: return super.mouseDragged(event, dragX, dragY)
        val config = UiDebugSession.hudDefaults()
        val value = config.regions.getValue(region.configKey())
        if (resizing) {
            val horizontal = if (usesLeftHandle(region)) pressX - event.x() else event.x() - pressX
            value.scale = (initialScale + ((horizontal + event.y() - pressY) / 220.0).toFloat())
                .coerceIn(HudLayout.MIN_SCALE, HudLayout.MAX_SCALE)
        } else {
            val current = region.rect(Minecraft.getInstance(), config, false)
            setDefaultTopLeft(region, value, event.x() - grabOffsetX, event.y() - grabOffsetY,
                current.width(), current.height())
        }
        UiDebugSession.updateHudDefaults(config)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        grabbed = null
        resizing = false
        return super.mouseReleased(event)
    }

    override fun onClose() {
        UiDebugSession.hudEditorOpen = false
        UiDebugSession.attach(null)
        Minecraft.getInstance().gui.setScreen(previousScreen)
    }

    override fun debugLayoutId(): String = layoutId(selected)

    override fun debugLayoutRoot(): FrameLayoutWidget = layoutRoots.getValue(debugLayoutId())

    override fun alwaysShowDebugEditor(): Boolean = true

    override fun sanitizeDebugCapture(json: JsonObject): JsonObject {
        val id = debugLayoutId()
        val definition = UiDebugLayoutRegistry.require(id)
        val bindingName = definition.bindings.first().name
        val captured = findNode(json.getAsJsonObject("root"), bindingName)
        val draft = findNode(UiDebugSession.documentJson(id).getAsJsonObject("root"), bindingName)
        if (captured != null && draft != null) {
            val capturedCommon = captured.getAsJsonObject("common") ?: JsonObject().also { captured.add("common", it) }
            val draftCommon = draft.getAsJsonObject("common")
            for (key in listOf("visibility", "translation_x", "translation_y", "scale_x", "scale_y")) {
                if (draftCommon?.has(key) == true) capturedCommon.add(key, draftCommon.get(key).deepCopy())
                else capturedCommon.remove(key)
            }
        }
        return json
    }

    private fun select(region: HudLayout.Region) {
        selected = region
        UiDebugSession.attach(layoutId(region))
    }

    private fun beginGrab(region: HudLayout.Region, rect: HudLayout.Rect, event: MouseButtonEvent, resize: Boolean) {
        grabbed = region
        resizing = resize
        grabOffsetX = event.x() - rect.x()
        grabOffsetY = event.y() - rect.y()
        pressX = event.x()
        pressY = event.y()
        initialScale = UiDebugSession.hudDefaults().regions.getValue(region.configKey()).scale
    }

    private fun setDefaultTopLeft(
        region: HudLayout.Region,
        value: HudLayoutDefaults.RegionValue,
        requestedLeft: Double,
        requestedTop: Double,
        width: Float,
        height: Float
    ) {
        val left = requestedLeft.coerceIn(0.0, maxOf(0.0, this.width - width.toDouble())).toFloat()
        val top = requestedTop.coerceIn(0.0, maxOf(0.0, this.height - height.toDouble())).toFloat()
        value.offsetX = when (value.anchor) {
            HudLayoutDefaults.Anchor.TOP_LEFT, HudLayoutDefaults.Anchor.CENTER_LEFT -> left
            HudLayoutDefaults.Anchor.TOP_RIGHT, HudLayoutDefaults.Anchor.CENTER_RIGHT -> left - (this.width - width)
        }
        value.offsetY = when (value.anchor) {
            HudLayoutDefaults.Anchor.TOP_LEFT, HudLayoutDefaults.Anchor.TOP_RIGHT -> top
            HudLayoutDefaults.Anchor.CENTER_LEFT, HudLayoutDefaults.Anchor.CENTER_RIGHT -> top - (this.height - height) / 2f
        }
    }

    private fun layoutId(region: HudLayout.Region): String = when (region) {
        HudLayout.Region.CP -> "ability_cp_hud"
        HudLayout.Region.SKILL_WHEEL -> "ability_skill_wheel_hud"
        HudLayout.Region.TOGGLE_STATUS -> "toggle_status_hud"
        HudLayout.Region.MENTAL_CONTROL -> "mental_control_hud"
    }

    private fun findNode(node: JsonObject?, name: String): JsonObject? {
        if (node == null) return null
        if (node.get("name")?.asString == name) return node
        node.getAsJsonArray("children")?.forEach { child ->
            findNode(child.asJsonObject, name)?.let { return it }
        }
        return null
    }

    private fun overHandle(region: HudLayout.Region, rect: HudLayout.Rect, x: Double, y: Double): Boolean {
        val left = if (usesLeftHandle(region)) rect.x() else rect.x() + rect.width() - 9f
        return x >= left && x <= left + 9f && y >= rect.y() + rect.height() - 9f && y <= rect.y() + rect.height()
    }

    private fun usesLeftHandle(region: HudLayout.Region): Boolean = region == HudLayout.Region.SKILL_WHEEL

    private fun inside(mouseX: Double, mouseY: Double, x: Int, y: Int, width: Int, height: Int): Boolean {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
    }

    private fun drawButton(
        graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, label: Component, mouseX: Int, mouseY: Int
    ) {
        val hover = inside(mouseX.toDouble(), mouseY.toDouble(), x, y, width, 18)
        graphics.fill(x, y, x + width, y + 18, if (hover) 0x554AA9C8 else 0x66101518)
        border(graphics, x, y, width, 18, if (hover) 0xFF66FFCC.toInt() else 0xFFFFFFFF.toInt())
        graphics.centeredText(font, label, x + width / 2, y + 5, 0xFFFFFFFF.toInt())
    }

    private fun border(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
        graphics.fill(x, y, x + width, y + 1, color)
        graphics.fill(x, y + height - 1, x + width, y + height, color)
        graphics.fill(x, y, x + 1, y + height, color)
        graphics.fill(x + width - 1, y, x + width, y + height, color)
    }

    companion object {
        @JvmStatic
        fun open() {
            val minecraft = Minecraft.getInstance()
            val previous = minecraft.gui.screen()
            minecraft.execute { minecraft.gui.setScreen(HudDebugScreen(previous)) }
        }
    }
}
