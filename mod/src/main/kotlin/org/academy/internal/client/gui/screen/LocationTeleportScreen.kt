package org.academy.internal.client.gui.screen

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.util.border
import org.academy.api.client.gui.widget.Widget
import org.academy.internal.client.gui.layout.LocationTeleportLayout
import org.academy.internal.client.gui.layout.buildLocationTeleportLayout
import org.academy.internal.common.ability.teleport.skills.lv3.LocationTeleport
import org.academy.internal.common.skilldata.LocationTeleportData.Mark
import org.misaka.MisakaNetworkClient
import kotlin.math.roundToInt

class LocationTeleportScreen : UiScreen(Component.translatable("skill.academy.location_teleport")) {
    private val marks = ArrayList<Mark>()
    private lateinit var layout: LocationTeleportLayout
    private lateinit var nameBox: EditBox
    private lateinit var xBox: EditBox
    private lateinit var yBox: EditBox
    private lateinit var zBox: EditBox
    private var panelX = 0
    private var panelY = 0
    private var panelWidth = PANEL_W
    private var listTop = 0
    private var listBottom = 0
    private var quickMarkIndex = -1
    private var defensiveMarkIndex = -1
    private var scroll = 0

    private fun place(box: EditBox, frame: Rect) {
        box.x = frame.x + INPUT_INSET_X
        box.y = frame.y + INPUT_INSET_Y
        box.width = kotlin.math.max(1, frame.width - INPUT_INSET_X * 2)
    }

    private fun configureInput(box: EditBox) {
        box.isBordered = false
        box.setTextColor(TEXT)
    }

    private fun renderInputFrame(graphics: GuiGraphicsExtractor, frame: Rect, box: EditBox) {
        graphics.fill(
            frame.x, frame.y, frame.x + frame.width, frame.y + frame.height,
            if (box.isFocused) INPUT_FOCUSED else INPUT
        )
        border(
            graphics, frame.x, frame.y, frame.width, frame.height,
            if (box.isFocused) ACTIVE else BORDER_DIM
        )
        if (box.isFocused) {
            renderFocusBrackets(graphics, frame)
        }
    }

    private fun inside(mouseX: Double, mouseY: Double, x: Int, y: Int, width: Int, height: Int): Boolean {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
    }

    private fun inside(mouseX: Double, mouseY: Double, bounds: Rect): Boolean {
        return inside(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height)
    }

    private fun rect(widget: Widget): Rect {
        return Rect(
            widget.getAbsoluteX().roundToInt(),
            widget.getAbsoluteY().roundToInt(),
            widget.width.roundToInt(),
            widget.height.roundToInt()
        )
    }

    private fun coordinateFrames(bounds: Rect): Array<Rect> {
        val width = (bounds.width - 8) / 3
        return arrayOf(
            Rect(bounds.x, bounds.y, width, bounds.height),
            Rect(bounds.x + width + 4, bounds.y, width, bounds.height),
            Rect(
                bounds.x + (width + 4) * 2, bounds.y,
                bounds.width - (width + 4) * 2, bounds.height
            )
        )
    }

    private fun integer(value: String): Int {
        return try {
            value.trim().toInt()
        } catch (_: RuntimeException) {
            0
        }
    }

    override fun onInit() {
        val built = buildLocationTeleportLayout()
        layout = built
        root.addChild("location_teleport_layout", built.root)

        panelX = (width - PANEL_W) / 2
        panelY = (height - PANEL_H) / 2
        val left = panelX + PANEL_INSET
        val contentWidth = PANEL_W - PANEL_INSET * 2
        val nameFrame = Rect(left, panelY + NAME_Y, contentWidth, CONTROL_H)
        nameBox = EditBox(font, 0, 0, 1, 16, Component.empty())
        place(nameBox, nameFrame)
        nameBox.setHint(Component.translatable("academy.location_teleport.name"))
        nameBox.setMaxLength(64)
        configureInput(nameBox)
        addRenderableWidget(nameBox)
        val coordinates = coordinateFrames(
            Rect(left, panelY + COORDINATES_Y, contentWidth, CONTROL_H)
        )
        xBox = coordinateBox(coordinates[0], "X")
        yBox = coordinateBox(coordinates[1], "Y")
        zBox = coordinateBox(coordinates[2], "Z")
        listTop = panelY + MARKS_Y + 2
        listBottom = panelY + MARKS_Y + MARKS_H - 2
        MisakaNetworkClient.send(LocationTeleport.RequestMarksPacket.INSTANCE)
    }

