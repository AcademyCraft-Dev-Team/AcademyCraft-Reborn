package org.academy.internal.client.ability.mentalout

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.widget.Widget
import org.academy.api.common.ability.program.AbilityProgram
import org.academy.api.common.entitycontrol.ControlCapability
import org.academy.internal.client.gui.layout.ProgramEditorLayout
import org.academy.internal.client.gui.layout.ProgramEditorVariant
import org.academy.internal.client.gui.layout.buildProgramEditorLayout
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph
import org.academy.internal.common.ability.mentalout.precision.PrecisionOperationManager
import org.academy.internal.common.ability.program.*
import org.academy.internal.common.ability.program.editor.PrecisionProgramExporter
import org.academy.internal.common.ability.program.editor.PrecisionProgramImporter
import org.academy.internal.common.ability.program.editor.ProgramEditorDocument
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions
import java.util.ArrayDeque
import java.util.Locale
import java.util.OptionalInt
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sign

class PrecisionOperationScreen(
    private var slot: Int,
    private var program: AbilityProgram,
    private var revision: Long
) : UiScreen(Component.translatable("screen.academy.precision_operation.title")) {
    private val undo = ArrayDeque<PrecisionGraph>()
    private val redo = ArrayDeque<PrecisionGraph>()
    private var document: ProgramEditorDocument =
        ProgramEditorDocument(program, AbilityProgramDefinitions.mentalout(), emptySet())
    private var graph: PrecisionGraph = run {
        val exported = PrecisionProgramExporter.export(program)
        if (exported.valid()) exported.graph() else PrecisionGraph.EMPTY
    }
    private var panelX = 0
    private var panelY = 0
    private var panelW = 0
    private var panelH = 0
    private var leftX = 0
    private var leftW = 0
    private var rightX = 0
    private var rightW = 0
    private var canvasX = 0
    private var canvasY = 0
    private var canvasW = 0
    private var canvasH = 0
    private var compactLeft = false
    private var compactRight = false
    private var leftDrawerOpen = false
    private var rightDrawerOpen = false
    private var search: EditBox? = null
    private var parameterInput: EditBox? = null
    private var parameterInputNode = -1
    private var updatingParameterInput = false
    private var parameterInputValid = true
    private var selectedGroup: PrecisionGraph.NodeGroup = PrecisionGraph.NodeGroup.TARGET
    private var paletteScroll = 0
    private var selectedNode = -1
    private var draggingNode: Int? = null
    private var panning = false
    private var spaceDown = false
    private var panX = 0.0
    private var panY = 0.0
    private var zoom = 1.0
    private var initialView = true
    private var connection: ConnectionDrag? = null
    private var quickInsert: QuickInsert? = null
    private var diagnostic: PrecisionGraph.Diagnostic = PrecisionGraph.Diagnostic.OK
    private var diagnosticNodeId = -1
    private var diagnosticPort = -1
    private var transientDiagnostic: PrecisionGraph.Diagnostic = PrecisionGraph.Diagnostic.OK
    private var transientUntil = 0L
    private lateinit var layout: ProgramEditorLayout

    init {
        this.slot = slot.coerceIn(0, AbilityProgramManager.SLOT_COUNT - 1)
    }

    override fun onInit() {
        val geometry = PrecisionEditorGeometry.layout(width, height)
        panelX = geometry.panelX()
        panelY = geometry.panelY()
        panelW = geometry.panelW()
        panelH = geometry.panelH()
        leftX = geometry.leftX()
        leftW = geometry.leftW()
        rightX = geometry.rightX()
        rightW = geometry.rightW()
        canvasX = geometry.canvasX()
        canvasY = geometry.canvasY()
        canvasW = geometry.canvasW()
        canvasH = geometry.canvasH()
        compactLeft = geometry.compactLeft()
        compactRight = geometry.compactRight()

        val built = buildProgramEditorLayout(ProgramEditorVariant.of(compactLeft, compactRight))
        layout = built
        root.addChild("precision_operation_layout", built.root)

        search = EditBox(
            font, paletteX() + 4, canvasY + PALETTE_SEARCH_OFFSET_Y,
            paletteWidth() - 8, 15,
            Component.empty()
        )
        search!!.setHint(Component.translatable("screen.academy.precision_operation.search"))
        search!!.setMaxLength(48)
        search!!.isBordered = false
        search!!.setTextColor(TEXT)
        search!!.visible = paletteVisible()
        parameterInput = object : EditBox(font, 0, 0, 80, 15, Component.empty()) {
            override fun insertText(input: String) {
                if (isNumericInsertionAllowed(input)) super.insertText(input)
            }
        }
        parameterInput!!.setHint(
            Component.translatable("screen.academy.precision_operation.value.permanent")
        )
        parameterInput!!.setMaxLength(4)
        parameterInput!!.isBordered = false
        parameterInput!!.setTextColor(TEXT)
        parameterInput!!.setResponder { this.parameterInputChanged(it) }
        parameterInput!!.visible = false
        if (initialView) {
            initialView = false
            if (graph.nodes().isNotEmpty()) fitCanvas(true)
        }
    }

    @Suppress("DuplicatedCode")
    private fun syncLayout() {
        if (!::layout.isInitialized || layout.panel.width <= 0.0f) return
        val panel = rect(layout.panel)
        val palette = rect(layout.palette)
        val canvas = rect(layout.canvas)
        val inspector = rect(layout.inspector)
        panelX = panel.x
        panelY = panel.y
        panelW = panel.width
        panelH = panel.height
        leftX = palette.x
        leftW = palette.width
        canvasX = canvas.x
        canvasY = canvas.y
        canvasW = canvas.width
        canvasH = canvas.height
        rightX = inspector.x
        rightW = inspector.width
        compactLeft = leftW <= RAIL_W
        compactRight = rightW <= RAIL_W
        updateSearchBounds()
    }

    fun applyServerState(selectedSlot: Int, serverProgram: AbilityProgram, serverRevision: Long) {
        revision = serverRevision
        if (slot != selectedSlot) return
        program = serverProgram
        document = ProgramEditorDocument(program, AbilityProgramDefinitions.mentalout(), emptySet())
        val exported = PrecisionProgramExporter.export(program)
        setGraph(if (exported.valid()) exported.graph() else PrecisionGraph.EMPTY, false)
    }

    fun applyResult(
        resultSlot: Int,
        type: PrecisionOperationManager.FeedbackType,
        serverRevision: Long,
        result: PrecisionGraph.Diagnostic,
        nodeId: Int,
        port: Int
    ) {
        revision = maxOf(revision, serverRevision)
        if (slot == resultSlot) {
            if (type == PrecisionOperationManager.FeedbackType.ERROR) {
                diagnostic = result
                diagnosticNodeId = nodeId
                diagnosticPort = port
                showTransient(result)
            } else if (type == PrecisionOperationManager.FeedbackType.STARTED
                || type == PrecisionOperationManager.FeedbackType.COMPLETED
                && PrecisionOperationClient.diagnostic(slot) == PrecisionGraph.Diagnostic.OK
            ) {
                diagnostic = PrecisionGraph.Diagnostic.OK
                diagnosticNodeId = -1
                diagnosticPort = -1
            }
        }
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractBackground(graphics, mouseX, mouseY, a)
    }

    private fun renderOverlays(graphics: GuiGraphicsExtractor) {
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BACKGROUND)
        renderInstrumentFrame(graphics, panelX, panelY, panelW, panelH)
        graphics.fill(panelX + 7, panelY + TOP_H, panelX + panelW - 7, panelY + TOP_H + 1, DIVIDER)
        graphics.fill(panelX + 7, panelY + TOP_H, panelX + 31, panelY + TOP_H + 1, ACCENT)
        graphics.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, CANVAS_BACKGROUND)
        border(graphics, canvasX, canvasY, canvasW, canvasH, BORDER_MUTED)
        if (!compactLeft) {
            renderSection(graphics, paletteX(), canvasY, paletteWidth(), canvasH)
        }
        if (!compactRight) {
            renderSection(graphics, inspectorX(), canvasY, inspectorWidth(), canvasH)
        }
    }

    private fun renderDrawerOverlays(graphics: GuiGraphicsExtractor) {
        if (compactLeft && leftDrawerOpen) {
            renderSection(graphics, paletteX(), canvasY, paletteWidth(), canvasH)
        }
        if (compactRight && rightDrawerOpen) {
            renderSection(graphics, inspectorX(), canvasY, inspectorWidth(), canvasH)
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, a)
        syncLayout()
        renderOverlays(graphics)
        renderTopBar(graphics, mouseX, mouseY)
        renderCanvas(graphics, mouseX, mouseY)
        renderDrawerOverlays(graphics)
        renderRails(graphics, mouseX, mouseY)
        if (paletteVisible()) renderPalette(graphics, mouseX, mouseY)
        if (search!!.visible) search!!.extractRenderState(graphics, mouseX, mouseY, a)
        if (inspectorVisible()) renderInspector(graphics, mouseX, mouseY)
        syncParameterInput()
        if (parameterInput!!.visible) {
            renderInput(
                graphics, parameterInput!!.x, parameterInput!!.y,
                parameterInput!!.width, parameterInput!!.isFocused
            )
            parameterInput!!.extractRenderState(graphics, mouseX, mouseY, a)
        }
        renderStatus(graphics)
        renderQuickInsert(graphics, mouseX, mouseY)
    }

    private fun renderTopBar(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        var x = panelX + 4
        if (panelW >= 620) {
            smallText(graphics, title.string, panelX + 12, panelY + 6, TEXT, 84)
            x += 96
        }
        val tabWidth = slotTabWidth()
        for (index in 0 until AbilityProgramManager.SLOT_COUNT) {
            val label = Component.translatable("screen.academy.precision_operation.slot", index + 1)
            button(graphics, x, panelY + 2, tabWidth, 16, label, mouseX, mouseY, index == slot, true)
            x += tabWidth + 2
        }
        var toolsX = panelX + panelW - TOOL_LABELS.size * (TOOL_SIZE + 2) - 2
        for (index in TOOL_LABELS.indices) {
            val disabled = index == 6 && (!graph.validate().valid() || !parameterInputValid)
            iconButton(graphics, toolsX, panelY + 3, TOOL_GLYPHS[index], mouseX, mouseY, disabled)
            if (inside(
                    mouseX.toDouble(),
                    mouseY.toDouble(),
                    toolsX.toDouble(),
                    (panelY + 3).toDouble(),
                    TOOL_SIZE.toDouble(),
                    TOOL_SIZE.toDouble()
                )
            ) {
                graphics.setTooltipForNextFrame(
                    Component.translatable("screen.academy.precision_operation." + TOOL_LABELS[index]),
                    mouseX, mouseY
                )
            }
            toolsX += TOOL_SIZE + 2
        }
    }

    private fun renderRails(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (compactLeft) {
            button(
                graphics, leftX + 1, canvasY + 2, 16, 16, Component.literal("N"),
                mouseX, mouseY, leftDrawerOpen, false
            )
        }
        if (compactRight) {
            button(
                graphics, rightX + 1, canvasY + 2, 16, 16, Component.literal("I"),
                mouseX, mouseY, rightDrawerOpen, false
            )
        }
    }

    private fun renderPalette(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val x = paletteX()
        val w = paletteWidth()
        smallText(
            graphics, Component.translatable("screen.academy.precision_operation.nodes").string,
            x + 4, canvasY + 4, DIM, w - 8
        )
        val tabY = canvasY + PALETTE_TAB_OFFSET_Y
        val tabW = maxOf(16, (w - 8) / PrecisionGraph.NodeGroup.entries.size)
        for (index in PrecisionGraph.NodeGroup.entries.indices) {
            val group = PrecisionGraph.NodeGroup.entries[index]
            button(
                graphics, x + 4 + index * tabW, tabY, tabW - 1, 12,
                Component.literal(groupGlyph(group)), mouseX, mouseY, group == selectedGroup, false
            )
            if (inside(
                    mouseX.toDouble(),
                    mouseY.toDouble(),
                    (x + 4 + index * tabW).toDouble(),
                    tabY.toDouble(),
                    (tabW - 1).toDouble(),
                    12.0
                )
            ) {
                graphics.setTooltipForNextFrame(Component.translatable(groupKey(group)), mouseX, mouseY)
            }
        }
        renderInput(graphics, search!!.x, search!!.y, search!!.width, search!!.isFocused)
        val kinds = visibleKinds()
        val listY = canvasY + PALETTE_LIST_OFFSET_Y
        val listBottom = canvasY + canvasH - 3
        val visibleRows = maxOf(1, (listBottom - listY) / ROW_H)
        paletteScroll = paletteScroll.coerceIn(0, maxOf(0, kinds.size - visibleRows))
        var row = 0
        while (row < visibleRows && paletteScroll + row < kinds.size) {
            val kind = kinds[paletteScroll + row]
            val y = listY + row * ROW_H
            val hover = inside(
                mouseX.toDouble(),
                mouseY.toDouble(),
                (x + 3).toDouble(),
                y.toDouble(),
                (w - 6).toDouble(),
                (ROW_H - 1).toDouble()
            )
            graphics.fill(
                x + 3, y, x + w - 3, y + ROW_H - 1,
                if (hover) HOVER_BACKGROUND else ROW_BACKGROUND
            )
            graphics.fill(x + 3, y, x + 5, y + ROW_H - 1, categoryColor(kind.category()))
            smallText(graphics, groupGlyph(kind.group()), x + 8, y + 3, categoryColor(kind.category()), 8)
            smallText(graphics, nodeLabel(kind).string, x + 17, y + 3, TEXT, w - 22)
            if (hover) {
                graphics.setComponentTooltipForNextFrame(
                    font, listOf(
                        nodeLabel(kind),
                        Component.translatable(nodeDescriptionKey(kind)).withColor(DIM)
                    ), mouseX, mouseY
                )
            }
            row++
        }
        if (kinds.size > visibleRows) {
            val trackH = listBottom - listY
            val thumbH = maxOf(8, trackH * visibleRows / kinds.size)
            val maxScroll = kinds.size - visibleRows
            val thumbY = listY + (trackH - thumbH) * paletteScroll / maxOf(1, maxScroll)
            graphics.fill(x + w - 3, listY, x + w - 1, listBottom, CONTROL_BACKGROUND)
            graphics.fill(x + w - 3, thumbY, x + w - 1, thumbY + thumbH, DIM)
        }
    }

    private fun renderCanvas(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        graphics.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH)
        renderCanvasGrid(graphics)
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate((canvasX + panX).toFloat(), (canvasY + panY).toFloat())
        pose.scale(zoom.toFloat(), zoom.toFloat())
        for (edge in graph.edges()) renderEdge(graphics, edge)
        for (node in graph.nodes()) renderNode(graphics, node, mouseX, mouseY)
        pose.popMatrix()
        renderConnectionPreview(graphics, mouseX, mouseY)
        renderPortTooltip(graphics, mouseX, mouseY)
        graphics.disableScissor()
        smallText(
            graphics, Component.literal((zoom * 100.0).roundToLong().toString() + "%").string,
            canvasX + 3, canvasY + canvasH - 10, DIM, 40
        )
    }

    private fun renderEdge(graphics: GuiGraphicsExtractor, edge: PrecisionGraph.Edge) {
        val from = node(edge.fromNode())
        val to = node(edge.toNode())
        if (from == null || to == null) return
        val x1 = (from.x() + NODE_W).roundToInt()
        val y1 = (from.y() + outputOffsetY(edge.fromPort())).roundToInt()
        val x2 = (to.x()).roundToInt()
        val y2 = (to.y() + inputOffsetY(edge.toPort())).roundToInt()
        val type = from.kind().outputDefinitions()[edge.fromPort()].type()
        orthogonalLine(graphics, x1, y1, x2, y2, portColor(type))
    }

    private fun renderNode(graphics: GuiGraphicsExtractor, node: PrecisionGraph.Node, mouseX: Int, mouseY: Int) {
        val x = (node.x()).roundToInt()
        val y = (node.y()).roundToInt()
        val h = nodeHeight(node)
        val selected = node.id() == selectedNode
        val validation = graph.validate()
        val errorNode = if (diagnostic != PrecisionGraph.Diagnostic.OK) diagnosticNodeId
        else if (validation.valid()) -1 else validation.nodeId()
        val hasError = node.id() == errorNode
        graphics.fill(
            x, y, x + NODE_W, y + h,
            if (hasError) ERROR_BACKGROUND
            else if (selected) NODE_SELECTED_BACKGROUND else NODE_BACKGROUND
        )
        border(
            graphics, x, y, NODE_W, h,
            if (hasError) ERROR else if (selected) ACCENT else BORDER_MUTED
        )
        graphics.fill(x, y, x + NODE_W, y + NODE_HEADER_H, if (hasError) ERROR else NODE_HEADER)
        if (!hasError) {
            graphics.fill(x, y, x + 2, y + NODE_HEADER_H, categoryColor(node.kind().category()))
        }
        val headerText = TEXT
        smallText(graphics, groupGlyph(node.kind().group()), x + 3, y + 2, headerText, 8)
        smallText(
            graphics, nodeLabel(node.kind()).string, x + 12, y + 2, headerText,
            if (hasError) NODE_W - 27 else NODE_W - 15
        )
        if (hasError) smallText(graphics, "!!", x + NODE_W - 12, y + 2, 0xFFFFFFFF.toInt(), 10)
        for ((port, definition) in node.kind().inputDefinitions().withIndex()) {
            val color = if (highlightedPort(node.id(), true, definition.type())) 0xFFFFFFFF.toInt()
            else portColor(definition.type())
            renderPort(
                graphics, x, y + inputOffsetY(port), color,
                definition.type() == PrecisionGraph.PortType.FLOW
                        && !endpointConnected(Endpoint(node.id(), port, true, definition.type()))
            )
        }
        for ((port, definition) in node.kind().outputDefinitions().withIndex()) {
            val color = if (highlightedPort(node.id(), false, definition.type())) 0xFFFFFFFF.toInt()
            else portColor(definition.type())
            renderPort(
                graphics, x + NODE_W, y + outputOffsetY(port), color,
                definition.type() == PrecisionGraph.PortType.FLOW
                        && !endpointConnected(Endpoint(node.id(), port, false, definition.type()))
            )
        }
        if (insideNode(mouseX.toDouble(), mouseY.toDouble(), node)) {
            val lines = ArrayList<Component>()
            lines.add(nodeLabel(node.kind()))
            lines.add(Component.translatable(nodeDescriptionKey(node.kind())).withColor(DIM))
            if (hasError) {
                val shown = if (diagnostic != PrecisionGraph.Diagnostic.OK) diagnostic else validation.diagnostic()
                lines.add(Component.translatable(shown.translationKey()).withColor(ERROR))
            }
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY)
        }
    }

    private fun renderConnectionPreview(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val current = connection ?: return
        val anchor = endpointScreen(current.endpoint)
        val target =
            snappedEndpoint(mouseX.toDouble(), mouseY.toDouble(), current.endpoint.type, current.endpoint.input)
        val endX = if (target == null) mouseX else endpointScreen(target).x
        val endY = if (target == null) mouseY else endpointScreen(target).y
        val color = if (target != null && connectionCreatesCycle(current.endpoint, target)) ERROR
        else portColor(current.endpoint.type)
        orthogonalLine(graphics, anchor.x, anchor.y, endX, endY, color)
    }

    private fun renderPortTooltip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val endpoint = endpointAt(mouseX.toDouble(), mouseY.toDouble()) ?: return
        if (endpoint.type != PrecisionGraph.PortType.FLOW) return
        graphics.setComponentTooltipForNextFrame(
            font, listOf(
                flowPortLabel(endpoint),
                Component.translatable("screen.academy.precision_operation.flow_open_chain_hint").withColor(DIM)
            ), mouseX, mouseY
        )
    }

    private fun renderInspector(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val x = inspectorX()
        val w = inspectorWidth()
        smallText(
            graphics, Component.translatable("screen.academy.precision_operation.inspector").string,
            x + 5, canvasY + 5, DIM, w - 10
        )
        val selected = node(selectedNode)
        if (selected == null) {
            smallText(
                graphics, Component.translatable("screen.academy.precision_operation.no_selection").string,
                x + 5, canvasY + 22, DIM, w - 10
            )
            return
        }
        smallText(graphics, nodeLabel(selected.kind()).string, x + 5, canvasY + 22, TEXT, w - 10)
        val description = Component.translatable(nodeDescriptionKey(selected.kind()))
        val descriptionHeight = smallWrappedText(graphics, description, x + 5, canvasY + 36, w - 10)
        val parameterY = canvasY + maxOf(58, descriptionHeight + 40)
        renderParameterEditor(graphics, selected, x + 5, parameterY, w - 10, mouseX, mouseY)
        val inputsY = parameterY + parameterEditorHeight(selected.kind().parameterKind())
        smallText(
            graphics, Component.translatable("screen.academy.precision_operation.ports").string,
            x + 5, inputsY, DIM, w - 10
        )
        var y = inputsY + 11
        for ((index, port) in selected.kind().inputDefinitions().withIndex()) {
            val endpoint = Endpoint(selected.id(), index, true, port.type())
            smallText(graphics, "< " + portLabel(endpoint, port).string, x + 7, y, portColor(port.type()), w - 12)
            y += 9
        }
        for ((index, port) in selected.kind().outputDefinitions().withIndex()) {
            val endpoint = Endpoint(selected.id(), index, false, port.type())
            smallText(graphics, "> " + portLabel(endpoint, port).string, x + 7, y, portColor(port.type()), w - 12)
            y += 9
        }
    }

    private fun renderParameterEditor(
        graphics: GuiGraphicsExtractor,
        node: PrecisionGraph.Node,
        x: Int,
        y: Int,
        width: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val kind = node.kind().parameterKind()
        if (kind == PrecisionGraph.ParameterKind.NONE) return
        smallText(
            graphics,
            Component.translatable("screen.academy.precision_operation.parameter", formatParameter(node)).string,
            x,
            y,
            TEXT,
            width
        )
        when (kind) {
            PrecisionGraph.ParameterKind.HEALTH_PERCENT -> {
                val min = 1.0
                val max = 100.0
                val trackY = y + 13
                graphics.fill(x, trackY, x + width, trackY + 2, BORDER_MUTED)
                val knob = x + ((node.parameter() - min) / (max - min) * (width - 4)).roundToInt()
                graphics.fill(knob, trackY - 2, knob + 4, trackY + 4, ACCENT)
            }

            PrecisionGraph.ParameterKind.DURATION_SECONDS, PrecisionGraph.ParameterKind.RANGE -> Unit
            else -> {
                iconButton(graphics, x, y + 11, "<", mouseX, mouseY, false)
                iconButton(graphics, x + 18, y + 11, ">", mouseX, mouseY, false)
            }
        }
    }

    private fun renderStatus(graphics: GuiGraphicsExtractor) {
        val validation = graph.validate()
        val shown = if (System.currentTimeMillis() < transientUntil) transientDiagnostic
        else if (diagnostic != PrecisionGraph.Diagnostic.OK) diagnostic else validation.diagnostic()
        val nodeId = if (diagnostic != PrecisionGraph.Diagnostic.OK) diagnosticNodeId
        else if (validation.valid()) -1 else validation.nodeId()
        var text = Component.translatable(shown.translationKey()).string
        if (nodeId >= 0) text += "  [#$nodeId]"
        smallText(
            graphics, text,
            panelX + 4, panelY + panelH - 11,
            if (shown == PrecisionGraph.Diagnostic.OK) DIM else ERROR, panelW - 8
        )
    }

    private fun quickInsertBounds(current: QuickInsert): IntArray {
        val rows = minOf(12, current.kinds.size)
        val w = 112
        val h = rows * ROW_H + 4
        val x = current.x.coerceIn(panelX + 2, panelX + panelW - w - 2)
        val y = current.y.coerceIn(canvasY + 2, canvasY + canvasH - h - 2)
        return intArrayOf(rows, w, h, x, y)
    }

    private fun renderQuickInsert(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val current = quickInsert ?: return
        val bounds = quickInsertBounds(current)
        val rows = bounds[0]
        val w = bounds[1]
        val h = bounds[2]
        val x = bounds[3]
        val y = bounds[4]
        graphics.fill(x, y, x + w, y + h, POPUP_BACKGROUND)
        border(graphics, x, y, w, h, BORDER)
        for (row in 0 until rows) {
            val kind = current.kinds[row]
            val rowY = y + 2 + row * ROW_H
            val hover = inside(
                mouseX.toDouble(),
                mouseY.toDouble(),
                (x + 2).toDouble(),
                rowY.toDouble(),
                (w - 4).toDouble(),
                (ROW_H - 1).toDouble()
            )
            if (hover) graphics.fill(x + 2, rowY, x + w - 2, rowY + ROW_H - 1, HOVER_BACKGROUND)
            smallText(graphics, nodeLabel(kind).string, x + 5, rowY + 3, TEXT, w - 10)
        }
    }

    private fun focusInput(
        input: EditBox?,
        other: EditBox?,
        x: Double,
        y: Double,
        e: MouseButtonEvent,
        isDoubleClick: Boolean
    ): Boolean {
        if (input == null || !input.visible) return false
        if (!inside(x, y, input.x.toDouble(), input.y.toDouble(), input.width.toDouble(), 15.0)) return false
        other?.isFocused = false
        input.isFocused = true
        input.mouseClicked(e, isDoubleClick)
        return true
    }

    override fun mouseClicked(e: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        syncLayout()
        val x = e.x()
        val y = e.y()
        if (e.button() == 0) {
            if (focusInput(search, parameterInput, x, y, e, isDoubleClick)) return true
            if (focusInput(parameterInput, search, x, y, e, isDoubleClick)) return true
            search?.isFocused = false
            parameterInput?.isFocused = false
            if (handleQuickInsertClick(x, y) || handleTopBarClick(x, y) || handleRailClick(x, y)
                || handleInspectorClick(x, y) || handlePaletteClick(x, y)
            ) return true
            if (spaceDown && inside(
                    x,
                    y,
                    canvasX.toDouble(),
                    canvasY.toDouble(),
                    canvasW.toDouble(),
                    canvasH.toDouble()
                )
            ) {
                panning = true
                return true
            }
            if (handleCanvasClick(x, y)) return true
        }
        if ((e.button() == 1 || e.button() == 2)
            && inside(x, y, canvasX.toDouble(), canvasY.toDouble(), canvasW.toDouble(), canvasH.toDouble())
        ) {
            val endpoint = endpointAt(x, y)
            if (e.button() == 1 && endpoint != null) {
                disconnect(endpoint)
            } else {
                panning = true
            }
            return true
        }
        return super.mouseClicked(e, isDoubleClick)
    }

    override fun mouseDragged(e: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        if (connection != null) return true
        val dragging = draggingNode
        if (dragging != null) {
            val selected = node(dragging)
            if (selected != null) replaceNode(
                PrecisionGraph.Node(
                    selected.id(), selected.kind(), selected.parameter(),
                    selected.x() + mouseX / zoom, selected.y() + mouseY / zoom
                ), false
            )
            return true
        }
        if (panning) {
            panX += mouseX
            panY += mouseY
            return true
        }
        return super.mouseDragged(e, mouseX, mouseY)
    }

    override fun mouseReleased(e: MouseButtonEvent): Boolean {
        if (connection != null && e.button() == 0) {
            finishConnection(e.x(), e.y())
            draggingNode = null
            return true
        }
        if (draggingNode != null) changed()
        draggingNode = null
        panning = false
        return super.mouseReleased(e)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (paletteVisible() && inside(
                mouseX,
                mouseY,
                paletteX().toDouble(),
                canvasY.toDouble(),
                paletteWidth().toDouble(),
                canvasH.toDouble()
            )
        ) {
            paletteScroll = maxOf(0, paletteScroll - sign(scrollY).toInt())
            return true
        }
        if (inside(mouseX, mouseY, canvasX.toDouble(), canvasY.toDouble(), canvasW.toDouble(), canvasH.toDouble())) {
            zoomAt(mouseX, mouseY, zoom + sign(scrollY) * 0.1)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(e: KeyEvent): Boolean {
        if (search != null && search!!.isFocused) {
            search!!.keyPressed(e)
            return true
        }
        if (parameterInput != null && parameterInput!!.isFocused) {
            parameterInput!!.keyPressed(e)
            return true
        }
        if (e.key() == InputConstants.KEY_SPACE) {
            spaceDown = true
            return true
        }
        if (e.key() == InputConstants.KEY_ESCAPE && (connection != null || quickInsert != null)) {
            connection = null
            quickInsert = null
            return true
        }
        if (e.key() == InputConstants.KEY_DELETE) {
            deleteSelected()
            return true
        }
        if ((e.modifiers() and InputConstants.MOD_CONTROL) != 0) {
            if (e.key() == InputConstants.KEY_C) {
                copySelected()
                return true
            }
            if (e.key() == InputConstants.KEY_Z) {
                undo()
                return true
            }
            if (e.key() == InputConstants.KEY_Y) {
                redo()
                return true
            }
            if (e.key() == InputConstants.KEY_S) {
                save()
                return true
            }
        }
        return super.keyPressed(e)
    }

    override fun charTyped(e: CharacterEvent): Boolean {
        if (search != null && search!!.isFocused) {
            search!!.charTyped(e)
            return true
        }
        if (parameterInput != null && parameterInput!!.isFocused) {
            parameterInput!!.charTyped(e)
            return true
        }
        return super.charTyped(e)
    }

    override fun keyReleased(e: KeyEvent): Boolean {
        if (e.key() == InputConstants.KEY_SPACE) {
            spaceDown = false
            panning = false
            return true
        }
        return super.keyReleased(e)
    }

    override fun onClose() {
        PrecisionOperationClient.updateLocal(slot, graph)
        PrecisionOperationClient.closed(this)
        super.onClose()
    }

    private fun handleTopBarClick(mouseX: Double, mouseY: Double): Boolean {
        var x = panelX + 4 + (if (panelW >= 620) 96 else 0)
        val tabWidth = slotTabWidth()
        for (index in 0 until AbilityProgramManager.SLOT_COUNT) {
            if (inside(mouseX, mouseY, x.toDouble(), (panelY + 2).toDouble(), tabWidth.toDouble(), 16.0)) {
                PrecisionOperationClient.updateLocal(slot, graph)
                slot = index
                PrecisionOperationClient.selectSlot(slot)
                setGraph(PrecisionOperationClient.graph(slot), false)
                revision = PrecisionOperationClient.revision()
                fitCanvas(true)
                return true
            }
            x += tabWidth + 2
        }
        var toolsX = panelX + panelW - TOOL_LABELS.size * (TOOL_SIZE + 2) - 2
        for (index in TOOL_LABELS.indices) {
            if (inside(
                    mouseX,
                    mouseY,
                    toolsX.toDouble(),
                    (panelY + 3).toDouble(),
                    TOOL_SIZE.toDouble(),
                    TOOL_SIZE.toDouble()
                )
            ) {
                when (index) {
                    0 -> deleteSelected()
                    1 -> copySelected()
                    2 -> undo()
                    3 -> redo()
                    4 -> autoLayout()
                    5 -> fitCanvas(false)
                    6 -> save()
                    7 -> setGraph(PrecisionOperationClient.serverGraph(slot), true)
                    else -> {
                    }
                }
                return true
            }
            toolsX += TOOL_SIZE + 2
        }
        return false
    }

    private fun slotTabWidth(): Int {
        val startX = panelX + 4 + (if (panelW >= 620) 96 else 0)
        val toolsX = panelX + panelW - TOOL_LABELS.size * (TOOL_SIZE + 2) - 2
        val available = maxOf(0, toolsX - startX - 4)
        return (available / AbilityProgramManager.SLOT_COUNT - 2).coerceIn(14, 38)
    }

    private fun handleRailClick(mouseX: Double, mouseY: Double): Boolean {
        if (compactLeft && inside(mouseX, mouseY, (leftX + 1).toDouble(), (canvasY + 2).toDouble(), 16.0, 16.0)) {
            leftDrawerOpen = !leftDrawerOpen
            if (leftDrawerOpen) rightDrawerOpen = false
            updateSearchBounds()
            return true
        }
        if (compactRight && inside(mouseX, mouseY, (rightX + 1).toDouble(), (canvasY + 2).toDouble(), 16.0, 16.0)) {
            rightDrawerOpen = !rightDrawerOpen
            if (rightDrawerOpen) leftDrawerOpen = false
            updateSearchBounds()
            return true
        }
        return false
    }

    private fun handlePaletteClick(mouseX: Double, mouseY: Double): Boolean {
        if (!paletteVisible()) return false
        val x = paletteX()
        val w = paletteWidth()
        val tabY = canvasY + PALETTE_TAB_OFFSET_Y
        val tabW = maxOf(16, (w - 8) / PrecisionGraph.NodeGroup.entries.size)
        for (index in PrecisionGraph.NodeGroup.entries.indices) {
            if (inside(
                    mouseX,
                    mouseY,
                    (x + 4 + index * tabW).toDouble(),
                    tabY.toDouble(),
                    (tabW - 1).toDouble(),
                    12.0
                )
            ) {
                selectedGroup = PrecisionGraph.NodeGroup.entries[index]
                paletteScroll = 0
                return true
            }
        }
        val listY = canvasY + PALETTE_LIST_OFFSET_Y
        if (!inside(
                mouseX, mouseY, (x + 3).toDouble(), listY.toDouble(), (w - 6).toDouble(),
                (canvasY + canvasH - 3 - listY).toDouble()
            )
        ) return false
        val row = ((mouseY - listY) / ROW_H).toInt()
        val kinds = visibleKinds()
        val index = paletteScroll + row
        if (index in kinds.indices) {
            addNode(
                kinds[index], screenToGraphX(canvasX + canvasW / 2.0),
                screenToGraphY(canvasY + canvasH / 2.0), true
            )
            return true
        }
        return false
    }

    private fun parameterEditorLayout(selected: PrecisionGraph.Node): IntArray {
        val x = inspectorX() + 5
        val width = inspectorWidth() - 10
        val description = Component.translatable(nodeDescriptionKey(selected.kind()))
        val descriptionHeight = ceil(
            font.wordWrapHeight(description, (width / SMALL_TEXT_SCALE).toInt()).toDouble() * SMALL_TEXT_SCALE
        ).toInt()
        return intArrayOf(x, width, canvasY + maxOf(58, descriptionHeight + 40))
    }

    private fun handleInspectorClick(mouseX: Double, mouseY: Double): Boolean {
        if (!inspectorVisible()) return false
        val selected = node(selectedNode)
        if (selected == null || selected.kind().parameterKind() == PrecisionGraph.ParameterKind.NONE) return false
        val layout = parameterEditorLayout(selected)
        val x = layout[0]
        val w = layout[1]
        val y = layout[2]
        val parameterKind = selected.kind().parameterKind()
        if (parameterKind == PrecisionGraph.ParameterKind.DURATION_SECONDS
            || parameterKind == PrecisionGraph.ParameterKind.RANGE
        ) return false
        if (parameterKind == PrecisionGraph.ParameterKind.HEALTH_PERCENT
            && inside(mouseX, mouseY, x.toDouble(), (y + 9).toDouble(), w.toDouble(), 12.0)
        ) {
            val max = 100.0
            val value = 1.0 + ((mouseX - x) / maxOf(1.0, w.toDouble())).coerceIn(0.0, 1.0) * (max - 1.0)
            setParameter(selected, round(value))
            return true
        }
        if (inside(mouseX, mouseY, x.toDouble(), (y + 11).toDouble(), TOOL_SIZE.toDouble(), TOOL_SIZE.toDouble())) {
            adjustParameter(selected, -1)
            return true
        }
        if (inside(
                mouseX,
                mouseY,
                (x + 18).toDouble(),
                (y + 11).toDouble(),
                TOOL_SIZE.toDouble(),
                TOOL_SIZE.toDouble()
            )
        ) {
            adjustParameter(selected, 1)
            return true
        }
        return false
    }

    private fun handleCanvasClick(mouseX: Double, mouseY: Double): Boolean {
        if (!inside(
                mouseX,
                mouseY,
                canvasX.toDouble(),
                canvasY.toDouble(),
                canvasW.toDouble(),
                canvasH.toDouble()
            )
        ) return false
        quickInsert = null
        val endpoint = endpointAt(mouseX, mouseY)
        if (endpoint != null) {
            if (connection != null && compatible(connection!!.endpoint, endpoint)) {
                connect(connection!!.endpoint, endpoint)
                connection = null
            } else {
                connection = ConnectionDrag(endpoint, mouseX, mouseY)
                selectedNode = endpoint.nodeId
            }
            return true
        }
        for (node in graph.nodes().asReversed()) {
            if (insideHeader(mouseX, mouseY, node)) {
                selectedNode = node.id()
                pushUndo()
                draggingNode = node.id()
                return true
            }
            if (insideNode(mouseX, mouseY, node)) {
                selectedNode = node.id()
                connection = null
                return true
            }
        }
        selectedNode = -1
        connection = null
        return true
    }

    private fun finishConnection(mouseX: Double, mouseY: Double) {
        val current = connection ?: return
        val moved = hypot(mouseX - current.startX, mouseY - current.startY) > 3.0
        val target = snappedEndpoint(mouseX, mouseY, current.endpoint.type, current.endpoint.input)
        if (target != null && compatible(current.endpoint, target)) {
            connect(current.endpoint, target)
            connection = null
            return
        }
        if (moved) {
            val candidates = compatibleKinds(current.endpoint).take(12)
            if (candidates.isNotEmpty()) {
                quickInsert = QuickInsert(mouseX.toInt(), mouseY.toInt(), candidates, current.endpoint)
            }
            connection = null
        }
    }

    private fun handleQuickInsertClick(mouseX: Double, mouseY: Double): Boolean {
        val current = quickInsert ?: return false
        val bounds = quickInsertBounds(current)
        val rows = bounds[0]
        val w = bounds[1]
        val h = bounds[2]
        val x = bounds[3]
        val y = bounds[4]
        if (!inside(mouseX, mouseY, x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble())) {
            quickInsert = null
            return false
        }
        val row = ((mouseY - y - 2) / ROW_H).toInt()
        if (row in 0 until rows) {
            val kind = current.kinds[row]
            val anchor = current.anchor
            val node = addNode(kind, screenToGraphX(current.x.toDouble()), screenToGraphY(current.y.toDouble()), false)
            if (node != null) {
                val endpoint = firstCompatibleEndpoint(node, anchor)
                if (endpoint != null) connect(anchor, endpoint)
            }
            quickInsert = null
            return true
        }
        return true
    }

    private fun connect(first: Endpoint, second: Endpoint) {
        val output = if (first.input) second else first
        val input = if (first.input) first else second
        if (output.input || !input.input || output.type != input.type || output.nodeId == input.nodeId) {
            showTransient(PrecisionGraph.Diagnostic.TYPE_MISMATCH)
            return
        }
        val candidate = graphWithConnection(output, input)
        val result = candidate.validate()
        if (result.diagnostic() == PrecisionGraph.Diagnostic.FLOW_CYCLE
            || result.diagnostic() == PrecisionGraph.Diagnostic.CYCLE
        ) {
            showTransient(result.diagnostic())
            return
        }
        pushUndo()
        graph = candidate
        changed()
    }

    private fun graphWithConnection(output: Endpoint, input: Endpoint): PrecisionGraph {
        val edges = ArrayList(graph.edges())
        edges.removeIf { edge -> edge.toNode() == input.nodeId && edge.toPort() == input.port }
        if (output.type == PrecisionGraph.PortType.FLOW) {
            edges.removeIf { edge -> edge.fromNode() == output.nodeId && edge.fromPort() == output.port }
        }
        edges.add(PrecisionGraph.Edge(output.nodeId, output.port, input.nodeId, input.port))
        return PrecisionGraph(graph.nodes(), edges)
    }

    private fun disconnect(endpoint: Endpoint) {
        val filtered = graph.edges().filter { edge ->
            if (endpoint.input) edge.toNode() != endpoint.nodeId || edge.toPort() != endpoint.port
            else edge.fromNode() != endpoint.nodeId || edge.fromPort() != endpoint.port
        }
        if (filtered.size == graph.edges().size) return
        pushUndo()
        graph = PrecisionGraph(graph.nodes(), filtered)
        changed()
    }

    private fun addNode(kind: PrecisionGraph.NodeKind, x: Double, y: Double, autoChain: Boolean): PrecisionGraph.Node? {
        if (graph.nodes().size >= PrecisionGraph.MAX_NODES) {
            showTransient(PrecisionGraph.Diagnostic.TOO_MANY_NODES)
            return null
        }
        val id = (graph.nodes().maxOfOrNull { it.id() } ?: -1) + 1
        val node = PrecisionGraph.Node(id, kind, kind.defaultParameter(), x, y)
        pushUndo()
        val nodes = ArrayList(graph.nodes())
        nodes.add(node)
        val edges = ArrayList(graph.edges())
        if (autoChain && kind.isAction) {
            tailAction()?.let { tail ->
                edges.add(
                    PrecisionGraph.Edge(
                        tail.id(), tail.kind().flowOutputPort(), node.id(), kind.flowInputPort()
                    )
                )
            }
        }
        graph = PrecisionGraph(nodes, edges)
        selectedNode = id
        changed()
        return node
    }

    private fun tailAction(): PrecisionGraph.Node? {
        val sources = graph.edges().filter { edge ->
            val source = node(edge.fromNode())
            source != null && source.kind().isAction
                    && source.kind().outputDefinitions()[edge.fromPort()].type() == PrecisionGraph.PortType.FLOW
        }.map { it.fromNode() }.toSet()
        return graph.nodes().filter { it.kind().isAction }
            .filter { it.id() !in sources }
            .minByOrNull { it.id() }
    }

    private fun deleteSelected() {
        val selected = node(selectedNode) ?: return
        pushUndo()
        var before: PrecisionGraph.Edge? = null
        var after: PrecisionGraph.Edge? = null
        if (selected.kind().isAction) {
            for (edge in graph.edges()) {
                if (edge.toNode() == selected.id() && edge.toPort() == selected.kind().flowInputPort()) before = edge
                if (edge.fromNode() == selected.id() && edge.fromPort() == selected.kind().flowOutputPort()) after =
                    edge
            }
        }
        val edges = ArrayList(graph.edges().filter { edge ->
            edge.fromNode() != selected.id() && edge.toNode() != selected.id()
        })
        if (before != null && after != null) {
            val previous = node(before.fromNode())
            val next = node(after.toNode())
            if (previous != null && next != null) {
                edges.add(
                    PrecisionGraph.Edge(
                        previous.id(), previous.kind().flowOutputPort(), next.id(), next.kind().flowInputPort()
                    )
                )
            }
        }
        graph = PrecisionGraph(graph.nodes().filter { it.id() != selected.id() }, edges)
        selectedNode = -1
        changed()
    }

    private fun copySelected() {
        val selected = node(selectedNode) ?: return
        addNode(selected.kind(), selected.x() + 18, selected.y() + 18, selected.kind().isAction)
        val added = node(selectedNode)
        if (added != null && added.parameter() != selected.parameter()) {
            replaceNode(
                PrecisionGraph.Node(added.id(), added.kind(), selected.parameter(), added.x(), added.y()), true
            )
        }
    }

    private fun autoLayout() {
        if (graph.nodes().isEmpty()) return
        pushUndo()
        val incoming = HashMap<Int, MutableList<Int>>()
        for (edge in graph.edges()) {
            val from = node(edge.fromNode())
            if (from == null || from.kind().isAction) continue
            incoming.getOrPut(edge.toNode()) { ArrayList() }.add(edge.fromNode())
        }
        val layers = HashMap<Int, Int>()
        repeat(graph.nodes().size) {
            for (node in graph.nodes()) {
                if (node.kind().isAction) continue
                val layer = (incoming[node.id()] ?: emptyList()).maxOfOrNull { (layers[it] ?: 0) + 1 } ?: 0
                layers[node.id()] = layer
            }
        }
        val rows = HashMap<Int, Int>()
        val replacement = ArrayList<PrecisionGraph.Node>()
        for (node in graph.nodes().filter { !it.kind().isAction }
            .sortedBy { layers[it.id()] ?: 0 }) {
            val layer = layers[node.id()] ?: 0
            val row = (rows.merge(layer, 1) { a, b -> a + b } ?: 0) - 1
            replacement.add(
                PrecisionGraph.Node(
                    node.id(), node.kind(), node.parameter(),
                    8 + layer * 100.0, 8 + row * 42.0
                )
            )
        }
        val actions = orderedActions()
        for (index in actions.indices) {
            val node = actions[index]
            replacement.add(
                PrecisionGraph.Node(
                    node.id(), node.kind(), node.parameter(),
                    8 + ((layers.values.maxOrNull() ?: 0) + 1) * 100.0,
                    8 + index * 50.0
                )
            )
        }
        graph = PrecisionGraph(replacement, graph.edges())
        changed()
        fitCanvas(false)
    }

    private fun orderedActions(): List<PrecisionGraph.Node> {
        val validation = graph.validate()
        if (validation.valid()) {
            val byId = graph.nodes().associateBy { it.id() }
            return validation.actionOrder().mapNotNull { byId[it] }
        }
        return graph.nodes().filter { it.kind().isAction }.sortedBy { it.id() }
    }

    private fun fitCanvas(initial: Boolean) {
        if (graph.nodes().isEmpty()) {
            zoom = 1.0
            panX = 0.0
            panY = 0.0
            return
        }
        val minX = graph.nodes().minOfOrNull { it.x() } ?: 0.0
        val minY = graph.nodes().minOfOrNull { it.y() } ?: 0.0
        val maxX = graph.nodes().maxOfOrNull { it.x() + NODE_W } ?: NODE_W.toDouble()
        val maxY = graph.nodes().maxOfOrNull { it.y() + nodeHeight(it) } ?: MIN_NODE_H.toDouble()
        val fit = minOf(
            (canvasW - 20.0) / maxOf(1.0, maxX - minX),
            (canvasH - 20.0) / maxOf(1.0, maxY - minY)
        )
        zoom = fit.coerceIn(if (initial) 0.65 else MIN_ZOOM, if (initial) 1.0 else MAX_ZOOM)
        panX = (canvasW - (minX + maxX) * zoom) / 2.0
        panY = (canvasH - (minY + maxY) * zoom) / 2.0
    }

    private fun zoomAt(mouseX: Double, mouseY: Double, requested: Double) {
        val view = PrecisionEditorGeometry.zoomAt(
            mouseX, mouseY, canvasX.toDouble(), canvasY.toDouble(), panX, panY, zoom, requested
        )
        panX = view.panX()
        panY = view.panY()
        zoom = view.zoom()
    }

    private fun adjustParameter(node: PrecisionGraph.Node, direction: Int) {
        val kind = node.kind().parameterKind()
        val max = when (kind) {
            PrecisionGraph.ParameterKind.COUNT -> 8
            PrecisionGraph.ParameterKind.CAPABILITY -> ControlCapability.entries.size - 1
            PrecisionGraph.ParameterKind.SORT_DIRECTION -> 1
            PrecisionGraph.ParameterKind.ENTITY_TYPE -> 7
            PrecisionGraph.ParameterKind.OFFSET_DISTANCE -> 32
            else -> Int.MAX_VALUE
        }
        var value = node.parameter() + direction
        if (kind == PrecisionGraph.ParameterKind.CAPABILITY
            || kind == PrecisionGraph.ParameterKind.SORT_DIRECTION
            || kind == PrecisionGraph.ParameterKind.ENTITY_TYPE
        ) {
            if (value < 0.0) value = max.toDouble()
            if (value > max) value = 0.0
        }
        setParameter(node, value)
    }

    private fun setParameter(node: PrecisionGraph.Node, value: Double) {
        if (!node.kind().isParameterValid(value)) return
        pushUndo()
        replaceNode(PrecisionGraph.Node(node.id(), node.kind(), value, node.x(), node.y()), true)
    }

    private fun undo() {
        if (undo.isEmpty()) return
        redo.push(graph)
        graph = undo.pop()
        changed()
    }

    private fun redo() {
        if (redo.isEmpty()) return
        undo.push(graph)
        graph = redo.pop()
        changed()
    }

    private fun save() {
        if (!parameterInputValid) {
            showTransient(PrecisionGraph.Diagnostic.INVALID_PARAMETER)
            return
        }
        val validation = graph.validate()
        if (!validation.valid()) {
            showTransient(validation.diagnostic())
            return
        }
        PrecisionOperationClient.saveProgram(slot, if (graph.nodes().isEmpty()) null else document.program(), revision)
    }

    private fun pushUndo() {
        undo.push(graph)
        while (undo.size > 64) undo.removeLast()
        redo.clear()
    }

    private fun setGraph(next: PrecisionGraph?, recordUndo: Boolean) {
        if (recordUndo) pushUndo()
        graph = next ?: PrecisionGraph.EMPTY
        syncDocument()
        selectedNode = -1
        connection = null
        quickInsert = null
        if (recordUndo) {
            changed()
        } else {
            val validation = graph.validate()
            if (validation.valid()
                && PrecisionOperationClient.diagnostic(slot) != PrecisionGraph.Diagnostic.OK
            ) {
                diagnostic = PrecisionOperationClient.diagnostic(slot)
                diagnosticNodeId = PrecisionOperationClient.diagnosticNode(slot)
                diagnosticPort = PrecisionOperationClient.diagnosticPort(slot)
            } else {
                diagnostic = validation.diagnostic()
                diagnosticNodeId = validation.nodeId()
                diagnosticPort = validation.port()
            }
            PrecisionOperationClient.updateLocal(slot, graph)
        }
    }

    private fun replaceNode(replacement: PrecisionGraph.Node, notify: Boolean) {
        graph = PrecisionGraph(
            graph.nodes().map { node -> if (node.id() == replacement.id()) replacement else node },
            graph.edges()
        )
        if (notify) changed()
    }

    private fun changed() {
        val validation = graph.validate()
        diagnostic = validation.diagnostic()
        diagnosticNodeId = validation.nodeId()
        diagnosticPort = validation.port()
        PrecisionOperationClient.clearDiagnostic(slot)
        PrecisionOperationClient.updateLocal(slot, graph)
        syncDocument()
    }

    private fun syncDocument() {
        val imported = PrecisionProgramImporter.importEditableGraph(graph)
        if (!imported.valid()) return
        program = AbilityProgram(
            program.schemaVersion(),
            program.id(),
            program.name(),
            program.category(),
            imported.graph(),
            imported.editorLayout()
        )
        document = ProgramEditorDocument(program, AbilityProgramDefinitions.mentalout(), emptySet())
        PrecisionOperationClient.updateLocalProgram(slot, program)
    }

    fun document(): ProgramEditorDocument = document

    private fun visibleKinds(): List<PrecisionGraph.NodeKind> {
        val query = search?.value?.trim()?.lowercase(Locale.ROOT) ?: ""
        return PrecisionGraph.NodeKind.entries.filter { kind ->
            if (query.isEmpty()) return@filter kind.group() == selectedGroup
            val label = nodeLabel(kind).string.lowercase(Locale.ROOT)
            val description = Component.translatable(nodeDescriptionKey(kind)).string.lowercase(Locale.ROOT)
            val group = Component.translatable(groupKey(kind.group())).string.lowercase(Locale.ROOT)
            label.contains(query) || description.contains(query) || group.contains(query)
                    || kind.name.lowercase(Locale.ROOT).contains(query)
        }
    }

    private fun compatibleKinds(anchor: Endpoint): List<PrecisionGraph.NodeKind> {
        return PrecisionGraph.NodeKind.entries.filter { kind ->
            if (anchor.input) kind.outputDefinitions().any { PrecisionGraph.isPortCompatible(it.type(), anchor.type) }
            else kind.inputDefinitions().any { PrecisionGraph.isPortCompatible(anchor.type, it.type()) }
        }
    }

    private fun firstCompatibleEndpoint(node: PrecisionGraph.Node, anchor: Endpoint): Endpoint? {
        if (anchor.input) {
            for ((port, definition) in node.kind().outputDefinitions().withIndex()) {
                val type = definition.type()
                if (PrecisionGraph.isPortCompatible(type, anchor.type)) {
                    return Endpoint(node.id(), port, false, type)
                }
            }
        } else {
            for ((port, definition) in node.kind().inputDefinitions().withIndex()) {
                val type = definition.type()
                if (PrecisionGraph.isPortCompatible(anchor.type, type)) {
                    return Endpoint(node.id(), port, true, type)
                }
            }
        }
        return null
    }

    private fun endpointAt(mouseX: Double, mouseY: Double): Endpoint? {
        var closest: Endpoint? = null
        var best = Double.MAX_VALUE
        for (node in graph.nodes()) {
            val candidates = ArrayList<Endpoint>(
                node.kind().inputDefinitions().size + node.kind().outputDefinitions().size
            )
            for ((port, definition) in node.kind().inputDefinitions().withIndex()) {
                candidates.add(Endpoint(node.id(), port, true, definition.type()))
            }
            for ((port, definition) in node.kind().outputDefinitions().withIndex()) {
                candidates.add(Endpoint(node.id(), port, false, definition.type()))
            }
            for (endpoint in candidates) {
                val point = endpointScreen(endpoint)
                val distance = hypot(mouseX - point.x, mouseY - point.y)
                if (distance <= PORT_HIT / 2.0 && distance < best) {
                    closest = endpoint
                    best = distance
                }
            }
        }
        return closest
    }

    private fun snappedEndpoint(
        mouseX: Double,
        mouseY: Double,
        type: PrecisionGraph.PortType,
        sourceInput: Boolean
    ): Endpoint? {
        var closest: Endpoint? = null
        var best = Double.MAX_VALUE
        for (node in graph.nodes()) {
            val definitions = if (sourceInput) node.kind().outputDefinitions() else node.kind().inputDefinitions()
            for ((port, definition) in definitions.withIndex()) {
                val compatible = if (sourceInput) PrecisionGraph.isPortCompatible(definition.type(), type)
                else PrecisionGraph.isPortCompatible(type, definition.type())
                if (!compatible) continue
                val endpoint = Endpoint(node.id(), port, !sourceInput, definition.type())
                val point = endpointScreen(endpoint)
                val distance = hypot(mouseX - point.x, mouseY - point.y)
                if (distance <= SNAP_DISTANCE && distance < best) {
                    closest = endpoint
                    best = distance
                }
            }
        }
        return closest
    }

    private fun compatible(first: Endpoint, second: Endpoint): Boolean {
        if (first.nodeId == second.nodeId || first.input == second.input) return false
        val output = if (first.input) second else first
        val input = if (first.input) first else second
        return PrecisionGraph.isPortCompatible(output.type, input.type)
    }

    private fun connectionCreatesCycle(first: Endpoint, second: Endpoint): Boolean {
        if (!compatible(first, second)) return false
        val output = if (first.input) second else first
        val input = if (first.input) first else second
        val result = graphWithConnection(output, input).validate().diagnostic()
        return result == PrecisionGraph.Diagnostic.FLOW_CYCLE || result == PrecisionGraph.Diagnostic.CYCLE
    }

    private fun endpointConnected(endpoint: Endpoint): Boolean {
        return graph.edges().any { edge ->
            if (endpoint.input) edge.toNode() == endpoint.nodeId && edge.toPort() == endpoint.port
            else edge.fromNode() == endpoint.nodeId && edge.fromPort() == endpoint.port
        }
    }

    private fun portLabel(endpoint: Endpoint, definition: PrecisionGraph.PortDefinition): Component {
        return if (definition.type() == PrecisionGraph.PortType.FLOW) flowPortLabel(endpoint)
        else Component.translatable(portKey(definition.key()))
    }

    private fun flowPortLabel(endpoint: Endpoint): Component {
        val position = graph.flowPosition(endpoint.nodeId)
        val suffix = if (endpoint.input) {
            if (position.isOpenInput) "flow_input_open" else "flow_input"
        } else {
            if (position.isOpenOutput) "flow_output_open" else "flow_output"
        }
        return Component.translatable(portKey(suffix))
    }

    private fun highlightedPort(nodeId: Int, input: Boolean, type: PrecisionGraph.PortType): Boolean {
        val current = connection ?: return false
        return current.endpoint.input != input && current.endpoint.type == type
                && current.endpoint.nodeId != nodeId
    }

    private fun endpointScreen(endpoint: Endpoint): ScreenPoint {
        val node = node(endpoint.nodeId) ?: return ScreenPoint(0, 0)
        val x = if (endpoint.input) node.x() else node.x() + NODE_W
        val y = node.y() + if (endpoint.input) inputOffsetY(endpoint.port) else outputOffsetY(endpoint.port)
        return ScreenPoint(
            (canvasX + panX + x * zoom).roundToInt(),
            (canvasY + panY + y * zoom).roundToInt()
        )
    }

    private fun insideNode(mouseX: Double, mouseY: Double, node: PrecisionGraph.Node): Boolean {
        val x = canvasX + panX + node.x() * zoom
        val y = canvasY + panY + node.y() * zoom
        return inside(mouseX, mouseY, x, y, NODE_W * zoom, nodeHeight(node) * zoom)
    }

    private fun insideHeader(mouseX: Double, mouseY: Double, node: PrecisionGraph.Node): Boolean {
        val x = canvasX + panX + node.x() * zoom
        val y = canvasY + panY + node.y() * zoom
        return inside(mouseX, mouseY, x, y, NODE_W * zoom, NODE_HEADER_H * zoom)
    }

    private fun nodeHeight(node: PrecisionGraph.Node): Int {
        return maxOf(
            MIN_NODE_H,
            NODE_HEADER_H + maxOf(
                node.kind().inputDefinitions().size,
                node.kind().outputDefinitions().size
            ) * PORT_ROW_H + 3
        )
    }

    private fun screenToGraphX(screenX: Double): Double =
        PrecisionEditorGeometry.screenToGraph(screenX, canvasX.toDouble(), panX, zoom)

    private fun screenToGraphY(screenY: Double): Double =
        PrecisionEditorGeometry.screenToGraph(screenY, canvasY.toDouble(), panY, zoom)

    private fun paletteX(): Int = if (compactLeft) leftX + RAIL_W + 2 else leftX

    private fun paletteWidth(): Int = if (compactLeft) 104 else leftW

    private fun paletteVisible(): Boolean = !compactLeft || leftDrawerOpen

    private fun inspectorX(): Int = if (compactRight) rightX - 128 - 2 else rightX

    private fun inspectorWidth(): Int = if (compactRight) 128 else rightW

    private fun inspectorVisible(): Boolean = !compactRight || rightDrawerOpen

    private fun updateSearchBounds() {
        val box = search ?: return
        box.visible = paletteVisible()
        if (!box.visible) box.isFocused = false
        box.x = paletteX() + 4
        box.y = canvasY + PALETTE_SEARCH_OFFSET_Y
        box.width = paletteWidth() - 8
    }

    private fun syncParameterInput() {
        val box = parameterInput ?: return
        val selected = node(selectedNode)
        val visible = inspectorVisible() && selected != null
                && (selected.kind().parameterKind() == PrecisionGraph.ParameterKind.DURATION_SECONDS
                || selected.kind().parameterKind() == PrecisionGraph.ParameterKind.RANGE)
        box.visible = visible
        if (!visible) {
            box.isFocused = false
            parameterInputNode = -1
            parameterInputValid = true
            box.setTextColor(TEXT)
            return
        }

        val layout = parameterEditorLayout(selected)
        val x = layout[0]
        val width = layout[1]
        val parameterY = layout[2]
        box.x = x
        box.y = parameterY + 11
        box.width = width

        val duration = selected.kind().parameterKind() == PrecisionGraph.ParameterKind.DURATION_SECONDS
        box.setHint(
            Component.translatable(
                if (duration) "screen.academy.precision_operation.value.permanent"
                else "screen.academy.precision_operation.value.default_range"
            )
        )
        val defaultValue = if (duration) 0.0 else 32.0
        val expected = if (selected.parameter() == defaultValue) ""
        else (selected.parameter()).roundToLong().toString()
        if (parameterInputNode != selected.id() || (!box.isFocused && box.value != expected)) {
            updatingParameterInput = true
            box.value = expected
            updatingParameterInput = false
            parameterInputNode = selected.id()
            parameterInputValid = true
            box.setTextColor(TEXT)
        }
    }

    private fun parameterInputChanged(value: String) {
        if (updatingParameterInput) return
        val selected = node(parameterInputNode) ?: return
        val kind = selected.kind().parameterKind()
        if (kind != PrecisionGraph.ParameterKind.DURATION_SECONDS
            && kind != PrecisionGraph.ParameterKind.RANGE
        ) return
        val parsed = if (kind == PrecisionGraph.ParameterKind.DURATION_SECONDS) parseDurationSeconds(value)
        else parseRange(value)
        val valid = parsed.isPresent
        parameterInputValid = valid
        parameterInput!!.setTextColor(if (valid) TEXT else ERROR)
        if (valid && selected.parameter() != parsed.asInt.toDouble()) setParameter(selected, parsed.asInt.toDouble())
    }

    private fun formatParameter(node: PrecisionGraph.Node): String {
        return when (node.kind().parameterKind()) {
            PrecisionGraph.ParameterKind.NONE -> "-"
            PrecisionGraph.ParameterKind.RANGE -> (node.parameter()).roundToLong().toString() + " m"
            PrecisionGraph.ParameterKind.COUNT -> (node.parameter()).roundToLong().toString()
            PrecisionGraph.ParameterKind.HEALTH_PERCENT -> (node.parameter()).roundToLong().toString() + "%"
            PrecisionGraph.ParameterKind.DURATION_SECONDS ->
                if (node.parameter() == 0.0) Component.translatable("screen.academy.precision_operation.value.permanent").string
                else (node.parameter()).roundToLong().toString() + " s"

            PrecisionGraph.ParameterKind.OFFSET_DISTANCE -> (node.parameter()).roundToLong().toString() + " m"
            PrecisionGraph.ParameterKind.SORT_DIRECTION -> Component.translatable(
                if (node.parameter() == 0.0) "screen.academy.precision_operation.value.near_first"
                else "screen.academy.precision_operation.value.far_first"
            ).string

            PrecisionGraph.ParameterKind.ENTITY_TYPE -> Component.translatable(
                "screen.academy.precision_operation.value.entity_type." + node.parameter().toInt()
            ).string

            PrecisionGraph.ParameterKind.CAPABILITY -> Component.translatable(
                "screen.academy.precision_operation.value.capability." + node.parameter().toInt()
            ).string
        }
    }

    private fun node(id: Int): PrecisionGraph.Node? = graph.nodes().firstOrNull { it.id() == id }

    private fun nodeLabel(kind: PrecisionGraph.NodeKind): Component =
        Component.translatable("screen.academy.precision_operation.node." + kind.name.lowercase(Locale.ROOT))

    private fun showTransient(shown: PrecisionGraph.Diagnostic) {
        transientDiagnostic = shown
        transientUntil = System.currentTimeMillis() + 1800L
    }

    private fun smallText(
        graphics: GuiGraphicsExtractor,
        value: String,
        x: Int,
        y: Int,
        color: Int,
        maxWidth: Int
    ) {
        val clipped = font.plainSubstrByWidth(value, maxOf(1, (maxWidth / SMALL_TEXT_SCALE).toInt()))
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(SMALL_TEXT_SCALE, SMALL_TEXT_SCALE)
        graphics.text(font, clipped, 0, 0, color, false)
        pose.popMatrix()
    }

    private fun smallWrappedText(
        graphics: GuiGraphicsExtractor,
        value: Component,
        x: Int,
        y: Int,
        maxWidth: Int
    ): Int {
        val lines = font.split(value, maxOf(1, (maxWidth / SMALL_TEXT_SCALE).toInt()))
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(SMALL_TEXT_SCALE, SMALL_TEXT_SCALE)
        for (index in lines.indices) {
            graphics.text(font, lines[index], 0, index * 9, DIM, false)
        }
        pose.popMatrix()
        return ceil(lines.size * 9.0 * SMALL_TEXT_SCALE).toInt()
    }

    private fun button(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        label: Component,
        mouseX: Int,
        mouseY: Int,
        selected: Boolean,
        small: Boolean
    ) {
        val hover = inside(
            mouseX.toDouble(),
            mouseY.toDouble(),
            x.toDouble(),
            y.toDouble(),
            width.toDouble(),
            height.toDouble()
        )
        renderControl(graphics, x, y, width, height, true, selected, hover)
        if (small) {
            smallText(graphics, label.string, x + 3, y + 5, TEXT, width - 6)
        } else {
            graphics.centeredText(
                font, font.plainSubstrByWidth(label.string, width - 3),
                x + width / 2, y + maxOf(1, (height - 8) / 2), TEXT
            )
        }
    }

    private fun iconButton(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        glyph: String,
        mouseX: Int,
        mouseY: Int,
        disabled: Boolean
    ) {
        val hover = !disabled && inside(
            mouseX.toDouble(),
            mouseY.toDouble(),
            x.toDouble(),
            y.toDouble(),
            TOOL_SIZE.toDouble(),
            TOOL_SIZE.toDouble()
        )
        renderControl(graphics, x, y, TOOL_SIZE, TOOL_SIZE, !disabled, false, hover)
        smallText(graphics, glyph, x + 4, y + 4, if (disabled) DISABLED else if (hover) TEXT else DIM, 8)
    }

    private fun renderCanvasGrid(graphics: GuiGraphicsExtractor) {
        val firstColumn = floor(screenToGraphX(canvasX.toDouble()) / 16.0).toInt()
        val lastColumn = ceil(screenToGraphX((canvasX + canvasW).toDouble()) / 16.0).toInt()
        for (column in firstColumn..lastColumn) {
            val x = (canvasX + panX + column * 16.0 * zoom).roundToInt()
            graphics.fill(
                x,
                canvasY,
                x + 1,
                canvasY + canvasH,
                if (Math.floorMod(column, 4) == 0) GRID_MAJOR else GRID_MINOR
            )
        }
        val firstRow = floor(screenToGraphY(canvasY.toDouble()) / 16.0).toInt()
        val lastRow = ceil(screenToGraphY((canvasY + canvasH).toDouble()) / 16.0).toInt()
        for (row in firstRow..lastRow) {
            val y = (canvasY + panY + row * 16.0 * zoom).roundToInt()
            graphics.fill(
                canvasX,
                y,
                canvasX + canvasW,
                y + 1,
                if (Math.floorMod(row, 4) == 0) GRID_MAJOR else GRID_MINOR
            )
        }
    }

    private data class Endpoint(
        val nodeId: Int,
        val port: Int,
        val input: Boolean,
        val type: PrecisionGraph.PortType
    )

    private data class ConnectionDrag(
        val endpoint: Endpoint,
        val startX: Double,
        val startY: Double
    )

    private data class QuickInsert(
        val x: Int,
        val y: Int,
        val kinds: List<PrecisionGraph.NodeKind>,
        val anchor: Endpoint
    )

    private data class ScreenPoint(val x: Int, val y: Int)

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)

    companion object {
        const val NODE_W = 80
        const val NODE_HEADER_H = 11
        const val PORT_ROW_H = 8
        const val MIN_NODE_H = 26
        const val MIN_ZOOM = 0.5
        const val MAX_ZOOM = 1.6
        private const val SMALL_TEXT_SCALE = 0.75f
        private const val PANEL_BACKGROUND = 0x10000000
        private const val SECTION_BACKGROUND = 0x14000000
        private const val CANVAS_BACKGROUND = 0x28000000
        private const val CONTROL_BACKGROUND = 0x16000000
        private const val INPUT_BACKGROUND = 0x28000000
        private const val ROW_BACKGROUND = 0x14FFFFFF
        private const val HOVER_BACKGROUND = 0x2AFFFFFF
        private const val SELECTED_BACKGROUND = 0x30FFFFFF
        private const val NODE_BACKGROUND = 0xB00A0A0A.toInt()
        private const val NODE_SELECTED_BACKGROUND = 0xC0121C20.toInt()
        private const val NODE_HEADER = 0x24FFFFFF
        private const val POPUP_BACKGROUND = 0xE0101010.toInt()
        private const val BORDER = 0xD9FFFFFF.toInt()
        private const val BORDER_MUTED = 0x54FFFFFF
        private const val DIVIDER = 0x80FFFFFF.toInt()
        private const val GRID_MINOR = 0x0FFFFFFF
        private const val GRID_MAJOR = 0x20FFFFFF
        private const val ACCENT = 0xFFFF6C00.toInt()
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val DIM = 0xBFFFFFFF.toInt()
        private const val DISABLED = 0x33FFFFFF
        private const val ERROR = 0xFFFF5A66.toInt()
        private const val ERROR_BACKGROUND = 0xB030090D.toInt()
        private const val TOP_H = 20
        private const val RAIL_W = 18
        private const val ROW_H = 14
        private const val TOOL_SIZE = 14
        private const val PORT_HIT = 14
        private const val SNAP_DISTANCE = 10
        private const val PALETTE_TAB_OFFSET_Y = 16
        private const val PALETTE_SEARCH_OFFSET_Y = 31
        private const val PALETTE_LIST_OFFSET_Y = 49
        private val TOOL_LABELS = arrayOf(
            "delete", "copy", "undo", "redo", "auto_layout", "fit", "save", "restore"
        )
        private val TOOL_GLYPHS = arrayOf("X", "C", "<", ">", "A", "F", "S", "R")

        @JvmStatic
        fun parameterEditorHeight(kind: PrecisionGraph.ParameterKind): Int =
            if (kind == PrecisionGraph.ParameterKind.NONE) 0 else 32

        @JvmStatic
        fun isNumericInsertionAllowed(input: String): Boolean = input.all { Character.isDigit(it) }

        @JvmStatic
        fun parseDurationSeconds(value: String): OptionalInt {
            if (value.isEmpty()) return OptionalInt.of(0)
            return try {
                val parsed = value.toInt()
                if (parsed in 1..3600) OptionalInt.of(parsed) else OptionalInt.empty()
            } catch (_: NumberFormatException) {
                OptionalInt.empty()
            }
        }

        @JvmStatic
        fun parseRange(value: String): OptionalInt {
            if (value.isEmpty()) return OptionalInt.of(32)
            return try {
                val parsed = value.toInt()
                if (parsed in 1..32) OptionalInt.of(parsed) else OptionalInt.empty()
            } catch (_: NumberFormatException) {
                OptionalInt.empty()
            }
        }

        private fun nodeDescriptionKey(kind: PrecisionGraph.NodeKind): String =
            "screen.academy.precision_operation.node." + kind.name.lowercase(Locale.ROOT) + ".description"

        private fun portKey(key: String): String = "screen.academy.precision_operation.port.$key"

        private fun groupKey(group: PrecisionGraph.NodeGroup): String =
            "screen.academy.precision_operation.group." + group.name.lowercase(Locale.ROOT)

        private fun groupGlyph(group: PrecisionGraph.NodeGroup): String = when (group) {
            PrecisionGraph.NodeGroup.TARGET -> "T"
            PrecisionGraph.NodeGroup.COLLECTION -> "S"
            PrecisionGraph.NodeGroup.FILTER -> "F"
            PrecisionGraph.NodeGroup.MENTAL_ACTION -> "M"
            PrecisionGraph.NodeGroup.CONTROL_ACTION -> "C"
        }

        private fun categoryColor(category: PrecisionGraph.NodeCategory): Int = when (category) {
            PrecisionGraph.NodeCategory.SOURCE -> 0xFFFFFFFF.toInt()
            PrecisionGraph.NodeCategory.COLLECTION -> 0xD9FFFFFF.toInt()
            PrecisionGraph.NodeCategory.FILTER -> 0xBFFFFFFF.toInt()
            PrecisionGraph.NodeCategory.ACTION -> 0xF2FFFFFF.toInt()
            PrecisionGraph.NodeCategory.CONTROL -> 0xA6FFFFFF.toInt()
        }

        private fun portColor(type: PrecisionGraph.PortType): Int = when (type) {
            PrecisionGraph.PortType.ENTITY -> 0xE6FFFFFF.toInt()
            PrecisionGraph.PortType.ENTITY_SET -> 0xCFFFFFFF.toInt()
            PrecisionGraph.PortType.DESTINATION -> 0xB8FFFFFF.toInt()
            PrecisionGraph.PortType.FLOW -> ACCENT
            PrecisionGraph.PortType.DIRECTION -> 0x99FFFFFF.toInt()
        }

        private fun renderPort(
            graphics: GuiGraphicsExtractor,
            centerX: Int,
            centerY: Int,
            color: Int,
            openEnd: Boolean
        ) {
            graphics.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, color)
            if (openEnd) {
                graphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, 0xFF111111.toInt())
            }
        }

        private fun inputOffsetY(port: Int): Int = NODE_HEADER_H + 4 + port * PORT_ROW_H

        private fun outputOffsetY(port: Int): Int = NODE_HEADER_H + 4 + port * PORT_ROW_H

        private fun renderInstrumentFrame(
            graphics: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            width: Int,
            height: Int
        ) {
            graphics.fill(x + 4, y, x + width - 4, y + 1, BORDER)
            graphics.fill(x + 4, y + height - 1, x + width - 4, y + height, BORDER)
            graphics.fill(x, y + 4, x + 1, y + 18, BORDER_MUTED)
            graphics.fill(x, y + height - 18, x + 1, y + height - 4, BORDER_MUTED)
            graphics.fill(x + width - 1, y + 4, x + width, y + 18, BORDER_MUTED)
            graphics.fill(x + width - 1, y + height - 18, x + width, y + height - 4, BORDER_MUTED)
        }

        private fun renderSection(
            graphics: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            width: Int,
            height: Int
        ) {
            graphics.fill(x, y, x + width, y + height, SECTION_BACKGROUND)
            graphics.fill(x, y, x + width, y + 1, BORDER_MUTED)
            graphics.fill(x, y + height - 1, x + width, y + height, BORDER_MUTED)
        }

        private fun renderInput(
            graphics: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            width: Int,
            focused: Boolean
        ) {
            graphics.fill(x, y, x + width, y + 15, INPUT_BACKGROUND)
            graphics.fill(x, y + 14, x + width, y + 15, if (focused) ACCENT else BORDER_MUTED)
            if (focused) graphics.fill(x, y, x + 2, y + 15, ACCENT)
        }

        private fun renderControl(
            graphics: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            enabled: Boolean,
            selected: Boolean,
            hovered: Boolean
        ) {
            val background = if (!enabled) 0x0C000000
            else if (selected) SELECTED_BACKGROUND
            else if (hovered) HOVER_BACKGROUND else CONTROL_BACKGROUND
            graphics.fill(x, y, x + width, y + height, background)
            if (selected) {
                graphics.fill(x, y, x + 2, y + height, ACCENT)
                graphics.fill(x + 2, y + height - 1, x + width, y + height, ACCENT)
            } else if (hovered) {
                graphics.fill(x, y + height - 1, x + width, y + height, TEXT)
            } else {
                graphics.fill(x, y + height - 1, x + width, y + height, BORDER_MUTED)
            }
        }

        private fun orthogonalLine(
            graphics: GuiGraphicsExtractor,
            x1: Int,
            y1: Int,
            x2: Int,
            y2: Int,
            color: Int
        ) {
            val mid = (x1 + x2) / 2
            line(graphics, x1, y1, mid, y1, color)
            line(graphics, mid, y1, mid, y2, color)
            line(graphics, mid, y2, x2, y2, color)
        }

        private fun line(graphics: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
            graphics.fill(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2) + 1, maxOf(y1, y2) + 1, color)
        }

        private fun border(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
            graphics.fill(x, y, x + width, y + 1, color)
            graphics.fill(x, y + height - 1, x + width, y + height, color)
            graphics.fill(x, y, x + 1, y + height, color)
            graphics.fill(x + width - 1, y, x + width, y + height, color)
        }

        private fun inside(x: Double, y: Double, left: Double, top: Double, width: Double, height: Double): Boolean {
            return x >= left && x < left + width && y >= top && y < top + height
        }

        private fun rect(widget: Widget): Rect = Rect(
            (widget.getAbsoluteX()).roundToInt(),
            (widget.getAbsoluteY()).roundToInt(),
            (widget.width).roundToInt(),
            (widget.height).roundToInt()
        )
    }
}
