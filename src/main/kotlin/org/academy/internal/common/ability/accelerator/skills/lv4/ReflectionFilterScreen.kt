package org.academy.internal.common.ability.accelerator.skills.lv4

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffectCategory
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.util.border
import org.academy.api.client.gui.widget.Widget
import org.academy.internal.client.gui.layout.ReflectionFilterLayout
import org.academy.internal.client.gui.layout.buildReflectionFilterLayout
import org.misaka.MisakaNetworkClient
import java.util.Locale
import kotlin.math.roundToInt

class ReflectionFilterScreen(data: ReflectionFilter.Data) :
    UiScreen(Component.translatable("screen.academy.reflection_filter.title")) {
    private val data: ReflectionFilter.Data = ReflectionFilter.normalizeData(data)
    private val allEffects = ArrayList<EffectEntry>()
    private val filteredEffects = ArrayList<EffectEntry>()
    private lateinit var layout: ReflectionFilterLayout
    private var searchBox: EditBox? = null
    private var panelX = 0
    private var panelY = 0
    private var panelW = 0
    private var panelH = 0
    private var leftX = 0
    private var midX = 0
    private var rightX = 0
    private var listY = 0
    private var listBottom = 0
    private var leftW = 0
    private var rightW = 0
    private var whiteX = 0
    private var blackX = 0
    private var sideListY = 0
    private var sideListBottom = 0
    private var effectScroll = 0
    private var whiteScroll = 0
    private var blackScroll = 0
    private var selectedEffect: String? = null
    private var lastSearch = ""

    init {
        rebuildAllEffects()
    }

    private fun modeDescription(mode: ReflectionFilter.Mode): Component = when (mode) {
        ReflectionFilter.Mode.REFLECT_ALL -> Component.translatable("screen.academy.reflection_filter.mode.all.desc")
        ReflectionFilter.Mode.POSITIVE_FILTER -> Component.translatable("screen.academy.reflection_filter.mode.positive.desc")
        ReflectionFilter.Mode.NEUTRAL_FILTER -> Component.translatable("screen.academy.reflection_filter.mode.neutral.desc")
    }

    private fun categoryColor(category: MobEffectCategory): Int {
        if (category == MobEffectCategory.BENEFICIAL) return GOOD
        if (category == MobEffectCategory.HARMFUL) return BAD
        return NEUTRAL
    }

    override fun onInit() {
        val compact = width < PREFERRED_W + 24 || height < PREFERRED_H + 24
        val built = buildReflectionFilterLayout(compact)
        layout = built
        root.addChild("reflection_filter_layout", built.root)

        panelW = Mth.clamp(width - 24, MIN_W, PREFERRED_W)
        panelW = minOf(panelW, width - 12)
        panelH = Mth.clamp(height - 24, MIN_H, PREFERRED_H)
        panelH = minOf(panelH, height - 12)
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2

        val columnsW = maxOf(1, panelW - INNER_PAD * 2 - MIDDLE_W - COLUMN_GAP * 2)
        leftW = Mth.clamp((columnsW * 0.4f).toInt(), 140, 170)
        if (columnsW - leftW < 196) leftW = maxOf(120, columnsW - 196)
        rightW = maxOf(1, columnsW - leftW)
        leftX = panelX + INNER_PAD
        midX = leftX + leftW + COLUMN_GAP
        rightX = midX + MIDDLE_W + COLUMN_GAP
        listBottom = panelY + panelH - CONTENT_BOTTOM_PAD

        searchBox = EditBox(
            font,
            leftX + SEARCH_X_PAD,
            panelY + SEARCH_Y,
            leftW - SEARCH_X_PAD * 2,
            SEARCH_H,
            Component.empty()
        )
        searchBox!!.setHint(Component.translatable("screen.academy.reflection_filter.search"))
        searchBox!!.setMaxLength(64)
        searchBox!!.isBordered = false
        searchBox!!.setTextColor(TEXT)
        addRenderableWidget(searchBox!!)

        syncSideListBounds()
        rebuildFilteredEffects()
    }

    @Suppress("DuplicatedCode")
    private fun syncLayout() {
        if (!::layout.isInitialized || layout.panel.width <= 0.0f) return
        val panel = rect(layout.panel)
        val left = rect(layout.leftColumn)
        val middle = rect(layout.middleColumn)
        val right = rect(layout.rightColumn)
        panelX = panel.x
        panelY = panel.y
        panelW = panel.width
        panelH = panel.height
        leftX = left.x
        leftW = left.width
        midX = middle.x
        rightX = right.x
        rightW = right.width
        listBottom = panelY + panelH - CONTENT_BOTTOM_PAD
        searchBox!!.x = leftX + SEARCH_X_PAD
        searchBox!!.y = panelY + SEARCH_Y
        searchBox!!.width = maxOf(1, leftW - SEARCH_X_PAD * 2)
        syncSideListBounds()
    }

    private fun syncSideListBounds() {
        val sideListW = sideListWidth()
        whiteX = rightX
        blackX = rightX + sideListW + 8
        listY = panelY + EFFECT_LIST_Y
        sideListY = panelY + SIDE_LIST_Y
        sideListBottom = panelY + panelH - CONTENT_BOTTOM_PAD
    }

    private fun rebuildAllEffects() {
        allEffects.clear()
        for (effect in BuiltInRegistries.MOB_EFFECT) {
            val id = BuiltInRegistries.MOB_EFFECT.getKey(effect) ?: continue
            allEffects.add(
                EffectEntry(
                    id.toString(),
                    Component.translatable(effect.descriptionId).string,
                    effect.category
                )
            )
        }
        allEffects.sortWith(compareBy({ it.name.lowercase(Locale.ROOT) }, { it.id }))
    }

    private fun rebuildFilteredEffects() {
        filteredEffects.clear()
        val query = searchBox?.value?.trim()?.lowercase(Locale.ROOT) ?: ""
        lastSearch = query
        for (entry in allEffects) {
            if (query.isEmpty()
                || entry.name.lowercase(Locale.ROOT).contains(query)
                || entry.id.lowercase(Locale.ROOT).contains(query)
            ) {
                filteredEffects.add(entry)
            }
        }
        effectScroll = Mth.clamp(effectScroll, 0, maxScroll(filteredEffects.size, effectVisibleRows()))
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractBackground(graphics, mouseX, mouseY, a)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, a)
        syncLayout()
        fillSection(graphics, leftX, panelY + SECTION_TOP, leftW, panelH - SECTION_TOP - SECTION_BOTTOM_PAD)
        fillSection(graphics, rightX - 5, panelY + SECTION_TOP, rightW + 5, panelH - SECTION_TOP - SECTION_BOTTOM_PAD)
        val searchFrame = searchFrame()
        drawInput(graphics, searchFrame.x, searchFrame.y, searchFrame.width, searchFrame.height, searchBox!!.isFocused)
        searchBox!!.extractRenderState(graphics, mouseX, mouseY, a)
        val query = searchBox?.value?.trim()?.lowercase(Locale.ROOT) ?: ""
        if (query != lastSearch) rebuildFilteredEffects()
        graphics.centeredText(font, title, panelX + panelW / 2, panelY + 8, TEXT)
        graphics.text(
            font, Component.translatable("screen.academy.reflection_filter.effects"),
            leftX + SEARCH_X_PAD, panelY + EFFECTS_LABEL_Y, DIM, false
        )
        renderEffects(graphics, mouseX, mouseY)
        renderMiddleButtons(graphics, mouseX, mouseY)
        renderModes(graphics, mouseX, mouseY)
        renderForcedMovementProtection(graphics, mouseX, mouseY)
        renderSideLists(graphics, mouseX, mouseY)
    }

    private fun renderEffects(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val visible = effectVisibleRows()
        effectScroll = Mth.clamp(effectScroll, 0, maxScroll(filteredEffects.size, visible))
        graphics.enableScissor(leftX + 4, listY, leftX + leftW - 4, listBottom)
        var row = 0
        while (row < visible && effectScroll + row < filteredEffects.size) {
            val entry = filteredEffects[effectScroll + row]
            val y = listY + row * (ROW_H + GAP)
            val selected = entry.id == selectedEffect
            val hover = inside(mouseX.toDouble(), mouseY.toDouble(), leftX + 5, y, leftW - 10, ROW_H)
            graphics.fill(
                leftX + 5, y, leftX + leftW - 5, y + ROW_H,
                if (selected) ROW_SELECTED else if (hover) ROW_HOVER else ROW
            )
            if (selected) graphics.fill(leftX + 5, y + 2, leftX + 7, y + ROW_H - 2, ACTIVE)
            val markerY = y + (ROW_H - 5) / 2
            graphics.fill(leftX + 10, markerY, leftX + 15, markerY + 5, categoryColor(entry.category))
            graphics.text(font, font.plainSubstrByWidth(entry.name, leftW - 36), leftX + 19, y + 2, TEXT, false)
            graphics.text(font, font.plainSubstrByWidth(entry.id, leftW - 36), leftX + 19, y + 11, DIM, false)
            row++
        }
        graphics.disableScissor()
        drawScrollBar(
            graphics,
            leftX + leftW - 7,
            listY,
            listBottom - listY,
            effectScroll,
            maxScroll(filteredEffects.size, visible)
        )
    }

    private fun renderMiddleButtons(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        drawButton(
            graphics, midX, panelY + MIDDLE_WHITE_Y, MIDDLE_W, MIDDLE_H,
            Component.translatable("screen.academy.reflection_filter.add_white"),
            mouseX, mouseY, selectedEffect != null, false
        )
        drawButton(
            graphics, midX, panelY + MIDDLE_BLACK_Y, MIDDLE_W, MIDDLE_H,
            Component.translatable("screen.academy.reflection_filter.add_black"),
            mouseX, mouseY, selectedEffect != null, false
        )
    }

    private fun renderModes(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val contentW = rightContentWidth()
        val buttonW = (contentW - 12) / 3
        val mode = data.mode
        drawButton(
            graphics, rightX, panelY + MODE_BUTTON_Y, buttonW, MODE_BUTTON_H,
            Component.translatable("screen.academy.reflection_filter.mode.all"),
            mouseX, mouseY, true, mode == ReflectionFilter.Mode.REFLECT_ALL
        )
        drawButton(
            graphics, rightX + buttonW + 6, panelY + MODE_BUTTON_Y, buttonW, MODE_BUTTON_H,
            Component.translatable("screen.academy.reflection_filter.mode.positive"),
            mouseX, mouseY, true, mode == ReflectionFilter.Mode.POSITIVE_FILTER
        )
        drawButton(
            graphics, rightX + (buttonW + 6) * 2, panelY + MODE_BUTTON_Y,
            contentW - (buttonW + 6) * 2, MODE_BUTTON_H,
            Component.translatable("screen.academy.reflection_filter.mode.neutral"),
            mouseX, mouseY, true, mode == ReflectionFilter.Mode.NEUTRAL_FILTER
        )
        graphics.text(
            font, Component.translatable("screen.academy.reflection_filter.mode"),
            rightX, panelY + MODE_LABEL_Y, DIM, false
        )
        graphics.text(
            font,
            font.plainSubstrByWidth(modeDescription(mode).string, contentW),
            rightX,
            panelY + MODE_DESCRIPTION_Y,
            TEXT,
            false
        )
    }

    private fun renderForcedMovementProtection(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val enabled = data.forcedMovementProtectionValue()
        drawButton(
            graphics,
            rightX,
            panelY + FORCED_MOVEMENT_Y,
            rightContentWidth(),
            FORCED_MOVEMENT_H,
            Component.translatable(
                if (enabled) "screen.academy.reflection_filter.forced_movement.on"
                else "screen.academy.reflection_filter.forced_movement.off"
            ),
            mouseX,
            mouseY,
            true,
            enabled
        )
    }

    private fun renderSideLists(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val listW = sideListWidth()
        graphics.centeredText(
            font, Component.translatable("screen.academy.reflection_filter.whitelist"),
            whiteX + listW / 2, sideListY - 13, TEXT
        )
        graphics.centeredText(
            font, Component.translatable("screen.academy.reflection_filter.blacklist"),
            blackX + listW / 2, sideListY - 13, TEXT
        )
        renderIdList(graphics, mouseX, mouseY, data.mutableWhitelist(), whiteX, listW, true)
        renderIdList(graphics, mouseX, mouseY, data.mutableBlacklist(), blackX, listW, false)
    }

    private fun renderIdList(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        ids: MutableList<String>,
        x: Int,
        width: Int,
        white: Boolean
    ) {
        val visible = sideVisibleRows()
        val scroll = Mth.clamp(if (white) whiteScroll else blackScroll, 0, maxScroll(ids.size, visible))
        if (white) whiteScroll = scroll else blackScroll = scroll
        graphics.enableScissor(x, sideListY, x + width, sideListBottom)
        var row = 0
        while (row < visible && scroll + row < ids.size) {
            val id = ids[scroll + row]
            val y = sideListY + row * (ROW_H + GAP)
            val hover = inside(mouseX.toDouble(), mouseY.toDouble(), x, y, width, ROW_H)
            graphics.fill(x, y, x + width, y + ROW_H, if (hover) ROW_HOVER else ROW)
            graphics.text(font, font.plainSubstrByWidth(effectDisplayName(id), width - 22), x + 5, y + 2, TEXT, false)
            graphics.text(font, font.plainSubstrByWidth(id, width - 22), x + 5, y + 11, DIM, false)
            graphics.text(font, "x", x + width - 12, y + (ROW_H - 8) / 2, if (hover) TEXT else BAD, false)
            row++
        }
        graphics.disableScissor()
        if (ids.isEmpty()) {
            graphics.centeredText(
                font, Component.translatable("screen.academy.reflection_filter.empty"),
                x + width / 2, sideListY + (ROW_H - 8) / 2, DIM
            )
        }
        drawScrollBar(
            graphics,
            x + width - 3,
            sideListY,
            sideListBottom - sideListY,
            scroll,
            maxScroll(ids.size, visible)
        )
    }

    override fun mouseClicked(e: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        syncLayout()
        if (e.button() == 0) {
            val mouseX = e.x()
            val mouseY = e.y()
            if (inside(mouseX, mouseY, searchFrame())) {
                focused = searchBox!!
                searchBox!!.isFocused = true
                searchBox!!.mouseClicked(e, isDoubleClick)
                return true
            }
            searchBox!!.isFocused = false
            if (handleEffectClick(mouseX, mouseY)
                || handleMiddleButtons(mouseX, mouseY)
                || handleModeButtons(mouseX, mouseY)
                || handleForcedMovementProtection(mouseX, mouseY)
                || handleSideListClick(mouseX, mouseY)
            ) {
                return true
            }
        }
        return super.mouseClicked(e, isDoubleClick)
    }

    override fun keyPressed(e: KeyEvent): Boolean {
        return searchBox != null && searchBox!!.isFocused && searchBox!!.keyPressed(e)
                || super.keyPressed(e)
    }

    override fun charTyped(e: CharacterEvent): Boolean {
        return searchBox != null && searchBox!!.isFocused && searchBox!!.charTyped(e)
                || super.charTyped(e)
    }

    private fun handleEffectClick(mouseX: Double, mouseY: Double): Boolean {
        if (!inside(mouseX, mouseY, leftX + 5, listY, leftW - 10, listBottom - listY)) return false
        val offset = mouseY - listY
        val row = (offset / (ROW_H + GAP)).toInt()
        if (offset % (ROW_H + GAP) <= ROW_H) {
            val index = effectScroll + row
            if (index >= 0 && index < filteredEffects.size) selectedEffect = filteredEffects[index].id
        }
        return true
    }

    private fun handleMiddleButtons(mouseX: Double, mouseY: Double): Boolean {
        val selected = selectedEffect ?: return false
        if (inside(mouseX, mouseY, midX, panelY + MIDDLE_WHITE_Y, MIDDLE_W, MIDDLE_H)) {
            addToList(data.mutableWhitelist(), data.mutableBlacklist(), selected)
            sendUpdate()
            return true
        }
        if (inside(mouseX, mouseY, midX, panelY + MIDDLE_BLACK_Y, MIDDLE_W, MIDDLE_H)) {
            addToList(data.mutableBlacklist(), data.mutableWhitelist(), selected)
            sendUpdate()
            return true
        }
        return false
    }

    private fun handleModeButtons(mouseX: Double, mouseY: Double): Boolean {
        val contentW = rightContentWidth()
        val buttonW = (contentW - 12) / 3
        if (inside(mouseX, mouseY, rightX, panelY + MODE_BUTTON_Y, buttonW, MODE_BUTTON_H)) {
            data.rawMode(ReflectionFilter.Mode.REFLECT_ALL.name)
        } else if (inside(mouseX, mouseY, rightX + buttonW + 6, panelY + MODE_BUTTON_Y, buttonW, MODE_BUTTON_H)) {
            data.rawMode(ReflectionFilter.Mode.POSITIVE_FILTER.name)
        } else if (inside(
                mouseX,
                mouseY,
                rightX + (buttonW + 6) * 2,
                panelY + MODE_BUTTON_Y,
                contentW - (buttonW + 6) * 2,
                MODE_BUTTON_H
            )
        ) {
            data.rawMode(ReflectionFilter.Mode.NEUTRAL_FILTER.name)
        } else {
            return false
        }
        sendUpdate()
        return true
    }

    private fun handleForcedMovementProtection(mouseX: Double, mouseY: Double): Boolean {
        if (!inside(
                mouseX,
                mouseY,
                rightX,
                panelY + FORCED_MOVEMENT_Y,
                rightContentWidth(),
                FORCED_MOVEMENT_H
            )
        ) return false
        data.forcedMovementProtectionValue(!data.forcedMovementProtectionValue())
        sendUpdate()
        return true
    }

    private fun handleSideListClick(mouseX: Double, mouseY: Double): Boolean {
        val listW = sideListWidth()
        return removeFromList(mouseX, mouseY, data.mutableWhitelist(), whiteX, listW, true)
                || removeFromList(mouseX, mouseY, data.mutableBlacklist(), blackX, listW, false)
    }

    private fun removeFromList(
        mouseX: Double,
        mouseY: Double,
        ids: MutableList<String>,
        x: Int,
        width: Int,
        white: Boolean
    ): Boolean {
        if (!inside(mouseX, mouseY, x, sideListY, width, sideListBottom - sideListY)) return false
        val offset = mouseY - sideListY
        val row = (offset / (ROW_H + GAP)).toInt()
        if (offset % (ROW_H + GAP) <= ROW_H) {
            val index = (if (white) whiteScroll else blackScroll) + row
            if (index >= 0 && index < ids.size) {
                ids.removeAt(index)
                sendUpdate()
            }
        }
        return true
    }

    private fun addToList(target: MutableList<String>, other: MutableList<String>, id: String) {
        other.remove(id)
        if (!target.contains(id) && target.size < ReflectionFilter.MAX_EFFECT_LIST_SIZE) target.add(id)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val step = Mth.sign(scrollY)
        if (inside(mouseX, mouseY, leftX, listY, leftW, listBottom - listY)) {
            effectScroll = Mth.clamp(effectScroll - step, 0, maxScroll(filteredEffects.size, effectVisibleRows()))
            return true
        }
        val listW = sideListWidth()
        if (inside(mouseX, mouseY, whiteX, sideListY, listW, sideListBottom - sideListY)) {
            whiteScroll = Mth.clamp(whiteScroll - step, 0, maxScroll(data.mutableWhitelist().size, sideVisibleRows()))
            return true
        }
        if (inside(mouseX, mouseY, blackX, sideListY, listW, sideListBottom - sideListY)) {
            blackScroll = Mth.clamp(blackScroll - step, 0, maxScroll(data.mutableBlacklist().size, sideVisibleRows()))
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun onClose() {
        sendUpdate()
        super.onClose()
    }

    private fun sendUpdate() {
        ReflectionFilter.normalizeData(data)
        MisakaNetworkClient.send(
            ReflectionFilter.UpdatePacket(
                data.rawMode(),
                data.mutableWhitelist(),
                data.mutableBlacklist(),
                data.forcedMovementProtectionValue()
            )
        )
    }

    fun setData(updated: ReflectionFilter.Data) {
        data.rawMode(updated.rawMode())
        data.mutableWhitelist().clear()
        data.mutableWhitelist().addAll(updated.mutableWhitelist())
        data.mutableBlacklist().clear()
        data.mutableBlacklist().addAll(updated.mutableBlacklist())
        data.forcedMovementProtectionValue(updated.forcedMovementProtectionValue())
        ReflectionFilter.normalizeData(data)
    }

    private fun effectVisibleRows(): Int = maxOf(1, (listBottom - listY) / (ROW_H + GAP))

    private fun sideVisibleRows(): Int = maxOf(1, (sideListBottom - sideListY) / (ROW_H + GAP))

    private fun rightContentWidth(): Int = maxOf(1, rightW - RIGHT_CONTENT_INSET)

    private fun sideListWidth(): Int = maxOf(1, (rightContentWidth() - 8) / 2)

    private fun searchFrame(): Rect = Rect(
        searchBox!!.x - SEARCH_FRAME_PAD_X,
        searchBox!!.y - SEARCH_FRAME_PAD_Y,
        searchBox!!.width + SEARCH_FRAME_PAD_X * 2,
        SEARCH_H + SEARCH_FRAME_PAD_Y * 2
    )

    private fun effectDisplayName(id: String): String {
        for ((entryId, name) in allEffects) {
            if (entryId == id) return name
        }
        return id
    }

    private fun fillSection(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        graphics.fill(x, y, x + width, y + height, SECTION)
        border(graphics, x, y, width, height, BORDER_DIM)
    }

    private fun drawInput(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        focused: Boolean
    ) {
        graphics.fill(x, y, x + width, y + height, if (focused) INPUT_FOCUSED else INPUT)
        border(graphics, x, y, width, height, if (focused) ACTIVE else BORDER_DIM)
        if (focused) graphics.fill(x + 1, y + 2, x + 3, y + height - 2, ACTIVE)
    }

    private fun drawButton(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        label: Component,
        mouseX: Int,
        mouseY: Int,
        enabled: Boolean,
        selected: Boolean
    ) {
        val hover = enabled && inside(mouseX.toDouble(), mouseY.toDouble(), x, y, width, height)
        val background = if (!enabled) 0x18000000
        else if (selected) ROW_SELECTED
        else if (hover) ROW else CONTROL
        graphics.fill(x, y, x + width, y + height, background)
        border(graphics, x, y, width, height, if (selected) ACTIVE else if (hover) BORDER else BORDER_DIM)
        if (selected) graphics.fill(x + 1, y + 2, x + 3, y + height - 2, ACTIVE)
        val color = if (enabled) TEXT else DISABLED
        graphics.centeredText(
            font, font.plainSubstrByWidth(label.string, maxOf(1, width - 6)),
            x + width / 2, y + (height - 8) / 2, color
        )
    }

    private data class EffectEntry(val id: String, val name: String, val category: MobEffectCategory)

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)

    companion object {
        private const val ACTIVE = 0xFFFFFFFF.toInt()
        private const val SECTION = 0x40000000
        private const val CONTROL = 0x28000000
        private const val INPUT = 0x5F1F1F1F
        private const val INPUT_FOCUSED = 0x5F5A5A5A
        private const val ROW = 0x3DFFFFFF
        private const val ROW_HOVER = 0x55FFFFFF
        private const val ROW_SELECTED = 0x55FFFFFF
        private const val BORDER = 0x99FFFFFF.toInt()
        private const val BORDER_DIM = 0x60FFFFFF
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val DIM = 0xBFFFFFFF.toInt()
        private const val DISABLED = 0x33FFFFFF
        private const val GOOD = 0xFF25C4FF.toInt()
        private const val BAD = 0xFFFF6C00.toInt()
        private const val NEUTRAL = 0xFF7680DE.toInt()
        private const val ROW_H = 20
        private const val GAP = 3
        private const val MIN_W = 460
        private const val PREFERRED_W = 520
        private const val MIN_H = 238
        private const val PREFERRED_H = 260
        private const val INNER_PAD = 12
        private const val COLUMN_GAP = 10
        private const val MIDDLE_W = 74
        private const val MIDDLE_H = 24
        private const val SECTION_TOP = 30
        private const val SECTION_BOTTOM_PAD = 14
        private const val CONTENT_BOTTOM_PAD = 19
        private const val SEARCH_X_PAD = 5
        private const val SEARCH_Y = 35
        private const val SEARCH_H = 16
        private const val SEARCH_FRAME_PAD_X = 3
        private const val SEARCH_FRAME_PAD_Y = 2
        private const val EFFECTS_LABEL_Y = 56
        private const val EFFECT_LIST_Y = 68
        private const val MODE_BUTTON_Y = 34
        private const val MODE_BUTTON_H = 22
        private const val MODE_LABEL_Y = 62
        private const val MODE_DESCRIPTION_Y = 74
        private const val FORCED_MOVEMENT_Y = 88
        private const val FORCED_MOVEMENT_H = 18
        private const val SIDE_LIST_Y = 126
        private const val RIGHT_CONTENT_INSET = 6
        private const val MIDDLE_WHITE_Y = 102
        private const val MIDDLE_BLACK_Y = 138
        private const val SCROLLBAR_W = 3

        private fun drawScrollBar(
            graphics: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            height: Int,
            scroll: Int,
            maxScroll: Int
        ) {
            graphics.fill(x, y, x + SCROLLBAR_W, y + height, CONTROL)
            if (maxScroll <= 0) {
                graphics.fill(x, y, x + SCROLLBAR_W, y + height, DISABLED)
                return
            }
            val thumbHeight = maxOf(10, height / 4)
            val thumbY = y + ((height - thumbHeight) * (scroll / maxScroll.toFloat())).toInt()
            graphics.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbHeight, DIM)
        }

        private fun maxScroll(size: Int, visible: Int): Int = maxOf(0, size - visible)

        private fun inside(mouseX: Double, mouseY: Double, x: Int, y: Int, width: Int, height: Int): Boolean {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
        }

        private fun inside(mouseX: Double, mouseY: Double, bounds: Rect): Boolean {
            return inside(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height)
        }

        private fun rect(widget: Widget): Rect = Rect(
            widget.getAbsoluteX().roundToInt(),
            widget.getAbsoluteY().roundToInt(),
            widget.width.roundToInt(),
            widget.height.roundToInt()
        )
    }
}