    private fun syncLayout() {
        if (layout.panel.width <= 0.0f) return
        val panel = rect(layout.panel)
        panelX = panel.x
        panelY = panel.y
        panelWidth = panel.width
        val name = rect(layout.nameInput)
        place(nameBox, name)
        val coordinates = coordinateFrames(rect(layout.coordinates))
        place(xBox, coordinates[0])
        place(yBox, coordinates[1])
        place(zBox, coordinates[2])
        val marks = rect(layout.marks)
        listTop = marks.y + 2
        listBottom = marks.y + marks.height - 2
    }

    private fun coordinateBox(frame: Rect, hint: String): EditBox {
        val box = EditBox(font, 0, 0, 1, 16, Component.empty())
        place(box, frame)
        box.setHint(Component.literal(hint))
        box.setMaxLength(12)
        configureInput(box)
        addRenderableWidget(box)
        return box
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, a)
        syncLayout()
        val panel = rect(layout.panel)
        val marks = rect(layout.marks)
        fillSection(graphics, marks)
        val coordinates = coordinateFrames(rect(layout.coordinates))
        renderInputFrame(graphics, rect(layout.nameInput), nameBox)
        renderInputFrame(graphics, coordinates[0], xBox)
        renderInputFrame(graphics, coordinates[1], yBox)
        renderInputFrame(graphics, coordinates[2], zBox)
        nameBox.extractRenderState(graphics, mouseX, mouseY, a)
        xBox.extractRenderState(graphics, mouseX, mouseY, a)
        yBox.extractRenderState(graphics, mouseX, mouseY, a)
        zBox.extractRenderState(graphics, mouseX, mouseY, a)
        graphics.centeredText(font, title, panel.x + panel.width / 2, panel.y + 8, TEXT)
        button(
            graphics,
            rect(layout.markCurrent),
            Component.translatable("academy.location_teleport.mark_current"),
            mouseX,
            mouseY
        )
        button(
            graphics,
            rect(layout.addMark),
            Component.translatable("academy.location_teleport.add_mark"),
            mouseX,
            mouseY
        )
        button(
            graphics,
            rect(layout.refresh),
            Component.translatable("academy.location_teleport.refresh"),
            mouseX,
            mouseY
        )
        button(graphics, rect(layout.done), Component.translatable("gui.done"), mouseX, mouseY)
        renderMarks(graphics, mouseX, mouseY)
    }

    private fun renderMarks(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val content = marksContent()
        val left = content.x
        val right = content.x + content.width
        val visible = kotlin.math.max(1, (listBottom - listTop) / ROW_H)
        scroll = Mth.clamp(scroll, 0, kotlin.math.max(0, marks.size - visible))
        graphics.enableScissor(left, listTop, right, listBottom)
        var row = 0
        while (row < visible && scroll + row < marks.size) {
            val index = scroll + row
            val mark = marks[index]
            val y = listTop + row * ROW_H
            val hover = mouseX in left..right && mouseY in y until y + ROW_H - 1
            val quickSelected = index == quickMarkIndex
            val defensiveSelected = index == defensiveMarkIndex
            graphics.fill(
                left, y, right, y + ROW_H - 1,
                if (quickSelected || defensiveSelected) ROW_SELECTED
                else if (hover) ROW_HOVER
                else if (index % 2 == 0) ROW else ROW_ALTERNATE
            )
            renderSelectionMarker(graphics, left, y, quickSelected, defensiveSelected)
            val name = mark.name().ifBlank { "Mark ${index + 1}" }
            val removeLeft = right - REMOVE_ACTION_WIDTH
            val teleportLeft = removeLeft - TELEPORT_ACTION_WIDTH
            val defensiveLeft = teleportLeft - DEFENSIVE_ACTION_WIDTH
            val quickLeft = defensiveLeft - QUICK_ACTION_WIDTH
            val textLeft = left + 6
            val textRight = quickLeft - 6
            val availableTextWidth = kotlin.math.max(2, textRight - textLeft - 6)
            val nameWidth = kotlin.math.max(1, availableTextWidth * 3 / 5)
            val coordinateWidth = kotlin.math.max(1, availableTextWidth - nameWidth)
            graphics.text(font, font.plainSubstrByWidth(name, nameWidth), textLeft, y + 5, TEXT, false)
            val coords = mark.x().toString() + ", " + mark.y() + ", " + mark.z()
            val clippedCoords = font.plainSubstrByWidth(coords, coordinateWidth)
            graphics.text(font, clippedCoords, textRight - font.width(clippedCoords), y + 5, DIM, false)
            rowAction(
                graphics, quickLeft, y, QUICK_ACTION_WIDTH,
                Component.translatable("academy.location_teleport.quick_point"),
                mouseX, mouseY, quickSelected, SelectionForm.RAIL
            )
            rowAction(
                graphics, defensiveLeft, y, DEFENSIVE_ACTION_WIDTH,
                Component.translatable("academy.location_teleport.defensive_point"),
                mouseX, mouseY, defensiveSelected, SelectionForm.BRACKETS
            )
            iconButton(graphics, teleportLeft, y, TELEPORT_ACTION_WIDTH, ">", TELEPORT, mouseX, mouseY)
            iconButton(graphics, removeLeft, y, REMOVE_ACTION_WIDTH, "x", DANGER, mouseX, mouseY)
            row++
        }
        graphics.disableScissor()
        renderScrollIndicator(graphics, rect(layout.marks), visible)
        if (marks.isEmpty()) {
            graphics.centeredText(
                font, Component.translatable("academy.location_teleport.empty"),
                panelX + panelWidth / 2, listTop + 6, DIM
            )
        }
    }

    private fun rowAction(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        label: Component,
        mouseX: Int,
        mouseY: Int,
        selected: Boolean,
        selectionForm: SelectionForm
    ) {
        val hovered = inside(mouseX.toDouble(), mouseY.toDouble(), x, y, width, ROW_H - 1)
        buttonSurface(graphics, x, y, width, ROW_H - 1, selected, hovered, selectionForm)
        graphics.centeredText(
            font,
            font.plainSubstrByWidth(label.string, width - 4),
            x + width / 2,
            y + 5,
            if (selected || hovered) TEXT else DIM
        )
    }

    private fun iconButton(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        glyph: String,
        semanticColor: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val hovered = inside(mouseX.toDouble(), mouseY.toDouble(), x, y, width, ROW_H - 1)
        buttonSurface(graphics, x, y, width, ROW_H - 1, false, hovered, SelectionForm.NONE)
        graphics.centeredText(font, glyph, x + width / 2, y + 5, if (hovered) TEXT else semanticColor)
    }

    private fun button(graphics: GuiGraphicsExtractor, bounds: Rect, text: Component, mouseX: Int, mouseY: Int) {
        val x = bounds.x
        val y = bounds.y
        val width = bounds.width
        val height = bounds.height
        val hover = inside(mouseX.toDouble(), mouseY.toDouble(), x, y, width, height)
        buttonSurface(graphics, x, y, width, height, false, hover, SelectionForm.NONE)
        graphics.centeredText(
            font, font.plainSubstrByWidth(text.string, width - 8),
            x + width / 2, y + (height - 8) / 2, if (hover) TEXT else DIM
        )
    }

    override fun mouseClicked(e: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val mouseX = e.x()
        val mouseY = e.y()
        syncLayout()
        if (e.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            val coordinates = coordinateFrames(rect(layout.coordinates))
            if (focusInputAt(e, isDoubleClick, nameBox, rect(layout.nameInput))
                || focusInputAt(e, isDoubleClick, xBox, coordinates[0])
                || focusInputAt(e, isDoubleClick, yBox, coordinates[1])
                || focusInputAt(e, isDoubleClick, zBox, coordinates[2])
            ) {
                return true
            }
            focused = null
            if (inside(mouseX, mouseY, rect(layout.markCurrent))) {
                MisakaNetworkClient.send(LocationTeleport.SaveMarkPacket(true, nameBox.value, 0, 0, 0))
                return true
            }
            if (inside(mouseX, mouseY, rect(layout.addMark))) {
                MisakaNetworkClient.send(
                    LocationTeleport.SaveMarkPacket(
                        false, nameBox.value,
                        integer(xBox.value), integer(yBox.value), integer(zBox.value)
                    )
                )
                return true
            }
            if (inside(mouseX, mouseY, rect(layout.refresh))) {
                MisakaNetworkClient.send(LocationTeleport.RequestMarksPacket.INSTANCE)
                return true
            }
            if (inside(mouseX, mouseY, rect(layout.done))) {
                onClose()
                return true
            }
            if (mouseY >= listTop && mouseY < listBottom) {
                val index = scroll + ((mouseY - listTop) / ROW_H).toInt()
                if (index >= 0 && index < marks.size) {
                    val content = marksContent()
                    val right = content.x + content.width
                    val removeLeft = right - REMOVE_ACTION_WIDTH
                    val teleportLeft = removeLeft - TELEPORT_ACTION_WIDTH
                    val defensiveLeft = teleportLeft - DEFENSIVE_ACTION_WIDTH
                    val quickLeft = defensiveLeft - QUICK_ACTION_WIDTH
                    if (mouseX >= removeLeft && mouseX < right) {
                        MisakaNetworkClient.send(LocationTeleport.RemoveMarkPacket(index))
                    } else if (mouseX >= teleportLeft && mouseX < removeLeft) {
                        MisakaNetworkClient.send(LocationTeleport.TeleportToMarkPacket(index))
                        onClose()
                    } else if (mouseX >= defensiveLeft && mouseX < teleportLeft) {
                        defensiveMarkIndex = if (defensiveMarkIndex == index) -1 else index
                        MisakaNetworkClient.send(
                            LocationTeleport.SelectMarkPacket(defensiveMarkIndex, true)
                        )
                    } else if (mouseX >= quickLeft && mouseX < defensiveLeft) {
                        quickMarkIndex = if (quickMarkIndex == index) -1 else index
                        MisakaNetworkClient.send(
                            LocationTeleport.SelectMarkPacket(quickMarkIndex, false)
                        )
                    } else {
                        nameBox.value = markValue(index).name()
                        xBox.value = markValue(index).x().toString()
                        yBox.value = markValue(index).y().toString()
                        zBox.value = markValue(index).z().toString()
                    }
                    return true
                }
            }
        }
        return super.mouseClicked(e, isDoubleClick)
    }

    private fun focusInputAt(e: MouseButtonEvent, isDoubleClick: Boolean, box: EditBox, frame: Rect): Boolean {
        if (!inside(e.x(), e.y(), frame)) return false
        focused = box
        nameBox.isFocused = box === nameBox
        xBox.isFocused = box === xBox
        yBox.isFocused = box === yBox
        zBox.isFocused = box === zBox
        if (inside(e.x(), e.y(), box.x, box.y, box.width, 16)) {
            box.mouseClicked(e, isDoubleClick)
        }
        return true
    }

    private fun marksContent(): Rect {
        val bounds = rect(layout.marks)
        return Rect(
            bounds.x + 2,
            bounds.y + 2,
            kotlin.math.max(1, bounds.width - 4 - SCROLLBAR_GAP - SCROLLBAR_WIDTH),
            kotlin.math.max(1, bounds.height - 4)
        )
    }

    private fun renderScrollIndicator(graphics: GuiGraphicsExtractor, bounds: Rect, visibleRows: Int) {
        if (marks.size <= visibleRows) return
        val trackX = bounds.x + bounds.width - 2 - SCROLLBAR_WIDTH
        val trackHeight = kotlin.math.max(1, listBottom - listTop)
        graphics.fill(trackX, listTop, trackX + SCROLLBAR_WIDTH, listBottom, SCROLL_TRACK)
        val thumbHeight = kotlin.math.max(12, trackHeight * visibleRows / marks.size)
        val maxScroll = marks.size - visibleRows
        val travel = kotlin.math.max(0, trackHeight - thumbHeight)
        val thumbY = listTop + travel * scroll / maxScroll
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, DIM)
    }

    private fun markValue(index: Int): Mark = marks[index]

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (mouseY >= listTop && mouseY < listBottom) {
            scroll -= Mth.sign(scrollY)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    fun setMarks(marks: List<Mark>, quickMarkIndex: Int, defensiveMarkIndex: Int) {
        this.marks.clear()
        this.marks.addAll(marks)
        this.quickMarkIndex = quickMarkIndex
        this.defensiveMarkIndex = defensiveMarkIndex
    }

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)

    private enum class SelectionForm {
        NONE,
        RAIL,
        BRACKETS
    }

    companion object {
        private const val ACTIVE = 0xFFFFFFFF.toInt()
        private const val SECTION = 0x14000000
        private const val CONTROL = 0x0C000000
        private const val INPUT = 0x201F1F1F
        private const val INPUT_FOCUSED = 0x305A5A5A
        private const val ROW = 0x18FFFFFF
        private const val ROW_ALTERNATE = 0x10FFFFFF
        private const val ROW_HOVER = 0x28FFFFFF
        private const val ROW_SELECTED = 0x30FFFFFF
        private const val BORDER = 0x99FFFFFF.toInt()
        private const val BORDER_DIM = 0x60FFFFFF
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val DIM = 0xBFFFFFFF.toInt()
        private const val TELEPORT = 0xFF25C4FF.toInt()
        private const val DANGER = 0xFFFF6C00.toInt()
        private const val SCROLL_TRACK = 0x28000000
        private const val PANEL_W = 420
        private const val PANEL_H = 236
        private const val ROW_H = 18
        private const val PANEL_INSET = 12
        private const val CONTROL_H = 20
        private const val NAME_Y = 32
        private const val COORDINATES_Y = 58
        private const val MARKS_Y = 108
        private const val MARKS_H = 94
        private const val INPUT_INSET_X = 4
        private const val INPUT_INSET_Y = 2
        private const val TELEPORT_ACTION_WIDTH = 24
        private const val QUICK_ACTION_WIDTH = 50
        private const val DEFENSIVE_ACTION_WIDTH = 50
        private const val REMOVE_ACTION_WIDTH = 18
        private const val SCROLLBAR_WIDTH = 5
        private const val SCROLLBAR_GAP = 2

        private fun renderFocusBrackets(graphics: GuiGraphicsExtractor, frame: Rect) {
            val left = frame.x + 1
            val right = frame.x + frame.width - 1
            val top = frame.y + 2
            val bottom = frame.y + frame.height - 2
            graphics.fill(left, top, left + 2, bottom, ACTIVE)
            graphics.fill(left + 2, top, left + 6, top + 1, ACTIVE)
            graphics.fill(left + 2, bottom - 1, left + 6, bottom, ACTIVE)
            graphics.fill(right - 2, top, right, bottom, ACTIVE)
            graphics.fill(right - 6, top, right - 2, top + 1, ACTIVE)
            graphics.fill(right - 6, bottom - 1, right - 2, bottom, ACTIVE)
        }

        private fun renderSelectionMarker(
            graphics: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            quickSelected: Boolean,
            defensiveSelected: Boolean
        ) {
            if (!quickSelected && !defensiveSelected) return
            if (quickSelected) {
                graphics.fill(x, y + 2, x + 2, y + ROW_H - 3, ACTIVE)
            }
            if (defensiveSelected) {
                val markerX = if (quickSelected) x + 2 else x
                graphics.fill(markerX, y + 2, markerX + 4, y + 3, ACTIVE)
                graphics.fill(markerX, y + ROW_H - 4, markerX + 4, y + ROW_H - 3, ACTIVE)
            }
        }

        private fun fillSection(graphics: GuiGraphicsExtractor, bounds: Rect) {
            graphics.fill(
                bounds.x,
                bounds.y,
                bounds.x + bounds.width,
                bounds.y + bounds.height,
                SECTION
            )
            border(graphics, bounds.x, bounds.y, bounds.width, bounds.height, BORDER_DIM)
        }

        private fun buttonSurface(
            graphics: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            selected: Boolean,
            hovered: Boolean,
            selectionForm: SelectionForm
        ) {
            graphics.fill(
                x, y, x + width, y + height,
                if (selected) ROW_SELECTED else if (hovered) ROW_HOVER else CONTROL
            )
            border(
                graphics, x, y, width, height,
                if (selected) ACTIVE else if (hovered) BORDER else BORDER_DIM
            )
            if (selected && selectionForm == SelectionForm.RAIL) {
                graphics.fill(x + 1, y + 2, x + 3, y + height - 2, ACTIVE)
            } else if (selected && selectionForm == SelectionForm.BRACKETS) {
                graphics.fill(x + 1, y + 2, x + 6, y + 3, ACTIVE)
                graphics.fill(x + 1, y + height - 3, x + 6, y + height - 2, ACTIVE)
            }
        }

    }
}
