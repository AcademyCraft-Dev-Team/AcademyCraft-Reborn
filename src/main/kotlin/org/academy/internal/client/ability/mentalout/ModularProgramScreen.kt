package org.academy.internal.client.ability.mentalout

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.locale.Language
import org.academy.api.client.ability.program.ProgramNodePalette
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.widget.*
import org.academy.api.common.ability.program.*
import org.academy.internal.client.ability.program.ProgramClipboardCodec
import org.academy.internal.client.ability.program.ProgramConfigurationOptions
import org.academy.internal.client.ability.program.ProgramDiagnosticText
import org.academy.internal.client.ability.program.ProgramStarterTemplates
import org.academy.internal.client.ability.program.ProgramTaskTemplates
import org.academy.internal.client.ability.program.ProgramTaskPreview
import org.academy.internal.client.ability.program.ProgramPaletteCatalog
import org.academy.internal.client.ability.program.ProgramEditorMetrics
import org.academy.internal.client.ability.program.ProgramEditorCachePolicy
import org.academy.internal.client.ability.program.ProgramUpgradeBackup
import org.academy.api.common.profiler.AcademyProfiler
import org.academy.internal.client.gui.layout.ProgramEditorLayout
import org.academy.internal.client.gui.layout.ProgramEditorVariant
import org.academy.internal.client.gui.layout.buildProgramEditorLayout
import org.academy.internal.common.ability.AbilityCategoryNames
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph
import org.academy.internal.common.ability.mentalout.precision.PrecisionOperationManager
import org.academy.internal.common.ability.program.*
import org.academy.internal.common.ability.program.editor.ProgramEditorDocument
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog
import org.academy.internal.common.ability.program.migrate.ProgramNumericUpgrades
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinition
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions
import org.academy.internal.common.ability.program.registry.CommonProgramNodeCatalog
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds
import org.academy.internal.common.ability.program.registry.PrecisionProgramNodeIds
import java.math.BigDecimal
import java.io.IOException
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sign

