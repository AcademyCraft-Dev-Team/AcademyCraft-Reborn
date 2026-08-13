package org.academy.desktop.uieditor

import com.google.gson.JsonObject
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.event.ScrollEvent
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.serialize.PropSpec
import org.academy.api.client.gui.serialize.PropType
import org.academy.api.client.gui.serialize.UiJson
import org.academy.api.client.gui.serialize.WidgetCodecRegistry
import org.academy.api.client.gui.serialize.WidgetNode
import org.academy.api.client.gui.serialize.WidgetSerializer
import org.academy.api.client.gui.serialize.asValueString
import org.academy.api.client.gui.serialize.containerFor
import org.academy.api.client.gui.serialize.setValue
import org.academy.api.client.gui.serialize.value
import org.academy.api.client.gui.widget.AbstractWidget
import org.academy.api.client.gui.widget.AbstractWidgetContainer
import org.academy.api.client.gui.widget.ButtonWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.ScrollPanelWidget
import org.academy.api.client.gui.widget.TextBoxWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.desktop.platform.DesktopEnvironment
import org.academy.desktop.platform.EditorApp
import org.academy.desktop.widgets.GravityPickerWidget
import org.academy.desktop.widgets.Menu
import org.academy.desktop.widgets.MenuBar
import org.academy.desktop.widgets.MenuItem
import org.academy.desktop.widgets.MenuPopup
import org.academy.desktop.widgets.PropFormWidget
import org.academy.desktop.widgets.SelectionBorderWidget
import org.academy.desktop.widgets.centeredLabel
import org.academy.desktop.widgets.vCenteredLabel
import org.academy.desktop.widgets.applyHoverState
import org.lwjgl.glfw.GLFW
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max

/**
 * Out-of-game visual layout editor. Layout: top toolbar (menu bar + quick
 * actions), body (left widget tree / center live preview / right inspector),
 * and a bottom editable JSON bar. Editing is driven by the widget codecs'
 * property schemas — never hand-written JSON.
 */
