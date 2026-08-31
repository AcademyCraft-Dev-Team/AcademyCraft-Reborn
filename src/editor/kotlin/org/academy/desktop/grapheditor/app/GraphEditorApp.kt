package org.academy.desktop.grapheditor.app

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.mojang.blaze3d.pipeline.RenderTarget
import imgui.ImGui
import imgui.flag.*
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry
import org.academy.api.client.render.graph.serialize.GraphCodec
import org.academy.api.client.render.graph.serialize.JsonGraphCodec
import org.academy.api.client.render.graph.type.ValueType
import org.academy.api.client.render.shader.codegen.GlslNodeRegistry
import org.academy.api.client.render.shader.nodes.ShaderNodes
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry
import org.academy.api.client.render.vfxgraph.nodes.VfxNodes
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry
import org.academy.api.client.render.vfxgraph.operator.VfxOperators
import org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec
import org.academy.api.client.render.vfxgraph.serialize.VfxGraphSchemaVersion
import org.academy.desktop.grapheditor.canvas.*
import org.academy.desktop.grapheditor.bridge.VfxGraphMcpBridge
import org.academy.desktop.grapheditor.clipboard.GraphClipboard
import org.academy.desktop.grapheditor.commandpalette.CommandPalette
import org.academy.desktop.grapheditor.container.VfxContainerCanvas
import org.academy.desktop.grapheditor.container.VfxContainerModel
import org.academy.desktop.grapheditor.container.VfxContainerModelRef
import org.academy.desktop.grapheditor.dialog.NoteEditDialog
import org.academy.desktop.grapheditor.dialog.PromptDialog
import org.academy.desktop.grapheditor.document.EditorMetadata
import org.academy.desktop.grapheditor.document.EditorMetadataCodec
import org.academy.desktop.grapheditor.editorcurve.CurveEditor
import org.academy.desktop.grapheditor.gradient.GradientEditor
import org.academy.desktop.grapheditor.inspector.PropertyInspector
import org.academy.desktop.grapheditor.palette.NodePalette
import org.academy.desktop.grapheditor.preview.ShaderPreview
import org.academy.desktop.grapheditor.preview.VfxPreview
import org.academy.desktop.grapheditor.project.ProjectBrowser
import org.academy.desktop.grapheditor.project.RecentFiles
import org.academy.desktop.grapheditor.shortcut.KeyMods
import org.academy.desktop.grapheditor.shortcut.ShortcutRegistry
import org.academy.desktop.grapheditor.viewport.OrbitCamera
import org.academy.desktop.grapheditor.viewport.ViewportPanel
import org.academy.desktop.platform.DesktopEnvironment
import org.academy.desktop.platform.EditorApp
import org.academy.desktop.platform.GraphHotReload
import org.academy.desktop.platform.ShaderHotReload
import java.nio.file.Files
import java.nio.file.Path
import imgui.internal.ImGui as ImGuiDock

/**
 * 桌面 Shader/VFX Graph 编辑器（多文档，M19，ADR-022）。ImGui docking 布局：
 * 可停靠的 Palette/Inspector/Canvas/Project 面板 + 顶部文档 TabBar，每文档独立模型/撤销栈，
 * 双击 subgraph 节点打开子图资产为新文档。
 */
class GraphEditorApp(private val environment: DesktopEnvironment) : EditorApp {
    override var title = "Academy Shader Graph"

    private val registry = SimpleNodeRegistry()
    private val glslRegistry = GlslNodeRegistry()
    private val vfxRegistry = VfxNodeRegistry()
    private val blockRegistry = VfxBlockRegistry()
    private val operatorRegistry = VfxOperatorRegistry()
    private val codec: GraphCodec
    private val containerCodec: JsonVfxGraphCodec
    private val documents = GraphEditorDocuments(registry)
    private val modelRef = GraphEditorModelRef(GraphEditorModel(registry))
    private val containerRef = VfxContainerModelRef(VfxContainerModel(registry))
    private val camera = Camera2D()
    private val clipboard = GraphClipboard(registry)
    private val canvas: NodeCanvas
    private val containerCanvas: VfxContainerCanvas
    private val palette: NodePalette
    private val inspector: PropertyInspector
    private val shaderPreview: ShaderPreview
    private val vfxPreview: VfxPreview
    private val shortcuts = ShortcutRegistry()
    private val commandPalette = CommandPalette()
    private val promptDialog = PromptDialog()
    private val noteDialog = NoteEditDialog()
    private val curveEditor = CurveEditor()
    private val gradientEditor = GradientEditor()
    private var curveEditorTarget: String? = null
    private var gradientEditorTarget: String? = null
    private val orbit = OrbitCamera()
    private val viewport: ViewportPanel
    private val projectBrowser = ProjectBrowser(::graphDir, ::loadGraph) { listOf(packagedGraphDir()) }
    private val recentFiles: RecentFiles
    private lateinit var shaderWatcher: ShaderHotReload
    private lateinit var graphWatcher: GraphHotReload
    private lateinit var mcpBridge: VfxGraphMcpBridge
    private var mode: GraphMode = GraphMode.SHADER

    /** 右键弹窗目标节点 id：弹窗打开期间跨帧存活（contextRequest 每帧被清空）。 */
    private var contextNodeId: String? = null

    private val fileName = ImString("graph", 64)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private var dockSpaceId = 0

    /** 活动文档；[model]/[metadata] 计算属性统一走当前文档。 */
    private val model: GraphEditorModel get() = documents.current().model
    private val metadata: EditorMetadata get() = documents.current().metadata
    private val containerModel: VfxContainerModel get() = containerRef.model

    init {
        try {
            Files.createDirectories(workingDir())
        } catch (_: Exception) {
        }
        ShaderNodes.registerAll(registry, glslRegistry)
        VfxNodes.registerAll(registry, vfxRegistry)
        VfxBlocks.registerAll(registry, blockRegistry)
        VfxOperators.registerAll(registry, operatorRegistry)
        codec = JsonGraphCodec(registry)
        containerCodec = JsonVfxGraphCodec(registry)
        canvas = NodeCanvas(modelRef, camera, clipboard)
        containerCanvas = VfxContainerCanvas(containerRef, camera)
        containerCanvas.canvasPalette = ::renderContainerPalette
        shaderPreview = ShaderPreview(modelRef, registry, glslRegistry, environment::loadTexture)
        vfxPreview = VfxPreview(modelRef, vfxRegistry, containerRef, blockRegistry, operatorRegistry)
        palette = NodePalette(registry, modelRef) { canvasCenterGraph() }
        inspector = PropertyInspector(modelRef)
        inspector.onEditCurve = { id -> openCurveEditor(id) }
        inspector.onEditGradient = { id -> openGradientEditor(id) }
        viewport = ViewportPanel(environment, orbit, modelRef)
        recentFiles = RecentFiles(workingDir().resolve("recent_graphs.json"))
        recentFiles.load()
        buildPaletteActions()
        registerShortcuts()
        documents.newDoc("graph", GraphMode.SHADER)
        documents.onChange = { applyDocState() }
        applyDocState()
        shaderWatcher = ShaderHotReload(
            environment.workingDir.resolve("src").resolve("main").resolve("resources")
                .resolve("assets").resolve("academy").resolve("shaders"),
        )
        graphWatcher = GraphHotReload(
            { listOf(packagedGraphDir(), graphDir()) },
            ::reloadGraphFile,
        )
        mcpBridge = VfxGraphMcpBridge(workingDir().resolve("vfxgraph-mcp").resolve("bridge"), ::handleMcpCommand)
    }

