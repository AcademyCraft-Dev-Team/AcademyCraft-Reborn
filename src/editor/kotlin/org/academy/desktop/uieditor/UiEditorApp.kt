package org.academy.desktop.uieditor

import com.google.gson.JsonObject
import com.mojang.blaze3d.pipeline.RenderTarget
import imgui.ImGui
import imgui.flag.ImGuiDockNodeFlags
import imgui.flag.ImGuiDir
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui as ImGuiDock
import imgui.type.ImInt
import imgui.type.ImString
import org.academy.api.client.gui.editor.UiEditorDocument
import org.academy.api.client.gui.serialize.UiJson
import org.academy.api.client.gui.serialize.WidgetCodecRegistry
import org.academy.api.client.gui.serialize.WidgetNode
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.desktop.platform.DesktopEnvironment
import org.academy.desktop.platform.EditorApp
import org.academy.desktop.uieditor.preview.PreviewPane
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.nio.file.Files
import java.nio.file.Path

/**
 * Out-of-game UI layout editor, mirroring [org.academy.desktop.grapheditor.app.GraphEditorApp]:
 * the editing/operations UI (menus, tree, inspector, JSON, status) is drawn with ImGui,
 * while the live preview is rendered by the self-developed GUI framework offscreen and
 * blitted into the docked Canvas window via [PreviewPane].
 *
 * Layout: top menu bar, docked Tree / Preview / Inspector / JSON panels. Editing is driven
 * by the widget codecs' property schemas — never hand-written JSON.
 */