class UiEditorApp(
    private val environment: DesktopEnvironment,
    private val layoutName: String,
) : EditorApp {
    private lateinit var doc: UiEditorDocument
    private lateinit var root: FrameLayoutWidget
    private lateinit var content: LinearLayoutWidget
    private lateinit var menuBar: MenuBar
    private lateinit var menuPopup: MenuPopup
    private lateinit var zoomLabel: LabelWidget

    private lateinit var treeScroll: ScrollPanelWidget
    private lateinit var treePanel: LinearLayoutWidget
    private lateinit var previewHost: ScrollPanelWidget
    private lateinit var previewFrame: ZoomCanvas
    private lateinit var previewLayer: FrameLayoutWidget
    private lateinit var inspectorScroll: ScrollPanelWidget
    private lateinit var inspectorPanel: LinearLayoutWidget
    private lateinit var jsonBar: LinearLayoutWidget
    private lateinit var jsonScroll: JsonScrollPanel
    private lateinit var jsonBox: TextBoxWidget
    private lateinit var editJsonButton: ButtonWidget
    private lateinit var statusLabel: LabelWidget
    private lateinit var nameBox: TextBoxWidget

    private val expandedPaths = HashSet<String>()
    private var showTree = true
    private var showPreview = true
    private var showInspector = true
    private var showJson = true
    private var showSelectionBounds = true
    private var showGrid = false
    private var previewBg = 0xFF151515.toInt()

    private var jsonEditMode = false
    private var previewRoot: Widget? = null
    private var needsRebuild = true
    private var needsPreviewRefresh = false
    private var needsSelectionUpdate = false
    private var pendingCenter = false
    private var quit = false

    override var title = "AcademyCraft UI Editor — $layoutName"
    override fun quitRequested(): Boolean = quit

    // ============ root ============

    override fun createRoot(): WidgetContainer {
        doc = loadDocument()
        expandedPaths.add("")
        root = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            background = ColorDrawable(0xFF1E1E1E.toInt())
        }
        content = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        root.addChild("content", content)
        buildToolbar()
        buildBody()
        buildJsonBar()
        menuPopup = MenuPopup().apply { layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT) }
        menuPopup.setOnAction { needsRebuild = true }
        menuPopup.setOnHide { menuBar.activeMenu = null }
        root.addChild("popup", menuPopup)
        return root
    }

    override fun onFrame(partialTick: Float) {
        previewFrame.setViewport(previewHost.width, previewHost.height)
        if (needsRebuild) {
            needsRebuild = false
            needsSelectionUpdate = false
            rebuildTree()
            rebuildInspector()
            rebuildPreview()
            syncJson()
            refreshStatus()
        } else if (needsPreviewRefresh) {
            needsPreviewRefresh = false
            rebuildPreview()
            syncJson()
            refreshStatus()
        } else if (needsSelectionUpdate) {
            needsSelectionUpdate = false
            rebuildTree()
            rebuildInspector()
            updateSelectionBorder()
        }
        if (pendingCenter) {
            pendingCenter = false
            centerPreview()
        }
    }

    // ============ layout ============

    private fun buildToolbar() {
        val toolbar = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            layoutParams = LinearLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.FIXED)
                .height(30f)
                .paddingHorizontal(4f)
            spacing = 6f
            background = ColorDrawable(0xFF2B2B2E.toInt())
        }
        menuBar = MenuBar().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .heightMode(SizeMode.MATCH_PARENT)
        }
        menuBar.onOpen = { ax, ay, label ->
            menuBar.activeMenu = label
            val menu = buildMenus().firstOrNull { it.label == label }
            if (menu != null) menuPopup.show(ax, ay + 2f, menu)
        }
        menuBar.setMenus(buildMenus())
        toolbar.addChild("menus", menuBar)

        nameBox = TextBoxWidget(64).apply {
            text = doc.fileName
            baseFontSize = 12f
            layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.FIXED, SizeMode.MATCH_PARENT).width(150f)
            background = ColorDrawable(0x40303030)
        }
        toolbar.addChild("name", nameBox)

        toolbar.addChild("save", ButtonWidget(centeredLabel("Save")).apply {
            layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)
            applyHoverState(this, 0x50408A3C)
            onClickListener = OnClickListener { save() }
        })

        zoomLabel = LabelWidget(formatZoom(1f)).apply {
            baseFontSize = 12f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        toolbar.addChild("zoom", zoomLabel)

        statusLabel = LabelWidget("").apply {
            baseFontSize = 12f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        toolbar.addChild("status", statusLabel)

        content.addChild("toolbar", toolbar)
    }

    private fun buildBody() {
        val body = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
        }

        treePanel = LinearLayoutWidget().apply { orientation = Orientation.VERTICAL }
        treeScroll = ScrollPanelWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.FIXED, SizeMode.MATCH_PARENT).width(180f)
            background = ColorDrawable(0xFF252526.toInt())
        }
        treeScroll.setContent(treePanel)
        body.addChild("tree", treeScroll)

        previewHost = ScrollPanelWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).heightMode(SizeMode.MATCH_PARENT)
            background = ColorDrawable(previewBg)
        }
        previewFrame = ZoomCanvas().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        previewLayer = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.WRAP_CONTENT)
        }
        previewFrame.setContent(previewLayer)
        previewFrame.addChild("picker", makePicker())
        previewHost.setContent(previewFrame)
        body.addChild("preview", previewHost)

        inspectorPanel = LinearLayoutWidget().apply { orientation = Orientation.VERTICAL }
        inspectorScroll = ScrollPanelWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.FIXED, SizeMode.MATCH_PARENT).width(260f)
            background = ColorDrawable(0xFF252526.toInt())
        }
        inspectorScroll.setContent(inspectorPanel)
        body.addChild("inspector", inspectorScroll)

        content.addChild("body", body)
    }

    private fun buildJsonBar() {
        jsonBar = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            layoutParams = LinearLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.FIXED)
                .height(300f)
            background = ColorDrawable(0xFF1A1A1B.toInt())
        }
        val header = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
            spacing = 6f
        }
        header.addChild("title", LabelWidget("JSON").apply {
            baseFontSize = 13f
            layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.WRAP_CONTENT, SizeMode.WRAP_CONTENT)
        })
        editJsonButton = ButtonWidget(centeredLabel("Edit", 12f)).apply {
            layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.WRAP_CONTENT, SizeMode.WRAP_CONTENT)
            applyHoverState(this)
            onClickListener = OnClickListener { toggleJsonEdit() }
        }
        header.addChild("edit", editJsonButton)
        header.addChild("apply", ButtonWidget(centeredLabel("Apply", 12f)).apply {
            layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.WRAP_CONTENT, SizeMode.WRAP_CONTENT)
            applyHoverState(this)
            onClickListener = OnClickListener { applyJson() }
        })
        jsonBar.addChild("header", header)

        jsonBox = JsonTextBox(65536).apply {
            baseFontSize = 14f
            setAllowLineBreak(true)
            background = ColorDrawable(0x00000000)
        }
        jsonScroll = JsonScrollPanel().apply {
            layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).widthMode(SizeMode.MATCH_PARENT)
            background = ColorDrawable(0x40101010)
        }
        jsonScroll.setContent(jsonBox)
        jsonBar.addChild("scroll", jsonScroll)

        content.addChild("json", jsonBar)
    }

    private fun makePicker(): Widget {
        var panning = false
        var panStartX = 0.0
        var panStartY = 0.0
        var panStartScrollX = 0f
        var panStartScrollY = 0f
        return object : AbstractWidget() {
            init {
                isClickable = true
            }

            override fun onMousePressed(event: MouseEvent) {
                if (!isMouseOver(event.x, event.y)) return
                event.consume()
                val z = previewFrame.zoom
                val ox = previewFrame.getAbsoluteX()
                val oy = previewFrame.getAbsoluteY()
                // Map the pointer into the canvas content space: subtract the canvas
                // origin and the centering offset, then divide by the zoom scale.
                val tx = ox + (event.x - ox - previewFrame.contentOffsetX()) / z
                val ty = oy + (event.y - oy - previewFrame.contentOffsetY()) / z
                val path = hitTestPath(tx, ty)
                if (path != null) {
                    doc.setSelection(path)
                    needsSelectionUpdate = true
                } else {
                    // Empty space: start panning the preview canvas.
                    panning = true
                    panStartX = event.x
                    panStartY = event.y
                    panStartScrollX = previewHost.scrollX
                    panStartScrollY = previewHost.scrollY
                }
            }

            override fun onMouseDragged(event: MouseEvent) {
                if (!panning) return
                event.consume()
                val dx = (event.x - panStartX).toFloat()
                val dy = (event.y - panStartY).toFloat()
                previewHost.scrollTo(panStartScrollX - dx, panStartScrollY - dy)
            }

            override fun onMouseReleased(event: MouseEvent) {
                panning = false
            }

            override fun onMouseScrolled(event: ScrollEvent) {
                if (!isMouseOver(event.x, event.y)) return
                event.consume()
                if (event.ctrlDown) {
                    // Ctrl+wheel: zoom preview, anchored at the cursor.
                    val factor = if (event.delta > 0) 1.1f else 1 / 1.1f
                    zoomAt(event.x, event.y, factor)
                } else {
                    // Plain wheel: pan vertically (fast).
                    previewHost.setScrollTarget(previewHost.scrollY + event.delta.toFloat() * 40f)
                }
            }
        }.apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
    }

    private fun zoomAt(mx: Double, my: Double, factor: Float) {
        val newZoom = (previewFrame.zoom * factor).coerceIn(0.5f, 4f)
        previewFrame.zoom = newZoom
        zoomLabel.text = formatZoom(newZoom)
        previewHost.requestLayout()
        pendingCenter = true
    }

    // ============ menus ============

    private fun buildMenus(): List<Menu> {
        val layouts = listLayoutFiles()
        return listOf(
            Menu("File", listOf(
                MenuItem("New", { newDocument() }, "Ctrl+N"),
                MenuItem("Open File…", { openNative() }, "Ctrl+O"),
                *layouts.map { MenuItem("· $it", { openLayout(it) }, separatorBefore = true) }.toTypedArray(),
                MenuItem("Save", { save() }, "Ctrl+S", separatorBefore = true),
                MenuItem("Save As…", { saveAs() }),
                MenuItem("Reload from disk", { reload() }),
                MenuItem("Quit", { quit = true }, separatorBefore = true)
            )),
            Menu("Edit", listOf(
                MenuItem("Undo", { undo() }, "Ctrl+Z"),
                MenuItem("Redo", { redo() }, "Ctrl+Y"),
                MenuItem("Copy Node", { copyNode() }, "Ctrl+C", separatorBefore = true),
                MenuItem("Paste Node", { pasteNode() }, "Ctrl+V"),
                MenuItem("Duplicate Node", { duplicate() }, "Ctrl+D"),
                MenuItem("Delete Node", { deleteSelected() }, "Del", separatorBefore = true),
                MenuItem("Move Up", { moveSelected(-1) }, "Alt+Up"),
                MenuItem("Move Down", { moveSelected(1) }, "Alt+Down"),
                MenuItem("Collapse All", { expandedPaths.clear(); needsRebuild = true }, separatorBefore = true),
                MenuItem("Expand All", { expandAll(doc.root, ""); needsRebuild = true })
            )),
            Menu("Display", listOf(
                MenuItem("Zoom In", { zoom(1f) }, "Ctrl+="),
                MenuItem("Zoom Out", { zoom(-1f) }, "Ctrl+-"),
                MenuItem("Reset Zoom", { setZoom(1f) }, "Ctrl+0"),
                MenuItem("Tree", { togglePanel { showTree = !showTree } }, checked = { showTree }, separatorBefore = true),
                MenuItem("Preview", { togglePanel { showPreview = !showPreview } }, checked = { showPreview }),
                MenuItem("Inspector", { togglePanel { showInspector = !showInspector } }, checked = { showInspector }),
                MenuItem("JSON", { togglePanel { showJson = !showJson } }, checked = { showJson }),
                MenuItem("Selection Bounds", { showSelectionBounds = !showSelectionBounds; needsSelectionUpdate = true }, checked = { showSelectionBounds }, separatorBefore = true),
                MenuItem("Preview Grid", { showGrid = !showGrid; needsPreviewRefresh = true }, checked = { showGrid }),
                MenuItem("Preview Background: Dark", { setPreviewBg(0xFF151515.toInt()) }, checked = { previewBg == 0xFF151515.toInt() }),
                MenuItem("Preview Background: Gray", { setPreviewBg(0xFF808080.toInt()) }, checked = { previewBg == 0xFF808080.toInt() }),
                MenuItem("Preview Background: White", { setPreviewBg(0xFFFFFFFF.toInt()) }, checked = { previewBg == 0xFFFFFFFF.toInt() })
            )),
            Menu("Insert", WidgetCodecRegistry.types().map { type ->
                MenuItem("Add $type", { insert(type) })
            }),
            Menu("Help", listOf(
                MenuItem("About", { statusLabel.text = "AcademyCraft UI Editor (out-of-game desktop tool)" }),
                MenuItem(
                    "Shortcuts",
                    { statusLabel.text = "Ctrl+Z/Y undo/redo · Ctrl+C/V copy/paste node · Ctrl+D duplicate · Del delete · Alt+Up/Down move · Ctrl+O/S open/save · Ctrl+=/-/0 zoom" }
                )
            ))
        )
    }

    private fun listLayoutFiles(): List<String> {
        val dir = environment.layoutDir()
        return try {
            Files.list(dir).use { stream ->
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

    private fun togglePanel(change: () -> Unit) {
        change()
        applyPanelVisibility()
        needsRebuild = true
    }

    private fun applyPanelVisibility() {
        treeScroll.visibility = if (showTree) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
        previewHost.visibility = if (showPreview) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
        inspectorScroll.visibility = if (showInspector) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
        jsonBar.visibility = if (showJson) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
        root.requestLayout()
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
        doc = UiEditorDocument("layout", WidgetNode("frame_layout", "root"))
        nameBox.text = doc.fileName
        expandedPaths.clear()
        expandedPaths.add("")
        jsonEditMode = false
        needsRebuild = true
        title = "AcademyCraft UI Editor — New Layout"
    }

    private fun openNative() {
        val dir = environment.layoutDir().toString()
        val path = openFileDialog("Open layout", dir) ?: return
        val file = Path.of(path)
        try {
            val obj = UiJson.GSON.fromJson(Files.readString(file), JsonObject::class.java)
            doc = UiEditorDocument(file.fileName.toString().removeSuffix(".json"), WidgetNode.fromJson(obj.getAsJsonObject("root") ?: obj))
            nameBox.text = doc.fileName
            expandedPaths.clear()
            expandedPaths.add("")
            jsonEditMode = false
            needsRebuild = true
            title = "AcademyCraft UI Editor — ${doc.fileName}"
            statusLabel.text = "Opened ${file.fileName}"
        } catch (e: Exception) {
            statusLabel.text = "Failed to open: ${e.message}"
        }
    }

    private fun openLayout(name: String) {
        val newDoc = UiEditorDocument("", WidgetNode("frame_layout", "root")).loadFrom(environment.layoutDir(), name)
        if (newDoc == null) {
            statusLabel.text = "Failed to load $name"
            return
        }
        doc = newDoc
        nameBox.text = doc.fileName
        expandedPaths.clear()
        expandedPaths.add("")
        jsonEditMode = false
        needsRebuild = true
        title = "AcademyCraft UI Editor — ${doc.fileName}"
        statusLabel.text = "Opened $name"
    }

    private fun save() {
        doc.fileName = nameBox.text.trim().ifBlank { doc.fileName }
        try {
            val file = doc.saveTo(environment.layoutDir())
            title = "AcademyCraft UI Editor — ${doc.fileName}"
            statusLabel.text = "Saved ${file.fileName}"
        } catch (e: Exception) {
            statusLabel.text = "Save failed: ${e.message}"
        }
    }

    private fun saveAs() {
        val defaultPath = environment.layoutDir().resolve("${doc.fileName}.json").toString()
        val path = saveFileDialog("Save layout as", defaultPath) ?: return
        try {
            val file = Path.of(path)
            Files.writeString(file, doc.prettyJson())
            doc.fileName = file.fileName.toString().removeSuffix(".json")
            nameBox.text = doc.fileName
            title = "AcademyCraft UI Editor — ${doc.fileName}"
            statusLabel.text = "Saved ${file.fileName}"
        } catch (e: Exception) {
            statusLabel.text = "Save failed: ${e.message}"
        }
    }

    private fun openFileDialog(title: String, defaultPath: String): String? {
        return try {
            MemoryStack.stackPush().use { stack ->
                val patterns = stack.mallocPointer(1)
                val pattern = stack.UTF8("*.json")
                patterns.put(0, pattern)
                patterns.flip()
                TinyFileDialogs.tinyfd_openFileDialog(title, defaultPath, patterns, "JSON files", false)
            }
        } catch (e: Throwable) {
            statusLabel.text = "Native dialog unavailable"
            null
        }
    }

    private fun saveFileDialog(title: String, defaultPath: String): String? {
        return try {
            MemoryStack.stackPush().use { stack ->
                val patterns = stack.mallocPointer(1)
                val pattern = stack.UTF8("*.json")
                patterns.put(0, pattern)
                patterns.flip()
                TinyFileDialogs.tinyfd_saveFileDialog(title, defaultPath, patterns, "JSON files")
            }
        } catch (e: Throwable) {
            statusLabel.text = "Native dialog unavailable"
            null
        }
    }

    private fun reload() {
        val newDoc = UiEditorDocument("", WidgetNode("frame_layout", "root")).loadFrom(environment.layoutDir(), doc.fileName)
        if (newDoc != null) {
            doc = newDoc
            expandedPaths.clear()
            expandedPaths.add("")
            jsonEditMode = false
            needsRebuild = true
            statusLabel.text = "Reloaded ${doc.fileName}"
        } else {
            statusLabel.text = "No file on disk yet"
        }
    }

    private fun undo() {
        if (doc.undo()) needsRebuild = true
    }

    private fun redo() {
        if (doc.redo()) needsRebuild = true
    }

    private fun copyNode() {
        val json = doc.copyNode() ?: return
        environment.setClipboard(json)
        statusLabel.text = "Copied ${doc.selectedNode?.name}"
    }

    private fun pasteNode() {
        if (doc.pasteNode(environment.clipboard())) {
            needsRebuild = true
        } else {
            statusLabel.text = if (doc.selectedNode != null && !WidgetCodecRegistry.isContainerType(doc.selectedNode!!.type))
                "Cannot paste into non-container widget"
            else "Clipboard is not a widget"
        }
    }

    private fun duplicate() {
        if (doc.duplicateSelected()) needsRebuild = true
    }

    private fun deleteSelected() {
        doc.deleteSelected()
        needsRebuild = true
    }

    private fun moveSelected(delta: Int) {
        doc.moveSelected(delta)
        needsRebuild = true
    }

    private fun insert(type: String) {
        if (doc.addChild(type)) {
            needsRebuild = true
        } else if (doc.selectedNode != null && !WidgetCodecRegistry.isContainerType(doc.selectedNode!!.type)) {
            statusLabel.text = "Cannot add child to non-container widget"
        }
    }

    private fun expandAll(node: WidgetNode, path: String) {
        expandedPaths.add(path)
        for (child in node.children) {
            expandAll(child, if (path.isEmpty()) child.name else "$path/${child.name}")
        }
    }

    // ============ view ============

    private fun zoom(delta: Float) = setZoom(previewFrame.zoom + delta)

    private fun setZoom(scale: Float) {
        previewFrame.zoom = scale.coerceIn(0.5f, 4f)
        zoomLabel.text = formatZoom(previewFrame.zoom)
        previewHost.requestLayout()
        pendingCenter = true
    }

    private fun formatZoom(scale: Float): String {
        val text = if (scale == scale.toInt().toFloat()) scale.toInt().toString() else String.format(java.util.Locale.ROOT, "%.2f", scale)
        return "${text}x"
    }

    private fun setPreviewBg(color: Int) {
        previewBg = color
        previewHost.background = ColorDrawable(color)
        needsRebuild = true
    }

    // ============ json bar ============

    private fun syncJson() {
        if (jsonEditMode) return
        jsonBox.text = doc.prettyJson()
    }

    private fun toggleJsonEdit() {
        jsonEditMode = !jsonEditMode
        val label = if (jsonEditMode) "Done" else "Edit"
        (editJsonButton.children.values.firstOrNull() as? LabelWidget)?.text = label
        if (jsonEditMode) jsonBox.text = doc.prettyJson()
    }

    private fun applyJson() {
        val err = doc.applyJsonText(jsonBox.text)
        if (err == null) {
            jsonEditMode = false
            (editJsonButton.children.values.firstOrNull() as? LabelWidget)?.text = "Edit"
            needsRebuild = true
            statusLabel.text = "Applied JSON"
        } else {
            statusLabel.text = err
        }
    }

    // ============ rebuilds ============

    private fun rebuildTree() {
        treePanel.clearChildren()
        val selected = doc.selectedNode
        fun addNode(node: WidgetNode, depth: Int, path: List<String>) {
            treePanel.addChild("node_${node.name}_${node.hashCode()}", buildTreeRow(node, depth, path, selected))
            if (expandedPaths.contains(path.joinToString("/"))) {
                for (child in node.children) addNode(child, depth + 1, path + child.name)
            }
        }
        addNode(doc.root, 0, emptyList())
    }

    private fun buildTreeRow(node: WidgetNode, depth: Int, path: List<String>, selected: WidgetNode?): Widget {
        val row = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            layoutParams = LinearLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.FIXED)
                .height(24f)
                .marginLeft(depth * 14f)
        }
        val hasChildren = node.children.isNotEmpty()
        if (hasChildren) {
            val key = path.joinToString("/")
            val expanded = expandedPaths.contains(key)
            row.addChild("toggle", ButtonWidget(centeredLabel(if (expanded) "▾" else "▸", 10f)).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.FIXED, SizeMode.MATCH_PARENT).width(16f)
                applyHoverState(this)
                onClickListener = OnClickListener {
                    if (expanded) expandedPaths.remove(key) else expandedPaths.add(key)
                    needsRebuild = true
                }
            })
        }
        val label = centeredLabel("${node.name}  [${node.type}]", 12f)
        val select = ButtonWidget(label).apply {
            layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).heightMode(SizeMode.MATCH_PARENT)
            applyHoverState(this, if (node === selected) 0xFF2D6A9F.toInt() else 0x00000000)
            onClickListener = OnClickListener {
                doc.setSelection(path)
                needsSelectionUpdate = true
            }
        }
        row.addChild("select", select)
        return row
    }

    private fun rebuildInspector() {
        inspectorPanel.clearChildren()
        addSection("Structure")
        buildStructureSection(doc.selectedNode)

        val node = doc.selectedNode
        if (node != null) {
            addSection("Layout")
            addLayoutForm(node)
            addSection("Common")
            addPropForm(COMMON_FIELDS, node)
            val props = WidgetCodecRegistry.byType<Widget>(node.type)?.propertySchema
            if (props?.isNotEmpty() == true) {
                addSection("Props")
                addPropForm(props, node)
            }
        }
    }

    private fun addSection(title: String) {
        inspectorPanel.addChild(
            "hdr_$title",
            LabelWidget(title).apply {
                baseFontSize = 13f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    .paddingVertical(3f)
            }
        )
    }

    private fun buildStructureSection(node: WidgetNode?) {
        if (node == null) {
            inspectorPanel.addChild(
                "none",
                vCenteredLabel("Select a widget to edit").apply {
                    baseFontSize = 12f
                    layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                }
            )
            return
        }
        inspectorPanel.addChild(
            "type",
            vCenteredLabel("Type: ${node.type}").apply {
                baseFontSize = 12f
                layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
            }
        )

        // Rename row
        val nameField = TextBoxWidget(64).apply {
            text = node.name
            baseFontSize = 12f
            background = ColorDrawable(0x40303030)
        }
        nameField.setOnFocusLost(Runnable {
            if (nameField.text.trim().isNotEmpty() && nameField.text.trim() != node.name) {
                doc.renameSelected(nameField.text.trim())
                needsRebuild = true
            }
        })
        inspectorPanel.addChild(
            "name",
            LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.FIXED).height(24f)
                spacing = 4f
                addChild("lbl", vCenteredLabel("Name").apply {
                    baseFontSize = 12f
                    layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.FIXED, SizeMode.MATCH_PARENT).width(48f)
                })
                addChild("field", nameField.apply {
                    layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).heightMode(SizeMode.MATCH_PARENT)
                })
            }
        )

        // Add-child row
        val types = WidgetCodecRegistry.types()
        var addType = types.firstOrNull() ?: "empty"
        val typeLabel = centeredLabel(addType, 12f)
        val typeButton = ButtonWidget(typeLabel).apply {
            layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)
            applyHoverState(this)
            onClickListener = OnClickListener {
                val idx = types.indexOf(addType)
                addType = types[(idx + 1) % types.size]
                typeLabel.text = addType
            }
        }
        val addButton = ButtonWidget(centeredLabel("Add", 12f)).apply {
            layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)
            applyHoverState(this)
            onClickListener = OnClickListener {
                if (doc.addChild(addType)) {
                    needsRebuild = true
                } else {
                    statusLabel.text = "Cannot add child to non-container widget"
                }
            }
        }
        inspectorPanel.addChild(
            "addRow",
            LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.FIXED).height(24f)
                spacing = 4f
                addChild("lbl", vCenteredLabel("Add").apply {
                    baseFontSize = 12f
                    layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.FIXED, SizeMode.MATCH_PARENT).width(48f)
                })
                addChild("type", typeButton.apply {
                    layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).heightMode(SizeMode.MATCH_PARENT)
                })
                addChild("go", addButton)
            }
        )

        // Action buttons
        fun actionButton(text: String, action: () -> Unit): ButtonWidget {
            return ButtonWidget(centeredLabel(text, 12f)).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).heightMode(SizeMode.MATCH_PARENT)
                applyHoverState(this)
                onClickListener = OnClickListener { action() }
            }
        }
        inspectorPanel.addChild(
            "actions",
            LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.FIXED).height(24f)
                spacing = 4f
                addChild("del", actionButton("Delete") { deleteSelected() })
                addChild("dup", actionButton("Duplicate") { duplicate() })
                addChild("up", actionButton("↑") { moveSelected(-1) })
                addChild("down", actionButton("↓") { moveSelected(1) })
            }
        )
    }

    // ============ semantic inspector forms ============

    private fun addLayoutForm(node: WidgetNode) {
        addSizeRow(node, "width_mode", "width", "Width")
        addSizeRow(node, "height_mode", "height", "Height")

        inspectorPanel.addChild("gravity", fieldRow("gravity", GravityPickerWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).heightMode(SizeMode.WRAP_CONTENT)
            val picker = this
            fun pick(g: Int) {
                editValue(node, "gravity", PropType.INT, g.toString())
                picker.setGravity(g) { pick(it) }
            }
            setGravity(node.value("gravity")?.asValueString(PropType.INT)?.toIntOrNull() ?: Gravity.CENTER) { pick(it) }
        }, wrapHeight = true))

        addPropForm(LAYOUT_DETAIL_FIELDS, node)
    }

    private fun addSizeRow(node: WidgetNode, modeKey: String, valueKey: String, label: String) {
        val modes = listOf("MATCH_PARENT", "WRAP_CONTENT", "FIXED")
        val modeLabel = centeredLabel(node.value(modeKey)?.asString ?: "WRAP_CONTENT", 10f)
        val modeButton = ButtonWidget(modeLabel).apply {
            layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.FIXED, SizeMode.MATCH_PARENT).width(96f)
            applyHoverState(this)
            onClickListener = OnClickListener {
                val cur = node.value(modeKey)?.asString ?: "WRAP_CONTENT"
                val next = modes[(modes.indexOf(cur) + 1).mod(modes.size)]
                modeLabel.text = next
                editValue(node, modeKey, PropType.TEXT, next)
            }
        }
        val valueField = numberEditor(node, valueKey, PropType.FLOAT, 0f, 4096f)
        inspectorPanel.addChild(
            "size_$label",
            fieldRow(label, LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                spacing = 4f
                layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).heightMode(SizeMode.MATCH_PARENT)
                addChild("mode", modeButton)
                addChild("value", valueField)
            })
        )
    }

    private fun fieldRow(label: String, editor: Widget, wrapHeight: Boolean = false): Widget {
        val lp = if (wrapHeight) {
            LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
        } else {
            LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.FIXED).height(24f)
        }
        return LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            layoutParams = lp
            spacing = 4f
            addChild("lbl", vCenteredLabel(label).apply {
                baseFontSize = 12f
                layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.FIXED, SizeMode.MATCH_PARENT).width(72f)
            })
            addChild("editor", editor)
        }
    }

    private fun numberEditor(node: WidgetNode, key: String, type: PropType, min: Float, max: Float): Widget {
        val box = TextBoxWidget(256).apply {
            text = node.value(key)?.asValueString(type) ?: ""
            placeholder = if (type == PropType.COLOR) "#00000000" else "0"
            baseFontSize = 12f
            background = ColorDrawable(0x40303030)
            layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).heightMode(SizeMode.MATCH_PARENT)
        }
        box.setOnFocusLost(Runnable {
            val parsed = box.text.trim().toFloatOrNull()
            if (parsed != null) {
                val clamped = parsed.coerceIn(min, max)
                val out = formatNumber(clamped, type)
                box.text = out
                editValue(node, key, type, out)
            } else {
                box.text = node.value(key)?.asValueString(type) ?: ""
            }
        })
        return box
    }

    private fun formatNumber(value: Float, type: PropType): String {
        if (type == PropType.INT) return value.toInt().toString()
        if (value % 1f == 0f && value in -1e7f..1e7f) return value.toInt().toString()
        return value.toString()
    }

    private fun editValue(node: WidgetNode, key: String, type: PropType, value: String) {
        node.setValue(key, type, value)
        doc.mutate { }
        statusLabel.text = "Modified $key"
        needsPreviewRefresh = true
    }

    private fun addPropForm(specs: List<PropSpec>, node: WidgetNode) {
        val form = PropFormWidget()
        val typeByKey = specs.associate { it.key to it.type }
        form.setForm(
            specs,
            { key -> node.value(key).asValueString(typeByKey[key] ?: PropType.TEXT) },
            { key, value ->
                val spec = specs.first { it.key == key }
                node.setValue(key, spec.type, value)
                doc.mutate { }
                statusLabel.text = "Modified $key"
                needsPreviewRefresh = true
            }
        )
        form.layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
        inspectorPanel.addChild("form_${specs.joinToString { it.key }}", form)
    }

    private fun rebuildPreview() {
        previewLayer.clearChildren()
        if (showGrid) {
            previewLayer.addChild("grid", GridWidget())
        }
        previewRoot = try {
            WidgetSerializer.decode(doc.documentJson())
        } catch (e: Exception) {
            doc.reportError("Invalid layout: ${e.message}")
            null
        }
        val decoded = previewRoot
        if (decoded != null) {
            doc.reportError(null)
            previewLayer.addChild("preview", decoded)
            updateSelectionBorder()
        }
        previewFrame.requestLayout()
        pendingCenter = true
    }

    /**
     * Scrolls the preview so the canvas center aligns with the viewport center
     * (content is already centered inside the canvas via its contentOffset).
     * Called on the frame *after* layout runs, so the canvas reports its size.
     */
    private fun centerPreview() {
        val vw = previewHost.width
        val vh = previewHost.height
        val canvasW = previewFrame.width
        val canvasH = previewFrame.height
        if (vw <= 0f || vh <= 0f || canvasW <= 0f || canvasH <= 0f) {
            pendingCenter = true
            return
        }
        val targetX = (canvasW - vw) / 2f
        val targetY = (canvasH - vh) / 2f
        previewHost.setScrollTarget(targetY)
        previewHost.scrollTo(targetX, previewHost.scrollY)
    }

    /** Re-attaches the selection highlight without re-decoding the preview tree. */
    private fun updateSelectionBorder() {
        previewLayer.removeChild("selection")
        val decoded = previewRoot ?: return
        if (showSelectionBounds) {
            val path = doc.selectedPath
            if (path.isNotEmpty()) {
                val target = findWidgetByPath(decoded, path)
                if (target != null) {
                    previewLayer.addChild("selection", SelectionBorderWidget { target })
                }
            }
        }
    }

    // ============ picker / hit-test ============

    private fun hitTestPath(x: Double, y: Double): List<String>? {
        val rootW = previewRoot ?: return null
        val hit = findDeepestWidgetAt(rootW, x, y) ?: return null
        if (hit === rootW) return null
        val path = mutableListOf<String>()
        var cur: Widget? = hit
        while (cur != null && cur !== rootW) {
            path.add(cur.name)
            cur = cur.parent
        }
        path.reverse()
        return if (UiEditorDocument.findNodeByPath(doc.root, path) != null) path else null
    }

    private fun findDeepestWidgetAt(w: Widget, x: Double, y: Double): Widget? {
        if (!w.isVisible() || !w.isMouseOver(x, y)) return null
        if (w is WidgetContainer) {
            for (child in w.children.values.toList().asReversed()) {
                val result = findDeepestWidgetAt(child, x, y)
                if (result != null) return result
            }
        }
        return w
    }

    private fun findWidgetByPath(w: Widget, path: List<String>): Widget? {
        if (path.isEmpty()) return w
        if (w !is WidgetContainer) return null
        val child = w.children[path[0]] ?: return null
        return findWidgetByPath(child, path.drop(1))
    }

    // ============ shortcuts ============

    override fun onKey(key: Int, action: Int, modifiers: Int): Boolean {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return false
        if (menuPopup.isOpen && key == GLFW.GLFW_KEY_ESCAPE) {
            menuPopup.hide()
            return true
        }
        val ctrl = (modifiers and GLFW.GLFW_MOD_CONTROL) != 0
        val alt = (modifiers and GLFW.GLFW_MOD_ALT) != 0
        val shift = (modifiers and GLFW.GLFW_MOD_SHIFT) != 0
        if (TextBoxWidget.isAnyTextEditing()) {
            // A text field (JSON box, name box, inspector) is being edited: let the
            // framework's text box handle clipboard/arrow/caret keys itself. Only
            // global shortcuts (save/open/new/zoom) are still handled here.
            return when {
                ctrl && key == GLFW.GLFW_KEY_S -> {
                    save()
                    true
                }

                ctrl && key == GLFW.GLFW_KEY_O -> {
                    openNative()
                    true
                }

                ctrl && key == GLFW.GLFW_KEY_N -> {
                    newDocument()
                    true
                }

                ctrl && (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) -> {
                    zoom(1f)
                    true
                }

                ctrl && (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) -> {
                    zoom(-1f)
                    true
                }

                ctrl && (key == GLFW.GLFW_KEY_0 || key == GLFW.GLFW_KEY_KP_0) -> {
                    setZoom(1f)
                    true
                }

                else -> false
            }
        }
        return when {
            ctrl && key == GLFW.GLFW_KEY_Z -> {
                if (shift) redo() else undo()
                true
            }

            ctrl && key == GLFW.GLFW_KEY_Y -> {
                redo()
                true
            }

            ctrl && key == GLFW.GLFW_KEY_C -> {
                copyNode()
                true
            }

            ctrl && key == GLFW.GLFW_KEY_V -> {
                pasteNode()
                true
            }

            ctrl && key == GLFW.GLFW_KEY_D -> {
                duplicate()
                true
            }

            ctrl && key == GLFW.GLFW_KEY_S -> {
                save()
                true
            }

            ctrl && key == GLFW.GLFW_KEY_O -> {
                openNative()
                true
            }

            ctrl && key == GLFW.GLFW_KEY_N -> {
                newDocument()
                true
            }

            key == GLFW.GLFW_KEY_DELETE -> {
                deleteSelected()
                true
            }

            alt && key == GLFW.GLFW_KEY_UP -> {
                moveSelected(-1)
                true
            }

            alt && key == GLFW.GLFW_KEY_DOWN -> {
                moveSelected(1)
                true
            }

            ctrl && (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) -> {
                zoom(1f)
                true
            }

            ctrl && (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) -> {
                zoom(-1f)
                true
            }

            ctrl && (key == GLFW.GLFW_KEY_0 || key == GLFW.GLFW_KEY_KP_0) -> {
                setZoom(1f)
                true
            }

            else -> false
        }
    }

    private fun refreshStatus() {
        when {
            doc.error != null -> {
                statusLabel.text = "✗ ${doc.error}"
                statusLabel.setRed(1f); statusLabel.setGreen(0.45f); statusLabel.setBlue(0.45f)
            }

            doc.dirty -> {
                statusLabel.text = "● modified"
                statusLabel.setRed(1f); statusLabel.setGreen(0.85f); statusLabel.setBlue(0.3f)
            }

            else -> {
                statusLabel.text = "● ok"
                statusLabel.setRed(0.4f); statusLabel.setGreen(0.8f); statusLabel.setBlue(0.4f)
            }
        }
    }

    companion object {
        private val LAYOUT_DETAIL_FIELDS = listOf(
            PropSpec("weight", PropType.FLOAT, -1f, 1024f),
            PropSpec("margin_left", PropType.FLOAT, -4096f, 4096f),
            PropSpec("margin_top", PropType.FLOAT, -4096f, 4096f),
            PropSpec("margin_right", PropType.FLOAT, -4096f, 4096f),
            PropSpec("margin_bottom", PropType.FLOAT, -4096f, 4096f),
            PropSpec("padding_left", PropType.FLOAT, 0f, 4096f),
            PropSpec("padding_top", PropType.FLOAT, 0f, 4096f),
            PropSpec("padding_right", PropType.FLOAT, 0f, 4096f),
            PropSpec("padding_bottom", PropType.FLOAT, 0f, 4096f)
        )
        private val COMMON_FIELDS = listOf(
            PropSpec("visibility", PropType.ENUM, options = listOf("VISIBLE", "INVISIBLE", "GONE")),
            PropSpec("alpha", PropType.FLOAT, 0f, 1f),
            PropSpec("enabled", PropType.BOOLEAN),
            PropSpec("clickable", PropType.BOOLEAN),
            PropSpec("selected", PropType.BOOLEAN),
            PropSpec("cover_all_prev", PropType.BOOLEAN),
            PropSpec("translation_x", PropType.FLOAT, -4096f, 4096f),
            PropSpec("translation_y", PropType.FLOAT, -4096f, 4096f),
            PropSpec("scale_x", PropType.FLOAT, -8f, 8f),
            PropSpec("scale_y", PropType.FLOAT, -8f, 8f),
            PropSpec("rotation", PropType.FLOAT, -360f, 360f),
            PropSpec("origin_x", PropType.FLOAT, -4f, 4f),
            PropSpec("origin_y", PropType.FLOAT, -4f, 4f)
        )
    }
}