    override val usesImGui: Boolean get() = true

    /** 编辑器退出：释放 VFX 预览 GPU 资源 + 停止着色器热重载监听。 */
    override fun onDispose() {
        vfxPreview.close()
        shaderWatcher.close()
        graphWatcher.close()
        mcpBridge.close()
    }

    override fun createRoot(): WidgetContainer = FrameLayoutWidget()

    override fun renderBackground(target: RenderTarget) {
        val vp = viewport.renderTarget()
        when (mode) {
            GraphMode.SHADER -> shaderPreview.render(vp)
            GraphMode.VFX -> vfxPreview.render(vp, orbit.toGraphCamera(aspectOf(vp)))
        }
    }

    private fun aspectOf(target: RenderTarget): Float =
        if (target.height > 0) target.width.toFloat() / target.height else 1f

    override fun renderImGui() {
        ensureImGuiIni()
        // MCP 命令在渲染线程执行，避免跨线程修改文档、ImGui 或实时预览状态。
        mcpBridge.poll()
        // 着色器/图资产热重载：每帧 mtime 轮询（节流）
        shaderWatcher.scanNow()
        graphWatcher.scanNow()
        val io = ImGui.getIO()
        val displayW = io.displaySizeX
        val displayH = io.displaySizeY

        renderMenuBar()
        val menuH = ImGui.getFrameHeightWithSpacing()

        renderDockHost(menuH, displayW, displayH)
        ensureDefaultLayout(displayW, displayH)

        if (isPanelVisible(PANEL_CANVAS)) {
            ImGui.begin("Canvas", CANVAS_FLAGS)
            renderDocumentTabs()
            if (mode == GraphMode.VFX) {
                containerCanvas.topInset = ImGui.getCursorPosY()
                containerCanvas.render()
            } else {
                canvas.topInset = ImGui.getCursorPosY()
                canvas.render()
            }
            renderContextMenus()
            handleOpenSubGraphRequest()
            ImGui.end()
        }
        if (isPanelVisible(PANEL_PALETTE)) {
            ImGui.begin("Palette")
            if (mode == GraphMode.VFX) {
                renderContainerPalette()
            } else {
                palette.render()
            }
            ImGui.end()
        }
        if (isPanelVisible(PANEL_INSPECTOR)) {
            ImGui.begin("Inspector")
            if (mode == GraphMode.VFX) {
                renderContainerInspector()
            } else {
                inspector.render(canvas.selected)
            }
            ImGui.end()
        }
        if (isPanelVisible(PANEL_PROJECT)) {
            ImGui.begin("Project")
            projectBrowser.render()
            ImGui.end()
        }
        if (isPanelVisible(PANEL_GLSL)) {
            renderGlslWindow()
        }

        if (curveEditorTarget != null) {
            ImGui.setNextWindowDockID(dockSpaceId, ImGuiCond.FirstUseEver)
            ImGui.begin("Curve Editor")
            curveEditor.render()
            ImGui.end()
        }
        if (gradientEditorTarget != null) {
            ImGui.setNextWindowDockID(dockSpaceId, ImGuiCond.FirstUseEver)
            ImGui.begin("Gradient Editor")
            gradientEditor.render()
            ImGui.end()
        }

        // 视口（M14）：docked 独立视口
        viewport.dockId = dockSpaceId
        viewport.orbitEnabled = mode == GraphMode.VFX
        viewport.emitterNodeId = selectedEmitterNode()
        viewport.updateParticleCount(vfxPreview.particleCount)
        viewport.render()

        renderStatus()
        promptDialog.render()
        noteDialog.render()
        commandPalette.render()
        handleShortcuts()
    }

    // ---- 多文档标签页 ----

    private fun renderDocumentTabs() {
        val docs = documents.list()
        if (docs.size <= 1) return
        var closeRequest: Int? = null
        val currentIndex = documents.indexOf(documents.current())
        if (ImGui.beginTabBar("doc_tabs")) {
            for ((i, doc) in docs.withIndex()) {
                val open = ImBoolean(true)
                // 当前文档标签强制选中（SetSelected），避免 ImGui 默认选中第一个标签；
                // 只有用户真正点击标签才切换文档，防止 beginTabItem 返回"被选中"导致逐帧互切
                val flags = if (i == currentIndex) ImGuiTabItemFlags.SetSelected else 0
                if (ImGui.beginTabItem(doc.name, open, flags)) {
                    if (i != currentIndex && ImGui.isItemClicked()) {
                        activateDoc(i)
                    }
                    ImGui.endTabItem()
                }
                if (!open.get()) {
                    closeRequest = i
                }
            }
            ImGui.endTabBar()
        }
        closeRequest?.let { closeDoc(it) }
    }

    private fun activateDoc(index: Int) {
        val cur = documents.current()
        cur.cameraZoom = camera.zoom
        cur.cameraPanX = camera.panX
        cur.cameraPanY = camera.panY
        documents.activate(index)
        applyDocState()
    }

    private fun closeDoc(index: Int) {
        documents.close(index)
        applyDocState()
    }

    /** 文档切换/变更后同步活动模型与面板状态。 */
    private fun applyDocState() {
        val doc = documents.current()
        modelRef.model = doc.model
        containerRef.model = doc.containerModel
        mode = doc.mode
        camera.zoom = doc.cameraZoom
        camera.panX = doc.cameraPanX
        camera.panY = doc.cameraPanY
        inspector.paramGroups = doc.metadata.paramGroups
        canvas.selected.clear()
        containerCanvas.selected.clear()
        containerCanvas.selectedContext.clear()
        fileName.set(doc.name)
    }

    private fun handleOpenSubGraphRequest() {
        val nodeId = canvas.openSubGraphRequest ?: return
        canvas.clearOpenSubGraphRequest()
        val node = model.nodes[nodeId] ?: return
        if (node.typeId != "subgraph") return
        val ref = node.properties["graph"]?.trim() ?: return
        if (ref.isEmpty()) return
        val file = graphDir().resolve("${ref.removeSuffix(".json")}.json")
        if (Files.isRegularFile(file)) {
            loadGraph(file)
        }
    }

    /** ImGui context 在 DesktopUiHost.bind() 后才创建，ini 文件名与配置须延迟到首个 ImGui 帧设置。 */
    private var iniConfigured = false

    private fun ensureImGuiIni() {
        if (iniConfigured) return
        iniConfigured = true
        val io = ImGui.getIO()
        io.iniFilename = iniFile().toString()
        // 仅标题栏/停靠标签可拖动窗口：画布内容区用 drawList 绘制、无 item，
        // 默认 ConfigWindowsMoveFromTitleBarOnly=false 会把内容拖拽误判为窗口移动。
        io.configWindowsMoveFromTitleBarOnly = true
    }