class ModularProgramScreen(private val session: ModularProgramEditorSession) :
    UiScreen(session.title()) {
    private val definition: AbilityProgramDefinition
    private val catalog: ProgramEditorNodeCatalog
    private val capabilities: Set<Identifier>
    private val accentColor: Int
    private var document: ProgramEditorDocument
    private var revision: Long
    private var slot: Int
    private val performanceMetrics = ProgramEditorMetrics()
    // Screen-local semantic snapshots only: never retain Canvas, glyphs, textures or VM/world results.
    private var cachedDocument: ProgramEditorDocument? = null
    private var cachedLanguage: Language? = null
    private var cachedExtensions: ProgramNodeExtensionIndex.Snapshot? = null
    private var cachedPaletteRevision = -1L
    private var semanticCacheAllowed = false
    private var cachedNodes: List<NodeView>? = null
    private var cachedNodesById: Map<Int, NodeView?> = emptyMap()
    private var cachedValidation: ProgramValidationResult? = null
    private var cachedPaletteEntries: List<ProgramEditorNodeCatalog.Entry>? = null
    private var cachedRootCards: List<ProgramPaletteCatalog.Card>? = null
    private var cachedCardQuery: String? = null
    private var cachedCardLayer: ProgramPaletteCatalog.Layer? = null
    private val undo = ArrayDeque<AbilityProgram>()
    private val redo = ArrayDeque<AbilityProgram>()
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
    private var starterDismissed = false
    private var taskTemplatesOpen = false
    private var search: TextInputWidget? = null
    private val configurationInputs = HashMap<String, TextInputWidget>()
    private val configurationInputValidity = HashMap<String, Boolean>()
    private var configurationNode = -1
    private var updatingConfigurationInput = false
    private var draggingPowerField: String? = null
    private var draggingPowerNode = -1
    private var selectedGroup: ProgramEditorNodeCatalog.Group = ProgramEditorNodeCatalog.Group.TARGET
    private var paletteLayer = ProgramPaletteCatalog.Layer.COMMON
    private var paletteFamilyId: String? = null
    private var paletteScroll = 0
    private var selectedNode = -1
    private var inspectorPortScroll = 0
    private val selectedNodes = LinkedHashSet<Int>()
    private var draggingNode: Int? = null
    private var selectionDrag: SelectionDrag? = null
    private var panning = false
    private var spaceDown = false
    private var panX = 0.0
    private var panY = 0.0
    private var zoom = 1.0
    private var initialView = true
    private var connection: ConnectionDrag? = null
    private var quickInsert: QuickInsert? = null
    private var numericUpgradePreview: ProgramNumericUpgrades.Plan? = null
    private var numericUpgradeScroll = 0
    private var numericUpgradeSlot = -1
    private var numericUpgradeRevision = -1L
    private var numericUpgradeSessionRevision = -1L
    private var numericUpgradeExtensions: ProgramNodeExtensionIndex.Snapshot? = null
    private var numericUpgradeError: Component? = null
    private var numericUpgradeLastBackup: ProgramUpgradeBackup.Receipt? = null
    private var numericUpgradeCachedPlan: ProgramNumericUpgrades.Plan? = null
    private var numericUpgradeCachedError: Component? = null
    private var numericUpgradeCachedLanguage: Language? = null
    private var numericUpgradeCachedExtensions: ProgramNodeExtensionIndex.Snapshot? = null
    private var numericUpgradeCachedWidth = -1
    private var numericUpgradeCachedLines: List<String> = emptyList()
    private var serverDiagnostic: PrecisionGraph.Diagnostic = PrecisionGraph.Diagnostic.OK
    private var serverCompileDiagnostic: ProgramDiagnostic? = null
    private var transientDetail: Component? = null
    private var serverVmDiagnostic: ProgramVmDiagnostic = ProgramVmDiagnostic.NONE
    private var serverVmPort: String? = null
    private var serverVmDetail: String? = null
    private var serverDiagnosticNode = -1
    private var runTrace: List<ProgramRunTrace.Step> = emptyList()
    private var runTraceTruncated = false
    private var showRunTrace = false
    private var showTaskPreview = false
    private var previewScroll = 0
    private var previewDocument: ProgramEditorDocument? = null
    private var previewLanguage: Language? = null
    private var previewExtensions: ProgramNodeExtensionIndex.Snapshot? = null
    private var previewPaletteRevision = -1L
    private var previewWidth = -1
    private var previewLines: List<PreviewLine> = emptyList()
    private var statusCached: ProgramRunStatus? = null
    private var statusDocument: ProgramEditorDocument? = null
    private var statusSaved: AbilityProgram? = null
    private var statusLanguage: Language? = null
    private var statusWidth = -1
    private var statusLines: List<String> = emptyList()
    private var runTraceScroll = 0
    private var transientDiagnostic: PrecisionGraph.Diagnostic = PrecisionGraph.Diagnostic.OK
    private var transientUntil = 0L
    private lateinit var layout: ProgramEditorLayout
    private var editorSurface: EditorSurface? = null
    private lateinit var inputLayer: FrameLayoutWidget
    private var tooltipSurface: TooltipSurface? = null

    init {
        require(session.slotCount() >= 1) { "Program editor session needs at least one slot" }
        slot = session.selectedSlot().coerceIn(0, session.slotCount() - 1)
        val program = session.editableProgram(slot)
        accentColor = categoryAccent(program.category())
        definition = AbilityProgramDefinitions.require(program.category())
        catalog = definition.editorCatalog()
        capabilities = session.capabilities().toSet()
        document = document(program)
        revision = session.revision()
        runTrace = session.lastRunTrace(slot)
        runTraceTruncated = session.lastRunTraceTruncated(slot)
    }

    override fun onInit() {
        cachedDocument = null
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

        editorSurface = EditorSurface()
        editorSurface!!.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        root.addChild("editor_surface", editorSurface!!)

        inputLayer = FrameLayoutWidget()
        inputLayer.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        root.addChild("input_layer", inputLayer)

        search = object : TextInputWidget(64) {
            override fun renderInternal(context: Canvas) {
                context.clipRect(0f, 0f, width, height)
                try {
                    super.renderInternal(context)
                } finally {
                    context.disableScissor()
                }
            }
        }
        search!!.textSize = ProgramUiGraphics.BODY_FONT_SIZE
        search!!.hint = Component.translatable("screen.academy.precision_operation.search").string
        search!!.background = null
        search!!.textColor = TEXT
        search!!.setOnTextChanged {
            paletteFamilyId = null
            paletteScroll = 0
        }
        setTextBoxBounds(
            search!!, paletteX() + 4, canvasY + PALETTE_SEARCH_OFFSET_Y,
            paletteWidth() - 8
        )
        search!!.visibility = if (paletteVisible()) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
        inputLayer.addChild("search_input", search!!)

        tooltipSurface = TooltipSurface()
        tooltipSurface!!.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        root.addChild("tooltip_surface", tooltipSurface!!)

        clearConfigurationInputs()
        if (initialView) {
            initialView = false
            if (nodes().isNotEmpty()) fitCanvas(true)
        }
    }

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
        closeNumericUpgradePreview()
        revision = serverRevision
        if (slot != selectedSlot) return
        setProgram(serverProgram, false)
    }

    fun applyProgramResult(
        resultSlot: Int,
        serverRevision: Long,
        diagnostic: ProgramDiagnosticCode?,
        nodeId: Int,
        vmDiagnostic: ProgramVmDiagnostic?,
        clearDiagnostic: Boolean,
        diagnosticPort: String?,
        diagnosticDetail: String?
    ) {
        revision = maxOf(revision, serverRevision)
        if (slot != resultSlot) return
        serverCompileDiagnostic = if (diagnostic == null) null
        else ProgramDiagnostic(diagnostic, nodeId, diagnosticPort)
        if (diagnostic != null) {
            serverDiagnostic = mapDiagnostic(diagnostic)
            serverVmDiagnostic = ProgramVmDiagnostic.NONE
            serverVmPort = null
            serverVmDetail = null
            serverDiagnosticNode = nodeId
            transientUntil = 0L
        } else if (vmDiagnostic != null && vmDiagnostic != ProgramVmDiagnostic.NONE) {
            serverDiagnostic = PrecisionGraph.Diagnostic.ACTION_FAILED
            serverVmDiagnostic = vmDiagnostic
            serverVmPort = diagnosticPort
            serverVmDetail = diagnosticDetail
            serverDiagnosticNode = nodeId
            transientUntil = 0L
        } else if (clearDiagnostic) {
            serverDiagnostic = PrecisionGraph.Diagnostic.OK
            serverVmDiagnostic = ProgramVmDiagnostic.NONE
            serverVmPort = null
            serverVmDetail = null
            serverDiagnosticNode = -1
        }
    }

    fun applyRunTrace(resultSlot: Int, steps: List<ProgramRunTrace.Step>, truncated: Boolean) {
        if (slot != resultSlot) return
        runTrace = steps.toList()
        runTraceTruncated = truncated
        runTraceScroll = 0
    }

    fun applyResult(
        resultSlot: Int,
        type: PrecisionOperationManager.FeedbackType,
        serverRevision: Long,
        result: PrecisionGraph.Diagnostic,
        nodeId: Int
    ) {
        revision = maxOf(revision, serverRevision)
        if (slot != resultSlot) return
        if (type == PrecisionOperationManager.FeedbackType.ERROR) {
            serverDiagnostic = result
            serverVmDiagnostic = ProgramVmDiagnostic.NONE
            serverVmPort = null
            serverVmDetail = null
            serverDiagnosticNode = nodeId
            showTransient(result)
        } else if (type == PrecisionOperationManager.FeedbackType.STARTED
            || type == PrecisionOperationManager.FeedbackType.COMPLETED
            && session.diagnostic(slot) == PrecisionGraph.Diagnostic.OK
        ) {
            serverDiagnostic = PrecisionGraph.Diagnostic.OK
            serverVmDiagnostic = ProgramVmDiagnostic.NONE
            serverVmPort = null
            serverVmDetail = null
            serverDiagnosticNode = -1
        }
    }

    override fun extractRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        a: Float
    ) {
        syncLayout()
        syncConfigurationInputs()
        editorSurface?.invalidate()
        tooltipSurface?.invalidate()
        super.extractRenderState(graphics, mouseX, mouseY, a)
    }

    override fun tick() {
        super.tick()
        session.observeRunStatus(this, slot, showRunTrace && inspectorVisible())
    }

    // A visible run monitor must not freeze its integrated server while it is waiting.
    override fun isPauseScreen(): Boolean = !showRunTrace

    override fun removed() {
        closeNumericUpgradePreview()
        session.closed(this)
        super.removed()
    }

    private inner class EditorSurface : AbstractWidget() {
        override fun renderInternal(context: Canvas) {
            val started = performanceMetrics.beginFrame()
            try {
                profileEditor("precision.editor.render.surface") {
                    super.renderInternal(context)
                    syncLayout()
                    val minecraft = Minecraft.getInstance()
                    val window = minecraft.window
                    val mouseX = renderMouseCoordinate(minecraft.mouseHandler.getScaledXPos(window))
                    val mouseY = renderMouseCoordinate(minecraft.mouseHandler.getScaledYPos(window))
                    val graphics = ProgramUiGraphics(context)
                    renderStructure(graphics)
                    renderTopBar(graphics, mouseX, mouseY)
                    renderCanvas(graphics, mouseX, mouseY)
                    renderDrawers(graphics)
                    renderRails(graphics, mouseX, mouseY)
                    if (paletteVisible()) renderPalette(graphics, mouseX, mouseY)
                    if (inspectorVisible()) renderInspector(graphics, mouseX, mouseY)
                    renderStatus(graphics)
                    renderQuickInsert(graphics, mouseX, mouseY)
                    renderNumericUpgradePreview(graphics, mouseX, mouseY)
                }
            } finally {
                performanceMetrics.endFrame(started)
            }
        }
    }

    private inner class TooltipSurface : AbstractWidget() {
        override fun renderInternal(context: Canvas) {
            super.renderInternal(context)
            if (numericUpgradePreview != null) return
            val minecraft = Minecraft.getInstance()
            val window = minecraft.window
            val mouseX = renderMouseCoordinate(minecraft.mouseHandler.getScaledXPos(window))
            val mouseY = renderMouseCoordinate(minecraft.mouseHandler.getScaledYPos(window))
            renderTooltip(ProgramUiGraphics(context), mouseX, mouseY)
        }
    }

    private fun renderStructure(graphics: ProgramUiGraphics) {
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BACKGROUND)
        renderInstrumentFrame(graphics, panelX, panelY, panelW, panelH)
        graphics.fill(panelX + 7, panelY + TOP_H, panelX + panelW - 7, panelY + TOP_H + 1, DIVIDER)
        graphics.fill(panelX + 7, panelY + TOP_H, panelX + 31, panelY + TOP_H + 1, accentColor)
        graphics.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, CANVAS_BACKGROUND)
        border(graphics, canvasX, canvasY, canvasW, canvasH, BORDER_MUTED)
        if (!compactLeft) renderSection(graphics, paletteX(), canvasY, paletteWidth(), canvasH)
        if (!compactRight) renderSection(graphics, inspectorX(), canvasY, inspectorWidth(), canvasH)
    }

    private fun renderDrawers(graphics: ProgramUiGraphics) {
        if (compactLeft && leftDrawerOpen) {
            renderSection(graphics, paletteX(), canvasY, paletteWidth(), canvasH)
        }
        if (compactRight && rightDrawerOpen) {
            renderSection(graphics, inspectorX(), canvasY, inspectorWidth(), canvasH)
        }
    }

    private fun renderTopBar(graphics: ProgramUiGraphics, mouseX: Int, mouseY: Int) {
        var x = panelX + 4
        if (panelW >= 620) {
            headingText(graphics, title.string, panelX + 12, panelY + 5, TEXT, 84)
            x += 96
        }
        val slotWidth = slotTabWidth(x)
        for (index in 0 until session.slotCount()) {
            button(
                graphics, x, panelY + 2, slotWidth, 16,
                Component.translatable("screen.academy.precision_operation.slot", index + 1),
                mouseX, mouseY, index == slot, true
            )
            x += slotWidth + 2
        }
        var toolsX = panelX + panelW - TOOL_LABELS.size * (TOOL_SIZE + 2) - 2
        for (index in TOOL_LABELS.indices) {
            val disabled = index == 9 && (!editorValidation().valid() || !configurationInputsValid())
            iconButton(graphics, toolsX, panelY + 3, TOOL_GLYPHS[index], mouseX, mouseY, disabled)
            toolsX += TOOL_SIZE + 2
        }
    }

    private fun renderRails(graphics: ProgramUiGraphics, mouseX: Int, mouseY: Int) {
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

    private fun renderPalette(graphics: ProgramUiGraphics, mouseX: Int, mouseY: Int) {
        val x = paletteX()
        val width = paletteWidth()
        headingText(graphics, Component.translatable("screen.academy.precision_operation.nodes").string,
            x + 4, canvasY + 3, DIM, width - 44)
        if (!session.precisionRules()) {
            val enabled = taskSlotEmpty()
            val hover = enabled && inside(mouseX.toDouble(), mouseY.toDouble(),
                (x + width - 37).toDouble(), (canvasY + 1).toDouble(), 33.0, 12.0)
            renderControl(graphics, x + width - 37, canvasY + 1, 33, 12, enabled, taskTemplatesOpen, hover)
            graphics.centeredText(Component.translatable("screen.academy.program.task_template.open").string,
                x + width - 20.5f, canvasY + 3f, if (enabled) TEXT else DIM,
                ProgramUiGraphics.BODY_FONT_SIZE, 30f)
        }
        val layers = ProgramPaletteCatalog.Layer.entries
        val layerW = (width - 8) / layers.size
        for (index in layers.indices) {
            button(graphics, x + 4 + index * layerW, canvasY + PALETTE_LAYER_OFFSET_Y,
                layerW - 1, 12, Component.translatable(layers[index].translationKey()),
                mouseX, mouseY, paletteLayer == layers[index], false)
        }
        val family = selectedPaletteFamily()
        if (family != null) {
            button(graphics, x + 4, canvasY + PALETTE_TAB_OFFSET_Y, width - 8, 12,
                Component.translatable("screen.academy.program.palette.back", cardLabel(family)),
                mouseX, mouseY, false, false)
        } else if (paletteLayer == ProgramPaletteCatalog.Layer.COMMON) {
            smallText(graphics, Component.translatable("screen.academy.program.palette.common_hint").string,
                x + 4, canvasY + PALETTE_TAB_OFFSET_Y + 3, DIM, width - 8)
        } else {
            val groups = ProgramEditorNodeCatalog.Group.entries
            val tabW = maxOf(11, (width - 8) / groups.size)
            for (index in groups.indices) {
                val group = groups[index]
                button(graphics, x + 4 + index * tabW, canvasY + PALETTE_TAB_OFFSET_Y,
                    tabW - 1, 12, Component.literal(groupGlyph(group)), mouseX, mouseY,
                    group == selectedGroup, false)
            }
        }
        renderInput(graphics, search!!.x.roundToInt(), search!!.y.roundToInt(),
            search!!.width.roundToInt(), search!!.isFocused)
        val cards = paletteCards()
        val listY = canvasY + PALETTE_LIST_OFFSET_Y
        val listBottom = canvasY + canvasH - 3
        val visibleRows = maxOf(1, (listBottom - listY) / ROW_H)
        paletteScroll = paletteScroll.coerceIn(0, maxOf(0, cards.size - visibleRows))
        for (row in 0 until minOf(visibleRows, cards.size - paletteScroll)) {
            val card = cards[paletteScroll + row]
            val y = listY + row * ROW_H
            val hover = inside(mouseX.toDouble(), mouseY.toDouble(), (x + 3).toDouble(),
                y.toDouble(), (width - 6).toDouble(), (ROW_H - 1).toDouble())
            graphics.fill(x + 3, y, x + width - 3, y + ROW_H - 1,
                if (hover) HOVER_BACKGROUND else ROW_BACKGROUND)
            graphics.fill(x + 3, y, x + 5, y + ROW_H - 1, groupColor(card.group()))
            smallText(graphics, groupGlyph(card.group()), x + 8, y + 3, groupColor(card.group()), 8)
            smallText(graphics, cardLabel(card), x + 17, y + 3, TEXT, width - 34)
            if (card.family()) {
                smallText(graphics, ">", x + width - 12, y + 3, DIM, 8)
            } else if (card.members().first().categoryRestricted()) {
                smallText(graphics, categoryGlyph(card.members().first()), x + width - 13, y + 3, accentColor, 8)
            }
        }
        if (cards.isEmpty()) {
            smallText(graphics, Component.translatable("screen.academy.program.palette.empty").string,
                x + 4, listY + 3, DIM, width - 8)
        }
        if (cards.size > visibleRows) {
            val trackH = listBottom - listY
            val thumbH = maxOf(8, trackH * visibleRows / cards.size)
            val thumbY = listY + (trackH - thumbH) * paletteScroll / maxOf(1, cards.size - visibleRows)
            graphics.fill(x + width - 3, listY, x + width - 1, listBottom, CONTROL_BACKGROUND)
            graphics.fill(x + width - 3, thumbY, x + width - 1, thumbY + thumbH, DIM)
        }
    }

    private fun renderCanvas(graphics: ProgramUiGraphics, mouseX: Int, mouseY: Int) {
        profileEditor("precision.editor.render.canvas") {
            graphics.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH)
            renderCanvasGrid(graphics)
            val pose = graphics.pose()
            pose.pushPose()
            pose.translate((canvasX + panX).toFloat(), (canvasY + panY).toFloat())
            pose.scale(zoom.toFloat(), zoom.toFloat())
            for (edge in document.program().graph().edges()) renderEdge(graphics, edge)
            for (node in nodes()) renderNode(graphics, node)
            pose.popPose()
            if (showStarter()) {
                renderStarterChoices(graphics, mouseX, mouseY)
            }
            renderSelectionDrag(graphics)
            renderConnectionPreview(graphics, mouseX, mouseY)
            graphics.disableScissor()
            smallText(
                graphics, (zoom * 100.0).roundToLong().toString() + "%",
                canvasX + 3, canvasY + canvasH - 10, DIM, 40
            )
        }
    }

    private fun starterTasks(): List<ProgramTaskTemplates.Kind> =
        ProgramTaskTemplates.available(definition, capabilities)

    private fun starterBounds(): Rect {
        val width = minOf(260, canvasW - 24).coerceAtLeast(1)
        val height = 110 + starterTasks().size * 19
        return Rect(canvasX + (canvasW - width) / 2, canvasY + maxOf(0, (canvasH - height) / 2),
            width, height)
    }

    private fun openTaskTemplates(): Boolean {
        if (session.precisionRules() || !taskSlotEmpty()) return false
        taskTemplatesOpen = true
        leftDrawerOpen = false
        rightDrawerOpen = false
        updateSearchBounds()
        return true
    }

    private fun showStarter(): Boolean = (taskTemplatesOpen || (!starterDismissed && session.showStarter()))
        && !session.precisionRules() && taskSlotEmpty()

    private fun taskSlotEmpty(): Boolean = document.program().graph().nodes().isEmpty()
        && document.program().graph().edges().isEmpty()

    private fun starterDismissBounds(permanent: Boolean): Rect {
        val bounds = starterBounds()
        val closeWidth = minOf(56, (bounds.width - 20) / 3)
        return if (permanent) Rect(bounds.x + 13 + closeWidth, bounds.y + bounds.height - 23,
            bounds.width - closeWidth - 20, 16)
        else Rect(bounds.x + 7, bounds.y + bounds.height - 23, closeWidth, 16)
    }

    private fun starterRowLabel(index: Int): Component {
        val tasks = starterTasks()
        if (index < tasks.size) {
            val label = Component.translatable(tasks[index].translationKey())
            return if (tasks[index].advanced()) Component.translatable("screen.academy.program.inspector.advanced", label) else label
        }
        val key = if (index == tasks.size) "manual" else "chat"
        return Component.translatable("screen.academy.program.starter.$key")
    }

    private fun renderStarterChoices(graphics: ProgramUiGraphics, mouseX: Int, mouseY: Int) {
        val bounds = starterBounds()
        graphics.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, POPUP_BACKGROUND)
        border(graphics, bounds.x, bounds.y, bounds.width, bounds.height, BORDER)
        smallText(graphics, Component.translatable("screen.academy.program.task_template.title").string,
            bounds.x + 8, bounds.y + 7, TEXT, bounds.width - 16)
        val rowCount = starterTasks().size + 2
        for (index in 0 until rowCount) {
            val rowY = bounds.y + 25 + index * 19
            val hovered = inside(mouseX.toDouble(), mouseY.toDouble(),
                (bounds.x + 7).toDouble(), rowY.toDouble(), (bounds.width - 14).toDouble(), 16.0)
            graphics.fill(bounds.x + 7, rowY, bounds.x + bounds.width - 7, rowY + 16,
                if (hovered) HOVER_BACKGROUND else NODE_BACKGROUND)
            graphics.fill(bounds.x + 7, rowY, bounds.x + 9, rowY + 16,
                if (hovered) accentColor else BORDER_MUTED)
            smallText(graphics, starterRowLabel(index).string,
                bounds.x + 15, rowY + 4, if (hovered) TEXT else DIM, bounds.width - 30)
        }
        smallText(graphics, Component.translatable("screen.academy.program.task_template.hint").string,
            bounds.x + 8, bounds.y + bounds.height - 41, DIM, bounds.width - 16)
        for (permanent in listOf(false, true)) {
            val control = starterDismissBounds(permanent)
            val key = if (permanent) "dismiss_world" else "close"
            button(graphics, control.x, control.y, control.width, control.height,
                Component.translatable("screen.academy.program.starter.$key"), mouseX, mouseY, false, false)
        }
    }

    private fun handleStarterClick(mouseX: Double, mouseY: Double): Boolean {
        if (!showStarter()) return false
        for (permanent in listOf(false, true)) {
            val control = starterDismissBounds(permanent)
            if (!inside(mouseX, mouseY, control.x.toDouble(), control.y.toDouble(),
                    control.width.toDouble(), control.height.toDouble())) continue
            starterDismissed = true
            taskTemplatesOpen = false
            if (permanent) session.dismissStarterForWorld()
            return true
        }
        val bounds = starterBounds()
        val tasks = starterTasks()
        for (index in 0 until tasks.size + 2) {
            val rowY = bounds.y + 25 + index * 19
            if (!inside(mouseX, mouseY, (bounds.x + 7).toDouble(), rowY.toDouble(),
                    (bounds.width - 14).toDouble(), 16.0)) continue
            val program = if (index < tasks.size) {
                ProgramTaskTemplates.create(document.program(), definition, capabilities, tasks[index])
            } else {
                val kind = if (index == tasks.size) ProgramStarterTemplates.Kind.MANUAL
                    else ProgramStarterTemplates.Kind.CHAT
                ProgramStarterTemplates.create(document.program(), definition, capabilities, kind,
                    Component.translatable("screen.academy.program.starter.chat_keyword").string,
                    Component.translatable("screen.academy.program.starter.output").string)
            }
            taskTemplatesOpen = false
            setProgram(program, true)
            fitCanvas(false)
            return true
        }
        return inside(mouseX, mouseY, bounds.x.toDouble(), bounds.y.toDouble(),
            bounds.width.toDouble(), bounds.height.toDouble())
    }

    private fun renderEdge(graphics: ProgramUiGraphics, edge: ProgramGraph.Edge) {
        val from = node(edge.from().nodeId())
        val to = node(edge.to().nodeId())
        if (from == null || to == null) return
        val fromIndex = portIndex(from.schema.outputs(), edge.from().port())
        val toIndex = portIndex(to.schema.inputs(), edge.to().port())
        if (fromIndex < 0 || toIndex < 0) return
        countEditor(ProgramEditorMetrics.Counter.EDGES_DRAWN)
        val type = from.schema.outputs()[fromIndex].type()
        orthogonalLine(
            graphics,
            (from.x + NODE_W).roundToInt(),
            (from.y + portOffsetY(from, fromIndex)).roundToInt(),
            (to.x).roundToInt(),
            (to.y + portOffsetY(to, toIndex)).roundToInt(),
            portColor(type)
        )
    }

    private fun renderNode(graphics: ProgramUiGraphics, node: NodeView) {
        countEditor(ProgramEditorMetrics.Counter.NODES_DRAWN)
        val x = (node.x).roundToInt()
        val y = (node.y).roundToInt()
        val height = nodeHeight(node)
        val localError = firstDiagnostic()
        val errorNode = if (serverDiagnostic != PrecisionGraph.Diagnostic.OK) serverDiagnosticNode
        else localError?.nodeId() ?: -1
        val hasError = node.id() == errorNode
        val selected = selectedNodes.contains(node.id())
        graphics.fill(
            x, y, x + NODE_W, y + height,
            if (hasError) ERROR_BACKGROUND else if (selected) NODE_SELECTED_BACKGROUND else NODE_BACKGROUND
        )
        border(
            graphics, x, y, NODE_W, height,
            if (hasError) ERROR else if (selected) accentColor else BORDER_MUTED
        )
        graphics.fill(x, y, x + NODE_W, y + NODE_HEADER_H, if (hasError) ERROR else NODE_HEADER)
        if (!hasError) graphics.fill(x, y, x + 2, y + NODE_HEADER_H, groupColor(node.entry.group()))
        smallText(graphics, groupGlyph(node.entry.group()), x + 3, y + 2, TEXT, 8)
        val categoryRestricted = node.entry.categoryRestricted()
        var rightInset = if (hasError) 15 else 3
        if (categoryRestricted) {
            val badgeX = x + NODE_W - rightInset - 8
            graphics.fill(badgeX - 2, y + 2, badgeX - 1, y + NODE_HEADER_H - 2, DIVIDER)
            smallText(graphics, categoryGlyph(node.entry), badgeX + 1, y + 2, accentColor, 7)
            rightInset += 11
        }
        smallText(
            graphics, nodeLabel(node.entry).string, x + 12, y + 2, TEXT,
            NODE_W - 12 - rightInset
        )
        if (hasError) smallText(graphics, "!!", x + NODE_W - 12, y + 2, TEXT, 10)
        var configurationY = y + NODE_HEADER_H + 2
        for (field in configurationFields(node)) {
            val value = node.source.configuration().asJsonObject.get(field)
            smallText(
                graphics,
                configurationFieldLabel(field).string + ": "
                        + configurationDisplayValue(node, field, value).string,
                x + 4, configurationY, NODE_SECONDARY_TEXT, NODE_W - 8
            )
            configurationY += NODE_CONFIGURATION_ROW_H
        }
        for ((index, port) in node.schema.inputs().withIndex()) {
            val endpoint = Endpoint(node.id(), port.name(), true, port.type())
            val color = if (highlightedPort(endpoint)) TEXT else portColor(port.type())
            renderPort(
                graphics, x, y + portOffsetY(node, index), color,
                port.type() == ProgramValueTypes.FLOW && !endpointConnected(endpoint)
            )
        }
        for ((index, port) in node.schema.outputs().withIndex()) {
            val endpoint = Endpoint(node.id(), port.name(), false, port.type())
            val color = if (highlightedPort(endpoint)) TEXT else portColor(port.type())
            renderPort(
                graphics, x + NODE_W, y + portOffsetY(node, index), color,
                port.type() == ProgramValueTypes.FLOW && !endpointConnected(endpoint)
            )
        }
    }

    private fun localDiagnostic(diagnostic: ProgramDiagnostic): Component {
        return ProgramDiagnosticText.describe(document.program(), catalog, diagnostic).copy().withColor(ERROR)
    }

    private fun nodeLabel(entry: ProgramEditorNodeCatalog.Entry): Component =
        ProgramNodePalette.label(entry.id(), entry.defaultConfiguration())
            ?: Component.translatable(entry.translationKey())

    private fun nodeDescription(entry: ProgramEditorNodeCatalog.Entry): Component {
        val description = Component.translatable(entry.descriptionTranslationKey())
        val status = entry.presentationStatus()
        val titleKey = status.titleTranslationKey() ?: return description
        val detailKey = status.descriptionTranslationKey() ?: return description
        // Every inspector/tooltip measurement already uses this description. Keeping the
        // notice in the same Component avoids an extra row with a different height contract.
        return Component.empty()
            .append(Component.translatable(titleKey)).append(": ")
            .append(Component.translatable(detailKey)).append("\n")
            .append(description)
    }

    private fun renderSelectionDrag(graphics: ProgramUiGraphics) {
        val drag = selectionDrag ?: return
        val bounds = drag.bounds()
        if (!bounds.exceeds(SELECTION_DRAG_THRESHOLD)) return
        val left = floor(bounds.left()).toInt()
        val top = floor(bounds.top()).toInt()
        val right = ceil(bounds.right()).toInt()
        val bottom = ceil(bounds.bottom()).toInt()
        graphics.fill(left, top, right, bottom, SELECTION_BACKGROUND)
        border(graphics, left, top, maxOf(1, right - left), maxOf(1, bottom - top), accentColor)
    }

    private fun renderConnectionPreview(graphics: ProgramUiGraphics, mouseX: Int, mouseY: Int) {
        val current = connection ?: return
        val anchor = endpointScreen(current.endpoint)
        val target = snappedEndpoint(mouseX.toDouble(), mouseY.toDouble(), current.endpoint)
        val end = if (target == null) ScreenPoint(mouseX, mouseY) else endpointScreen(target)
        val color = if (target != null && connectionRejected(current.endpoint, target)) ERROR
        else portColor(current.endpoint.type)
        orthogonalLine(graphics, anchor.x, anchor.y, end.x, end.y, color)
    }

    private fun renderInspector(graphics: ProgramUiGraphics, mouseX: Int, mouseY: Int) {
        val x = inspectorX()
        val width = inspectorWidth()
        if (session.precisionRules()) {
            headingText(graphics, Component.translatable("screen.academy.precision_operation.inspector").string,
                x + 5, canvasY + 4, DIM, width - 10)
        } else {
            val tabWidth = (width - 8) / 3
            for ((index, key) in listOf("node", "preview", "run").withIndex()) {
                val selected = when (index) { 1 -> showTaskPreview; 2 -> showRunTrace; else -> !showRunTrace && !showTaskPreview }
                button(graphics, x + 4 + tabWidth * index, canvasY + 2, tabWidth, 15,
                    Component.translatable("screen.academy.program.inspector.$key"), mouseX, mouseY, selected, true)
            }
            if (showTaskPreview) {
                renderTaskPreview(graphics, x, width)
                return
            }
            if (showRunTrace) {
                renderRunTrace(graphics, x, width, mouseX, mouseY)
                return
            }
        }
        val selected = node(selectedNode)
        if (selected == null) {
            val status = if (selectedNodes.size > 1) Component.translatable(
                "screen.academy.precision_operation.multi_selection", selectedNodes.size
            ) else Component.translatable("screen.academy.precision_operation.no_selection")
            smallText(graphics, status.string, x + 5, canvasY + 22, DIM, width - 10)
            return
        }
        headingText(graphics, nodeLabel(selected.entry).string, x + 5, canvasY + 21, TEXT, width - 10)
        if (selected.entry.categoryRestricted()) {
            smallText(graphics, categoryScopeLabel(selected.entry).string, x + 5, canvasY + 33, accentColor, width - 10)
        }
        val descriptionOffset = inspectorDescriptionOffset(selected.entry)
        val descriptionHeight = smallWrappedText(
            graphics, nodeDescription(selected.entry),
            x + 5, canvasY + descriptionOffset, width - 10
        )
        val configY = canvasY + inspectorConfigurationOffset(selected.entry, descriptionHeight)
        renderConfigurationEditor(graphics, selected, x + 5, configY, width - 10, mouseX, mouseY)
        val portsY = configY + configurationEditorHeight(selected, width - 10)
        headingText(
            graphics, Component.translatable("screen.academy.precision_operation.ports").string,
            x + 5, portsY - 1, DIM, width - 10
        )
        val ports = selected.schema.inputs().map { "< " to it } + selected.schema.outputs().map { "> " to it }
        val listY = portsY + 11
        val rows = maxOf(0, (canvasY + canvasH - listY - 2) / 9)
        inspectorPortScroll = inspectorPortScroll.coerceIn(0, maxOf(0, ports.size - rows))
        for ((index, entry) in ports.drop(inspectorPortScroll).take(rows).withIndex()) {
            val (prefix, port) = entry
            smallText(graphics, prefix + portLabel(selected.entry, port.name()).string,
                x + 7, listY + index * 9, portColor(port.type()), width - 14)
        }
        if (rows > 0 && ports.size > rows) {
            val trackHeight = rows * 9
            val thumbHeight = maxOf(4, trackHeight * rows / ports.size)
            val thumbY = listY + (trackHeight - thumbHeight) * inspectorPortScroll / (ports.size - rows)
            graphics.fill(x + width - 3, listY, x + width - 1, listY + trackHeight, CONTROL_BACKGROUND)
            graphics.fill(x + width - 3, thumbY, x + width - 1, thumbY + thumbHeight, DIM)
        }
    }

    private fun inspectorPortRows(node: NodeView): Int {
        val width = inspectorWidth() - 10
        val descriptionHeight = ProgramUiGraphics.wrappedHeight(
            nodeDescription(node.entry).string, width.toFloat(), ProgramUiGraphics.BODY_FONT_SIZE, 9.0f)
        val listY = canvasY + inspectorConfigurationOffset(node.entry, descriptionHeight) +
            configurationEditorHeight(node, width) + 11
        return maxOf(0, (canvasY + canvasH - listY - 2) / 9)
    }

    private fun runTraceOffset(): Int = 40 + runStatusLines().size * 10

    private fun traceVisibleRows(): Int = maxOf(0, (canvasH - runTraceOffset() - 16) / 25)

    private fun runStatusLines(): List<String> {
        val status = session.runStatus(slot)
        val saved = session.restoredProgram(slot)
        val language = Language.getInstance()
        val width = inspectorWidth() - 10
        if (statusCached === status && statusDocument === document && statusSaved === saved &&
            statusLanguage === language && statusWidth == width) return statusLines
        val lines = ArrayList<String>()
        fun add(key: String, vararg args: Any) {
            lines.addAll(ProgramUiGraphics.wrap(Component.translatable("screen.academy.program.run_status.$key", *args).string,
                width.toFloat(), ProgramUiGraphics.BODY_FONT_SIZE))
        }
        if (saved == null) add("unsaved")
        else {
            add("saved_revision", session.revision())
            if (saved != document.program()) add("draft_differs")
            if (status == null) add("connecting")
            else {
                add("phase.${status.phase().name.lowercase(Locale.ROOT)}")
                if (status.rollbackIncomplete()) add("rollback_incomplete")
                val source = if (status.sourceNodeId() >= 0) status.sourceNodeId() else status.nodeId()
                if (source >= 0) {
                    val entry = status.nodeType()?.let { catalog.entry(it) }
                    add("node", source, if (entry == null) "" else nodeLabel(entry).string)
                }
                when (status.waitKind()) {
                    ProgramRunStatus.WaitKind.WAIT -> if (status.logicalTick() >= 0 && status.wakeAtLogicalTick() >= 0)
                        add("wait", maxOf(0L, status.wakeAtLogicalTick() - status.logicalTick()))
                    ProgramRunStatus.WaitKind.RETRY -> add("retry")
                    ProgramRunStatus.WaitKind.FUEL -> add("fuel")
                    else -> Unit
                }
                if (status.diagnosticCode().isNotEmpty()) {
                    val diagnostic = runCatching { ProgramVmDiagnostic.valueOf(status.diagnosticCode()) }.getOrNull()
                    if (diagnostic != null && diagnostic != ProgramVmDiagnostic.NONE)
                        lines.addAll(ProgramUiGraphics.wrap(Component.translatable(diagnostic.translationKey()).string,
                            width.toFloat(), ProgramUiGraphics.BODY_FONT_SIZE))
                }
            }
        }
        statusCached = status; statusDocument = document; statusSaved = saved
        statusLanguage = language; statusWidth = width; statusLines = lines
        return lines
    }

    private data class PreviewLine(val text: String, val nodeId: Int, val color: Int)

    /** An explicit, bounded explanation of the draft; never a live world query or a VM execution. */
    private fun taskPreviewLines(): List<PreviewLine> {
        val language = Language.getInstance()
        val extensions = ProgramNodeExtensionIndex.snapshot(document.program().category())
        val paletteRevision = ProgramNodePalette.registrationRevision()
        val width = inspectorWidth() - 12
        if (previewDocument === document && previewLanguage === language && previewWidth == width &&
            previewExtensions === extensions && previewPaletteRevision == paletteRevision) return previewLines
        val preview = ProgramTaskPreview.inspect(document.program(), definition, capabilities)
        val lines = ArrayList<PreviewLine>()
        fun add(text: String, nodeId: Int = -1, color: Int = DIM) {
            for (part in ProgramUiGraphics.wrap(text, width.toFloat(), ProgramUiGraphics.BODY_FONT_SIZE))
                lines.add(PreviewLine(part, nodeId, color))
        }
        for (notice in preview.notices()) {
            val prefix = if (notice.nodeId() < 0) "" else "#${notice.nodeId()} "
            add(prefix + Component.translatable(notice.translationKey(), *notice.arguments().toTypedArray()).string,
                notice.nodeId())
        }
        val authored = document.program().graph().nodes().associateBy { it.id() }
        for (item in preview.nodes()) {
            add("#${item.nodeId()} ${Component.translatable(item.titleKey()).string}", item.nodeId(), TEXT)
            val entry = catalog.entry(item.nodeType())
            fun configuredValue(field: String, fallback: String): String {
                val json = authored[item.nodeId()]?.configuration()
                if (entry == null || json == null || !json.isJsonObject) return fallback
                val raw = json.asJsonObject.get(field) ?: return fallback
                val options = ProgramConfigurationOptions.options(entry, field, raw)
                return if (options.isEmpty()) fallback else ProgramConfigurationOptions.selected(options, raw).label().string
            }
            for (input in item.inputs()) {
                val port = if (entry == null) input.port() else portLabel(entry, input.port()).string
                val source = Component.translatable("screen.academy.program.preview.source.${input.source().name.lowercase(Locale.ROOT)}").string
                val detail = when (input.source()) {
                    ProgramTaskPreview.Source.CONNECTED -> input.from().joinToString(", ") { "#${it.nodeId()}" }
                    ProgramTaskPreview.Source.DEFAULT -> Component.translatable(input.defaultKey()).string +
                        if (input.literal().isEmpty()) "" else " (${configuredValue(input.port(), input.literal())})"
                    ProgramTaskPreview.Source.LITERAL -> input.literal()
                    else -> ""
                }
                add("$port: $source${if (detail.isEmpty()) "" else " · $detail"}", item.nodeId())
            }
            for (parameter in item.configuration()) {
                if (item.inputs().any { it.port() == parameter.name() && it.source() == ProgramTaskPreview.Source.DEFAULT }) continue
                add("${configurationFieldLabel(parameter.name()).string}: ${configuredValue(parameter.name(), parameter.value())}", item.nodeId())
            }
        }
        if (preview.omittedNodes() > 0) add(Component.translatable("screen.academy.program.preview.omitted", preview.omittedNodes()).string)
        previewDocument = document
        previewLanguage = language
        previewExtensions = extensions
        previewPaletteRevision = paletteRevision
        previewWidth = width
        previewLines = lines
        return lines
    }

    private fun renderTaskPreview(graphics: ProgramUiGraphics, x: Int, width: Int) {
        val lines = taskPreviewLines()
        val rows = maxOf(1, (canvasH - 30) / 10)
        previewScroll = previewScroll.coerceIn(0, maxOf(0, lines.size - rows))
        lines.drop(previewScroll).take(rows).forEachIndexed { index, line ->
            smallText(graphics, line.text, x + 5, canvasY + 23 + index * 10, line.color, width - 10)
        }
    }

    private fun renderRunTrace(graphics: ProgramUiGraphics, x: Int, width: Int,
                               mouseX: Int, mouseY: Int) {
        runStatusLines().forEachIndexed { index, line ->
            smallText(graphics, line, x + 5, canvasY + 23 + index * 10, DIM, width - 10)
        }
        val traceY = canvasY + runTraceOffset()
        smallText(graphics, Component.translatable("screen.academy.program.run_trace.history").string,
            x + 5, traceY - 11, DIM, width - 10)
        if (runTrace.isEmpty()) {
            smallText(graphics, Component.translatable("screen.academy.program.run_trace.empty").string,
                x + 5, traceY, DIM, width - 10)
            return
        }
        val rows = traceVisibleRows()
        runTraceScroll = runTraceScroll.coerceIn(0, maxOf(0, runTrace.size - rows))
        for (row in 0 until rows) {
            val index = runTraceScroll + row
            if (index >= runTrace.size) break
            val step = runTrace[index]
            val y = traceY + row * 25
            val hovered = inside(mouseX.toDouble(), mouseY.toDouble(),
                (x + 3).toDouble(), y.toDouble(), (width - 6).toDouble(), 23.0)
            graphics.fill(x + 3, y, x + width - 3, y + 23,
                if (hovered) HOVER_BACKGROUND else ROW_BACKGROUND)
            val traceNode = node(step.nodeId())
            val title = if (traceNode == null) "#${step.nodeId()}"
                else "#${step.nodeId()} ${nodeLabel(traceNode.entry).string}"
            val branchLabel = when {
                step.flowOutput().isBlank() -> ""
                step.flowOutput() == "error" -> Component.translatable(
                    "screen.academy.program.run_trace.error").string
                traceNode != null -> portLabel(traceNode.entry, step.flowOutput()).string
                else -> step.flowOutput()
            }
            val branch = if (branchLabel.isBlank()) "" else " → $branchLabel"
            smallText(graphics, title + branch, x + 7, y + 2, TEXT, width - 14)
            if (step.detail().isNotBlank())
                smallText(graphics, step.detail(), x + 7, y + 12, DIM, width - 14)
        }
        if (runTraceTruncated) smallText(graphics,
            Component.translatable("screen.academy.program.run_trace.truncated").string,
            x + 5, canvasY + canvasH - 12, DIM, width - 10)
    }

    private fun renderConfigurationEditor(
        graphics: ProgramUiGraphics,
        node: NodeView,
        x: Int,
        y: Int,
        width: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val fields = configurationFields(node)
        if (fields.isEmpty()) return
        var rowY = y
        for (index in fields.indices) {
            val field = fields[index]
            smallText(graphics, configurationFieldLabel(field).string, x, rowY, TEXT, width)
            val currentValue = node.source.configuration().asJsonObject.get(field)
            val options = ProgramConfigurationOptions.options(node.entry, field, currentValue)
            if (ProgramConfigurationOptions.isToggle(field, currentValue)) {
                val valueY = rowY + 11
                val checked = currentValue.asBoolean
                val hovered = inside(
                    mouseX.toDouble(),
                    mouseY.toDouble(),
                    x.toDouble(),
                    valueY.toDouble(),
                    width.toDouble(),
                    TOOL_SIZE.toDouble()
                )
                val trackWidth = 20
                val trackHeight = 10
                val trackY = valueY + 2
                graphics.fill(
                    x,
                    trackY,
                    x + trackWidth,
                    trackY + trackHeight,
                    if (hovered || checked) TEXT else BORDER_MUTED
                )
                val thumbX = if (checked) x + trackWidth - 9 else x + 1
                graphics.fill(
                    thumbX,
                    trackY + 1,
                    thumbX + 8,
                    trackY + trackHeight - 1,
                    if (checked) 0xFF000000.toInt() else TEXT
                )
                val selected = ProgramConfigurationOptions.selected(options, currentValue)
                smallText(
                    graphics,
                    selected.label().string,
                    x + trackWidth + 5,
                    valueY + 4,
                    if (checked) TEXT else DIM,
                    width - trackWidth - 5
                )
            } else if (ProgramConfigurationOptions.isPowerSlider(field, currentValue)) {
                val valueY = rowY + 11
                val valueWidth = 32
                val trackWidth = maxOf(16, width - valueWidth - 4)
                val power = currentValue.asFloat.coerceIn(ProgramPowerScale.MIN, ProgramPowerScale.MAX)
                val progress = (power - ProgramPowerScale.MIN) / (ProgramPowerScale.MAX - ProgramPowerScale.MIN)
                val fillWidth = (trackWidth * progress).roundToInt()
                graphics.fill(x, valueY + 6, x + trackWidth, valueY + 8, BORDER_MUTED)
                graphics.fill(x, valueY + 6, x + fillWidth, valueY + 8, accentColor)
                val thumbX = x + fillWidth
                graphics.fill(thumbX - 1, valueY + 3, thumbX + 1, valueY + 11, TEXT)
                smallText(
                    graphics,
                    String.format(Locale.ROOT, "%.2f", power),
                    x + trackWidth + 5,
                    valueY + 3,
                    TEXT,
                    valueWidth - 1
                )
            } else if (options.isNotEmpty()) {
                val valueY = rowY + 11
                iconButton(graphics, x, valueY, "<", mouseX, mouseY, false)
                iconButton(graphics, x + width - TOOL_SIZE, valueY, ">", mouseX, mouseY, false)
                renderControl(
                    graphics, x + TOOL_SIZE + 2, valueY,
                    width - TOOL_SIZE * 2 - 4, TOOL_SIZE, enabled = true, selected = true, hovered = false
                )
                val selected = ProgramConfigurationOptions.selected(options, currentValue)
                val selectedLabel = selected.label().string
                smallText(graphics, selectedLabel, x + TOOL_SIZE + 5, valueY + 4, TEXT, width - TOOL_SIZE * 2 - 10)
                if (configurationOptionNeedsTooltip(
                        TextWidget.getTextWidth(selectedLabel, ProgramUiGraphics.BODY_FONT_SIZE),
                        width - TOOL_SIZE * 2 - 10
                    )
                ) {
                    val detailY = valueY + TOOL_SIZE + CONFIGURATION_DETAIL_GAP
                    val detailLines =
                        ProgramUiGraphics.wrap(selectedLabel, (width - 5).toFloat(), ProgramUiGraphics.BODY_FONT_SIZE)
                    graphics.fill(
                        x,
                        detailY,
                        x + 2,
                        detailY + detailLines.size * CONFIGURATION_DETAIL_LINE_H,
                        accentColor
                    )
                    for (lineIndex in detailLines.indices) {
                        smallText(
                            graphics,
                            detailLines[lineIndex],
                            x + 5,
                            detailY + lineIndex * CONFIGURATION_DETAIL_LINE_H,
                            DIM,
                            width - 5
                        )
                    }
                }
            } else {
                val input = configurationInputs[field]
                renderInput(graphics, x, rowY + 11, width, input != null && input.isFocused)
            }
            rowY += configurationRowHeight(node, field, width)
        }
    }

    private fun renderStatus(graphics: ProgramUiGraphics) {
        val local = firstDiagnostic()
        val shown = if (System.currentTimeMillis() < transientUntil) transientDiagnostic
        else if (serverDiagnostic != PrecisionGraph.Diagnostic.OK) serverDiagnostic
        else if (local == null) PrecisionGraph.Diagnostic.OK else mapDiagnostic(local.code())
        var text = if (serverVmDiagnostic != ProgramVmDiagnostic.NONE && shown == serverDiagnostic)
            Component.translatable(serverVmDiagnostic.translationKey()).string
        else Component.translatable(shown.translationKey()).string
        if (System.currentTimeMillis() < transientUntil && transientDetail != null) {
            text = transientDetail!!.string
        } else if (serverDiagnostic != PrecisionGraph.Diagnostic.OK) {
            text = serverDiagnosticText()
        } else if (local != null) {
            text = ProgramDiagnosticText.describe(document.program(), catalog, local).string
        }
        smallText(
            graphics,
            text,
            panelX + 4,
            panelY + panelH - 11,
            if (shown == PrecisionGraph.Diagnostic.OK) DIM else ERROR,
            panelW - 8
        )
    }

    private fun renderQuickInsert(graphics: ProgramUiGraphics, mouseX: Int, mouseY: Int) {
        val current = quickInsert ?: return
        val entries = quickInsertEntries(current)
        val bounds = quickInsertBounds(current, entries.size)
        val rows = minOf(8, entries.size)
        graphics.fill(bounds.x, bounds.y, bounds.x + bounds.width,
            bounds.y + bounds.height, POPUP_BACKGROUND)
        border(graphics, bounds.x, bounds.y, bounds.width, bounds.height, BORDER)
        smallText(graphics,
            Component.translatable("screen.academy.program.quick_insert.search").string + ": "
                    + current.query + "_",
            bounds.x + 5, bounds.y + 5, TEXT, bounds.width - 10)
        graphics.fill(bounds.x + 4, bounds.y + 17,
            bounds.x + bounds.width - 4, bounds.y + 18, DIVIDER)
        for (row in 0 until rows) {
            val index = current.scroll + row
            if (index !in entries.indices) break
            val entry = entries[index]
            val rowY = bounds.y + 20 + row * ROW_H
            if (inside(
                    mouseX.toDouble(),
                    mouseY.toDouble(),
                    (bounds.x + 2).toDouble(),
                    rowY.toDouble(),
                    (bounds.width - 4).toDouble(),
                    (ROW_H - 1).toDouble()
                ) || index == current.selected
            ) {
                graphics.fill(bounds.x + 2, rowY,
                    bounds.x + bounds.width - 2, rowY + ROW_H - 1, HOVER_BACKGROUND)
            }
            smallText(graphics, nodeLabel(entry).string,
                bounds.x + 5, rowY + 3, TEXT, bounds.width - 10)
        }
        if (entries.isEmpty()) smallText(graphics,
            Component.translatable("screen.academy.program.quick_insert.empty").string,
            bounds.x + 5, bounds.y + 23, DIM, bounds.width - 10)
        else if (entries.size > rows) smallText(graphics,
            "${current.selected + 1}/${entries.size}",
            bounds.x + bounds.width - 36, bounds.y + 5, DIM, 31)
    }

    private fun quickInsertEntries(current: QuickInsert): List<ProgramEditorNodeCatalog.Entry> {
        val query = current.query.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) return current.entries
        return current.entries.filter { entry ->
            nodeLabel(entry).string.lowercase(Locale.ROOT).contains(query)
                    || nodeDescription(entry).string.lowercase(Locale.ROOT).contains(query)
                    || entry.id().toString().lowercase(Locale.ROOT).contains(query)
        }
    }

    private fun quickInsertBounds(current: QuickInsert, count: Int): Rect {
        val width = minOf(162, canvasW - 8)
        val height = 24 + minOf(8, maxOf(1, count)) * ROW_H
        return Rect(
            current.x.coerceIn(panelX + 2, panelX + panelW - width - 2),
            current.y.coerceIn(canvasY + 2, canvasY + canvasH - height - 2),
            width, height
        )
    }

    private fun renderTooltip(graphics: ProgramUiGraphics, mouseX: Int, mouseY: Int) {
        val tooltip = hoveredTooltip(mouseX, mouseY)
        if (tooltip.isEmpty()) return
        val expanded = ArrayList<TooltipLine>()
        var contentWidth = 0.0f
        for ((text, color) in tooltip) {
            for (wrapped in ProgramUiGraphics.wrap(text, 176f, ProgramUiGraphics.BODY_FONT_SIZE)) {
                expanded.add(TooltipLine(wrapped, color))
                contentWidth = maxOf(contentWidth, TextWidget.getTextWidth(wrapped, ProgramUiGraphics.BODY_FONT_SIZE))
            }
        }
        val tooltipWidth = maxOf(32, ceil(contentWidth).toInt() + 10)
        val tooltipHeight = expanded.size * 9 + 8
        var x = mouseX + 10
        var y = mouseY + 8
        if (x + tooltipWidth > width - 4) x = mouseX - tooltipWidth - 10
        if (y + tooltipHeight > height - 4) y = mouseY - tooltipHeight - 8
        x = x.coerceIn(4, maxOf(4, width - tooltipWidth - 4))
        y = y.coerceIn(4, maxOf(4, height - tooltipHeight - 4))
        graphics.fill(x, y, x + tooltipWidth, y + tooltipHeight, POPUP_BACKGROUND)
        border(graphics, x, y, tooltipWidth, tooltipHeight, BORDER)
        graphics.fill(x + 1, y + 1, x + 3, y + tooltipHeight - 1, accentColor)
        for (index in expanded.indices) {
            val line = expanded[index]
            graphics.text(
                line.text,
                (x + 6).toFloat(),
                (y + 4 + index * 9).toFloat(),
                line.color,
                ProgramUiGraphics.BODY_FONT_SIZE,
                (tooltipWidth - 10).toFloat()
            )
        }
    }

    private fun hoveredTooltip(mouseX: Int, mouseY: Int): List<TooltipLine> {
        if (inside(
                mouseX.toDouble(),
                mouseY.toDouble(),
                panelX.toDouble(),
                (panelY + panelH - 14).toDouble(),
                panelW.toDouble(),
                14.0
            )
        ) {
            val local = firstDiagnostic()
            val detail =
                if (System.currentTimeMillis() < transientUntil && transientDetail != null) transientDetail!!.string
                else if (serverDiagnostic != PrecisionGraph.Diagnostic.OK) serverDiagnosticText()
                else if (local == null) ""
                else ProgramDiagnosticText.describe(document.program(), catalog, local).string
            if (detail.isNotEmpty()) return listOf(TooltipLine(detail, ERROR))
        }
        var toolsX = panelX + panelW - TOOL_LABELS.size * (TOOL_SIZE + 2) - 2
        for (index in TOOL_LABELS.indices) {
            if (inside(
                    mouseX.toDouble(),
                    mouseY.toDouble(),
                    toolsX.toDouble(),
                    (panelY + 3).toDouble(),
                    TOOL_SIZE.toDouble(),
                    TOOL_SIZE.toDouble()
                )
            ) {
                return listOf(
                    TooltipLine(
                        Component.translatable("screen.academy.precision_operation." + TOOL_LABELS[index]).string,
                        TEXT
                    )
                )
            }
            toolsX += TOOL_SIZE + 2
        }
        if (compactLeft && inside(
                mouseX.toDouble(),
                mouseY.toDouble(),
                (leftX + 1).toDouble(),
                (canvasY + 2).toDouble(),
                16.0,
                16.0
            )
        ) {
            return listOf(TooltipLine(Component.translatable("screen.academy.precision_operation.nodes").string, TEXT))
        }
        if (compactRight && inside(
                mouseX.toDouble(),
                mouseY.toDouble(),
                (rightX + 1).toDouble(),
                (canvasY + 2).toDouble(),
                16.0,
                16.0
            )
        ) {
            return listOf(
                TooltipLine(
                    Component.translatable("screen.academy.precision_operation.inspector").string,
                    TEXT
                )
            )
        }
        if (paletteVisible()) {
            val x = paletteX()
            val width = paletteWidth()
            val query = search?.text.orEmpty()
            if (query.isNotBlank() && inside(mouseX.toDouble(), mouseY.toDouble(),
                    (x + 4).toDouble(), (canvasY + PALETTE_SEARCH_OFFSET_Y).toDouble(),
                    (width - 8).toDouble(), 15.0)) {
                return listOf(TooltipLine(query, TEXT))
            }
            if (!session.precisionRules() && inside(mouseX.toDouble(), mouseY.toDouble(),
                    (x + width - 37).toDouble(), (canvasY + 1).toDouble(), 33.0, 12.0)) {
                return listOf(TooltipLine(Component.translatable("screen.academy.program.task_template.open_hint").string, TEXT))
            }
            val layers = ProgramPaletteCatalog.Layer.entries
            val layerW = (width - 8) / layers.size
            for (index in layers.indices) {
                if (inside(mouseX.toDouble(), mouseY.toDouble(), (x + 4 + index * layerW).toDouble(),
                        (canvasY + PALETTE_LAYER_OFFSET_Y).toDouble(), (layerW - 1).toDouble(), 12.0)) {
                    return listOf(TooltipLine(Component.translatable(layers[index].translationKey() + ".description").string, TEXT))
                }
            }
            if (paletteFamilyId == null && paletteLayer != ProgramPaletteCatalog.Layer.COMMON) {
                val groups = ProgramEditorNodeCatalog.Group.entries
                val tabW = maxOf(11, (width - 8) / groups.size)
                for (index in groups.indices) {
                    if (inside(mouseX.toDouble(), mouseY.toDouble(), (x + 4 + index * tabW).toDouble(),
                            (canvasY + PALETTE_TAB_OFFSET_Y).toDouble(), (tabW - 1).toDouble(), 12.0)) {
                        return listOf(TooltipLine(Component.translatable(groupKey(groups[index])).string, TEXT))
                    }
                }
            }
            val cards = paletteCards()
            val listY = canvasY + PALETTE_LIST_OFFSET_Y
            val rows = maxOf(1, (canvasY + canvasH - 3 - listY) / ROW_H)
            for (row in 0 until minOf(rows, cards.size - paletteScroll)) {
                if (inside(mouseX.toDouble(), mouseY.toDouble(), (x + 3).toDouble(),
                        (listY + row * ROW_H).toDouble(), (width - 6).toDouble(), (ROW_H - 1).toDouble())) {
                    return cardTooltip(cards[paletteScroll + row])
                }
            }
        }
        if (showStarter()) {
            val bounds = starterBounds()
            val tasks = starterTasks()
            for (index in 0 until tasks.size + 2) {
                if (!inside(mouseX.toDouble(), mouseY.toDouble(), (bounds.x + 7).toDouble(),
                        (bounds.y + 25 + index * 19).toDouble(), (bounds.width - 14).toDouble(), 16.0)) continue
                val detail = if (index < tasks.size) tasks[index].descriptionTranslationKey()
                    else "screen.academy.program.starter.hint"
                return listOf(TooltipLine(starterRowLabel(index).string, TEXT),
                    TooltipLine(Component.translatable(detail).string, DIM),
                    TooltipLine(Component.translatable("screen.academy.program.task_template.hint").string, DIM))
            }
        }
        val configurationTooltip = hoveredConfigurationTooltip(mouseX, mouseY)
        if (configurationTooltip.isNotEmpty()) return configurationTooltip
        if (!inside(
                mouseX.toDouble(),
                mouseY.toDouble(),
                canvasX.toDouble(),
                canvasY.toDouble(),
                canvasW.toDouble(),
                canvasH.toDouble()
            )
        ) return emptyList()
        val endpoint = endpointAt(mouseX.toDouble(), mouseY.toDouble())
        if (endpoint != null) {
            val owner = node(endpoint.nodeId)
            val label = if (owner == null) Component.literal(endpoint.port) else portLabel(owner.entry, endpoint.port)
            val type = endpoint.type.id().path.replace("program_type/", "")
            return listOf(
                TooltipLine((if (endpoint.input) "< " else "> ") + label.string, TEXT),
                TooltipLine(type, DIM)
            )
        }
        val localError = firstDiagnostic()
        val errorNode = if (serverDiagnostic != PrecisionGraph.Diagnostic.OK) serverDiagnosticNode
        else localError?.nodeId() ?: -1
        for (node in nodes().asReversed()) {
            if (!insideNode(mouseX.toDouble(), mouseY.toDouble(), node)) continue
            val error = if (node.id() != errorNode) null
            else if (serverDiagnostic != PrecisionGraph.Diagnostic.OK) serverDiagnosticText()
            else localDiagnostic(localError!!).string
            return tooltipLines(node, error)
        }
        return emptyList()
    }

    private fun hoveredConfigurationTooltip(mouseX: Int, mouseY: Int): List<TooltipLine> {
        if (!inspectorVisible()) return emptyList()
        val selected = node(selectedNode) ?: return emptyList()
        val fields = configurationFields(selected)
        if (fields.isEmpty()) return emptyList()
        val width = inspectorWidth() - 10
        val descriptionHeight = ProgramUiGraphics.wrappedHeight(
            nodeDescription(selected.entry).string, width.toFloat(),
            ProgramUiGraphics.BODY_FONT_SIZE, 9.0f
        )
        val y = canvasY + inspectorConfigurationOffset(selected.entry, descriptionHeight)
        val x = inspectorX() + 5
        var rowY = y
        for (field in fields) {
            val currentValue = selected.source.configuration().asJsonObject.get(field)
            val options = ProgramConfigurationOptions.options(selected.entry, field, currentValue)
            if (options.isEmpty()
                || ProgramConfigurationOptions.isToggle(field, currentValue)
                || ProgramConfigurationOptions.isPowerSlider(field, currentValue)
            ) {
                rowY += configurationRowHeight(selected, field, width)
                continue
            }
            val controlX = x + TOOL_SIZE + 2
            val controlWidth = width - TOOL_SIZE * 2 - 4
            val controlY = rowY + 11
            if (!inside(
                    mouseX.toDouble(),
                    mouseY.toDouble(),
                    controlX.toDouble(),
                    controlY.toDouble(),
                    controlWidth.toDouble(),
                    TOOL_SIZE.toDouble()
                )
            ) {
                rowY += configurationRowHeight(selected, field, width)
                continue
            }
            val option = ProgramConfigurationOptions.selected(options, currentValue)
            val label = option.label().string
            val labelWidth = TextWidget.getTextWidth(label, ProgramUiGraphics.BODY_FONT_SIZE)
            if (!configurationOptionNeedsTooltip(labelWidth, controlWidth - 6)) return emptyList()
            return listOf(
                TooltipLine(configurationFieldLabel(field).string, DIM),
                TooltipLine(label, TEXT)
            )
        }
        return emptyList()
    }

    private fun tooltipLines(entry: ProgramEditorNodeCatalog.Entry, showDefaults: Boolean = true): List<TooltipLine> {
        val lines = ArrayList<TooltipLine>()
        lines.add(TooltipLine(nodeLabel(entry).string, TEXT))
        if (entry.categoryRestricted()) {
            lines.add(TooltipLine(categoryScopeLabel(entry).string, accentColor))
        }
        lines.add(TooltipLine(nodeDescription(entry).string, DIM))
        if (showDefaults && entry.defaultConfiguration().isJsonObject) {
            for ((field, value) in entry.defaultConfiguration().asJsonObject.entrySet()) {
                if (!value.isJsonPrimitive) continue
                val options = ProgramConfigurationOptions.options(entry, field, value)
                val label = if (options.isEmpty()) value.asString
                    else ProgramConfigurationOptions.selected(options, value).label().string
                lines.add(TooltipLine(Component.translatable("screen.academy.program.palette.default_value",
                    configurationFieldLabel(field), label).string, DIM))
            }
        }
        return lines.toList()
    }

    private fun tooltipLines(node: NodeView, error: String?): List<TooltipLine> {
        val lines = ArrayList<TooltipLine>()
        lines.addAll(tooltipLines(node.entry, false))
        if (node.source.configuration().isJsonObject) {
            for (field in configurationFields(node)) {
                val currentValue = node.source.configuration().asJsonObject.get(field)
                val options = ProgramConfigurationOptions.options(node.entry, field, currentValue)
                if (options.isEmpty()) continue
                val option = ProgramConfigurationOptions.selected(options, currentValue)
                lines.add(
                    TooltipLine(configurationFieldLabel(field).string + ": " + option.label().string, DIM)
                )
            }
        }
        if (error != null) lines.add(TooltipLine(error, ERROR))
        return lines.toList()
    }

    override fun mouseClicked(e: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        syncLayout()
        val x = e.x()
        val y = e.y()
        if (numericUpgradePreview != null) {
            if (e.button() == 0) handleNumericUpgradeClick(x, y)
            return true
        }
        if (e.button() == 0) {
            if (search != null && search!!.isVisible()
                && inside(x, y, search!!.x.toDouble(), search!!.y.toDouble(), search!!.width.toDouble(), 15.0)
            ) {
                unfocusConfigurationInputs()
                root.dispatchEvent(
                    org.academy.api.client.gui.event.MouseEvent.createPressEvent(
                        e.x(),
                        e.y(),
                        e.button()
                    )
                )
                return true
            }
            val configurationInput = configurationInputAt(x, y)
            if (configurationInput != null) {
                search!!.isFocused = false
                unfocusConfigurationInputs()
                root.dispatchEvent(
                    org.academy.api.client.gui.event.MouseEvent.createPressEvent(
                        e.x(),
                        e.y(),
                        e.button()
                    )
                )
                return true
            }
            search!!.isFocused = false
            unfocusConfigurationInputs()
            if (handleQuickInsertClick(x, y) || handleTopBarClick(x, y)
                || handleRailClick(x, y) || handleInspectorClick(x, y)
                || handlePaletteClick(x, y)
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
                selectionDrag = null
                panning = true
                return true
            }
            if (handleStarterClick(x, y) || handleCanvasClick(x, y)) return true
        }
        if ((e.button() == 1 || e.button() == 2)
            && inside(x, y, canvasX.toDouble(), canvasY.toDouble(), canvasW.toDouble(), canvasH.toDouble())
        ) {
            val endpoint = endpointAt(x, y)
            if (e.button() == 1 && endpoint != null) disconnect(endpoint)
            else panning = true
            return true
        }
        return super.mouseClicked(e, isDoubleClick)
    }

    override fun mouseDragged(e: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        if (numericUpgradePreview != null) return true
        if (draggingPowerField != null && e.button() == 0) {
            val selected = node(draggingPowerNode)
            if (selected != null) updatePowerSlider(selected, draggingPowerField!!, e.x())
            return true
        }
        if (connection != null) return true
        if (selectionDrag != null && e.button() == 0) {
            updateSelectionDrag(e.x(), e.y())
            return true
        }
        val dragging = draggingNode
        if (dragging != null) {
            val nodesToMove = if (selectedNodes.contains(dragging)) selectedNodes.toSet() else setOf(dragging)
            val result = document.translateNodes(nodesToMove, mouseX / zoom, mouseY / zoom)
            if (result.successful()) install(result.document(), false)
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
        if (numericUpgradePreview != null) return true
        if (draggingPowerField != null && e.button() == 0) {
            draggingPowerField = null
            draggingPowerNode = -1
            return true
        }
        if (connection != null && e.button() == 0) {
            finishConnection(e.x(), e.y())
            draggingNode = null
            return true
        }
        if (selectionDrag != null && e.button() == 0) {
            updateSelectionDrag(e.x(), e.y())
            selectionDrag = null
            return true
        }
        draggingNode = null
        panning = false
        return super.mouseReleased(e)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double
    ): Boolean {
        if (numericUpgradePreview != null) {
            scrollNumericUpgrade(-sign(scrollY).toInt() * 3)
            return true
        }
        if (showTaskPreview && inspectorVisible() && inside(mouseX, mouseY,
                inspectorX().toDouble(), canvasY.toDouble(), inspectorWidth().toDouble(), canvasH.toDouble())) {
            val rows = maxOf(1, (canvasH - 30) / 10)
            previewScroll = (previewScroll - sign(scrollY).toInt() * 3)
                .coerceIn(0, maxOf(0, taskPreviewLines().size - rows))
            return true
        }
        if (showRunTrace && inspectorVisible() && inside(mouseX, mouseY,
                inspectorX().toDouble(), canvasY.toDouble(), inspectorWidth().toDouble(), canvasH.toDouble())) {
            runTraceScroll = (runTraceScroll - sign(scrollY).toInt())
                .coerceIn(0, maxOf(0, runTrace.size - traceVisibleRows()))
            return true
        }
        val quick = quickInsert
        if (quick != null) {
            val entries = quickInsertEntries(quick)
            val bounds = quickInsertBounds(quick, entries.size)
            if (inside(mouseX, mouseY, bounds.x.toDouble(), bounds.y.toDouble(),
                    bounds.width.toDouble(), bounds.height.toDouble())) {
                quick.scroll = (quick.scroll - sign(scrollY).toInt())
                    .coerceIn(0, maxOf(0, entries.size - 8))
                quick.selected = quick.selected.coerceIn(quick.scroll,
                    minOf(entries.size - 1, quick.scroll + 7).coerceAtLeast(quick.scroll))
                return true
            }
        }
        if (inspectorVisible() && inside(mouseX, mouseY, inspectorX().toDouble(),
                canvasY.toDouble(), inspectorWidth().toDouble(), canvasH.toDouble())) {
            val selected = node(selectedNode)
            if (selected != null) {
                val count = selected.schema.inputs().size + selected.schema.outputs().size
                inspectorPortScroll = (inspectorPortScroll - sign(scrollY).toInt())
                    .coerceIn(0, maxOf(0, count - inspectorPortRows(selected)))
            }
            return true
        }
        if (paletteVisible()
            && inside(
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
        if (numericUpgradePreview != null) {
            when (e.key()) {
                InputConstants.KEY_ESCAPE -> closeNumericUpgradePreview()
                InputConstants.KEY_UP -> scrollNumericUpgrade(-1)
                InputConstants.KEY_DOWN -> scrollNumericUpgrade(1)
                InputConstants.KEY_PAGEUP -> scrollNumericUpgrade(-numericUpgradeVisibleRows())
                InputConstants.KEY_PAGEDOWN -> scrollNumericUpgrade(numericUpgradeVisibleRows())
            }
            return true
        }
        val quick = quickInsert
        if (quick != null) {
            val entries = quickInsertEntries(quick)
            when (e.key()) {
                InputConstants.KEY_ESCAPE -> quickInsert = null
                InputConstants.KEY_BACKSPACE -> {
                    if (quick.query.isNotEmpty()) quick.query = quick.query.dropLast(1)
                    quick.selected = 0
                    quick.scroll = 0
                }
                InputConstants.KEY_UP -> {
                    quick.selected = (quick.selected - 1).coerceAtLeast(0)
                    if (quick.selected < quick.scroll) quick.scroll = quick.selected
                }
                InputConstants.KEY_DOWN -> {
                    quick.selected = (quick.selected + 1).coerceAtMost(entries.size - 1)
                        .coerceAtLeast(0)
                    if (quick.selected >= quick.scroll + 8) quick.scroll = quick.selected - 7
                }
                InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                    if (quick.selected in entries.indices) insertQuickEntry(quick, entries[quick.selected])
                }
                InputConstants.KEY_V -> {
                    if ((e.modifiers() and InputConstants.MOD_CONTROL) != 0) {
                        quick.query = (quick.query + UiEnvironment.get().clipboard())
                            .filter { !Character.isISOControl(it) }.take(64)
                        quick.selected = 0
                        quick.scroll = 0
                    }
                }
            }
            return true
        }
        if (search != null && search!!.isFocused) {
            dispatchTextInputKey(e)
            return true
        }
        val configurationInput = focusedConfigurationInput()
        if (configurationInput != null) {
            dispatchTextInputKey(e)
            return true
        }
        if (e.key() == InputConstants.KEY_SPACE) {
            spaceDown = true
            return true
        }
        if (e.key() == InputConstants.KEY_ESCAPE
            && (connection != null || quickInsert != null || selectionDrag != null)
        ) {
            connection = null
            quickInsert = null
            selectionDrag = null
            return true
        }
        if (isDeleteKey(e.key())) {
            deleteSelected()
            return true
        }
        if ((e.modifiers() and InputConstants.MOD_CONTROL) != 0) {
            if (e.key() == InputConstants.KEY_C) {
                if ((e.modifiers() and InputConstants.MOD_SHIFT) != 0) exportProgram()
                else copySelected()
                return true
            }
            if (e.key() == InputConstants.KEY_V) {
                if ((e.modifiers() and InputConstants.MOD_SHIFT) != 0) importProgram()
                else pasteClipboard()
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
        if (numericUpgradePreview != null) return true
        val quick = quickInsert
        if (quick != null) {
            if (quick.query.length < 64 && !Character.isISOControl(e.codepoint())) {
                quick.query += String(Character.toChars(e.codepoint()))
                quick.selected = 0
                quick.scroll = 0
            }
            return true
        }
        if (search != null && search!!.isFocused) {
            root.dispatchEvent(org.academy.api.client.gui.event.CharTypedEvent(e.codepoint()))
            return true
        }
        val configurationInput = focusedConfigurationInput()
        if (configurationInput != null) {
            root.dispatchEvent(org.academy.api.client.gui.event.CharTypedEvent(e.codepoint()))
            return true
        }
        return super.charTyped(e)
    }

    private fun dispatchTextInputKey(e: KeyEvent) {
        root.dispatchEvent(
            org.academy.api.client.gui.event.KeyEvent(
                org.academy.api.client.gui.event.EventType.KEY_PRESSED,
                e.key(), e.scancode(), e.modifiers()
            )
        )
    }

    override fun keyReleased(e: KeyEvent): Boolean {
        if (numericUpgradePreview != null) return true
        if (e.key() == InputConstants.KEY_SPACE) {
            spaceDown = false
            panning = false
            return true
        }
        return super.keyReleased(e)
    }

    override fun onClose() {
        closeNumericUpgradePreview()
        session.updateLocalProgram(slot, document.program())
        session.closed(this)
        super.onClose()
    }

    private fun handleTopBarClick(mouseX: Double, mouseY: Double): Boolean {
        if (numericUpgradePreview != null) return handleNumericUpgradeClick(mouseX, mouseY)
        var x = panelX + 4 + (if (panelW >= 620) 96 else 0)
        val slotWidth = slotTabWidth(x)
        for (index in 0 until session.slotCount()) {
            if (inside(mouseX, mouseY, x.toDouble(), (panelY + 2).toDouble(), slotWidth.toDouble(), 16.0)) {
                session.updateLocalProgram(slot, document.program())
                slot = index
                session.selectSlot(slot)
                runTrace = session.lastRunTrace(slot)
                runTraceTruncated = session.lastRunTraceTruncated(slot)
                runTraceScroll = 0
                setProgram(session.editableProgram(slot), false)
                revision = session.revision()
                fitCanvas(true)
                return true
            }
            x += slotWidth + 2
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
                    2 -> pasteClipboard()
                    3 -> undo()
                    4 -> redo()
                    5 -> autoLayout()
                    6 -> fitCanvas(false)
                    7 -> exportProgram()
                    8 -> importProgram()
                    9 -> save()
                    10 -> setProgram(session.restoredProgram(slot), true)
                    11 -> openNumericUpgradePreview()
                    else -> {
                    }
                }
                return true
            }
            toolsX += TOOL_SIZE + 2
        }
        return false
    }

    private fun openNumericUpgradePreview(): Boolean {
        if (numericUpgradePreview != null) return true
        if (!configurationInputsValid()) {
            showTransient(PrecisionGraph.Diagnostic.INVALID_PARAMETER)
            return false
        }
        val plan = try {
            ProgramNumericUpgrades.preview(document.program(), definition, session.capabilities().toSet())
        } catch (_: RuntimeException) {
            transientDetail = upgradeText("validation_failed")
            transientUntil = System.currentTimeMillis() + 6000
            return false
        }
        numericUpgradeSlot = slot
        numericUpgradeRevision = revision
        numericUpgradeSessionRevision = session.revision()
        numericUpgradeExtensions = ProgramNodeExtensionIndex.snapshot(document.program().category())
        numericUpgradePreview = plan
        numericUpgradeScroll = 0
        numericUpgradeError = null
        clearNumericUpgradeLines()
        numericUpgradeLastBackup = null
        quickInsert = null
        taskTemplatesOpen = false
        connection = null
        draggingNode = null
        selectionDrag = null
        draggingPowerField = null
        draggingPowerNode = -1
        panning = false
        spaceDown = false
        search?.isFocused = false
        clearConfigurationInputs()
        updateSearchBounds()
        return true
    }

    private fun closeNumericUpgradePreview() {
        numericUpgradePreview = null
        numericUpgradeError = null
        numericUpgradeExtensions = null
        numericUpgradeSlot = -1
        numericUpgradeScroll = 0
        clearNumericUpgradeLines()
        updateSearchBounds()
    }

    private fun clearNumericUpgradeLines() {
        numericUpgradeCachedPlan = null
        numericUpgradeCachedError = null
        numericUpgradeCachedLanguage = null
        numericUpgradeCachedExtensions = null
        numericUpgradeCachedWidth = -1
        numericUpgradeCachedLines = emptyList()
    }

    private fun upgradeText(key: String, vararg args: Any): Component =
        Component.translatable("screen.academy.program.upgrade.$key", *args)

    private fun numericUpgradeBounds(): Rect {
        val w = minOf(620, (panelW - 12).coerceAtLeast(1))
        val h = minOf(380, (panelH - 12).coerceAtLeast(1))
        return Rect(panelX + (panelW - w) / 2, panelY + (panelH - h) / 2, w, h)
    }

    private fun numericUpgradeApplyBounds(): Rect {
        val b = numericUpgradeBounds()
        val w = (b.width - 24).coerceAtLeast(2) / 2
        return Rect(b.x + b.width - w - 8, b.y + b.height - 28, w, 20)
    }

    private fun numericUpgradeKeepBounds(): Rect {
        val b = numericUpgradeBounds()
        return Rect(b.x + 8, b.y + b.height - 28, (b.width - 24).coerceAtLeast(2) / 2, 20)
    }

    private fun numericUpgradeVisibleRows(): Int = maxOf(1, (numericUpgradeBounds().height - 68) / 10)

    private fun numericUpgradeLines(): List<String> {
        val plan = numericUpgradePreview ?: return emptyList()
        val width = (numericUpgradeBounds().width - 26).coerceAtLeast(1)
        val language = Language.getInstance()
        val extensions = ProgramNodeExtensionIndex.snapshot(plan.source().category())
        if (numericUpgradeCachedPlan === plan && numericUpgradeCachedError === numericUpgradeError
            && numericUpgradeCachedLanguage === language && numericUpgradeCachedExtensions === extensions
            && numericUpgradeCachedWidth == width) return numericUpgradeCachedLines
        val lines = ArrayList<String>()
        fun add(value: String) {
            lines.addAll(ProgramUiGraphics.wrap(value, width.toFloat(), ProgramUiGraphics.BODY_FONT_SIZE))
            lines.add("")
        }
        numericUpgradeError?.let { add(it.string) }
        add(upgradeText("explanation").string)
        add(upgradeText("backup_help").string)
        add(upgradeText("summary", plan.changes().size, plan.retained().size).string)
        for (change in plan.changes()) {
            val from = catalog.entry(change.sourceType())?.let { Component.translatable(it.translationKey()).string }
                ?: change.sourceType().toString()
            val to = catalog.entry(change.targetType())?.let { Component.translatable(it.translationKey()).string }
                ?: change.targetType().toString()
            val config = change.configuration().asJsonObject
            // These two fields belong to the planner's audited built-in mapping. Resolve the same
            // finite labels as the inspector without retaining results from dynamic option providers.
            val kind = Component.translatable("screen.academy.program.configuration.scalar_type." + config.get("type").asString).string
            val operatorGroup = if (change.targetType() == CommonProgramNodeIds.NUMERIC_COMPARE) "comparison" else "arithmetic"
            val operator = Component.translatable("screen.academy.program.configuration.$operatorGroup." + config.get("operator").asString).string
            add(upgradeText("change", change.nodeId(), from, to, "$kind · $operator").string)
        }
        for (retained in plan.retained()) {
            val label = catalog.entry(retained.type())?.let { Component.translatable(it.translationKey()).string }
                ?: retained.type().toString()
            add(upgradeText("retained", retained.nodeId(), label,
                upgradeText("reason." + retained.reason().name.lowercase(Locale.ROOT)).string).string)
        }
        if (plan.changes().isEmpty()) add(upgradeText("no_changes").string)
        for (diagnostic in plan.diagnostics()) {
            add(ProgramDiagnosticText.describe(plan.source(), catalog, diagnostic).string)
        }
        numericUpgradeCachedPlan = plan
        numericUpgradeCachedError = numericUpgradeError
        numericUpgradeCachedLanguage = language
        numericUpgradeCachedExtensions = extensions
        numericUpgradeCachedWidth = width
        numericUpgradeCachedLines = lines.toList()
        return numericUpgradeCachedLines
    }

    private fun scrollNumericUpgrade(delta: Int) {
        numericUpgradeScroll = (numericUpgradeScroll + delta)
            .coerceIn(0, maxOf(0, numericUpgradeLines().size - numericUpgradeVisibleRows()))
    }

    private fun renderNumericUpgradePreview(graphics: ProgramUiGraphics, mouseX: Int, mouseY: Int) {
        val plan = numericUpgradePreview ?: return
        val b = numericUpgradeBounds()
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xC0000000.toInt())
        graphics.fill(b.x, b.y, b.x + b.width, b.y + b.height, POPUP_BACKGROUND)
        border(graphics, b.x, b.y, b.width, b.height, BORDER)
        headingText(graphics, upgradeText("title").string, b.x + 8, b.y + 8, TEXT, b.width - 16)
        val lines = numericUpgradeLines()
        val rows = numericUpgradeVisibleRows()
        numericUpgradeScroll = numericUpgradeScroll.coerceIn(0, maxOf(0, lines.size - rows))
        graphics.enableScissor(b.x + 8, b.y + 24, b.x + b.width - 8, b.y + b.height - 40)
        try {
            for (i in 0 until rows) {
                val index = numericUpgradeScroll + i
                if (index >= lines.size) break
                smallText(graphics, lines[index], b.x + 8, b.y + 25 + i * 10,
                    if (numericUpgradeError != null && index == 0) ERROR else DIM, b.width - 26)
            }
        } finally { graphics.disableScissor() }
        if (lines.size > rows) {
            val track = (b.height - 68).coerceAtLeast(1)
            val thumb = maxOf(8, track * rows / lines.size).coerceAtMost(track)
            val top = b.y + 25 + (track - thumb) * numericUpgradeScroll / (lines.size - rows)
            graphics.fill(b.x + b.width - 11, top, b.x + b.width - 9, top + thumb, accentColor)
        }
        smallText(graphics, upgradeText("scroll_hint").string, b.x + 8, b.y + b.height - 39, DIM, b.width - 16)
        val keep = numericUpgradeKeepBounds()
        button(graphics, keep.x, keep.y, keep.width, keep.height, upgradeText("keep"), mouseX, mouseY, false, false)
        val apply = numericUpgradeApplyBounds()
        val enabled = plan.canApply() && numericUpgradeIdentityCurrent(plan)
        renderControl(graphics, apply.x, apply.y, apply.width, apply.height, enabled, false,
            enabled && inside(mouseX.toDouble(), mouseY.toDouble(), apply.x.toDouble(), apply.y.toDouble(),
                apply.width.toDouble(), apply.height.toDouble()))
        graphics.centeredText(upgradeText("apply").string, apply.x + apply.width / 2f, apply.y + 6f,
            if (enabled) TEXT else DISABLED, ProgramUiGraphics.BODY_FONT_SIZE, (apply.width - 6).toFloat())
    }

    private fun handleNumericUpgradeClick(x: Double, y: Double): Boolean {
        if (numericUpgradePreview == null) return false
        val keep = numericUpgradeKeepBounds()
        val apply = numericUpgradeApplyBounds()
        if (inside(x, y, keep.x.toDouble(), keep.y.toDouble(), keep.width.toDouble(), keep.height.toDouble())) {
            closeNumericUpgradePreview()
        } else if (inside(x, y, apply.x.toDouble(), apply.y.toDouble(), apply.width.toDouble(), apply.height.toDouble())) {
            applyNumericUpgrade()
        }
        return true
    }

    private fun numericUpgradeIdentityCurrent(plan: ProgramNumericUpgrades.Plan): Boolean =
        Minecraft.getInstance().gui.screen() === this && slot == numericUpgradeSlot
            && revision == numericUpgradeRevision && session.revision() == numericUpgradeSessionRevision
            && ProgramNodeExtensionIndex.snapshot(plan.source().category()) === numericUpgradeExtensions

    private fun applyNumericUpgrade(): Boolean {
        val plan = numericUpgradePreview ?: return false
        fun fail(key: String, vararg args: Any): Boolean {
            numericUpgradeError = upgradeText(key, *args)
            numericUpgradeScroll = 0
            clearNumericUpgradeLines()
            return false
        }
        if (!numericUpgradeIdentityCurrent(plan) || !ProgramUpgradeBackup.sameProgram(plan.source(), document.program())) {
            return fail("stale")
        }
        val fresh: ProgramNumericUpgrades.Plan
        val replacement: ProgramEditorDocument
        try {
            val currentCapabilities = session.capabilities().toSet()
            fresh = ProgramNumericUpgrades.preview(document.program(), definition, currentCapabilities)
            if (!fresh.canApply() || !ProgramUpgradeBackup.sameProgram(fresh.source(), plan.source())
                || !ProgramUpgradeBackup.sameProgram(fresh.candidate(), plan.candidate())
                || !definition.compile(fresh.candidate(), currentCapabilities).valid()) return fail("validation_failed")
            replacement = ProgramEditorDocument(fresh.candidate(), definition, currentCapabilities)
            // Both the new document and a future undo must preserve the exact archived graph.
            if (!ProgramUpgradeBackup.sameProgram(replacement.program(), fresh.candidate())
                || !ProgramUpgradeBackup.sameProgram(document(fresh.source()).program(), fresh.source())) {
                return fail("normalization")
            }
        } catch (_: RuntimeException) { return fail("validation_failed") }
        val receipt = try {
            ProgramUpgradeBackup.create(Minecraft.getInstance().gameDirectory.toPath(), slot,
                numericUpgradeSessionRevision, fresh.source(), fresh.candidate())
        } catch (failure: IOException) {
            return fail("backup_failed", failure.message ?: failure.javaClass.simpleName)
        }
        numericUpgradeLastBackup = receipt
        if (!numericUpgradeIdentityCurrent(plan) || !ProgramUpgradeBackup.sameProgram(plan.source(), document.program())) {
            return fail("stale_backup_written", receipt.path().toString())
        }
        pushUndo(fresh.source())
        closeNumericUpgradePreview()
        install(replacement, true)
        selectNode(-1)
        selectionDrag = null
        connection = null
        quickInsert = null
        configurationInputValidity.clear()
        transientDiagnostic = PrecisionGraph.Diagnostic.OK
        transientDetail = upgradeText("backup_saved", receipt.path().toString())
        transientUntil = System.currentTimeMillis() + 15000
        return true
    }

    private fun slotTabWidth(startX: Int): Int {
        val toolsX = panelX + panelW - TOOL_LABELS.size * (TOOL_SIZE + 2) - 2
        val count = maxOf(1, session.slotCount())
        val available = maxOf(1, toolsX - startX - (count - 1) * 2 - 2)
        return (available / count).coerceIn(18, 38)
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
        val width = paletteWidth()
        if (inside(mouseX, mouseY, (x + width - 37).toDouble(), (canvasY + 1).toDouble(), 33.0, 12.0)) {
            return openTaskTemplates()
        }
        val layers = ProgramPaletteCatalog.Layer.entries
        val layerW = (width - 8) / layers.size
        for (index in layers.indices) {
            if (inside(mouseX, mouseY, (x + 4 + index * layerW).toDouble(),
                    (canvasY + PALETTE_LAYER_OFFSET_Y).toDouble(), (layerW - 1).toDouble(), 12.0)) {
                paletteLayer = layers[index]
                paletteFamilyId = null
                paletteScroll = 0
                return true
            }
        }
        val tabY = canvasY + PALETTE_TAB_OFFSET_Y
        if (selectedPaletteFamily() != null) {
            if (inside(mouseX, mouseY, (x + 4).toDouble(), tabY.toDouble(), (width - 8).toDouble(), 12.0)) {
                paletteFamilyId = null
                paletteScroll = 0
                return true
            }
        } else if (paletteLayer != ProgramPaletteCatalog.Layer.COMMON) {
            val groups = ProgramEditorNodeCatalog.Group.entries
            val tabW = maxOf(11, (width - 8) / groups.size)
            for (index in groups.indices) {
                if (inside(mouseX, mouseY, (x + 4 + index * tabW).toDouble(),
                        tabY.toDouble(), (tabW - 1).toDouble(), 12.0)) {
                    selectedGroup = groups[index]
                    paletteScroll = 0
                    return true
                }
            }
        }
        val listY = canvasY + PALETTE_LIST_OFFSET_Y
        if (!inside(mouseX, mouseY, (x + 3).toDouble(), listY.toDouble(), (width - 6).toDouble(),
                (canvasY + canvasH - 3 - listY).toDouble())) return false
        val row = ((mouseY - listY) / ROW_H).toInt()
        val visibleRows = maxOf(1, (canvasY + canvasH - 3 - listY) / ROW_H)
        if (row >= visibleRows) return false
        val cards = paletteCards()
        val index = paletteScroll + row
        if (index !in cards.indices) return false
        val card = cards[index]
        if (card.family()) {
            paletteFamilyId = card.id()
            paletteScroll = 0
        } else {
            addNode(card.members().first(), screenToGraphX(canvasX + canvasW / 2.0),
                screenToGraphY(canvasY + canvasH / 2.0))
        }
        return true
    }

    private fun handleInspectorClick(mouseX: Double, mouseY: Double): Boolean {
        if (!inspectorVisible()) return false
        if (!inside(mouseX, mouseY, inspectorX().toDouble(), canvasY.toDouble(),
                inspectorWidth().toDouble(), canvasH.toDouble())) return false
        val tabWidth = (inspectorWidth() - 8) / 3
        if (!session.precisionRules() && inside(mouseX, mouseY,
                (inspectorX() + 4).toDouble(), (canvasY + 2).toDouble(), (tabWidth * 3).toDouble(), 15.0)) {
            val tab = ((mouseX - inspectorX() - 4) / tabWidth).toInt()
            showTaskPreview = tab == 1
            showRunTrace = tab == 2
            if (showTaskPreview) { previewDocument = null; previewScroll = 0 }
            return true
        }
        if (showTaskPreview) {
            val row = ((mouseY - canvasY - 23) / 10).toInt()
            val lines = taskPreviewLines()
            val index = previewScroll + row
            if (mouseY >= canvasY + 23 && index in lines.indices && node(lines[index].nodeId) != null) {
                selectNode(lines[index].nodeId)
                showTaskPreview = false
            }
            return true
        }
        if (showRunTrace) {
            val offset = runTraceOffset()
            val row = ((mouseY - canvasY - offset) / 25).toInt()
            val index = runTraceScroll + row
            if (session.restoredProgram(slot) == document.program() && mouseY >= canvasY + offset &&
                row < traceVisibleRows() && index in runTrace.indices) {
                val traceNode = node(runTrace[index].nodeId())
                if (traceNode != null) {
                    selectNode(traceNode.id())
                    showRunTrace = false
                }
            }
            return true
        }
        val selected = node(selectedNode) ?: return false
        val fields = configurationFields(selected)
        if (fields.isEmpty()) return false
        val width = inspectorWidth() - 10
        val descriptionHeight = ProgramUiGraphics.wrappedHeight(
            nodeDescription(selected.entry).string, width.toFloat(),
            ProgramUiGraphics.BODY_FONT_SIZE, 9.0f
        )
        val y = canvasY + inspectorConfigurationOffset(selected.entry, descriptionHeight)
        val x = inspectorX() + 5
        var rowY = y
        for (field in fields) {
            val currentValue = selected.source.configuration().asJsonObject.get(field)
            val options = ProgramConfigurationOptions.options(selected.entry, field, currentValue)
            val valueY = rowY + 11
            if (ProgramConfigurationOptions.isToggle(field, currentValue)
                && inside(mouseX, mouseY, x.toDouble(), valueY.toDouble(), width.toDouble(), TOOL_SIZE.toDouble())
            ) {
                toggleConfiguration(selected, field, currentValue)
                return true
            }
            if (ProgramConfigurationOptions.isPowerSlider(field, currentValue)) {
                val trackWidth = maxOf(16, width - 36)
                if (inside(
                        mouseX,
                        mouseY,
                        x.toDouble(),
                        valueY.toDouble(),
                        trackWidth.toDouble(),
                        TOOL_SIZE.toDouble()
                    )
                ) {
                    pushUndo()
                    draggingPowerField = field
                    draggingPowerNode = selected.id()
                    updatePowerSlider(selected, field, mouseX)
                    return true
                }
            }
            if (options.isNotEmpty() && inside(
                    mouseX,
                    mouseY,
                    x.toDouble(),
                    valueY.toDouble(),
                    TOOL_SIZE.toDouble(),
                    TOOL_SIZE.toDouble()
                )
            ) {
                stepConfiguration(selected, field, currentValue, options, -1)
                return true
            }
            if (options.isNotEmpty()
                && inside(
                    mouseX,
                    mouseY,
                    (x + width - TOOL_SIZE).toDouble(),
                    valueY.toDouble(),
                    TOOL_SIZE.toDouble(),
                    TOOL_SIZE.toDouble()
                )
            ) {
                stepConfiguration(selected, field, currentValue, options, 1)
                return true
            }
            rowY += configurationRowHeight(selected, field, width)
        }
        return false
    }

    private fun toggleConfiguration(
        selected: NodeView,
        field: String,
        currentValue: JsonElement
    ) {
        val obj = selected.source.configuration().asJsonObject.deepCopy()
        obj.addProperty(field, !currentValue.asBoolean)
        val result = document.configureNode(selected.id(), obj)
        if (!result.successful()) {
            showTransient(PrecisionGraph.Diagnostic.INVALID_PARAMETER)
            return
        }
        pushUndo()
        configurationInputValidity.clear()
        install(result.document(), true)
        configurationNode = -1
        return
    }

    private fun updatePowerSlider(selected: NodeView, field: String, mouseX: Double) {
        val width = inspectorWidth() - 10
        val trackWidth = maxOf(16, width - 36)
        val trackX = inspectorX() + 5
        val progress = ((mouseX - trackX) / trackWidth).coerceIn(0.0, 1.0)
        var power =
            ProgramPowerScale.MIN.toDouble() + progress * (ProgramPowerScale.MAX - ProgramPowerScale.MIN).toDouble()
        power = (power * 100.0).roundToLong() / 100.0
        power = power.coerceIn(ProgramPowerScale.MIN.toDouble(), ProgramPowerScale.MAX.toDouble())
        val obj = selected.source.configuration().asJsonObject.deepCopy()
        obj.addProperty(field, power)
        val result = document.configureNode(selected.id(), obj)
        if (!result.successful()) {
            showTransient(PrecisionGraph.Diagnostic.INVALID_PARAMETER)
            return
        }
        configurationInputValidity.clear()
        install(result.document(), true)
        configurationNode = -1
    }

    private fun stepConfiguration(
        selected: NodeView,
        field: String,
        currentValue: JsonElement,
        options: List<ProgramConfigurationOptions.Option>,
        direction: Int
    ) {
        val obj = selected.source.configuration().asJsonObject.deepCopy()
        val next = ProgramConfigurationOptions.step(options, currentValue, direction)
        obj.add(field, next.value().deepCopy())
        if (selected.entry.id() == CommonProgramNodeIds.SCALAR_CONSTANT && field == "type") {
            obj.addProperty("value", if (next.value().asString == "boolean") "false" else "0")
        }
        val result = document.configureNode(selected.id(), obj)
        if (!result.successful()) {
            showTransient(PrecisionGraph.Diagnostic.INVALID_PARAMETER)
            return
        }
        pushUndo()
        configurationInputValidity.clear()
        install(result.document(), true)
        configurationNode = -1
        return
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
                selectNode(endpoint.nodeId)
            }
            return true
        }
        for (node in nodes().asReversed()) {
            if (insideHeader(mouseX, mouseY, node)) {
                if (!selectedNodes.contains(node.id())) selectNode(node.id())
                pushUndo()
                draggingNode = node.id()
                return true
            }
            if (insideNode(mouseX, mouseY, node)) {
                selectNode(node.id())
                connection = null
                return true
            }
        }
        selectNode(-1)
        connection = null
        selectionDrag = SelectionDrag(mouseX, mouseY, mouseX, mouseY)
        return true
    }

    private fun updateSelectionDrag(mouseX: Double, mouseY: Double) {
        val current = selectionDrag ?: return
        val clampedX = mouseX.coerceIn(canvasX.toDouble(), (canvasX + canvasW).toDouble())
        val clampedY = mouseY.coerceIn(canvasY.toDouble(), (canvasY + canvasH).toDouble())
        selectionDrag = current.update(clampedX, clampedY)
        val screenBounds = selectionDrag!!.bounds()
        if (!screenBounds.exceeds(SELECTION_DRAG_THRESHOLD)) {
            selectNodes(emptySet())
            return
        }
        val graphBounds = PrecisionEditorGeometry.selectionBounds(
            screenToGraphX(screenBounds.left()),
            screenToGraphY(screenBounds.top()),
            screenToGraphX(screenBounds.right()),
            screenToGraphY(screenBounds.bottom())
        )
        val selected = LinkedHashSet<Int>()
        for (node in nodes()) {
            if (graphBounds.intersects(node.x, node.y, NODE_W.toDouble(), nodeHeight(node).toDouble())) {
                selected.add(node.id())
            }
        }
        selectNodes(selected)
    }

    private fun finishConnection(mouseX: Double, mouseY: Double) {
        val current = connection ?: return
        val moved = hypot(mouseX - current.startX, mouseY - current.startY) > 3.0
        val target = snappedEndpoint(mouseX, mouseY, current.endpoint)
        if (target != null && compatible(current.endpoint, target)) {
            connect(current.endpoint, target)
            connection = null
            return
        }
        if (moved) {
            val entries = compatibleEntries(current.endpoint)
            if (entries.isNotEmpty()) {
                quickInsert = QuickInsert(mouseX.toInt(), mouseY.toInt(), entries, current.endpoint)
            }
        }
        connection = null
    }

    private fun handleQuickInsertClick(mouseX: Double, mouseY: Double): Boolean {
        val current = quickInsert ?: return false
        val entries = quickInsertEntries(current)
        val bounds = quickInsertBounds(current, entries.size)
        if (!inside(mouseX, mouseY, bounds.x.toDouble(), bounds.y.toDouble(),
                bounds.width.toDouble(), bounds.height.toDouble())) {
            quickInsert = null
            return false
        }
        val row = ((mouseY - bounds.y - 20) / ROW_H).toInt()
        val index = current.scroll + row
        if (mouseY >= bounds.y + 20 && row in 0 until 8 && index in entries.indices) {
            insertQuickEntry(current, entries[index])
        }
        return true
    }

    private fun insertQuickEntry(current: QuickInsert, entry: ProgramEditorNodeCatalog.Entry) {
        val anchor = current.anchor
        val added = addNode(
            entry,
            screenToGraphX(current.x.toDouble()), screenToGraphY(current.y.toDouble()),
            ProgramConfigurationOptions.defaultsForConnection(catalog, entry, anchor.type, anchor.input)
        )
        if (added != null) {
            val endpoint = firstCompatibleEndpoint(added, anchor)
            if (endpoint != null) connect(anchor, endpoint)
        }
        quickInsert = null
    }

    private fun connect(first: Endpoint, second: Endpoint) {
        if (!compatible(first, second)) {
            showTransient(PrecisionGraph.Diagnostic.TYPE_MISMATCH)
            return
        }
        val output = if (first.input) second else first
        val input = if (first.input) first else second
        var working = document
        for (edge in working.program().graph().edges().toList()) {
            if (edge.to() == input.graphEndpoint()) {
                working = working.disconnect(edge.from(), edge.to()).orElseThrow()
            }
        }
        val outputNode = node(output.nodeId)
        val outputDefinition = outputNode?.schema?.output(output.port)?.orElse(null)
        if (outputDefinition != null && outputDefinition.maxConnections() == 1) {
            for (edge in working.program().graph().edges().toList()) {
                if (edge.from() == output.graphEndpoint()) {
                    working = working.disconnect(edge.from(), edge.to()).orElseThrow()
                }
            }
        }
        val result = working.connect(output.graphEndpoint(), input.graphEndpoint())
        if (!result.successful()) {
            showTransient(result.diagnostic())
            return
        }
        pushUndo()
        install(result.document(), true)
    }

    private fun disconnect(endpoint: Endpoint) {
        var working = document
        var changed = false
        for (edge in working.program().graph().edges().toList()) {
            if (endpoint.input && edge.to() == endpoint.graphEndpoint()
                || !endpoint.input && edge.from() == endpoint.graphEndpoint()
            ) {
                working = working.disconnect(edge.from(), edge.to()).orElseThrow()
                changed = true
            }
        }
        if (!changed) return
        pushUndo()
        install(working, true)
    }

    private fun addNode(entry: ProgramEditorNodeCatalog.Entry, x: Double, y: Double): NodeView? {
        return addNode(entry, x, y, entry.defaultConfiguration())
    }

    private fun addNode(
        entry: ProgramEditorNodeCatalog.Entry, x: Double, y: Double, configuration: JsonElement?
    ): NodeView? {
        val existingIds = document.program().graph().nodes().map { it.id() }.toSet()
        val result = document.addNode(entry.id(), x, y, configuration)
        if (!result.successful()) {
            showTransient(result.diagnostic())
            return null
        }
        pushUndo()
        install(result.document(), true)
        val addedId = document.program().graph().nodes().map { it.id() }.firstOrNull { it !in existingIds } ?: -1
        selectNode(addedId)
        return node(selectedNode)
    }

    private fun deleteSelected() {
        if (selectedNodes.isEmpty()) return
        val result = document.removeNodes(selectedNodes.toSet())
        if (!result.successful()) {
            showTransient(result.diagnostic())
            return
        }
        pushUndo()
        install(result.document(), true)
        selectNode(-1)
    }

    private fun copySelected() {
        val copyable = LinkedHashSet<Int>()
        for (id in selectedNodes) {
            val selected = node(id)
            if (selected != null && selected.entry.type().role() != ProgramNodeRole.ENTRY) copyable.add(id)
        }
        if (copyable.isEmpty()) return
        UiEnvironment.get().setClipboard(ProgramClipboardCodec.encodeFragment(document.program(), copyable))
    }

    private fun pasteClipboard() {
        val fragment = ProgramClipboardCodec.decodeFragment(
            UiEnvironment.get().clipboard(), document.program().category()
        )
        if (fragment == null || fragment.graph().nodes().isEmpty()) {
            showTransient(PrecisionGraph.Diagnostic.MALFORMED)
            return
        }
        var working = document
        val idMap = HashMap<Int, Int>()
        val newIds = LinkedHashSet<Int>()
        for (source in fragment.graph().nodes().sortedBy { it.id() }) {
            val position = fragment.editorLayout().nodePositions()[source.id()]
            val x = (position?.x() ?: 0.0) + 18.0
            val y = (position?.y() ?: 0.0) + 18.0
            val before = working.program().graph().nodes().map { it.id() }.toSet()
            val added = working.addNode(source.type(), x, y)
            if (!added.successful()) {
                showTransient(added.diagnostic())
                return
            }
            val addedId =
                added.document()!!.program().graph().nodes().map { it.id() }.firstOrNull { it !in before } ?: -1
            val configured = added.document()!!.configureNode(addedId, source.configuration())
            if (!configured.successful()) {
                showTransient(configured.diagnostic())
                return
            }
            working = configured.document()!!
            idMap[source.id()] = addedId
            newIds.add(addedId)
        }
        for (edge in fragment.graph().edges()) {
            val from = idMap[edge.from().nodeId()]
            val to = idMap[edge.to().nodeId()]
            if (from == null || to == null) continue
            val connected = working.connect(
                ProgramGraph.Endpoint(from, edge.from().port()),
                ProgramGraph.Endpoint(to, edge.to().port())
            )
            if (!connected.successful()) {
                showTransient(connected.diagnostic())
                return
            }
            working = connected.document()!!
        }
        pushUndo()
        install(working, true)
        selectNodes(newIds)
    }

    private fun exportProgram() {
        UiEnvironment.get().setClipboard(ProgramClipboardCodec.encodeProgram(document.program()))
    }

    private fun importProgram() {
        val imported = ProgramClipboardCodec.decodeProgram(
            UiEnvironment.get().clipboard(), document.program().category()
        )
        if (imported == null) {
            showTransient(PrecisionGraph.Diagnostic.MALFORMED)
            return
        }
        val current = document.program()
        val replacement = AbilityProgram(
            AbilityProgram.CURRENT_SCHEMA_VERSION,
            current.id(),
            current.name(),
            current.category(),
            imported.graph(),
            imported.editorLayout()
        )
        val importedDocument = document(replacement)
        if (importedDocument.validation().diagnostics().any { diagnostic ->
                diagnostic.code() == ProgramDiagnosticCode.UNKNOWN_NODE_TYPE
                        || diagnostic.code() == ProgramDiagnosticCode.CATEGORY_MISMATCH
                        || diagnostic.code() == ProgramDiagnosticCode.CAPABILITY_MISSING
            }
        ) {
            showTransient(PrecisionGraph.Diagnostic.MALFORMED)
            return
        }
        setProgram(replacement, true)
    }

    private fun autoLayout() {
        if (nodes().isEmpty()) return
        pushUndo()
        val layers = HashMap<Int, Int>()
        repeat(nodes().size) {
            for (node in nodes()) {
                if (node.entry.type().role().requiresFlow()) continue
                val layer = document.program().graph().edges()
                    .filter { edge -> edge.to().nodeId() == node.id() }
                    .filter { edge ->
                        val source = node(edge.from().nodeId())
                        source != null && source.schema.output(edge.from().port())
                            .map { port -> port.type() != ProgramValueTypes.FLOW }
                            .orElse(false) == true
                    }
                    .maxOfOrNull { edge -> (layers[edge.from().nodeId()] ?: 0) + 1 } ?: 0
                layers[node.id()] = layer
            }
        }
        val maxDataLayer = layers.values.maxOrNull() ?: 0
        val rows = HashMap<Int, Int>()
        var working = document
        for (node in nodes().sortedBy { node ->
            if (node.entry.type().role().requiresFlow()) maxDataLayer + 1 else layers[node.id()] ?: 0
        }) {
            val layer = if (node.entry.type().role().requiresFlow()) maxDataLayer + 1 else layers[node.id()] ?: 0
            val row = (rows.merge(layer, 1) { a, b -> a + b } ?: 0) - 1
            working = working.moveNode(node.id(), 8 + layer * 108.0, 8 + row * 46.0).orElseThrow()
        }
        install(working, true)
        fitCanvas(false)
    }

    private fun fitCanvas(initial: Boolean) {
        val nodes = nodes()
        if (nodes.isEmpty()) {
            zoom = 1.0
            panX = 0.0
            panY = 0.0
            return
        }
        val minX = nodes.minOfOrNull { it.x } ?: 0.0
        val minY = nodes.minOfOrNull { it.y } ?: 0.0
        val maxX = nodes.maxOfOrNull { it.x + NODE_W } ?: NODE_W.toDouble()
        val maxY = nodes.maxOfOrNull { it.y + nodeHeight(it) } ?: MIN_NODE_H.toDouble()
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

    private fun undo() {
        if (undo.isEmpty()) return
        redo.push(document.program())
        setProgram(undo.pop(), false)
    }

    private fun redo() {
        if (redo.isEmpty()) return
        undo.push(document.program())
        setProgram(redo.pop(), false)
    }

    private fun save() {
        if (!configurationInputsValid()) {
            showTransient(PrecisionGraph.Diagnostic.INVALID_PARAMETER)
            return
        }
        val validation = document.validation()
        if (!validation.valid()) {
            showTransient(validation.diagnostics().first())
            return
        }
        val hasAction = document.program().graph().nodes().any { node ->
            if (!session.precisionRules()) {
                val type = definition.nodeLookup().find(node.type())
                type != null && type.role() == ProgramNodeRole.ACTION
            } else {
                val kind = PrecisionProgramNodeIds.kind(node.type())
                kind.isAction && !kind.isConditionalBranch
            }
        }
        if (session.precisionRules() && !hasAction) {
            showTransient(PrecisionGraph.Diagnostic.EMPTY_PROGRAM)
            return
        }
        session.saveProgram(
            slot,
            if (document.program().graph().nodes().isEmpty()) null else document.program(),
            revision
        )
        revision = session.revision()
    }

    private fun pushUndo() {
        pushUndo(document.program())
    }

    private fun pushUndo(program: AbilityProgram) {
        undo.push(program)
        while (undo.size > 64) undo.removeLast()
        redo.clear()
    }

    private fun setProgram(program: AbilityProgram?, recordUndo: Boolean) {
        closeNumericUpgradePreview()
        if (recordUndo) pushUndo()
        document = document(program ?: session.emptyProgram(slot))
        selectNode(-1)
        selectionDrag = null
        connection = null
        quickInsert = null
        configurationInputValidity.clear()
        serverCompileDiagnostic = null
        transientUntil = 0L
        serverDiagnostic = session.diagnostic(slot)
        serverVmDiagnostic = ProgramVmDiagnostic.NONE
        serverVmPort = null
        serverVmDetail = null
        serverDiagnosticNode = session.diagnosticNode(slot)
        session.restoreDiagnostic(this, slot)
        session.updateLocalProgram(slot, document.program())
    }

    private fun install(next: ProgramEditorDocument?, clearServerDiagnostic: Boolean) {
        document = next!!
        session.updateLocalProgram(slot, document.program())
        if (clearServerDiagnostic) {
            session.clearDiagnostic(slot)
            serverCompileDiagnostic = null
            serverDiagnostic = PrecisionGraph.Diagnostic.OK
            serverVmDiagnostic = ProgramVmDiagnostic.NONE
            serverVmPort = null
            serverVmDetail = null
            serverDiagnosticNode = -1
        }
    }

    private fun serverDiagnosticText(): String {
        val key = if (serverVmDiagnostic == ProgramVmDiagnostic.NONE) serverDiagnostic.translationKey()
        else serverVmDiagnostic.translationKey()
        return if (serverCompileDiagnostic != null)
            ProgramDiagnosticText.describe(document.program(), catalog, serverCompileDiagnostic!!).string
        else ProgramDiagnosticText.locateRuntimeFailure(
            document.program(), catalog, serverDiagnosticNode, serverVmPort, serverVmDetail,
            Component.translatable(key)
        ).string
    }

    private fun document(program: AbilityProgram?): ProgramEditorDocument {
        if (program == null) throw IllegalArgumentException("Editor program cannot be null")
        return ProgramEditorDocument(program, definition, capabilities)
    }

    private fun paletteEntries(): List<ProgramEditorNodeCatalog.Entry> = profileEditor("precision.editor.palette.entries") {
        val cache = refreshSemanticCaches()
        if (cache) cachedPaletteEntries?.let { return@profileEditor it }
        countEditor(ProgramEditorMetrics.Counter.PALETTE_REBUILT)
        val entries = buildList {
            for (entry in catalog.entries()) {
                if (!ProgramNodePalette.hasProvider(entry.id())) {
                    add(entry)
                    continue
                }
                for (preset in ProgramNodePalette.presets(entry.id())) {
                    val schema = catalog.schema(entry.id(), preset.configuration()) ?: continue
                    add(presetEntry(entry, preset.configuration(), schema))
                }
            }
        }
        if (cache) cachedPaletteEntries = entries
        entries
    }

    private fun presetEntry(
        entry: ProgramEditorNodeCatalog.Entry,
        configuration: JsonElement,
        schema: ProgramNodeSchema
    ): ProgramEditorNodeCatalog.Entry = ProgramEditorNodeCatalog.Entry(
        entry.id(), entry.type(), configuration, schema,
        entry.group(), entry.displayName(), entry.translationKey(), entry.portTranslationPrefix(),
        entry.visible(), entry.metadata()
    )

    private fun rootPaletteCards(): List<ProgramPaletteCatalog.Card> = profileEditor("precision.editor.palette.cards") {
        countEditor(ProgramEditorMetrics.Counter.PALETTE_REQUESTS)
        val cache = refreshSemanticCaches()
        val query = search?.text ?: ""
        if (cache && cachedCardQuery == query && cachedCardLayer == paletteLayer) {
            cachedRootCards?.let {
                countEditor(ProgramEditorMetrics.Counter.PALETTE_CACHE_HITS)
                return@profileEditor it
            }
        }
        val cards = ProgramPaletteCatalog.cards(
            paletteEntries(), capabilities, nodes().any { it.entry.type().role() == ProgramNodeRole.ENTRY },
            paletteLayer, query,
            { key -> Component.translatable(key).string },
            { entry -> nodeLabel(entry).string + "\n" + nodeDescription(entry).string + "\n" +
                categoryScopeLabel(entry).string + "\n" + Component.translatable(groupKey(entry.group())).string }
        )
        if (cache) {
            cachedRootCards = cards
            cachedCardQuery = query
            cachedCardLayer = paletteLayer
        }
        cards
    }

    private fun selectedPaletteFamily(): ProgramPaletteCatalog.Card? =
        paletteFamilyId?.let { id -> rootPaletteCards().firstOrNull { it.id() == id && it.family() } }

    private fun paletteCards(): List<ProgramPaletteCatalog.Card> {
        val cards = rootPaletteCards()
        val family = cards.firstOrNull { it.id() == paletteFamilyId && it.family() }
        if (family != null) return family.members().mapIndexed { index, entry ->
            ProgramPaletteCatalog.Card("${family.id()}/$index", entry.translationKey(),
                entry.descriptionTranslationKey(), entry.group(), listOf(entry), false)
        }
        return if (paletteLayer != ProgramPaletteCatalog.Layer.COMMON && search?.text.isNullOrBlank())
            cards.filter { it.group() == selectedGroup } else cards
    }

    private fun cardLabel(card: ProgramPaletteCatalog.Card): String = if (card.family())
        Component.translatable(card.titleTranslationKey()).string else nodeLabel(card.members().first()).string

    private fun cardTooltip(card: ProgramPaletteCatalog.Card): List<TooltipLine> = if (!card.family())
        tooltipLines(card.members().first()) else listOf(
            TooltipLine(cardLabel(card), TEXT),
            TooltipLine(Component.translatable(card.descriptionTranslationKey()).string, DIM),
            TooltipLine(Component.translatable("screen.academy.program.palette.family_hint", card.members().size).string, DIM)
        )

    private fun compatibleEntries(anchor: Endpoint): List<ProgramEditorNodeCatalog.Entry> {
        return paletteEntries()
            .asSequence()
            .filter { it.visible() }
            .filter { it.presentationStatus() == ProgramEditorNodeCatalog.PresentationStatus.AVAILABLE }
            .filter { it.type().role() != ProgramNodeRole.ENTRY }
            .filter { entryUnlocked(it) }
            .filter {
                ProgramConfigurationOptions.defaultsForConnection(catalog, it, anchor.type, anchor.input) != null
            }
            .sortedBy { entry ->
                ProgramConfigurationOptions.connectionScore(
                    catalog, entry,
                    ProgramConfigurationOptions.defaultsForConnection(catalog, entry, anchor.type, anchor.input),
                    anchor.type, anchor.input
                )
            }
            .toList()
    }

    private fun entryUnlocked(entry: ProgramEditorNodeCatalog.Entry): Boolean =
        capabilities.containsAll(entry.type().scope().requiredCapabilities())

    private fun firstCompatibleEndpoint(node: NodeView, anchor: Endpoint): Endpoint? {
        var ports = if (anchor.input) node.schema.outputs() else node.schema.inputs()
        ports = ports.sortedBy { if (it.type() == anchor.type) 0 else 1 }
        for (port in ports) {
            val compatible = if (anchor.input) ProgramValueTypes.canConnect(port.type(), anchor.type)
            else ProgramValueTypes.canConnect(anchor.type, port.type())
            if (compatible) return Endpoint(node.id(), port.name(), !anchor.input, port.type())
        }
        return null
    }

    private fun endpointAt(mouseX: Double, mouseY: Double): Endpoint? {
        var closest: Endpoint? = null
        var best = Double.MAX_VALUE
        for (node in nodes()) {
            for (input in booleanArrayOf(true, false)) {
                val ports = if (input) node.schema.inputs() else node.schema.outputs()
                for (port in ports) {
                    val endpoint = Endpoint(node.id(), port.name(), input, port.type())
                    val point = endpointScreen(endpoint)
                    val distance = hypot(mouseX - point.x, mouseY - point.y)
                    if (distance <= PORT_HIT / 2.0 && distance < best) {
                        closest = endpoint
                        best = distance
                    }
                }
            }
        }
        return closest
    }

    private fun snappedEndpoint(mouseX: Double, mouseY: Double, source: Endpoint): Endpoint? {
        var closest: Endpoint? = null
        var best = Double.MAX_VALUE
        for (node in nodes()) {
            val ports = if (source.input) node.schema.outputs() else node.schema.inputs()
            for (port in ports) {
                val candidate = Endpoint(node.id(), port.name(), !source.input, port.type())
                if (!compatible(source, candidate)) continue
                val point = endpointScreen(candidate)
                val distance = hypot(mouseX - point.x, mouseY - point.y)
                if (distance <= SNAP_DISTANCE && distance < best) {
                    closest = candidate
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
        return ProgramValueTypes.canConnect(output.type, input.type)
    }

    private fun connectionRejected(first: Endpoint, second: Endpoint): Boolean {
        if (!compatible(first, second)) return true
        val output = if (first.input) second else first
        val input = if (first.input) first else second
        var working = document
        for (edge in working.program().graph().edges().toList()) {
            if (edge.to() == input.graphEndpoint()) {
                working = working.disconnect(edge.from(), edge.to()).orElseThrow()
            }
        }
        val result = working.connect(output.graphEndpoint(), input.graphEndpoint())
        return !result.successful() && result.diagnostic()!!.code() == ProgramDiagnosticCode.DATA_CYCLE
    }

    private fun endpointConnected(endpoint: Endpoint): Boolean {
        return document.program().graph().edges().any { edge ->
            if (endpoint.input) edge.to() == endpoint.graphEndpoint()
            else edge.from() == endpoint.graphEndpoint()
        }
    }

    private fun highlightedPort(endpoint: Endpoint): Boolean {
        val current = connection ?: return false
        return current.endpoint.input != endpoint.input && compatible(current.endpoint, endpoint)
    }

    private fun endpointScreen(endpoint: Endpoint): ScreenPoint {
        val node = node(endpoint.nodeId) ?: return ScreenPoint(0, 0)
        val ports = if (endpoint.input) node.schema.inputs() else node.schema.outputs()
        val index = portIndex(ports, endpoint.port)
        val x = if (endpoint.input) node.x else node.x + NODE_W
        val y = node.y + portOffsetY(node, maxOf(0, index))
        return ScreenPoint(
            (canvasX + panX + x * zoom).roundToInt(),
            (canvasY + panY + y * zoom).roundToInt()
        )
    }

    private fun insideNode(mouseX: Double, mouseY: Double, node: NodeView): Boolean {
        val x = canvasX + panX + node.x * zoom
        val y = canvasY + panY + node.y * zoom
        return inside(mouseX, mouseY, x, y, NODE_W * zoom, nodeHeight(node) * zoom)
    }

    private fun insideHeader(mouseX: Double, mouseY: Double, node: NodeView): Boolean {
        val x = canvasX + panX + node.x * zoom
        val y = canvasY + panY + node.y * zoom
        return inside(mouseX, mouseY, x, y, NODE_W * zoom, NODE_HEADER_H * zoom)
    }

    private fun nodeHeight(node: NodeView): Int {
        return maxOf(
            MIN_NODE_H,
            NODE_HEADER_H + nodeConfigurationHeight(node)
                    + maxOf(node.schema.inputs().size, node.schema.outputs().size) * PORT_ROW_H + 3
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
        box.visibility = if (numericUpgradePreview == null && paletteVisible()) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
        if (!box.isVisible()) box.isFocused = false
        setTextBoxBounds(box, paletteX() + 4, canvasY + PALETTE_SEARCH_OFFSET_Y, paletteWidth() - 8)
    }

    private fun syncConfigurationInputs() {
        if (numericUpgradePreview != null) {
            clearConfigurationInputs()
            return
        }
        val selected = node(selectedNode)
        val fields = if (selected == null) emptyList() else configurationFields(selected)
        val visible = inspectorVisible() && !showRunTrace && !showTaskPreview && selected != null && fields.isNotEmpty()
        if (!visible) {
            clearConfigurationInputs()
            return
        }
        if (configurationNode != selected.id()) {
            clearConfigurationInputs()
            configurationNode = selected.id()
        }
        val width = inspectorWidth() - 10
        val descriptionHeight = ProgramUiGraphics.wrappedHeight(
            nodeDescription(selected.entry).string, width.toFloat(),
            ProgramUiGraphics.BODY_FONT_SIZE, 9.0f
        )
        val configurationY = canvasY + inspectorConfigurationOffset(selected.entry, descriptionHeight)
        var rowY = configurationY
        for (field in fields) {
            val currentValue = selected.source.configuration().asJsonObject.get(field)
            val options = ProgramConfigurationOptions.options(selected.entry, field, currentValue)
            val fieldY = rowY
            rowY += configurationRowHeight(selected, field, width)
            if (ProgramConfigurationOptions.isPowerSlider(field, currentValue) || options.isNotEmpty()) {
                val removed = configurationInputs.remove(field)
                if (removed != null) {
                    removed.isFocused = false
                    inputLayer.removeChild(configurationInputName(field))
                }
                configurationInputValidity.remove(field)
                continue
            }
            val input = configurationInputs.getOrPut(field) { createConfigurationInput(field) }
            input.visibility = Widget.Visibility.VISIBLE
            setTextBoxBounds(input, inspectorX() + 5, fieldY + 11, width)
            val expected = currentValue.asString
            if (!input.isFocused && input.text != expected) {
                updatingConfigurationInput = true
                input.text = expected
                updatingConfigurationInput = false
                configurationInputValidity[field] = true
                input.textColor = TEXT
            }
        }
        for (field in configurationInputs.keys.toList()) {
            if (fields.contains(field)) continue
            configurationInputs.remove(field)!!.isFocused = false
            inputLayer.removeChild(configurationInputName(field))
            configurationInputValidity.remove(field)
        }
    }

    private fun createConfigurationInput(field: String): TextInputWidget {
        val maximumLength = when (field) {
            "selectors" -> 512
            "text" -> CommonProgramNodeCatalog.DebugOutputConfiguration.MAX_TEXT_LENGTH
            else -> 128
        }
        val input = TextInputWidget(maximumLength)
        input.textSize = ProgramUiGraphics.BODY_FONT_SIZE
        input.background = null
        input.textColor = TEXT
        input.setOnTextChanged { value -> configurationInputChanged(field, value) }
        inputLayer.addChild(configurationInputName(field), input)
        configurationInputValidity[field] = true
        return input
    }

    private fun configurationInputChanged(field: String, value: String) {
        if (updatingConfigurationInput) return
        val selected = node(configurationNode) ?: return
        val fields = configurationFields(selected)
        if (!fields.contains(field)) return
        val obj = selected.source.configuration().asJsonObject.deepCopy()
        val previous = obj.get(field)
        val input = configurationInputs[field] ?: return
        val replacement: JsonElement
        try {
            if (previous.isJsonPrimitive && previous.asJsonPrimitive.isBoolean) {
                if (!value.equals("true", ignoreCase = true) && !value.equals("false", ignoreCase = true)) {
                    throw IllegalArgumentException("Invalid boolean")
                }
                replacement = JsonPrimitive(value.toBoolean())
            } else if (previous.isJsonPrimitive && previous.asJsonPrimitive.isNumber) {
                replacement = JsonPrimitive(BigDecimal(value))
            } else {
                replacement = JsonPrimitive(value)
            }
        } catch (_: RuntimeException) {
            configurationInputValidity[field] = false
            input.textColor = ERROR
            return
        }
        obj.add(field, replacement)
        val result = document.configureNode(selected.id(), obj)
        configurationInputValidity[field] = result.successful()
        input.textColor = if (result.successful()) TEXT else ERROR
        if (!result.successful()) return
        pushUndo()
        install(result.document(), true)
    }

    private fun configurationInputAt(x: Double, y: Double): TextInputWidget? {
        for (input in configurationInputs.values) {
            if (input.isVisible() && inside(
                    x, y,
                    input.x.toDouble(), input.y.toDouble(), input.width.toDouble(), input.height.toDouble()
                )
            ) {
                return input
            }
        }
        return null
    }

    private fun focusedConfigurationInput(): TextInputWidget? =
        configurationInputs.values.firstOrNull { it.isFocused }

    private fun unfocusConfigurationInputs() {
        configurationInputs.values.forEach { it.isFocused = false }
    }

    private fun clearConfigurationInputs() {
        unfocusConfigurationInputs()
        for (field in configurationInputs.keys.toList()) {
            inputLayer.removeChild(configurationInputName(field))
        }
        configurationInputs.clear()
        configurationInputValidity.clear()
        configurationNode = -1
    }

    private fun configurationInputsValid(): Boolean = configurationInputValidity.values.all { it }

    private fun selectNode(nodeId: Int) {
        selectNodes(if (nodeId >= 0) setOf(nodeId) else emptySet())
    }

    private fun selectNodes(nodeIds: Set<Int>) {
        inspectorPortScroll = 0
        selectedNodes.clear()
        document.program().graph().nodes().map { it.id() }.filter { it in nodeIds }.forEach { selectedNodes.add(it) }
        selectedNode = if (selectedNodes.size == 1) selectedNodes.iterator().next() else -1
        clearConfigurationInputs()
    }

    private fun nodes(): List<NodeView> = profileEditor("precision.editor.nodes") {
        countEditor(ProgramEditorMetrics.Counter.NODES_REQUESTS)
        val cache = refreshSemanticCaches()
        if (cache) cachedNodes?.let {
            countEditor(ProgramEditorMetrics.Counter.NODE_CACHE_HITS)
            return@profileEditor it
        }
        val byId = if (cache) LinkedHashMap<Int, NodeView?>() else null
        val result = document.program().graph().nodes().mapNotNull { source ->
            val resolved = view(source)
            // Preserve first-source lookup even for an invalid imported graph with duplicate IDs.
            if (byId != null && !byId.containsKey(source.id())) byId[source.id()] = resolved
            resolved
        }
        if (cache) {
            cachedNodes = result
            cachedNodesById = byId!!
        }
        result
    }

    private fun node(id: Int): NodeView? {
        if (refreshSemanticCaches()) {
            if (cachedNodes == null) nodes()
            return cachedNodesById[id]
        }
        val source = document.program().graph().nodes().firstOrNull { it.id() == id }
        return source?.let { view(it) }
    }

    private fun view(source: ProgramGraph.Node): NodeView? = profileEditor("precision.editor.view") {
        var entry = catalog.entry(source.type()) ?: return@profileEditor null
        countEditor(ProgramEditorMetrics.Counter.SCHEMA_DECODES)
        val schema = catalog.schema(source.type(), source.configuration()) ?: return@profileEditor null
        val position = document.program().editorLayout().nodePositions()[source.id()]
        if (ProgramNodePalette.hasProvider(entry.id())) {
            entry = presetEntry(entry, source.configuration(), schema)
        }
        countEditor(ProgramEditorMetrics.Counter.NODE_VIEWS_REBUILT)
        NodeView(source, entry, schema, position?.x() ?: 0.0, position?.y() ?: 0.0)
    }

    private fun firstDiagnostic(): ProgramDiagnostic? = profileEditor("precision.editor.validation") {
        editorValidation().diagnostics().firstOrNull()
    }

    private fun editorValidation(): ProgramValidationResult {
        countEditor(ProgramEditorMetrics.Counter.VALIDATION_REQUESTS)
        val cache = refreshSemanticCaches()
        if (cache) cachedValidation?.let {
            countEditor(ProgramEditorMetrics.Counter.VALIDATION_CACHE_HITS)
            return it
        }
        countEditor(ProgramEditorMetrics.Counter.VALIDATION_REBUILT)
        val result = document.validation()
        if (cache) cachedValidation = result
        return result
    }

    /** Immutable document replacement includes configuration, layout, import, undo and slot changes. */
    private fun refreshSemanticCaches(): Boolean {
        val language = Language.getInstance()
        val extensions = ProgramNodeExtensionIndex.snapshot(document.program().category())
        val paletteRevision = ProgramNodePalette.registrationRevision()
        if (cachedDocument !== document || cachedLanguage !== language || cachedExtensions !== extensions ||
            cachedPaletteRevision != paletteRevision) {
            cachedNodes = null
            cachedNodesById = emptyMap()
            cachedValidation = null
            cachedPaletteEntries = null
            cachedRootCards = null
            cachedCardQuery = null
            cachedCardLayer = null
            cachedDocument = document
            cachedLanguage = language
            cachedExtensions = extensions
            cachedPaletteRevision = paletteRevision
            // Extensions and preset suppliers may produce dynamic schemas or labels. Keep their live path.
            semanticCacheAllowed = extensions.registrations().isEmpty() &&
                catalog.entries().none { ProgramNodePalette.hasProvider(it.id()) } &&
                ProgramEditorCachePolicy.stableGraph(document.program().graph())
        }
        return semanticCacheAllowed
    }

    private fun resetPerformanceCapture(enabled: Boolean) = performanceMetrics.reset(enabled)

    private fun performanceSnapshot(): Map<String, Long> = performanceMetrics.snapshot()

    private fun countEditor(counter: ProgramEditorMetrics.Counter) {
        performanceMetrics.increment(counter)
        AcademyProfiler.incrementCounter(counter.key())
    }

    private inline fun <T> profileEditor(name: String, block: () -> T): T {
        if (!AcademyProfiler.isCapturingZones()) return block()
        AcademyProfiler.push(name)
        try {
            return block()
        } finally {
            AcademyProfiler.pop()
        }
    }

    private fun showTransient(diagnostic: ProgramDiagnostic?) {
        if (diagnostic == null) return
        showTransient(mapDiagnostic(diagnostic.code()))
        transientDetail = ProgramDiagnosticText.describe(document.program(), catalog, diagnostic)
    }

    private fun showTransient(diagnostic: PrecisionGraph.Diagnostic) {
        transientDetail = null
        transientDiagnostic = diagnostic
        transientUntil = System.currentTimeMillis() + 1800L
    }

    private data class TooltipLine(val text: String, val color: Int)

    private data class NodeView(
        val source: ProgramGraph.Node,
        val entry: ProgramEditorNodeCatalog.Entry,
        val schema: ProgramNodeSchema,
        val x: Double,
        val y: Double
    ) {
        fun id(): Int = source.id()
    }

    private data class Endpoint(
        val nodeId: Int,
        val port: String,
        val input: Boolean,
        val type: ProgramValueType
    ) {
        fun graphEndpoint(): ProgramGraph.Endpoint = ProgramGraph.Endpoint(nodeId, port)
    }

    private data class ConnectionDrag(val endpoint: Endpoint, val startX: Double, val startY: Double)

    private data class SelectionDrag(
        val startX: Double,
        val startY: Double,
        val currentX: Double,
        val currentY: Double
    ) {
        fun update(x: Double, y: Double): SelectionDrag = SelectionDrag(startX, startY, x, y)

        fun bounds(): PrecisionEditorGeometry.SelectionBounds =
            PrecisionEditorGeometry.selectionBounds(startX, startY, currentX, currentY)
    }

    private data class QuickInsert(
        val x: Int,
        val y: Int,
        val entries: List<ProgramEditorNodeCatalog.Entry>,
        val anchor: Endpoint,
        var query: String = "",
        var selected: Int = 0,
        var scroll: Int = 0
    )

    private data class ScreenPoint(val x: Int, val y: Int)

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)

    companion object {
        private const val NODE_W = 88
        private const val NODE_HEADER_H = 11
        private const val PORT_ROW_H = 8
        private const val NODE_CONFIGURATION_ROW_H = 7
        private const val CONFIGURATION_ROW_H = 27
        private const val CONFIGURATION_DETAIL_GAP = 3
        private const val CONFIGURATION_DETAIL_LINE_H = 9
        private const val MIN_NODE_H = 26
        private const val MIN_ZOOM = 0.5
        private const val MAX_ZOOM = 1.6
        private const val PANEL_BACKGROUND = 0x10000000
        private const val SECTION_BACKGROUND = 0x14000000
        private const val CANVAS_BACKGROUND = 0x28000000
        private const val CONTROL_BACKGROUND = 0x16000000
        private const val INPUT_BACKGROUND = 0x28000000
        private const val ROW_BACKGROUND = 0x14FFFFFF
        private const val HOVER_BACKGROUND = 0x2AFFFFFF
        private const val SELECTED_BACKGROUND = 0x30FFFFFF
        private const val NODE_BACKGROUND = 0xB00A0A0A.toInt()
        private const val NODE_SELECTED_BACKGROUND = 0xC00D1720.toInt()
        private const val NODE_HEADER = 0x24FFFFFF
        private const val POPUP_BACKGROUND = 0xE0101010.toInt()
        private const val BORDER = 0xD9FFFFFF.toInt()
        private const val BORDER_MUTED = 0x54FFFFFF
        private const val DIVIDER = 0x80FFFFFF.toInt()
        private const val GRID_MINOR = 0x0FFFFFFF
        private const val GRID_MAJOR = 0x20FFFFFF
        private const val SELECTION_BACKGROUND = 0x20FFFFFF
        private const val SELECTION_DRAG_THRESHOLD = 3.0
        private const val DEFAULT_ACCENT = 0xFF1177D6.toInt()
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val DIM = 0xBFFFFFFF.toInt()
        private const val NODE_SECONDARY_TEXT = 0xE6FFFFFF.toInt()
        private const val DISABLED = 0x33FFFFFF
        private const val ERROR = 0xFFFF5A66.toInt()
        private const val ERROR_BACKGROUND = 0xB030090D.toInt()
        private const val TOP_H = 20
        private const val RAIL_W = 18
        private const val ROW_H = 14
        private const val TOOL_SIZE = 14
        private const val PORT_HIT = 14
        private const val SNAP_DISTANCE = 10
        private const val PALETTE_LAYER_OFFSET_Y = 16
        private const val PALETTE_TAB_OFFSET_Y = 31
        private const val PALETTE_SEARCH_OFFSET_Y = 46
        private const val PALETTE_LIST_OFFSET_Y = 64
        private val TOOL_LABELS = arrayOf(
            "delete", "copy", "paste", "undo", "redo", "auto_layout", "fit",
            "export", "import", "save", "restore", "upgrade_numeric"
        )
        private val TOOL_GLYPHS = arrayOf(
            "X", "C", "P", "<", ">", "A", "F", "E", "I", "S", "R", "U"
        )

        @JvmStatic
        fun isDeleteKey(key: Int): Boolean =
            key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE

        @JvmStatic
        fun configurationOptionNeedsTooltip(labelWidth: Float, availableWidth: Int): Boolean =
            labelWidth > 0.0f && availableWidth > 0 && labelWidth > availableWidth

        @JvmStatic
        fun expandedConfigurationRowHeight(detailLineCount: Int): Int =
            if (detailLineCount <= 0) CONFIGURATION_ROW_H
            else CONFIGURATION_ROW_H + CONFIGURATION_DETAIL_GAP + detailLineCount * CONFIGURATION_DETAIL_LINE_H

        @JvmStatic
        fun portColor(type: ProgramValueType): Int {
            if (type == ProgramValueTypes.FLOW) return 0xFF4A9FE8.toInt()
            if (type == ProgramValueTypes.BOOLEAN) return 0xFF8D8FF0.toInt()
            if (type == ProgramValueTypes.INTEGER) return 0xFF62C7E8.toInt()
            if (type == ProgramValueTypes.BIG_INTEGER) return 0xFF56AFD5.toInt()
            if (type == ProgramValueTypes.FLOAT) return 0xFF5DD6C5.toInt()
            if (type == ProgramValueTypes.IDENTIFIER) return 0xFF8FB9D2.toInt()
            if (type == ProgramValueTypes.DURATION) return 0xFF78B7D5.toInt()
            if (type == ProgramValueTypes.STRUCTURE_REFERENCE) return 0xFFE1BA75.toInt()
            if (type == ProgramValueTypes.STRUCTURE_SET) return 0xFFB08F53.toInt()
            if (type == ProgramValueTypes.ENTITY_REFERENCE
                || type == ProgramValueTypes.LIVING_ENTITY_REFERENCE
            ) return 0xFFB6D8F2.toInt()
            if (type == ProgramValueTypes.ENTITY_SET
                || type == ProgramValueTypes.LIVING_ENTITY_SET
            ) return 0xFF8CAFCB.toInt()
            if (type == ProgramValueTypes.WORLD_POSITION) return 0xFF73A7F2.toInt()
            if (type == ProgramValueTypes.WORLD_POSITION_SET) return 0xFF5E8BCB.toInt()
            if (type == ProgramValueTypes.BLOCK_POSITION) return 0xFF7F91D8.toInt()
            if (type == ProgramValueTypes.BLOCK_POSITION_SET) return 0xFF6879B7.toInt()
            if (type == ProgramValueTypes.CONTROL_DESTINATION) return 0xFF8886DC.toInt()
            if (type == ProgramValueTypes.DIRECTION) return 0xFF72D0E4.toInt()
            if (type == ProgramValueTypes.DIRECTION_SET) return 0xFF5CAABD.toInt()
            if (type == ProgramValueTypes.ACTION_RESULT) return 0xFF9AACE0.toInt()
            return 0xFF8298AA.toInt()
        }

        /** Minimized windows can expose NaN scaled cursor coordinates during a render. */
        @JvmStatic
        fun renderMouseCoordinate(value: Double): Int = if (value.isFinite()) value.roundToInt() else -1

        @JvmStatic
        fun categoryAccent(category: Identifier): Int = when (category.path) {
            AbilityCategoryNames.ACCELERATOR -> 0xFFD4DCE2.toInt()
            AbilityCategoryNames.MELTDOWNER -> 0xFF59D68A.toInt()
            AbilityCategoryNames.DARKMATTER -> 0xFFF4F6F7.toInt()
            AbilityCategoryNames.AEROMANIP -> 0xFF8EDCF3.toInt()
            AbilityCategoryNames.ELECTROMASTER -> 0xFF328EE8.toInt()
            AbilityCategoryNames.MENTALOUT -> 0xFFFFB83D.toInt()
            AbilityCategoryNames.TELEPORT -> 0xFFA17BE8.toInt()
            else -> DEFAULT_ACCENT
        }

        private fun mapDiagnostic(code: ProgramDiagnosticCode): PrecisionGraph.Diagnostic = when (code) {
            ProgramDiagnosticCode.EMPTY_PROGRAM -> PrecisionGraph.Diagnostic.EMPTY_PROGRAM
            ProgramDiagnosticCode.TOO_MANY_NODES -> PrecisionGraph.Diagnostic.TOO_MANY_NODES
            ProgramDiagnosticCode.TOO_MANY_EDGES -> PrecisionGraph.Diagnostic.TOO_MANY_EDGES
            ProgramDiagnosticCode.DUPLICATE_NODE -> PrecisionGraph.Diagnostic.DUPLICATE_NODE
            ProgramDiagnosticCode.DUPLICATE_EDGE -> PrecisionGraph.Diagnostic.DUPLICATE_EDGE
            ProgramDiagnosticCode.UNKNOWN_PORT -> PrecisionGraph.Diagnostic.INVALID_PORT
            ProgramDiagnosticCode.TYPE_MISMATCH -> PrecisionGraph.Diagnostic.TYPE_MISMATCH
            ProgramDiagnosticCode.TOO_MANY_CONNECTIONS -> PrecisionGraph.Diagnostic.MULTIPLE_INPUTS
            ProgramDiagnosticCode.MISSING_INPUT -> PrecisionGraph.Diagnostic.MISSING_INPUT
            ProgramDiagnosticCode.DATA_CYCLE -> PrecisionGraph.Diagnostic.CYCLE
            ProgramDiagnosticCode.NO_ENTRY,
            ProgramDiagnosticCode.MULTIPLE_ENTRIES,
            ProgramDiagnosticCode.INVALID_ENTRY,
            ProgramDiagnosticCode.AMBIGUOUS_FLOW -> PrecisionGraph.Diagnostic.INVALID_FLOW

            ProgramDiagnosticCode.UNREACHABLE_FLOW_NODE -> PrecisionGraph.Diagnostic.DISCONNECTED_FLOW
            ProgramDiagnosticCode.INVALID_CONFIGURATION -> PrecisionGraph.Diagnostic.INVALID_PARAMETER
            else -> PrecisionGraph.Diagnostic.MALFORMED
        }

        private fun configurationFieldLabel(field: String): Component =
            Component.translatable("screen.academy.program.configuration.field.$field")

        private fun portLabel(entry: ProgramEditorNodeCatalog.Entry, port: String): Component {
            if (port.startsWith("value_")) {
                val suffix = port.substring("value_".length)
                if (suffix.all { it.isDigit() }) {
                    return Component.translatable("screen.academy.program.port.collection_builder_value", suffix)
                }
            }
            return Component.translatable(entry.portTranslationKey(port))
        }

        private fun configurationDisplayValue(
            node: NodeView,
            field: String,
            currentValue: JsonElement?
        ): Component {
            val options = ProgramConfigurationOptions.options(node.entry, field, currentValue)
            if (ProgramConfigurationOptions.isPowerSlider(field, currentValue)) {
                return Component.literal(String.format(Locale.ROOT, "%.2f", currentValue!!.asFloat))
            }
            return if (options.isEmpty()) Component.literal(if (currentValue == null) "" else currentValue.asString)
            else ProgramConfigurationOptions.selected(options, currentValue).label()
        }

        private fun configurationFields(node: NodeView): List<String> {
            if (ProgramNodePalette.hasProvider(node.entry.id())) return emptyList()
            if (!node.source.configuration().isJsonObject) return emptyList()
            return node.source.configuration().asJsonObject.keySet().sorted()
        }

        private fun categoryScopeLabel(entry: ProgramEditorNodeCatalog.Entry): Component {
            val category = entry.exclusiveCategory().orElse(null)
                ?: return if (entry.categoryRestricted())
                    Component.translatable("screen.academy.program.node_scope.category_restricted")
                else Component.empty()
            return Component.translatable(
                "screen.academy.program.node_scope.category_specific",
                categoryName(category)
            )
        }

        private fun categoryName(category: Identifier): Component {
            val registered = org.academy.api.common.registries.Registries.ABILITY_CATEGORIES.getValue(category)
            return Component.translatable(
                registered?.descriptionId
                    ?: "ability_category.${category.namespace}.${category.path}"
            )
        }

        private fun categoryGlyph(entry: ProgramEditorNodeCatalog.Entry): String {
            val category = entry.exclusiveCategory().orElse(null) ?: return "*"
            val name = categoryName(category).string.trim()
            if (name.isEmpty()) return "*"
            val end = name.offsetByCodePoints(0, 1)
            return name.substring(0, end).uppercase(Locale.ROOT)
        }

        private fun inspectorDescriptionOffset(entry: ProgramEditorNodeCatalog.Entry): Int =
            if (entry.categoryRestricted()) 44 else 36

        private fun inspectorConfigurationOffset(
            entry: ProgramEditorNodeCatalog.Entry,
            descriptionHeight: Int
        ): Int {
            val descriptionOffset = inspectorDescriptionOffset(entry)
            return maxOf(
                if (entry.categoryRestricted()) 66 else 58,
                descriptionHeight + descriptionOffset + 4
            )
        }

        private fun groupKey(group: ProgramEditorNodeCatalog.Group): String =
            "screen.academy.precision_operation.program_group." + group.name.lowercase(Locale.ROOT)

        private fun groupGlyph(group: ProgramEditorNodeCatalog.Group): String = when (group) {
            ProgramEditorNodeCatalog.Group.TARGET -> "T"
            ProgramEditorNodeCatalog.Group.COLLECTION -> "S"
            ProgramEditorNodeCatalog.Group.FILTER -> "F"
            ProgramEditorNodeCatalog.Group.LOGIC -> "L"
            ProgramEditorNodeCatalog.Group.FLOW -> ">"
            ProgramEditorNodeCatalog.Group.ACTION -> "A"
            ProgramEditorNodeCatalog.Group.VALUE -> "V"
        }

        private fun groupColor(group: ProgramEditorNodeCatalog.Group): Int = when (group) {
            ProgramEditorNodeCatalog.Group.TARGET, ProgramEditorNodeCatalog.Group.VALUE -> 0xFFFFFFFF.toInt()
            ProgramEditorNodeCatalog.Group.COLLECTION -> 0xD9FFFFFF.toInt()
            ProgramEditorNodeCatalog.Group.FILTER, ProgramEditorNodeCatalog.Group.LOGIC -> 0xBFFFFFFF.toInt()
            ProgramEditorNodeCatalog.Group.FLOW, ProgramEditorNodeCatalog.Group.ACTION -> 0xF2FFFFFF.toInt()
        }

        private fun portOffsetY(node: NodeView, index: Int): Int =
            NODE_HEADER_H + nodeConfigurationHeight(node) + 4 + index * PORT_ROW_H

        private fun nodeConfigurationHeight(node: NodeView): Int {
            val fieldCount = configurationFields(node).size
            return if (fieldCount == 0) 0 else 2 + fieldCount * NODE_CONFIGURATION_ROW_H
        }

        private fun configurationEditorHeight(node: NodeView, width: Int): Int {
            val fields = configurationFields(node)
            if (fields.isEmpty()) return 0
            return fields.sumOf { configurationRowHeight(node, it, width) } + 3
        }

        private fun configurationRowHeight(node: NodeView, field: String, width: Int): Int {
            val currentValue = node.source.configuration().asJsonObject.get(field)
            val options = ProgramConfigurationOptions.options(node.entry, field, currentValue)
            if (options.isEmpty()
                || ProgramConfigurationOptions.isToggle(field, currentValue)
                || ProgramConfigurationOptions.isPowerSlider(field, currentValue)
            ) {
                return CONFIGURATION_ROW_H
            }
            val label = ProgramConfigurationOptions.selected(options, currentValue).label().string
            val availableWidth = width - TOOL_SIZE * 2 - 10
            val labelWidth = TextWidget.getTextWidth(label, ProgramUiGraphics.BODY_FONT_SIZE)
            if (!configurationOptionNeedsTooltip(labelWidth, availableWidth)) {
                return CONFIGURATION_ROW_H
            }
            val detailLines =
                ProgramUiGraphics.wrap(label, (width - 5).toFloat(), ProgramUiGraphics.BODY_FONT_SIZE).size
            return expandedConfigurationRowHeight(detailLines)
        }

        private fun portIndex(ports: List<ProgramPortDefinition>, name: String): Int {
            for (index in ports.indices) {
                if (ports[index].name() == name) return index
            }
            return -1
        }

        private fun setTextBoxBounds(input: TextInputWidget, x: Int, y: Int, width: Int) {
            if ((input.getAbsoluteX()).roundToInt() == x
                && (input.getAbsoluteY()).roundToInt() == y
                && (input.width).roundToInt() == width
                && (input.height).roundToInt() == 15
            ) return
            input.layoutParams = FrameLayoutWidget.LayoutParams()
                .size(width.toFloat(), 15f)
                .gravity(Gravity.TOP_LEFT)
                .margin(x.toFloat(), y.toFloat(), 0f, 0f)
                .padding(4f, 3f, 2f, 2f)
        }

        private fun configurationInputName(field: String): String = "configuration_input_$field"

        private fun renderPort(
            graphics: ProgramUiGraphics,
            centerX: Int,
            centerY: Int,
            color: Int,
            openEnd: Boolean
        ) {
            graphics.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, color)
            if (openEnd) graphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, 0xFF111111.toInt())
        }

        private fun headingText(
            graphics: ProgramUiGraphics,
            value: String,
            x: Int,
            y: Int,
            color: Int,
            maxWidth: Int
        ) {
            graphics.text(
                value,
                x.toFloat(),
                y.toFloat(),
                color,
                ProgramUiGraphics.HEADING_FONT_SIZE,
                maxWidth.toFloat()
            )
        }

        private fun renderInstrumentFrame(
            graphics: ProgramUiGraphics,
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
            graphics: ProgramUiGraphics,
            x: Int,
            y: Int,
            width: Int,
            height: Int
        ) {
            graphics.fill(x, y, x + width, y + height, SECTION_BACKGROUND)
            graphics.fill(x, y, x + width, y + 1, BORDER_MUTED)
            graphics.fill(x, y + height - 1, x + width, y + height, BORDER_MUTED)
        }

        private fun orthogonalLine(
            graphics: ProgramUiGraphics,
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

        private fun line(graphics: ProgramUiGraphics, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
            graphics.fill(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2) + 1, maxOf(y1, y2) + 1, color)
        }

        private fun border(graphics: ProgramUiGraphics, x: Int, y: Int, width: Int, height: Int, color: Int) {
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

    private fun renderCanvasGrid(graphics: ProgramUiGraphics) {
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

    private fun renderInput(
        graphics: ProgramUiGraphics,
        x: Int,
        y: Int,
        width: Int,
        focused: Boolean
    ) {
        graphics.fill(x, y, x + width, y + 15, INPUT_BACKGROUND)
        graphics.fill(x, y + 14, x + width, y + 15, if (focused) accentColor else BORDER_MUTED)
        if (focused) graphics.fill(x, y, x + 2, y + 15, accentColor)
    }

    private fun renderControl(
        graphics: ProgramUiGraphics,
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
            graphics.fill(x, y, x + 2, y + height, accentColor)
            graphics.fill(x + 2, y + height - 1, x + width, y + height, accentColor)
        } else if (hovered) {
            graphics.fill(x, y + height - 1, x + width, y + height, TEXT)
        } else {
            graphics.fill(x, y + height - 1, x + width, y + height, BORDER_MUTED)
        }
    }

    private fun smallText(
        graphics: ProgramUiGraphics,
        value: String,
        x: Int,
        y: Int,
        color: Int,
        maxWidth: Int
    ) {
        graphics.text(value, x.toFloat(), y.toFloat(), color, ProgramUiGraphics.BODY_FONT_SIZE, maxWidth.toFloat())
    }

    private fun smallWrappedText(
        graphics: ProgramUiGraphics,
        value: Component,
        x: Int,
        y: Int,
        maxWidth: Int
    ): Int {
        val lines = ProgramUiGraphics.wrap(value.string, maxWidth.toFloat(), ProgramUiGraphics.BODY_FONT_SIZE)
        for (index in lines.indices) {
            graphics.text(
                lines[index],
                x.toFloat(),
                (y + index * 9).toFloat(),
                DIM,
                ProgramUiGraphics.BODY_FONT_SIZE,
                maxWidth.toFloat()
            )
        }
        return lines.size * 9
    }

    private fun button(
        graphics: ProgramUiGraphics,
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
        if (small) smallText(graphics, label.string, x + 3, y + 5, TEXT, width - 6)
        else graphics.centeredText(
            label.string,
            x + width / 2.0f,
            y + maxOf(1f, (height - 8) / 2.0f),
            TEXT,
            ProgramUiGraphics.BODY_FONT_SIZE,
            (width - 3).toFloat()
        )
    }

    private fun iconButton(
        graphics: ProgramUiGraphics,
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
}