/**
 * A multi-line text box that renders glyphs at their true size instead of
 * scaling the whole block down to fit its constraints (as [LabelWidget] does),
 * so the JSON editor stays readable and scrolls instead of shrinking.
 */
private class JsonTextBox(maxLength: Int) : TextBoxWidget(maxLength) {
    override fun calculateLayoutScale(
        baseTextWidth: Float,
        baseTextHeight: Float,
        constraintWidth: Float,
        constraintHeight: Float
    ): Float = 1f
}

/**
 * A scroll panel that measures its content with both axes unconstrained, so the
 * JSON text can scroll horizontally (long lines) and vertically (many lines).
 */
private class JsonScrollPanel : ScrollPanelWidget(Orientation.VERTICAL) {
    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val content = children["content"]
        val lp = layoutParams
        var desiredWidth = lp.paddingLeft + lp.paddingRight
        var desiredHeight = lp.paddingTop + lp.paddingBottom
        if (content != null && content.isVisible()) {
            content.measure(
                MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f),
                MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
            )
            val clp = content.layoutParams
            desiredWidth += content.measuredWidth + clp.marginLeft + clp.marginRight
            desiredHeight += content.measuredHeight + clp.marginTop + clp.marginBottom
        }
        setMeasuredDimension(
            AbstractWidget.resolveSize(desiredWidth, widthMeasureSpec),
            AbstractWidget.resolveSize(desiredHeight, heightMeasureSpec)
        )
    }
}