    private fun renderDockHost(menuH: Float, displayW: Float, displayH: Float) {
        ImGui.setNextWindowPos(0f, menuH)
        ImGui.setNextWindowSize(displayW, displayH - menuH)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
        ImGui.begin("##GraphDockHost", DOCK_HOST_FLAGS)
        ImGui.popStyleVar(3)
        dockSpaceId = ImGui.getID("##graph_dockspace")
        ImGui.dockSpace(dockSpaceId, 0f, 0f, ImGuiDockNodeFlags.PassthruCentralNode)
        ImGui.end()
    }

    // ---- 默认 dock 布局 ----

    private var layoutBuilt = false

    /** 每次启动首帧重建默认 dock 布局（imgui demo 同款），保证窗口位置/尺寸正确，不受历史 ini 污染。 */
    private fun ensureDefaultLayout(displayW: Float, displayH: Float) {
        if (layoutBuilt) return
        layoutBuilt = true
        buildDefaultLayout(displayW, displayH)
    }

    /** 重建默认布局：左 Palette/Project，右 Inspector，中 Canvas + 底部 Viewport。 */
    private fun buildDefaultLayout(displayW: Float, displayH: Float) {
        ImGuiDock.dockBuilderRemoveNode(dockSpaceId)
        ImGuiDock.dockBuilderAddNode(dockSpaceId)
        ImGuiDock.dockBuilderSetNodeSize(dockSpaceId, displayW, displayH)

        val left = ImInt()
        val rest = ImInt()
        ImGuiDock.dockBuilderSplitNode(dockSpaceId, ImGuiDir.Left, 0.2f, left, rest)
        val right = ImInt()
        val center = ImInt()
        ImGuiDock.dockBuilderSplitNode(rest.get(), ImGuiDir.Right, 0.25f, right, center)
        val viewport = ImInt()
        val canvas = ImInt()
        ImGuiDock.dockBuilderSplitNode(center.get(), ImGuiDir.Down, 0.35f, viewport, canvas)
        val project = ImInt()
        val palette = ImInt()
        ImGuiDock.dockBuilderSplitNode(left.get(), ImGuiDir.Down, 0.45f, project, palette)

        ImGuiDock.dockBuilderDockWindow(PANEL_PALETTE, palette.get())
        ImGuiDock.dockBuilderDockWindow(PANEL_PROJECT, project.get())
        ImGuiDock.dockBuilderDockWindow(PANEL_CANVAS, canvas.get())
        ImGuiDock.dockBuilderDockWindow("Viewport", viewport.get())
        ImGuiDock.dockBuilderDockWindow(PANEL_INSPECTOR, right.get())

        ImGuiDock.dockBuilderFinish(dockSpaceId)
    }

    private fun resetLayout() {
        layoutBuilt = false
    }

    // ---- 菜单栏 ----