class UiEditorApp(
    private val environment: DesktopEnvironment,
    private val layoutName: String,
) : EditorApp {

    override var title = "AcademyCraft UI Editor — $layoutName"

    private var doc: UiEditorDocument = loadDocument()
    private val preview = PreviewPane(environment) { doc }
    private val treePanel = WidgetTreePanel({ doc }) { path -> doc.setSelection(path) }
    private val inspectorPanel = WidgetInspectorPanel { doc }
    private val jsonPanel = JsonPanel { doc }
    private val shortcuts = UiShortcutRegistry()
    private val fileName = ImString(doc.fileName, 64)

    private var showTree = true
    private var showPreview = true
    private var showInspector = true
    private var showJson = true

    private var quit = false
    private var hint: String? = null
    private var dockSpaceId = 0
    private var layoutBuilt = false
    private var iniConfigured = false

    init {
        doc.onMutated = { preview.rebuild() }
        registerShortcuts()
    }

    override val usesImGui: Boolean get() = true

    override fun createRoot(): WidgetContainer = FrameLayoutWidget()

    override fun onDispose() {
        preview.close()
    }

    override fun renderBackground(target: RenderTarget) {
        preview.renderBackground()
    }

    override fun renderImGui() {
        ensureImGuiIni()

        val io = ImGui.getIO()
        val displayW = io.getDisplaySizeX()
        val displayH = io.getDisplaySizeY()

        renderMenuBar()
        val menuH = ImGui.getFrameHeightWithSpacing()
        renderDockHost(menuH, displayW, displayH)
        ensureDefaultLayout(displayW, displayH)

        if (showTree) {
            ImGui.begin(PANEL_TREE, FLAG_NO_DOCK)
            treePanel.render()
            ImGui.end()
        }
        if (showInspector) {
            ImGui.begin(PANEL_INSPECTOR, FLAG_NO_DOCK)
            inspectorPanel.render()
            ImGui.end()
        }
        if (showJson) {
            ImGui.begin(PANEL_JSON, FLAG_NO_DOCK)
            jsonPanel.render()
            ImGui.end()
        }
        if (showPreview) renderPreviewWindow()

        renderStatus()
        if (!ImGui.getIO().getWantCaptureKeyboard()) shortcuts.handle()
    }

    // ============ menus ============

    private fun renderMenuBar() {
        if (!ImGui.beginMainMenuBar()) return
        if (ImGui.beginMenu("File")) {
            if (ImGui.menuItem("New", "Ctrl+N")) newDocument()
            if (ImGui.menuItem("Open File...", "Ctrl+O")) openNative()
            ImGui.separator()
            for (name in listLayoutFiles()) {
                if (ImGui.menuItem(name)) openLayout(name)
            }
            ImGui.separator()
            if (ImGui.menuItem("Save", "Ctrl+S")) save()
            if (ImGui.menuItem("Save As...")) saveAs()
            if (ImGui.menuItem("Reload from disk")) reload()
            ImGui.separator()
            if (ImGui.menuItem("Quit")) quit = true
            ImGui.endMenu()
        }
        if (ImGui.beginMenu("Edit")) {
            if (ImGui.menuItem("Undo", "Ctrl+Z", false, doc.canUndo)) undo()
            if (ImGui.menuItem("Redo", "Ctrl+Y", false, doc.canRedo)) redo()
            ImGui.separator()
            if (ImGui.menuItem("Copy Node", "Ctrl+C", false, doc.selectedNode != null)) copyNode()
            if (ImGui.menuItem("Paste Node", "Ctrl+V")) pasteNode()
            if (ImGui.menuItem("Duplicate Node", "Ctrl+D", false, doc.selectedNode != null)) duplicate()
            if (ImGui.menuItem("Delete Node", "Del", false, doc.selectedNode != null)) deleteSelected()
            ImGui.separator()
            if (ImGui.menuItem("Move Up", "Alt+Up", false, doc.selectedNode != null)) moveSelected(-1)
            if (ImGui.menuItem("Move Down", "Alt+Down", false, doc.selectedNode != null)) moveSelected(1)
            ImGui.separator()
            if (ImGui.menuItem("Collapse All")) treePanel.collapseAll()
            if (ImGui.menuItem("Expand All")) treePanel.expandAll()
            ImGui.endMenu()
        }
        if (ImGui.beginMenu("Display")) {
            if (ImGui.menuItem("Zoom In", "Ctrl+=")) zoomStep(1f)
            if (ImGui.menuItem("Zoom Out", "Ctrl+-")) zoomStep(-1f)
            if (ImGui.menuItem("Reset Zoom", "Ctrl+0")) setZoom(1f)
            if (ImGui.menuItem("Zoom To Fit", "F")) preview.zoomToFit()
            if (ImGui.menuItem("Center On Selection", null, doc.selectedNode != null)) preview.centerOnSelection()
            ImGui.separator()
            if (ImGui.menuItem("Tree", null, showTree)) showTree = !showTree
            if (ImGui.menuItem("Preview", null, showPreview)) showPreview = !showPreview
            if (ImGui.menuItem("Inspector", null, showInspector)) showInspector = !showInspector
            if (ImGui.menuItem("JSON", null, showJson)) showJson = !showJson
            ImGui.separator()
            if (ImGui.menuItem("Overlays", null, preview.showOverlays)) preview.showOverlays = !preview.showOverlays
            if (ImGui.menuItem("Rulers", null, preview.showRulers)) preview.showRulers = !preview.showRulers
            if (ImGui.menuItem("Preview Grid", null, preview.gridShown)) preview.setGrid(!preview.gridShown)
            if (ImGui.menuItem("Preview Background: Dark", null, preview.artboardColor == PreviewPane.ARTBOARD_DARK)) preview.setArtboard(PreviewPane.ARTBOARD_DARK)
            if (ImGui.menuItem("Preview Background: Gray", null, preview.artboardColor == PreviewPane.ARTBOARD_GRAY)) preview.setArtboard(PreviewPane.ARTBOARD_GRAY)
            if (ImGui.menuItem("Preview Background: White", null, preview.artboardColor == PreviewPane.ARTBOARD_WHITE)) preview.setArtboard(PreviewPane.ARTBOARD_WHITE)
            ImGui.endMenu()
        }
        if (ImGui.beginMenu("Insert")) {
            for (type in WidgetCodecRegistry.types()) {
                if (ImGui.menuItem("Add $type")) insert(type)
            }
            ImGui.endMenu()
        }
        if (ImGui.beginMenu("Help")) {
            if (ImGui.menuItem("About")) hint = "AcademyCraft UI Editor (out-of-game desktop tool)"
            if (ImGui.menuItem("Shortcuts")) hint = "Ctrl+Z/Y undo/redo · Ctrl+C/V copy/paste · Ctrl+D duplicate · Del delete · Alt+Up/Down move · Ctrl+O/S open/save · Ctrl+=/-/0 zoom"
            ImGui.endMenu()
        }
        ImGui.sameLine()
        ImGui.text("File name")
        ImGui.sameLine()
        ImGui.setNextItemWidth(160f)
        if (ImGui.inputText("##filename", fileName)) Unit
        ImGui.endMainMenuBar()
    }

    // ============ docking ============

    private fun ensureImGuiIni() {
        if (iniConfigured) return
        iniConfigured = true
        val io = ImGui.getIO()
        io.setIniFilename(iniFile().toString())
        io.setConfigWindowsMoveFromTitleBarOnly(true)
    }

    private fun renderDockHost(menuH: Float, displayW: Float, displayH: Float) {
        ImGui.setNextWindowPos(0f, menuH)
        ImGui.setNextWindowSize(displayW, displayH - menuH)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
        ImGui.begin("##UiDockHost", FLAG_DOCK_HOST)
        ImGui.popStyleVar(3)
        dockSpaceId = ImGui.getID("##ui_dockspace")
        ImGui.dockSpace(dockSpaceId, 0f, 0f, ImGuiDockNodeFlags.PassthruCentralNode)
        ImGui.end()
    }

    private fun ensureDefaultLayout(displayW: Float, displayH: Float) {
        if (layoutBuilt) return
        layoutBuilt = true
        buildDefaultLayout(displayW, displayH)
    }

    private fun buildDefaultLayout(displayW: Float, displayH: Float) {
        ImGuiDock.dockBuilderRemoveNode(dockSpaceId)
        ImGuiDock.dockBuilderAddNode(dockSpaceId)
        ImGuiDock.dockBuilderSetNodeSize(dockSpaceId, displayW, displayH)

        val left = ImInt()
        val rest = ImInt()
        ImGuiDock.dockBuilderSplitNode(dockSpaceId, ImGuiDir.Left, 0.18f, left, rest)
        val right = ImInt()
        val center = ImInt()
        ImGuiDock.dockBuilderSplitNode(rest.get(), ImGuiDir.Right, 0.26f, right, center)
        val bottom = ImInt()
        val canvas = ImInt()
        ImGuiDock.dockBuilderSplitNode(center.get(), ImGuiDir.Down, 0.3f, bottom, canvas)

        ImGuiDock.dockBuilderDockWindow(PANEL_TREE, left.get())
        ImGuiDock.dockBuilderDockWindow(PANEL_JSON, bottom.get())
        ImGuiDock.dockBuilderDockWindow(PANEL_CANVAS, canvas.get())
        ImGuiDock.dockBuilderDockWindow(PANEL_INSPECTOR, right.get())

        ImGuiDock.dockBuilderFinish(dockSpaceId)
    }

    private fun renderPreviewWindow() {
        ImGui.begin(PANEL_CANVAS, FLAG_CANVAS)
        // Content draws at CursorScreenPos (below title bar/padding), NOT at GetWindowPos.
        // Anchor win there so the blitted texture and the overlay share the same origin.
        val winX = ImGui.getCursorScreenPosX()
        val winY = ImGui.getCursorScreenPosY()
        val winW = ImGui.getContentRegionAvailX()
        val winH = ImGui.getContentRegionAvailY()
        preview.render(winX, winY, winW, winH)
        ImGui.end()
    }

    private fun renderStatus() {
        if (doc.error == null && !doc.dirty && hint == null) return
        ImGui.setNextWindowPos(0f, ImGui.getIO().getDisplaySizeY() - 40f)
        ImGui.setNextWindowSize(ImGui.getIO().getDisplaySizeX(), 40f)
        ImGui.begin("##uistatus", FLAG_NO_MOVE_RESIZE)
        when {
            doc.error != null -> ImGui.textColored(1f, 0.35f, 0.35f, 1f, "Error: ${doc.error}")
            doc.dirty -> {
                ImGui.textColored(1f, 0.85f, 0.4f, 1f, "Modified")
                ImGui.sameLine()
                hint?.let { ImGui.textColored(1f, 1f, 1f, 1f, it) }
            }
            else -> hint?.let { ImGui.textColored(1f, 1f, 1f, 1f, it) }
        }
        ImGui.end()
    }

    // ============ file / document ops ============

    private fun loadDocument(): UiEditorDocument {
        val file = environment.layoutDir().resolve("$layoutName.json")
        val rootJson = try {
            val obj = UiJson.GSON.fromJson(Files.readString(file), JsonObject::class.java)
            obj.getAsJsonObject("root") ?: obj
        } catch (e: Exception) {
            WidgetNode("frame_layout", "root").toJson()
        }
        return UiEditorDocument(layoutName, WidgetNode.fromJson(rootJson))
    }

    private fun newDocument() {
        switchDoc(UiEditorDocument("layout", WidgetNode("frame_layout", "root")))
        title = "AcademyCraft UI Editor — New Layout"
    }

    private fun openNative() {
        val dir = environment.layoutDir().toString()
        val path = openFileDialog("Open layout", dir) ?: return
        val file = Path.of(path)
        try {
            val obj = UiJson.GSON.fromJson(Files.readString(file), JsonObject::class.java)
            val name = file.fileName.toString().removeSuffix(".json")
            switchDoc(UiEditorDocument(name, WidgetNode.fromJson(obj.getAsJsonObject("root") ?: obj)))
            title = "AcademyCraft UI Editor — $name"
        } catch (e: Exception) {
            hint = "Failed to open: ${e.message}"
        }
    }

    private fun openLayout(name: String) {
        val newDoc = UiEditorDocument("", WidgetNode("frame_layout", "root")).loadFrom(environment.layoutDir(), name) ?: run {
            hint = "Failed to load $name"
            return
        }
        switchDoc(newDoc)
        title = "AcademyCraft UI Editor — ${doc.fileName}"
    }

    private fun switchDoc(newDoc: UiEditorDocument) {
        doc = newDoc
        doc.onMutated = { preview.rebuild() }
        fileName.set(doc.fileName)
        doc.setSelection(emptyList())
        jsonPanel.reset()
        treePanel.collapseAll()
        preview.onDocumentReplaced()
    }

    private fun save() {
        doc.fileName = fileName.get().trim().ifBlank { doc.fileName }
        try {
            val file = doc.saveTo(environment.layoutDir())
            title = "AcademyCraft UI Editor — ${doc.fileName}"
            hint = "Saved ${file.fileName}"
        } catch (e: Exception) {
            hint = "Save failed: ${e.message}"
        }
    }

    private fun saveAs() {
        val defaultPath = environment.layoutDir().resolve("${doc.fileName}.json").toString()
        val path = saveFileDialog("Save layout as", defaultPath) ?: return
        try {
            val file = Path.of(path)
            Files.writeString(file, doc.prettyJson())
            doc.fileName = file.fileName.toString().removeSuffix(".json")
            fileName.set(doc.fileName)
            title = "AcademyCraft UI Editor — ${doc.fileName}"
            hint = "Saved ${file.fileName}"
        } catch (e: Exception) {
            hint = "Save failed: ${e.message}"
        }
    }

    private fun reload() {
        val newDoc = UiEditorDocument("", WidgetNode("frame_layout", "root")).loadFrom(environment.layoutDir(), doc.fileName) ?: run {
            hint = "No file on disk yet"
            return
        }
        switchDoc(newDoc)
        title = "AcademyCraft UI Editor — ${doc.fileName}"
    }

    private fun listLayoutFiles(): List<String> {
        return try {
            Files.list(environment.layoutDir()).use { stream ->
                stream.map { it.fileName.toString() }
                    .filter { it.endsWith(".json") }
                    .map { it.removeSuffix(".json") }
                    .sorted()
                    .toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun openFileDialog(title: String, defaultPath: String): String? = try {
        MemoryStack.stackPush().use { stack ->
            val patterns = stack.mallocPointer(1)
            val pattern = stack.UTF8("*.json")
            patterns.put(0, pattern)
            patterns.flip()
            TinyFileDialogs.tinyfd_openFileDialog(title, defaultPath, patterns, "JSON files", false)
        }
    } catch (e: Throwable) {
        hint = "Native dialog unavailable"
        null
    }

    private fun saveFileDialog(title: String, defaultPath: String): String? = try {
        MemoryStack.stackPush().use { stack ->
            val patterns = stack.mallocPointer(1)
            val pattern = stack.UTF8("*.json")
            patterns.put(0, pattern)
            patterns.flip()
            TinyFileDialogs.tinyfd_saveFileDialog(title, defaultPath, patterns, "JSON files")
        }
    } catch (e: Throwable) {
        hint = "Native dialog unavailable"
        null
    }

    // ============ editing ops ============

    private fun undo() {
        doc.undo()
    }

    private fun redo() {
        doc.redo()
    }

    private fun copyNode() {
        val json = doc.copyNode() ?: return
        environment.setClipboard(json)
        hint = "Copied ${doc.selectedNode?.name}"
    }

    private fun pasteNode() {
        if (!doc.pasteNode(environment.clipboard()))
            hint = if (doc.selectedNode != null && !WidgetCodecRegistry.isContainerType(doc.selectedNode!!.type))
                "Cannot paste into non-container widget" else "Clipboard is not a widget"
    }

    private fun duplicate() {
        doc.duplicateSelected()
    }

    private fun deleteSelected() {
        doc.deleteSelected()
    }

    private fun moveSelected(delta: Int) {
        doc.moveSelected(delta)
    }

    private fun insert(type: String) {
        if (!doc.addChild(type) && doc.selectedNode != null && !WidgetCodecRegistry.isContainerType(doc.selectedNode!!.type)) {
            hint = "Cannot add child to non-container widget"
        }
    }

    // ============ zoom / helpers ============

    private fun setZoom(scale: Float) {
        preview.setZoomScale(scale)
    }

    private fun zoomStep(delta: Float) {
        preview.zoomStep(delta)
    }

    private fun iniFile(): Path = environment.workingDir.resolve("run").resolve("academy").resolve("imgui-ui.ini")

    // ============ shortcuts ============

    private fun registerShortcuts() {
        shortcuts.register(UiKeyMods.CTRL, ImGuiKey.Z) { undo() }
        shortcuts.register(UiKeyMods.CTRL or UiKeyMods.SHIFT, ImGuiKey.Z) { redo() }
        shortcuts.register(UiKeyMods.CTRL, ImGuiKey.Y) { redo() }
        shortcuts.register(UiKeyMods.CTRL, ImGuiKey.C) { copyNode() }
        shortcuts.register(UiKeyMods.CTRL, ImGuiKey.V) { pasteNode() }
        shortcuts.register(UiKeyMods.CTRL, ImGuiKey.D) { duplicate() }
        shortcuts.register(UiKeyMods.NONE, ImGuiKey.Delete) { deleteSelected() }
        shortcuts.register(UiKeyMods.NONE, ImGuiKey.UpArrow) { moveSelected(-1) }
        shortcuts.register(UiKeyMods.NONE, ImGuiKey.DownArrow) { moveSelected(1) }
        shortcuts.register(UiKeyMods.CTRL, ImGuiKey.N) { newDocument() }
        shortcuts.register(UiKeyMods.CTRL, ImGuiKey.O) { openNative() }
        shortcuts.register(UiKeyMods.CTRL, ImGuiKey.S) { save() }
        shortcuts.register(UiKeyMods.CTRL, ImGuiKey.Equal) { zoomStep(1f) }
        shortcuts.register(UiKeyMods.CTRL, ImGuiKey.Minus) { zoomStep(-1f) }
        shortcuts.register(UiKeyMods.CTRL, ImGuiKey._0) { setZoom(1f) }
        shortcuts.register(UiKeyMods.NONE, ImGuiKey.F) { preview.zoomToFit() }
        shortcuts.register(UiKeyMods.NONE, ImGuiKey.E) { preview.centerOnSelection() }
    }

    companion object {
        const val PANEL_CANVAS = "Preview"
        const val PANEL_TREE = "Tree"
        const val PANEL_INSPECTOR = "Inspector"
        const val PANEL_JSON = "JSON"

        private val FLAG_NO_MOVE_RESIZE = ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove or
            ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoBringToFrontOnFocus
        private val FLAG_DOCK_HOST = ImGuiWindowFlags.NoDocking or ImGuiWindowFlags.NoTitleBar or
            ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove or
            ImGuiWindowFlags.NoBringToFrontOnFocus or ImGuiWindowFlags.NoNavFocus
        private val FLAG_CANVAS = ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse
        private val FLAG_NO_DOCK = 0
    }
}