/** Dotted alignment grid drawn behind the live preview. */
private class GridWidget : AbstractWidget() {
    private val spacing = 20f

    override fun renderInternal(context: RenderContext) {
        val w = width
        val h = height
        if (w <= 0f || h <= 0f) return
        val r = 0.3f
        val g = 0.45f
        val b = 0.65f
        val a = 0.25f
        var x = spacing
        while (x < w) {
            context.pose().pushPose()
            context.pose().translate(x, 0f)
            context.submit(FillRectDrawCommand(1f, h, r, g, b, a * context.accumulatedAlpha))
            context.pose().popPose()
            x += spacing
        }
        var y = spacing
        while (y < h) {
            context.pose().pushPose()
            context.pose().translate(0f, y)
            context.submit(FillRectDrawCommand(w, 1f, r, g, b, a * context.accumulatedAlpha))
            context.pose().popPose()
            y += spacing
        }
    }
}

/**
 * Canvas that hosts the live preview and scales its content by [zoom] (preview-only
 * zoom, independent of the editor UI's guiScale). Content is laid out at natural
 * size and rendered through a uniform scale about the origin; the canvas reports a
 * measured size of `natural * zoom` so the host ScrollPanel can scroll.
 */
private class ZoomCanvas : AbstractWidgetContainer() {
    var zoom: Float = 1f
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    private var naturalWidth = 0f
    private var naturalHeight = 0f
    private var minCanvasWidth = 0f
    private var minCanvasHeight = 0f