    private fun renderMenuBar() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("New", "Ctrl+N")) newGraph()
                if (ImGui.menuItem("Save", "Ctrl+S")) save()
                if (ImGui.menuItem("Load", "Ctrl+O")) load()
                if (ImGui.menuItem("Close", "Ctrl+W")) closeDoc(documents.indexOf(documents.current()))
                ImGui.separator()
                val recent = recentFiles.recent()
                if (recent.isNotEmpty() && ImGui.beginMenu("Recent")) {
                    for (path in recent) {
                        if (ImGui.menuItem(path.substringAfterLast('/'))) {
                            loadGraph(Path.of(path))
                        }
                    }
                    ImGui.separator()
                    if (ImGui.menuItem("Clear Recent")) recentFiles.clear()
                    ImGui.endMenu()
                }
                ImGui.endMenu()
            }
            if (ImGui.beginMenu("Edit")) {
                if (ImGui.menuItem("Undo", "Ctrl+Z", false, activeCanUndo())) activeUndo()
                if (ImGui.menuItem("Redo", "Ctrl+Y", false, activeCanRedo())) activeRedo()
                ImGui.separator()
                if (ImGui.menuItem("Copy", "Ctrl+C", false, canvas.selected.isNotEmpty())) canvas.copySelection()
                if (ImGui.menuItem("Paste", "Ctrl+V")) canvas.pasteAtCursor()
                if (ImGui.menuItem(
                        "Duplicate",
                        "Ctrl+D",
                        false,
                        canvas.selected.isNotEmpty()
                    )
                ) canvas.duplicateSelection()
                if (ImGui.menuItem("Delete", "Del", false, canvas.selected.isNotEmpty())) canvas.deleteSelection()
                ImGui.separator()
                if (ImGui.menuItem("Select All", "Ctrl+A")) canvas.selectAll()
                if (ImGui.menuItem("Command Palette", "Ctrl+P")) commandPalette.toggle()
                ImGui.endMenu()
            }
            if (ImGui.beginMenu("Arrange")) {
                val canAlign = canvas.selected.size >= 2
                if (ImGui.menuItem(
                        "Align Left",
                        "Ctrl+Alt+1",
                        false,
                        canAlign
                    )
                ) canvas.alignSelected(AlignOps.Align.LEFT)
                if (ImGui.menuItem(
                        "Align Center H",
                        "Ctrl+Alt+2",
                        false,
                        canAlign
                    )
                ) canvas.alignSelected(AlignOps.Align.CENTER_H)
                if (ImGui.menuItem(
                        "Align Right",
                        "Ctrl+Alt+3",
                        false,
                        canAlign
                    )
                ) canvas.alignSelected(AlignOps.Align.RIGHT)
                if (ImGui.menuItem("Align Top", "Ctrl+Alt+4", false, canAlign)) canvas.alignSelected(AlignOps.Align.TOP)
                if (ImGui.menuItem(
                        "Align Middle V",
                        "Ctrl+Alt+5",
                        false,
                        canAlign
                    )
                ) canvas.alignSelected(AlignOps.Align.MIDDLE_V)
                if (ImGui.menuItem(
                        "Align Bottom",
                        "Ctrl+Alt+6",
                        false,
                        canAlign
                    )
                ) canvas.alignSelected(AlignOps.Align.BOTTOM)
                ImGui.separator()
                val canDistribute = canvas.selected.size >= 3
                if (ImGui.menuItem("Distribute Horizontally", "Ctrl+Alt+H", false, canDistribute)) {
                    canvas.distributeSelected(AlignOps.Distribute.HORIZONTAL)
                }
                if (ImGui.menuItem("Distribute Vertically", "Ctrl+Alt+V", false, canDistribute)) {
                    canvas.distributeSelected(AlignOps.Distribute.VERTICAL)
                }
                ImGui.separator()
                if (ImGui.menuItem("Group Selection")) canvas.groupSelection()
                if (ImGui.menuItem("Frame Selection", "F")) canvas.frameSelection()
                if (ImGui.menuItem("Frame All")) canvas.frameAll()
                ImGui.endMenu()
            }
            if (ImGui.beginMenu("View")) {
                if (ImGui.menuItem("Palette", "Ctrl+Alt+P", isPanelVisible(PANEL_PALETTE))) togglePanel(PANEL_PALETTE)
                if (ImGui.menuItem("Inspector", "Ctrl+Alt+I", isPanelVisible(PANEL_INSPECTOR))) togglePanel(
                    PANEL_INSPECTOR
                )
                if (ImGui.menuItem("Project", "Ctrl+Alt+R", isPanelVisible(PANEL_PROJECT))) togglePanel(PANEL_PROJECT)
                if (ImGui.menuItem("Generated GLSL", null, isPanelVisible(PANEL_GLSL))) togglePanel(PANEL_GLSL)
                if (ImGui.menuItem("Viewport", null, true)) Unit
                if (ImGui.menuItem("Reset Layout")) resetLayout()
                ImGui.separator()
                if (ImGui.menuItem("Snap to Grid", "Ctrl+G", canvas.snapEnabled)) {
                    canvas.snapEnabled = !canvas.snapEnabled
                }
                if (ImGui.menuItem("Viewport Grid", null, viewport.showGrid)) viewport.showGrid = !viewport.showGrid
                if (ImGui.menuItem("Viewport Stats", null, viewport.showStats)) viewport.showStats = !viewport.showStats
                if (ImGui.beginMenu("Resolution")) {
                    for (scale in listOf(0.5f, 0.75f, 1f, 1.5f, 2f)) {
                        if (ImGui.menuItem("${scale}x", null, viewport.resolutionScale == scale)) {
                            viewport.resolutionScale = scale
                        }
                    }
                    ImGui.endMenu()
                }
                ImGui.endMenu()
            }
            if (ImGui.beginMenu("Mode")) {
                if (ImGui.menuItem("Shader Graph", mode == GraphMode.SHADER)) setMode(GraphMode.SHADER)
                if (ImGui.menuItem("VFX Graph", mode == GraphMode.VFX)) setMode(GraphMode.VFX)
                ImGui.endMenu()
            }
            if (mode == GraphMode.VFX) {
                ImGui.sameLine()
                if (ImGui.button(if (vfxPreview.playing) "Pause" else "Play")) vfxPreview.playing = !vfxPreview.playing
                ImGui.sameLine()
                if (ImGui.button("Step")) vfxPreview.stepOnce()
                ImGui.sameLine()
                if (ImGui.button("Reset")) vfxPreview.reset()
                ImGui.sameLine()
                if (ImGui.button(if (vfxPreview.loop) "Loop:On" else "Loop:Off")) vfxPreview.loop = !vfxPreview.loop
                ImGui.sameLine()
                ImGui.text("t=%.2f".format(vfxPreview.time))
            }
            ImGui.endMainMenuBar()
        }
        ImGui.text("File name")
        ImGui.sameLine()
        if (ImGui.inputText("##filename", fileName)) {
            val name = fileName.get().trim().ifEmpty { "graph" }
            if (documents.current().name != name) {
                documents.current().name = name
                documents.refresh()
            }
        }
    }

    // ---- 面板显隐 ----

    private fun isPanelVisible(name: String): Boolean = metadata.panelVisibility[name] ?: true

    private fun togglePanel(name: String) {
        metadata.panelVisibility[name] = !(metadata.panelVisibility[name] ?: true)
    }

    // ---- 右键上下文菜单 ----

    private fun renderContextMenus() {
        // 容器画布自行渲染右键弹窗（id/时机一致）；扁平画布仍由宿主渲染
        if (mode == GraphMode.VFX) return
        val ctx = canvas.contextRequest
        canvas.clearContextRequest()
        renderCanvasContext(ctx)
        renderNodeContext(ctx)
        renderEdgeContext(ctx)
        renderFrameContext(ctx)
        renderNoteContext(ctx)
    }

    // ---- VFX 容器画布：调色板 / 检查器 / 右键菜单（M26） ----

    private fun renderContainerPalette() {
        val cm = containerCanvas
        val mouse = Pair(ImGui.getMousePosX(), ImGui.getMousePosY())
        if (ImGui.beginMenu("Add Context")) {
            for (type in listOf(
                org.academy.api.client.render.vfxgraph.model.VfxContextType.SPAWN,
                org.academy.api.client.render.vfxgraph.model.VfxContextType.INITIALIZE,
                org.academy.api.client.render.vfxgraph.model.VfxContextType.UPDATE,
                org.academy.api.client.render.vfxgraph.model.VfxContextType.OUTPUT,
            )) {
                if (ImGui.menuItem(type.name)) {
                    val gx = camera.screenToGraphX(mouse.first)
                    val gy = camera.screenToGraphY(mouse.second)
                    containerModel.addContext(type, camera.snap(gx - 100f), camera.snap(gy - 20f))
                }
            }
            ImGui.endMenu()
        }
        val blockTypes = registry.all().filter {
            it.category() == "spawn" || it.category() == "init"
                    || it.category() == "update" || it.category() == "output"
        }
            .sortedBy { it.category() + it.id() }
        if (blockTypes.isNotEmpty() && ImGui.beginMenu("Add Block")) {
            val targetCtx = containerModel.contexts.values.firstOrNull { it.id in cm.selectedContext }
            for (type in blockTypes) {
                if (ImGui.menuItem("${type.category()}: ${type.displayName()}")) {
                    val ctxId = targetCtx?.id ?: containerModel.contexts.keys.firstOrNull()
                    if (ctxId != null) {
                        containerModel.addBlock(ctxId, type.id())
                    }
                }
            }
            ImGui.endMenu()
        }
        val opTypes = registry.all().filter { it.id().startsWith("vfx.op.") }.sortedBy { it.displayName() }
        if (opTypes.isNotEmpty() && ImGui.beginMenu("Add Operator")) {
            for (type in opTypes) {
                if (ImGui.menuItem(type.displayName())) {
                    val gx = camera.screenToGraphX(mouse.first)
                    val gy = camera.screenToGraphY(mouse.second)
                    containerModel.addOperator(type.id(), camera.snap(gx), camera.snap(gy))
                }
            }
            ImGui.endMenu()
        }
    }

    private fun renderContainerInspector() {
        val cm = containerCanvas
        // 选中块 → 编辑属性
        val selectedBlock = cm.selected.firstOrNull { containerModel.findBlock(it) != null }
            ?.let { containerModel.findBlock(it) }
        if (selectedBlock != null) {
            ImGui.text(displayName(selectedBlock.typeId))
            ImGui.sameLine()
            if (ImGui.button("Delete")) {
                containerModel.contextOf(selectedBlock.id)?.let { ctxId ->
                    containerModel.removeBlock(ctxId, selectedBlock.id)
                }
                cm.selected.clear()
            }
            if (ImGui.button("Set as Output")) containerModel.setOutput(selectedBlock.id)
            val type = registry.find(selectedBlock.typeId)
            if (type != null) {
                for (spec in type.properties()) {
                    val current = selectedBlock.properties[spec.id()] ?: defaultPropertyString(spec.defaultValue())
                    val updated = editPropertyValue(spec.name(), spec.type(), current)
                    if (updated != null) {
                        containerModel.setProperty(selectedBlock.id, spec.id(), updated)
                    }
                }
            }
            return
        }
        // 选中算子 → 编辑属性
        val selectedOp = cm.selected.firstOrNull { containerModel.operators.containsKey(it) }
            ?.let { containerModel.operators[it] }
        if (selectedOp != null) {
            ImGui.text(displayName(selectedOp.typeId))
            ImGui.sameLine()
            if (ImGui.button("Delete")) {
                containerModel.removeOperator(selectedOp.id)
                cm.selected.clear()
            }
            val type = registry.find(selectedOp.typeId)
            if (type != null) {
                for (spec in type.properties()) {
                    val current = selectedOp.properties[spec.id()] ?: defaultPropertyString(spec.defaultValue())
                    val updated = editPropertyValue(spec.name(), spec.type(), current)
                    if (updated != null) {
                        containerModel.setProperty(selectedOp.id, spec.id(), updated)
                    }
                }
            }
            return
        }
        // 选中 context → 显示阶段名
        val selectedCtx = cm.selectedContext.firstOrNull()
        if (selectedCtx != null) {
            val ctx = containerModel.contexts[selectedCtx]
            if (ctx != null) {
                ImGui.text("${ctx.type.name} Context")
                if (ImGui.button("Add Block")) {
                    val type = registry.all().firstOrNull { it.category() == "spawn" } ?: return
                    containerModel.addBlock(ctx.id, type.id())
                }
                if (ImGui.button("Delete Context")) {
                    containerModel.removeContext(ctx.id)
                    cm.selectedContext.clear()
                }
            }
            return
        }
        ImGui.text(if (containerModel.contexts.isEmpty()) "No context. Use Palette > Add Context." else "Nothing selected")
    }

    private fun displayName(typeId: String): String = registry.find(typeId)?.displayName() ?: typeId

    private fun defaultPropertyString(value: org.academy.api.client.render.graph.type.Value): String =
        when (value.type()) {
            ValueType.FLOAT -> value.asFloat().toString()
            ValueType.INT -> value.asInt().toString()
            ValueType.BOOL -> value.asBool().toString()
            ValueType.COLOR -> {
                val c = value.asColor()
                "${c.x},${c.y},${c.z},${c.w}"
            }

            else -> value.asString().let { "" }
        }

    private fun editPropertyValue(label: String, type: ValueType, current: String): String? {
        when (type) {
            ValueType.FLOAT -> {
                val v = floatArrayOf(current.toFloatOrNull() ?: 0f)
                if (ImGui.dragFloat(label, v, 0.01f)) return v[0].toString()
            }

            ValueType.INT -> {
                val v = intArrayOf(current.toIntOrNull() ?: 0)
                if (ImGui.dragInt(label, v)) return v[0].toString()
            }

            ValueType.COLOR -> {
                val parts = current.split(",").map { it.trim().toFloatOrNull() ?: 1f }
                val arr = floatArrayOf(
                    parts[0], parts.getOrElse(1) { 1f }, parts.getOrElse(2) { 1f }, parts.getOrElse(3) { 1f })
                if (ImGui.colorEdit4(label, arr)) {
                    return "${arr[0]},${arr[1]},${arr[2]},${arr[3]}"
                }
            }

            else -> {
                val s = ImString(current, 256)
                if (ImGui.inputText(label, s)) return s.get()
            }
        }
        return null
    }


    private fun renderCanvasContext(ctx: NodeCanvas.ContextRequest?) {
        if (ImGui.beginPopup(NodeCanvas.POPUP_CANVAS)) {
            if (ImGui.menuItem("Paste")) canvas.pasteAtCursor()
            ImGui.separator()
            if (ImGui.menuItem("Add Frame")) canvas.addFrameAtCursor()
            if (ImGui.menuItem("Add Note")) canvas.addNoteAtCursor()
            if (ImGui.menuItem("Group Selection", false, canvas.selected.size >= 2)) canvas.groupSelection()
            ImGui.separator()
            if (ImGui.beginMenu("Add Node")) {
                renderNodeAddMenu()
                ImGui.endMenu()
            }
            ImGui.separator()
            if (ImGui.menuItem("Select All")) canvas.selectAll()
            if (ImGui.menuItem("Frame All")) canvas.frameAll()
            ImGui.endPopup()
        }
    }

    private fun renderNodeContext(ctx: NodeCanvas.ContextRequest?) {
        if (ImGui.beginPopup(NodeCanvas.POPUP_NODE)) {
            val nodeId = ctx?.nodeId ?: contextNodeId
            contextNodeId = nodeId
            val node = nodeId?.let { model.nodes[it] }
            if (node != null && node.typeId == "subgraph") {
                if (ImGui.menuItem("Open Sub Graph")) {
                    nodeId.let { openSubGraph(it) }
                }
                ImGui.separator()
            }
            if (ImGui.menuItem("Delete")) {
                val target = nodeId ?: canvas.selected.firstOrNull()
                if (target != null) {
                    model.removeNodes(listOf(target))
                    canvas.selected.remove(target)
                } else {
                    canvas.deleteSelection()
                }
                contextNodeId = null
                ImGui.closeCurrentPopup()
            }
            if (ImGui.menuItem("Duplicate")) canvas.duplicateSelection()
            if (ImGui.menuItem("Copy")) canvas.copySelection()
            if (ImGui.menuItem("Set as Output") && nodeId != null) canvas.setOutput(nodeId)
            if (node != null && node.typeId.startsWith("vfx.")) {
                ImGui.separator()
                val index = model.nodes.keys.indexOf(nodeId)
                val count = model.nodes.size
                if (ImGui.menuItem("Move Up in Execution Order", null, false, index > 0)) {
                    model.moveNodeExecutionOrder(nodeId, -1)
                    ImGui.closeCurrentPopup()
                }
                if (ImGui.menuItem("Move Down in Execution Order", null, false, index < count - 1)) {
                    model.moveNodeExecutionOrder(nodeId, 1)
                    ImGui.closeCurrentPopup()
                }
            }
            ImGui.separator()
            if (ImGui.beginMenu("Align")) {
                if (ImGui.menuItem("Left")) canvas.alignSelected(AlignOps.Align.LEFT)
                if (ImGui.menuItem("Center H")) canvas.alignSelected(AlignOps.Align.CENTER_H)
                if (ImGui.menuItem("Right")) canvas.alignSelected(AlignOps.Align.RIGHT)
                if (ImGui.menuItem("Top")) canvas.alignSelected(AlignOps.Align.TOP)
                if (ImGui.menuItem("Middle V")) canvas.alignSelected(AlignOps.Align.MIDDLE_V)
                if (ImGui.menuItem("Bottom")) canvas.alignSelected(AlignOps.Align.BOTTOM)
                ImGui.endMenu()
            }
            if (ImGui.beginMenu("Distribute")) {
                if (ImGui.menuItem("Horizontally")) canvas.distributeSelected(AlignOps.Distribute.HORIZONTAL)
                if (ImGui.menuItem("Vertically")) canvas.distributeSelected(AlignOps.Distribute.VERTICAL)
                ImGui.endMenu()
            }
            ImGui.endPopup()
        } else {
            contextNodeId = null
        }
    }

    private fun openSubGraph(nodeId: String) {
        val node = model.nodes[nodeId] ?: return
        val ref = node.properties["graph"]?.trim() ?: return
        if (ref.isEmpty()) return
        val file = graphDir().resolve("${ref.removeSuffix(".json")}.json")
        if (Files.isRegularFile(file)) {
            loadGraph(file)
        }
    }

    private fun renderEdgeContext(ctx: NodeCanvas.ContextRequest?) {
        if (ImGui.beginPopup(NodeCanvas.POPUP_EDGE)) {
            val edge = ctx?.edge
            if (ImGui.menuItem("Delete Edge") && edge != null) {
                model.disconnect(edge.toNode, edge.toPort)
            }
            ImGui.endPopup()
        }
    }

    private fun renderFrameContext(ctx: NodeCanvas.ContextRequest?) {
        if (ImGui.beginPopup(NodeCanvas.POPUP_FRAME)) {
            val frameId = ctx?.frameId
            if (ImGui.menuItem("Delete") && frameId != null) model.removeFrame(frameId)
            if (ImGui.menuItem("Rename...") && frameId != null) {
                val frame = model.frames[frameId]
                if (frame != null) {
                    promptDialog.open("Rename Frame", "Title:", frame.title) { model.renameFrame(frameId, it) }
                }
            }
            ImGui.endPopup()
        }
    }

    private fun renderNoteContext(ctx: NodeCanvas.ContextRequest?) {
        if (ImGui.beginPopup(NodeCanvas.POPUP_NOTE)) {
            val noteId = ctx?.noteId
            if (ImGui.menuItem("Delete") && noteId != null) model.removeNote(noteId)
            if (ImGui.menuItem("Edit...") && noteId != null) {
                val note = model.notes[noteId]
                if (note != null) {
                    noteDialog.open(note.title, note.body) { title, body ->
                        model.setNoteContent(noteId, title, body, note.color)
                    }
                }
            }
            ImGui.endPopup()
        }
    }

    private fun renderNodeAddMenu() {
        val grouped = registry.all().groupBy { it.category() }.toSortedMap()
        for ((category, types) in grouped) {
            if (ImGui.beginMenu(category)) {
                for (type in types) {
                    if (ImGui.menuItem(type.displayName())) {
                        canvas.addNodeAtCursor(type.id())
                    }
                }
                ImGui.endMenu()
            }
        }
    }

    // ---- 快捷键 / 命令面板 ----

    private fun registerShortcuts() {
        shortcuts.register(KeyMods.CTRL, ImGuiKey.N) { newGraph() }
        shortcuts.register(KeyMods.CTRL, ImGuiKey.S) { save() }
        shortcuts.register(KeyMods.CTRL, ImGuiKey.O) { load() }
        shortcuts.register(KeyMods.CTRL, ImGuiKey.W) { closeDoc(documents.indexOf(documents.current())) }
        shortcuts.register(KeyMods.CTRL, ImGuiKey.Z) { activeUndo() }
        shortcuts.register(KeyMods.CTRL, ImGuiKey.Y) { activeRedo() }
        shortcuts.register(KeyMods.CTRL or KeyMods.SHIFT, ImGuiKey.Z) { activeRedo() }
        shortcuts.register(KeyMods.CTRL, ImGuiKey.C) { canvas.copySelection() }
        shortcuts.register(KeyMods.CTRL, ImGuiKey.V) { canvas.pasteAtCursor() }
        shortcuts.register(KeyMods.CTRL, ImGuiKey.D) { canvas.duplicateSelection() }
        shortcuts.register(KeyMods.CTRL, ImGuiKey.A) { canvas.selectAll() }
        shortcuts.register(KeyMods.CTRL, ImGuiKey.P) { commandPalette.toggle() }
        shortcuts.register(KeyMods.NONE, ImGuiKey.Delete) { canvas.deleteSelection() }
        shortcuts.register(KeyMods.NONE, ImGuiKey.F) { canvas.frameSelection() }
        shortcuts.register(KeyMods.CTRL, ImGuiKey.G) { canvas.snapEnabled = !canvas.snapEnabled }
        shortcuts.register(KeyMods.CTRL or KeyMods.ALT, ImGuiKey._1) { canvas.alignSelected(AlignOps.Align.LEFT) }
        shortcuts.register(KeyMods.CTRL or KeyMods.ALT, ImGuiKey._2) { canvas.alignSelected(AlignOps.Align.CENTER_H) }
        shortcuts.register(KeyMods.CTRL or KeyMods.ALT, ImGuiKey._3) { canvas.alignSelected(AlignOps.Align.RIGHT) }
        shortcuts.register(KeyMods.CTRL or KeyMods.ALT, ImGuiKey._4) { canvas.alignSelected(AlignOps.Align.TOP) }
        shortcuts.register(KeyMods.CTRL or KeyMods.ALT, ImGuiKey._5) { canvas.alignSelected(AlignOps.Align.MIDDLE_V) }
        shortcuts.register(KeyMods.CTRL or KeyMods.ALT, ImGuiKey._6) { canvas.alignSelected(AlignOps.Align.BOTTOM) }
        shortcuts.register(
            KeyMods.CTRL or KeyMods.ALT,
            ImGuiKey.H
        ) { canvas.distributeSelected(AlignOps.Distribute.HORIZONTAL) }
        shortcuts.register(
            KeyMods.CTRL or KeyMods.ALT,
            ImGuiKey.V
        ) { canvas.distributeSelected(AlignOps.Distribute.VERTICAL) }
        shortcuts.register(KeyMods.CTRL or KeyMods.ALT, ImGuiKey.P) { togglePanel(PANEL_PALETTE) }
        shortcuts.register(KeyMods.CTRL or KeyMods.ALT, ImGuiKey.I) { togglePanel(PANEL_INSPECTOR) }
        shortcuts.register(KeyMods.CTRL or KeyMods.ALT, ImGuiKey.R) { togglePanel(PANEL_PROJECT) }
    }

    private fun handleShortcuts() {
        if (ImGui.getIO().wantCaptureKeyboard) return
        shortcuts.handle()
    }

    private fun buildPaletteActions() {
        val nodeActions: List<Pair<String, () -> Unit>> = registry.all().sortedBy { it.displayName() }.map { type ->
            "Add: ${type.displayName()} (${type.category()})" to {
                val pos = canvasCenterGraph()
                canvas.addNodeAtGraph(type.id(), pos.first, pos.second)
            }
        }
        commandPalette.setActions(
            nodeActions + listOf(
                "Undo" to { activeUndo() },
                "Redo" to { activeRedo() },
                "Select All" to { canvas.selectAll() },
                "Group Selection" to { canvas.groupSelection() },
                "Add Frame" to { canvas.addFrameAtCursor() },
                "Add Note" to { canvas.addNoteAtCursor() },
                "Frame Selection" to { canvas.frameSelection() },
                "Frame All" to { canvas.frameAll() },
                "New Graph" to { newGraph() },
                "Save" to { save() },
                "Load" to { load() },
            )
        )
    }

    /** 活动模型（VFX 容器模式用容器 undo 栈，否则扁平）。 */
    private val activeModelCanUndo: Boolean
        get() =
            if (mode == GraphMode.VFX) containerModel.canUndo else model.canUndo
    private val activeModelCanRedo: Boolean
        get() =
            if (mode == GraphMode.VFX) containerModel.canRedo else model.canRedo

    private fun activeCanUndo(): Boolean = activeModelCanUndo

    private fun activeCanRedo(): Boolean = activeModelCanRedo

    private fun activeUndo() {
        if (mode == GraphMode.VFX) containerModel.undo() else model.undo()
    }

    private fun activeRedo() {
        if (mode == GraphMode.VFX) containerModel.redo() else model.redo()
    }

    // ---- 状态 / 文件 ----

    private fun setMode(newMode: GraphMode) {
        if (mode == newMode) return
        documents.current().mode = newMode
        mode = newMode
        canvas.selected.clear()
        containerCanvas.selected.clear()
        containerCanvas.selectedContext.clear()
    }

    // ---- 曲线 / 渐变编辑器（M12-03/04）----

    private fun openCurveEditor(paramId: String) {
        val index = model.parameters.indexOfFirst { it.id() == paramId }
        if (index < 0) return
        val curve = model.parameters[index].defaultValue().asCurve()
        curveEditorTarget = paramId
        curveEditor.open(curve) { newCurve ->
            applyParamValue(paramId) { p -> org.academy.api.client.render.graph.type.Value.curve(newCurve) }
        }
    }

    private fun openGradientEditor(paramId: String) {
        val index = model.parameters.indexOfFirst { it.id() == paramId }
        if (index < 0) return
        val gradient = model.parameters[index].defaultValue().asGradient()
        gradientEditorTarget = paramId
        gradientEditor.open(gradient) { newGradient ->
            applyParamValue(paramId) { p -> org.academy.api.client.render.graph.type.Value.gradient(newGradient) }
        }
    }

    private fun applyParamValue(
        paramId: String,
        valueFactory: (org.academy.api.client.render.graph.model.GraphParameter) -> org.academy.api.client.render.graph.type.Value
    ) {
        val index = model.parameters.indexOfFirst { it.id() == paramId }
        if (index < 0) return
        val p = model.parameters[index]
        model.replaceParameter(
            index,
            org.academy.api.client.render.graph.model.GraphParameter(
                p.id(),
                p.name(),
                p.type(),
                valueFactory(p),
                p.range()
            )
        )
    }

    private fun renderStatus() {
        val error = when (mode) {
            GraphMode.SHADER -> shaderPreview.error
            GraphMode.VFX -> vfxPreview.error
        }
        error?.let { err ->
            ImGui.setNextWindowPos(0f, ImGui.getIO().displaySizeY - 40f)
            ImGui.setNextWindowSize(ImGui.getIO().displaySizeX, 40f)
            ImGui.begin("##errorbar", NO_TITLE_MOVE_RESIZE)
            ImGui.textColored(1f, 0.35f, 0.35f, 1f, "Error: $err")
            ImGui.end()
        }
    }

    private fun renderGlslWindow() {
        val source = shaderPreview.fragmentSource ?: return
        ImGui.setNextWindowPos(0f, ImGui.getIO().displaySizeY - 240f, ImGuiCond.FirstUseEver)
        ImGui.setNextWindowSize(ImGui.getIO().displaySizeX, 220f, ImGuiCond.FirstUseEver)
        ImGui.begin("Generated GLSL")
        if (ImGui.beginChild("##glsl_child", 0f, 0f)) {
            for (line in source.split('\n')) {
                ImGui.textUnformatted(line)
            }
        }
        ImGui.endChild()
        ImGui.end()
    }

    private fun newGraph() {
        val base = if (mode == GraphMode.SHADER) "shader_graph" else "vfx_graph"
        val name = uniqueName(base)
        documents.newDoc(name, mode)
        applyDocState()
    }

    private fun uniqueName(base: String): String {
        val taken = documents.list().map { it.name }.toSet()
        var name = base
        var i = 2
        while (name in taken) {
            name = "$base$i"
            i++
        }
        return name
    }

    private fun save() {
        try {
            val doc = documents.current()
            val name = fileName.get().trim().ifEmpty { "graph" }.removeSuffix(".json")
            doc.name = name
            // 已打开的文件（含打包资产）写回原路径；新文档写 run/academy/graphs
            val target = doc.path ?: graphDir().resolve("$name.json")
            if (doc.path == null) {
                Files.createDirectories(target.parent)
            }
            Files.writeString(
                target, gson.toJson(
                    if (mode == GraphMode.VFX) containerCodec.encode(containerModel.toSystem())
                    else codec.encode(model.toGraph())
                )
            )
            writeSidecar(target.resolveSibling("${target.fileName.toString().removeSuffix(".json")}.editor.json"))
            doc.path = target
            recentFiles.add(target)
            documents.refresh()
            projectBrowser.refresh()
            // 本编辑器写入后确认 mtime，避免热重载把"自己保存"当成外部修改
            graphWatcher.acknowledge(target)
        } catch (e: Exception) {
            println("[graph-editor] save failed: ${e.message}")
        }
    }

    /** 图资产热重载：外部修改（IDE/文件）且该文档已打开时，就地替换内容（保留标签页/相机）。 */
    private fun reloadGraphFile(file: Path) {
        val name0 = file.fileName.toString()
        if (!name0.endsWith(".json") || name0.endsWith(".editor.json")) return
        try {
            if (!Files.isRegularFile(file)) return
            val json = gson.fromJson(Files.readString(file), JsonObject::class.java) ?: return
            val isContainer = json.get(VfxGraphSchemaVersion.KIND_FIELD)?.asString == "vfx"
            var meta = EditorMetadata()
            val editorFile = file.resolveSibling("${file.fileName.toString().removeSuffix(".json")}.editor.json")
            if (Files.isRegularFile(editorFile)) {
                meta = EditorMetadataCodec.decode(gson.fromJson(Files.readString(editorFile), JsonObject::class.java))
            }
            val name = file.fileName.toString().removeSuffix(".json")
            val detectedMode = if (isContainer) GraphMode.VFX else detectMode(codec.decode(json))
            if (documents.reload(file, name, json, isContainer, meta, detectedMode) != null) {
                println("[graph-hot-reload] reloaded: $file")
            }
        } catch (e: Exception) {
            println("[graph-hot-reload] reload failed: ${e.message}")
        }
    }

    /** 本地 MCP 文件桥命令；路径严格限制在打包 VFX 资产与 run/academy/graphs。 */
    private fun handleMcpCommand(request: JsonObject): JsonObject {
        return when (request.get("action")?.asString ?: error("missing action")) {
            "status" -> mcpEditorState()
            "open" -> {
                val target = resolveMcpGraphPath(request.get("path")?.asString ?: error("missing path"))
                require(Files.isRegularFile(target)) { "graph does not exist: $target" }
                val openIndex = documents.list().indexOfFirst { samePath(it.path, target) }
                if (openIndex >= 0) activateDoc(openIndex) else loadGraph(target)
                mcpEditorState()
            }
            "reload" -> {
                val target = request.get("path")?.asString?.let(::resolveMcpGraphPath)
                    ?: documents.current().path
                    ?: error("active document has no path")
                require(Files.isRegularFile(target)) { "graph does not exist: $target" }
                if (documents.list().none { samePath(it.path, target) }) loadGraph(target)
                else reloadGraphFile(target)
                mcpEditorState()
            }
            "save" -> {
                save()
                mcpEditorState()
            }
            "play" -> {
                vfxPreview.playing = true
                mcpEditorState()
            }
            "pause" -> {
                vfxPreview.playing = false
                mcpEditorState()
            }
            "set_playback" -> {
                request.get("playing")?.let { vfxPreview.playing = it.asBoolean }
                request.get("loop")?.let { vfxPreview.loop = it.asBoolean }
                mcpEditorState()
            }
            "reset" -> {
                vfxPreview.reset()
                mcpEditorState()
            }
            "step" -> {
                vfxPreview.stepOnce()
                mcpEditorState()
            }
            else -> error("unsupported action: ${request.get("action")}")
        }
    }

    private fun mcpEditorState(): JsonObject = JsonObject().apply {
        val current = documents.current()
        addProperty("title", title)
        addProperty("activeDocument", current.name)
        current.path?.let { addProperty("activePath", it.toAbsolutePath().normalize().toString()) }
        addProperty("mode", current.mode.name.lowercase())
        addProperty("playing", vfxPreview.playing)
        addProperty("loop", vfxPreview.loop)
        addProperty("time", vfxPreview.time)
        addProperty("particleCount", vfxPreview.particleCount)
        vfxPreview.error?.let { addProperty("previewError", it) }
        add("documents", com.google.gson.JsonArray().apply {
            documents.list().forEach { doc ->
                add(JsonObject().apply {
                    addProperty("name", doc.name)
                    addProperty("mode", doc.mode.name.lowercase())
                    addProperty("active", doc === current)
                    doc.path?.let { addProperty("path", it.toAbsolutePath().normalize().toString()) }
                })
            }
        })
    }

    private fun resolveMcpGraphPath(value: String): Path {
        val supplied = Path.of(value)
        val resolved = (if (supplied.isAbsolute) supplied else environment.workingDir.resolve(supplied))
            .toAbsolutePath().normalize()
        val allowed = listOf(packagedGraphDir(), graphDir()).map { it.toAbsolutePath().normalize() }
        require(allowed.any(resolved::startsWith)) { "path is outside VFXGraph roots: $resolved" }
        val name = resolved.fileName.toString()
        require(name.endsWith(".json") && !name.endsWith(".editor.json")) { "not a VFXGraph JSON file: $resolved" }
        return resolved
    }

    private fun samePath(left: Path?, right: Path): Boolean =
        left?.toAbsolutePath()?.normalize() == right.toAbsolutePath().normalize()

    private fun writeSidecar(editorFile: Path) {
        metadata.frames.clear()
        metadata.frames.putAll(model.frames)
        metadata.notes.clear()
        metadata.notes.putAll(model.notes)
        metadata.cameraZoom = camera.zoom
        metadata.cameraPanX = camera.panX
        metadata.cameraPanY = camera.panY
        Files.writeString(editorFile, gson.toJson(EditorMetadataCodec.encode(metadata)))
    }

    private fun load() {
        val file = graphDir().resolve("${fileName.get().trim().ifEmpty { "graph" }}.json")
        loadGraph(file)
    }

    private fun loadGraph(file: Path) {
        try {
            if (!Files.isRegularFile(file)) return
            val json = gson.fromJson(Files.readString(file), JsonObject::class.java) ?: return
            val isContainer = json.get(VfxGraphSchemaVersion.KIND_FIELD)?.asString == "vfx"
            var meta = EditorMetadata()
            val editorFile = file.resolveSibling("${file.fileName.toString().removeSuffix(".json")}.editor.json")
            if (Files.isRegularFile(editorFile)) {
                val metaJson = gson.fromJson(Files.readString(editorFile), JsonObject::class.java)
                meta = EditorMetadataCodec.decode(metaJson)
            }
            val name = file.fileName.toString().removeSuffix(".json")
            // 按 schema 判定模式：容器图（kind:"vfx"）→ VFX；否则按节点类型判定
            val detectedMode = if (isContainer) GraphMode.VFX else detectMode(codec.decode(json))
            documents.openDoc(file, name, json, isContainer, meta, detectedMode)
            applyDocState()
            recentFiles.add(file)
            projectBrowser.refresh()
        } catch (e: Exception) {
            println("[graph-editor] load failed: ${e.message}")
        }
    }

    private fun graphDir(): Path = workingDir().resolve("graphs")

    /** 共享 main 打包资产（src/main/resources/assets/academy/vfxgraph），供观察/测试（M21）。 */
    private fun packagedGraphDir(): Path = environment.workingDir.resolve("src").resolve("main").resolve("resources")
        .resolve("assets").resolve("academy").resolve("vfxgraph")

    private fun detectMode(graph: org.academy.api.client.render.graph.model.Graph): GraphMode =
        if (graph.nodes().any { it.type().startsWith("vfx.") }) GraphMode.VFX else GraphMode.SHADER

    private fun workingDir(): Path = environment.workingDir.resolve("run").resolve("academy")

    private fun iniFile(): Path = workingDir().resolve("imgui-graph.ini")

    private fun canvasCenterGraph(): Pair<Float, Float> {
        val r = canvas.canvasRect
        return Pair(camera.screenToGraphX(r[0] + r[2] / 2f), camera.screenToGraphY(r[1] + r[3] / 2f))
    }

    /** 选中单个 spawn/init_position 节点时返回其 id（供视口 gizmo 编辑发射器位置）。 */
    private fun selectedEmitterNode(): String? {
        if (canvas.selected.size != 1) return null
        val id = canvas.selected.first()
        val node = model.nodes[id] ?: return null
        return if (node.typeId.startsWith("vfx.spawn") || node.typeId.startsWith("vfx.init_position")) id else null
    }

    companion object {
        const val PANEL_CANVAS = "Canvas"
        const val PANEL_PALETTE = "Palette"
        const val PANEL_INSPECTOR = "Inspector"
        const val PANEL_PROJECT = "Project"
        const val PANEL_GLSL = "Generated GLSL"
        const val INSPECTOR_W = 300f
        const val NO_TITLE_MOVE_RESIZE = ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove or
                ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoBringToFrontOnFocus
        private val DOCK_HOST_FLAGS = ImGuiWindowFlags.NoDocking or ImGuiWindowFlags.NoTitleBar or
                ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove or
                ImGuiWindowFlags.NoBringToFrontOnFocus or ImGuiWindowFlags.NoNavFocus
        private val CANVAS_FLAGS = ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse
    }
}