    /** Ensures the canvas (paper) is at least as large as the given viewport. */
    fun setViewport(width: Float, height: Float) {
        if (minCanvasWidth != width || minCanvasHeight != height) {
            minCanvasWidth = width
            minCanvasHeight = height
            requestLayout()
            invalidate()
        }
    }

    override fun generateDefaultLayoutParams(): WidgetContainer.LayoutParams {
        return FrameLayoutWidget.LayoutParams()
    }

    override fun generateLayoutParams(p: WidgetContainer.LayoutParams): WidgetContainer.LayoutParams {
        return FrameLayoutWidget.LayoutParams(p)
    }

    override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean {
        return p is FrameLayoutWidget.LayoutParams
    }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val child = children["content"] ?: run {
            setMeasuredDimension(0f, 0f)
            return
        }
        if (child.isVisible()) {
            // MATCH_PARENT roots fill the viewport width (like the pre-zoom preview);
            // height stays natural so the ScrollPanel can scroll once zoomed past it.
            val widthSpec = if (widthMeasureSpec.mode == MeasureSpec.Mode.EXACTLY) {
                MeasureSpec(MeasureSpec.Mode.EXACTLY, widthMeasureSpec.size)
            } else {
                MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
            }
            child.measure(widthSpec, MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f))
            naturalWidth = child.measuredWidth
            naturalHeight = child.measuredHeight
        }
        val scaledW = max(naturalWidth * zoom, minCanvasWidth)
        val scaledH = max(naturalHeight * zoom, minCanvasHeight)
        // One viewport of margin around the scaled content gives the ScrollPanel a
        // scroll range (== scaled content size) even when content is tiny, so it can
        // be panned freely; the content is centered inside via [contentOffsetX/Y].
        val canvasW = scaledW + minCanvasWidth
        val canvasH = scaledH + minCanvasHeight
        setMeasuredDimension(canvasW, canvasH)
        // The overlay (picker) covers the full canvas at its rendered size.
        for ((name, overlay) in children) {
            if (name == "content" || !overlay.isVisible()) continue
            overlay.measure(MeasureSpec(MeasureSpec.Mode.EXACTLY, canvasW), MeasureSpec(MeasureSpec.Mode.EXACTLY, canvasH))
        }
    }

    /** Offset (in canvas units, before zoom scale) that centers the content. */
    fun contentOffsetX(): Float = (width - naturalWidth * zoom) / 2f
    fun contentOffsetY(): Float = (height - naturalHeight * zoom) / 2f

    override fun onLayout() {
        val child = children["content"] ?: return
        if (child.isVisible()) {
            child.layout(0f, 0f, child.measuredWidth, child.measuredHeight)
        }
        for ((name, overlay) in children) {
            if (name == "content" || !overlay.isVisible()) continue
            overlay.layout(0f, 0f, width, height)
        }
    }

    override fun render(context: RenderContext) {
        if (!isVisible()) return
        context.pose().pushPose()
        context.drawOrder().push()
        run {
            context.drawOrder().advance()
            drawCanvasPaper(context)
            val content = children["content"]
            if (content != null && content.isVisible()) {
                context.pose().pushPose()
                run {
                    context.pose().translate(contentOffsetX(), contentOffsetY())
                    context.pose().scale(zoom, zoom)
                    context.alpha().push(alpha)
                    run {
                        renderInternal(context)
                        renderContent(content, context)
                    }
                    context.alpha().pop()
                }
                context.pose().popPose()
            }
            // Overlays (picker) render unscaled on top of the scaled content.
            for ((name, overlay) in children) {
                if (name == "content" || !overlay.isVisible()) continue
                context.pose().pushPose()
                run {
                    context.pose().translate(overlay.x, overlay.y)
                    overlay.render(context)
                }
                context.pose().popPose()
            }
        }
        context.drawOrder().pop()
        context.pose().popPose()
    }

    private fun renderContent(content: Widget, context: RenderContext) {
        context.pose().pushPose()
        run {
            context.pose().translate(content.x, content.y)
            content.render(context)
        }
        context.pose().popPose()
    }

    /** Draws the canvas "paper" backdrop + border behind the zoomed content. */
    private fun drawCanvasPaper(context: RenderContext) {
        val w = width
        val h = height
        if (w <= 0f || h <= 0f) return
        val a = alpha * context.accumulatedAlpha
        context.submit(FillRectDrawCommand(w, h, 0x1F / 255f, 0x1F / 255f, 0x23 / 255f, a))
        val br = 0x3A / 255f
        val bg = 0x3A / 255f
        val bb = 0x40 / 255f
        context.submit(FillRectDrawCommand(w, 1f, br, bg, bb, a))
        context.pose().pushPose()
        context.pose().translate(0f, h - 1f)
        context.submit(FillRectDrawCommand(w, 1f, br, bg, bb, a))
        context.pose().popPose()
        context.submit(FillRectDrawCommand(1f, h, br, bg, bb, a))
        context.pose().pushPose()
        context.pose().translate(w - 1f, 0f)
        context.submit(FillRectDrawCommand(1f, h, br, bg, bb, a))
        context.pose().popPose()
    }

    fun setContent(content: Widget?) {
        if (children["content"] === content) return
        clearChildren()
        if (content != null) addChild("content", content)
    }
}
