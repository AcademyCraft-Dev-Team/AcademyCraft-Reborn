package org.academy.desktop.grapheditor.canvas

import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImDrawFlags
import imgui.flag.ImGuiMouseButton
import org.academy.api.client.render.graph.model.PortDirection
import org.academy.api.client.render.graph.type.TypeConversions
import org.academy.api.client.render.graph.type.ValueType
import org.academy.desktop.grapheditor.clipboard.GraphClipboard
import org.academy.desktop.grapheditor.document.FrameData
import org.academy.desktop.grapheditor.document.NoteData

/**
 * ImGui 节点画布：网格 + 平移缩放 + 节点/端口/贝塞尔连线 + 选择/成组移动/框选/
 * 连线/边重连/端口高亮 + 分组 frame + sticky note + 网格吸附 + 右键上下文菜单请求。
 *
 * 删除/复制/粘贴/对齐/分组等动作经公开方法暴露，供快捷键与上下文菜单调用（宿主渲染弹窗）。
 */
class NodeCanvas(
    private val modelRef: GraphEditorModelRef,
    private val camera: Camera2D,
    private val clipboard: GraphClipboard,
) {
    private val model: GraphEditorModel get() = modelRef.model
    val selected: MutableSet<String> = mutableSetOf()

    /** 最近一帧画布窗口矩形 [x, y, w, h]（屏幕坐标），供宿主计算画布中心。 */
    var canvasRect: FloatArray = floatArrayOf(0f, 0f, 0f, 0f)
        private set

    var snippet: String? = null

    /** 节点/帧/笔记移动是否吸附网格。 */
    var snapEnabled = true

    /** 画布内容顶部内缩（宿主在窗口顶部渲染文档标签栏后设置，单位屏幕像素）。 */
    var topInset = 0f

    /** 本帧由右键设置的上下文菜单请求；宿主读取后渲染弹窗。 */
    var contextRequest: ContextRequest? = null
        private set

    /** 宿主渲染完弹窗后清除请求（保持仅当前右键目标有效）。 */
    fun clearContextRequest() {
        contextRequest = null
    }

    /** 本帧双击节点请求（宿主读取后决定是否打开子图并清除）。 */
    var openSubGraphRequest: String? = null
        private set

    fun clearOpenSubGraphRequest() {
        openSubGraphRequest = null
    }

    private var connecting: PortDrag? = null
    private var draggingNode: NodeDrag? = null
    private var draggingFrame: FrameDrag? = null
    private var resizingFrame: ResizeDrag? = null
    private var draggingNote: NoteDrag? = null
    private var boxSelect: BoxSelect? = null
    private var hoverPort: Pair<String, String>? = null

    private val minimap = Minimap(model, camera)

    private var lastWinX = Float.NaN
    private var lastWinY = Float.NaN

    private enum class DragKind { FROM_OUTPUT, FROM_INPUT }

    private class PortDrag(val nodeId: String, val portId: String, val kind: DragKind)
    private class NodeDrag(val startGraphX: Float, val startGraphY: Float) {
        val initialPositions = mutableMapOf<String, Pair<Float, Float>>()
    }

    private class FrameDrag(
        val frameId: String,
        val startGraphX: Float,
        val startGraphY: Float,
        val initialX: Float,
        val initialY: Float,
    ) {
        val initialNodePositions = mutableMapOf<String, Pair<Float, Float>>()
    }

    private class ResizeDrag(
        val frameId: String,
        val startGraphX: Float,
        val startGraphY: Float,
        val initialW: Float,
        val initialH: Float,
    )

    private class NoteDrag(
        val noteId: String,
        val startGraphX: Float,
        val startGraphY: Float,
        val initialX: Float,
        val initialY: Float
    )

    private class BoxSelect(val startX: Float, val startY: Float, var curX: Float, var curY: Float)

    /**
     * 窗口移动（拖标题/重停靠）时同步平移相机，使图内容跟随窗口。
     * 节点/网格用相机 panX/panY 的绝对屏幕坐标绘制，不跟随窗口自身移动（bug）。
     */
    private fun syncCameraToWindow() {
        val winX = ImGui.getWindowPosX()
        val winY = ImGui.getWindowPosY()
        if (lastWinX.isNaN()) {
            lastWinX = winX
            lastWinY = winY
            return
        }
        val dx = winX - lastWinX
        val dy = winY - lastWinY
        if (dx != 0f || dy != 0f) {
            camera.panX += dx
            camera.panY += dy
        }
        lastWinX = winX
        lastWinY = winY
    }

    fun render() {
        syncCameraToWindow()
        val drawList = ImGui.getWindowDrawList()
        val canvasHovered = ImGui.isWindowHovered()
        val mouseX = ImGui.getMousePosX()
        val mouseY = ImGui.getMousePosY()
        val originX = ImGui.getWindowPosX()
        val originY = ImGui.getWindowPosY() + topInset
        val sizeX = ImGui.getWindowSizeX()
        val sizeY = ImGui.getWindowSizeY() - topInset
        canvasRect = floatArrayOf(originX, originY, sizeX, sizeY)

        // 裁剪到标签栏下方的画布内容区，避免网格/节点/连线画到标签栏之上
        ImGui.pushClipRect(originX, originY, originX + sizeX, originY + sizeY, true)
        try {
            handlePanZoom(canvasHovered)
            drawGrid(drawList)

            for (frame in model.frames.values) {
                drawFrame(drawList, frame)
            }

            for (edge in model.edges.values) {
                drawEdge(drawList, edge)
            }

            connecting?.let { c ->
                val from = portScreenPos(model.nodes[c.nodeId], c.portId) ?: return@let
                drawBezier(drawList, from.first, from.second, mouseX, mouseY, edgeColor())
            }

            if (boxSelect != null) {
                val b = boxSelect!!
                drawList.addRectFilled(b.startX, b.startY, b.curX, b.curY, col(0.3f, 0.5f, 1f, 0.15f))
                drawList.addRect(b.startX, b.startY, b.curX, b.curY, col(0.4f, 0.6f, 1f, 0.8f))
            }

            hoverPort = computeHoveredPort(mouseX, mouseY)

            var nodeIndex = 0
            for (node in model.nodes.values) {
                drawNode(drawList, node, nodeIndex)
                nodeIndex++
            }

            for (note in model.notes.values) {
                drawNote(drawList, note)
            }

            minimap.topInset = topInset
            minimap.render()

            handleInteraction(canvasHovered)
        } finally {
            ImGui.popClipRect()
        }
    }

    // ---- 公开动作（快捷键 / 菜单 / 弹窗调用）----

    fun deleteSelection() {
        if (selected.isEmpty()) return
        model.removeNodes(selected.toList())
        selected.clear()
    }

    fun selectAll() {
        selected.clear()
        selected.addAll(model.nodes.keys)
    }

    fun zoomToFit() {
        val allX = model.nodes.values.map { it.x } + model.frames.values.map { it.x }
        val allY = model.nodes.values.map { it.y } + model.frames.values.map { it.y }
        val allMaxX = model.nodes.values.map { it.x } + model.frames.values.map { it.x + it.w }
        val allMaxY = model.nodes.values.map { it.y } + model.frames.values.map { it.y + it.h }
        if (allX.isEmpty()) {
            camera.zoom = 1f
            camera.panX = 0f
            camera.panY = 0f
            return
        }
        frameToBounds(allX.min(), allY.min(), allMaxX.max(), allMaxY.max())
    }

    fun frameAll() {
        zoomToFit()
    }

    fun frameSelection() {
        val ids = selected.ifEmpty { model.nodes.keys }
        if (ids.isEmpty()) return
        val nodes = ids.mapNotNull { model.nodes[it] }
        if (nodes.isEmpty()) return
        frameToBounds(
            nodes.minOf { it.x } - FRAME_PAD,
            nodes.minOf { it.y } - FRAME_PAD,
            nodes.maxOf { it.x } + NODE_WIDTH + FRAME_PAD,
            nodes.maxOf { it.y } + 40f + FRAME_PAD
        )
    }

    fun alignSelected(align: AlignOps.Align) {
        val positions = selected.mapNotNull { id ->
            model.nodes[id]?.let { id to Pair(it.x, it.y) }
        }.toMap()
        AlignOps.applyPositions(model, AlignOps.align(positions, align))
    }

    fun distributeSelected(axis: AlignOps.Distribute) {
        val positions = selected.mapNotNull { id ->
            model.nodes[id]?.let { id to Pair(it.x, it.y) }
        }.toMap()
        AlignOps.applyPositions(model, AlignOps.distribute(positions, axis))
    }

    fun copySelection() {
        snippet = clipboard.copy(model, selected)
    }

    fun duplicateSelection() {
        val newIds = clipboard.duplicate(model, selected)
        if (newIds.isEmpty()) return
        selected.clear()
        selected.addAll(newIds)
    }

    fun pasteAtGraph(x: Float, y: Float) {
        val newIds = clipboard.pasteAt(model, snippet, x, y)
        if (newIds.isEmpty()) return
        selected.clear()
        selected.addAll(newIds)
    }

    fun pasteAtCursor() {
        pasteAtGraph(camera.screenToGraphX(ImGui.getMousePosX()), camera.screenToGraphY(ImGui.getMousePosY()))
    }

    fun addNodeAtGraph(typeId: String, x: Float, y: Float): String {
        val node = model.addNode(typeId, x, y)
        selected.clear()
        selected.add(node.id)
        return node.id
    }

    fun addNodeAtCursor(typeId: String): String =
        addNodeAtGraph(typeId, camera.screenToGraphX(ImGui.getMousePosX()), camera.screenToGraphY(ImGui.getMousePosY()))

    fun setOutput(nodeId: String) {
        model.setOutput(nodeId)
    }

    /** 在选中节点包围盒上创建分组 frame。 */
    fun groupSelection() {
        val nodes = selected.mapNotNull { model.nodes[it] }
        if (nodes.isEmpty()) return
        val minX = nodes.minOf { it.x } - FRAME_PAD
        val minY = nodes.minOf { it.y } - FRAME_PAD
        val maxX = nodes.maxOf { it.x } + NODE_WIDTH + FRAME_PAD
        val maxY = nodes.maxOf { it.y } + 40f + FRAME_PAD
        model.addFrame("Group", minX, minY, maxX - minX, maxY - minY)
    }

    fun addFrameAtCursor() {
        val gx = camera.screenToGraphX(ImGui.getMousePosX())
        val gy = camera.screenToGraphY(ImGui.getMousePosY())
        model.addFrame("Frame", gx - FRAME_DEFAULT_W / 2f, gy - FRAME_DEFAULT_H / 2f, FRAME_DEFAULT_W, FRAME_DEFAULT_H)
    }

    fun addNoteAtCursor() {
        val gx = camera.screenToGraphX(ImGui.getMousePosX())
        val gy = camera.screenToGraphY(ImGui.getMousePosY())
        model.addNote("Note", gx - 90f, gy - 60f)
    }

    fun removeFrame(id: String) {
        model.removeFrame(id)
    }

    fun removeNote(id: String) {
        model.removeNote(id)
    }

    // ---- 平移 / 缩放 ----

    private fun handlePanZoom(canvasHovered: Boolean) {
        if (!canvasHovered) return
        val io = ImGui.getIO()

        if (ImGui.isMouseDragging(ImGuiMouseButton.Right) || ImGui.isMouseDragging(ImGuiMouseButton.Middle)) {
            camera.panX += io.mouseDeltaX
            camera.panY += io.mouseDeltaY
        }

        val wheel = io.mouseWheel
        if (wheel != 0f) {
            val factor = if (wheel > 0) 1.1f else 1f / 1.1f
            val mouseX = ImGui.getMousePosX()
            val mouseY = ImGui.getMousePosY()
            val gx = camera.screenToGraphX(mouseX)
            val gy = camera.screenToGraphY(mouseY)
            camera.zoom = (camera.zoom * factor).coerceIn(0.1f, 4f)
            camera.panX = mouseX - gx * camera.zoom
            camera.panY = mouseY - gy * camera.zoom
        }
    }

    private fun frameToBounds(minX: Float, minY: Float, maxX: Float, maxY: Float) {
        camera.frameToBounds(
            minX, minY, maxX, maxY,
            ImGui.getWindowPosX(), ImGui.getWindowPosY() + topInset,
            ImGui.getWindowSizeX(), ImGui.getWindowSizeY() - topInset
        )
    }

    // ---- 网格 ----

    private fun drawGrid(drawList: ImDrawList) {
        val originX = ImGui.getWindowPosX()
        val originY = ImGui.getWindowPosY() + topInset
        val width = ImGui.getWindowSizeX()
        val height = ImGui.getWindowSizeY() - topInset
        val gridColor = col(0.5f, 0.5f, 0.5f, 0.25f)

        val step = 100f * camera.zoom
        if (step < 4f) return

        val startGx = Math.floor(camera.screenToGraphX(originX).toDouble() / 100.0).toInt() - 1
        val endGx = Math.ceil(camera.screenToGraphX(originX + width).toDouble() / 100.0).toInt() + 1
        val startGy = Math.floor(camera.screenToGraphY(originY).toDouble() / 100.0).toInt() - 1
        val endGy = Math.ceil(camera.screenToGraphY(originY + height).toDouble() / 100.0).toInt() + 1

        for (gx in startGx..endGx) {
            val sx = camera.graphToScreenX(gx * 100f)
            drawList.addLine(sx, originY, sx, originY + height, gridColor)
        }
        for (gy in startGy..endGy) {
            val sy = camera.graphToScreenY(gy * 100f)
            drawList.addLine(originX, sy, originX + width, sy, gridColor)
        }
    }

    // ---- 分组 frame ----

    private fun frameRect(frame: FrameData): FloatArray {
        val x = camera.graphToScreenX(frame.x)
        val y = camera.graphToScreenY(frame.y)
        val w = frame.w * camera.zoom
        val h = frame.h * camera.zoom
        return floatArrayOf(x, y, w, h)
    }

    private fun drawFrame(drawList: ImDrawList, frame: FrameData) {
        val r = frameRect(frame)
        val titleH = FRAME_TITLE_H
        val fill = frame.color
        val border = (frame.color and -256) or 0xFF
        val titleFill = col(0.2f, 0.25f, 0.22f, 0.9f)

        drawList.addRectFilled(r[0], r[1], r[0] + r[2], r[1] + r[3], fill, 6f)
        drawList.addRectFilled(
            r[0],
            r[1],
            r[0] + r[2],
            r[1] + titleH,
            titleFill,
            6f,
            ImDrawFlags.RoundCornersBottomRight
        )
        drawList.addRect(r[0], r[1], r[0] + r[2], r[1] + r[3], border, 6f, ImDrawFlags.RoundCornersAll, 2f)
        if (frame.title.isNotEmpty()) {
            drawList.addText(r[0] + 8f, r[1] + 3f, col(1f, 1f, 1f, 1f), frame.title)
        }
        // 右下角缩放手柄
        drawList.addRectFilled(
            r[0] + r[2] - 10f,
            r[1] + r[3] - 10f,
            r[0] + r[2],
            r[1] + r[3],
            col(0.5f, 0.5f, 0.5f, 0.8f)
        )
    }

    private fun frameResizeHandlePos(frame: FrameData): Pair<Float, Float> {
        val r = frameRect(frame)
        return Pair(r[0] + r[2], r[1] + r[3])
    }

    private fun hitFrame(mouseX: Float, mouseY: Float): Pair<FrameData, Boolean>? {
        for (frame in model.frames.values.toList().asReversed()) {
            val r = frameRect(frame)
            if (mouseX !in r[0]..(r[0] + r[2]) || mouseY !in r[1]..(r[1] + r[3])) continue
            val handle = frameResizeHandlePos(frame)
            val onHandle = dist(mouseX, mouseY, handle.first, handle.second) <= 12f
            return Pair(frame, onHandle)
        }
        return null
    }

    private fun frameContainsNode(frame: FrameData, node: GraphEditorModel.EdNode): Boolean =
        node.x >= frame.x && node.y >= frame.y &&
                node.x + NODE_WIDTH <= frame.x + frame.w && node.y + 30f <= frame.y + frame.h

    // ---- sticky note ----

    private fun noteRect(note: NoteData): FloatArray {
        val x = camera.graphToScreenX(note.x)
        val y = camera.graphToScreenY(note.y)
        val w = note.w * camera.zoom
        val h = note.h * camera.zoom
        return floatArrayOf(x, y, w, h)
    }

    private fun drawNote(drawList: ImDrawList, note: NoteData) {
        val r = noteRect(note)
        val border = (note.color and -256) or 0xFF
        drawList.addRectFilled(r[0], r[1], r[0] + r[2], r[1] + r[3], note.color, 4f)
        drawList.addRect(r[0], r[1], r[0] + r[2], r[1] + r[3], border, 4f, ImDrawFlags.RoundCornersAll, 1.5f)
        if (note.title.isNotEmpty()) {
            drawList.addText(r[0] + 8f, r[1] + 6f, col(0.1f, 0.1f, 0.1f, 1f), note.title)
        }
        val textY = r[1] + (if (note.title.isNotEmpty()) 26f else 8f)
        var lineY = textY
        for (line in wrapText(note.body, r[2] - 16f).take(NOTE_MAX_LINES)) {
            drawList.addText(r[0] + 8f, lineY, col(0.15f, 0.15f, 0.15f, 1f), line)
            lineY += 16f
        }
    }

    private fun wrapText(text: String, maxWidth: Float): List<String> {
        if (text.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        val buf = StringBuilder()
        for (word in text.split(" ")) {
            val candidate = if (buf.isEmpty()) word else "$buf $word"
            val w = ImGui.calcTextSize(candidate).x
            if (buf.isNotEmpty() && w > maxWidth) {
                lines.add(buf.toString())
                buf.setLength(0)
                buf.append(word)
            } else {
                if (buf.isNotEmpty()) buf.append(' ')
                buf.append(word)
            }
        }
        if (buf.isNotEmpty()) lines.add(buf.toString())
        return lines
    }

    private fun hitNote(mouseX: Float, mouseY: Float): NoteData? {
        for (note in model.notes.values.toList().asReversed()) {
            val r = noteRect(note)
            if (mouseX in r[0]..(r[0] + r[2]) && mouseY in r[1]..(r[1] + r[3])) return note
        }
        return null
    }

    // ---- 节点 / 端口 ----

    private fun nodeRect(node: GraphEditorModel.EdNode): FloatArray {
        val x = camera.graphToScreenX(node.x)
        val y = camera.graphToScreenY(node.y)
        val inputs = ports(node, PortDirection.INPUT)
        val outputs = ports(node, PortDirection.OUTPUT)
        val h = TITLE_H + 2 * PAD_Y + maxOf(inputs.size, outputs.size) * PORT_SPACING
        return floatArrayOf(x, y, NODE_WIDTH, h)
    }

    private fun ports(node: GraphEditorModel.EdNode, dir: PortDirection) =
        model.portsFor(node).filter { it.direction() == dir }

    private fun portScreenPos(node: GraphEditorModel.EdNode?, portId: String): Pair<Float, Float>? {
        node ?: return null
        val rect = nodeRect(node)
        val ports = model.portsFor(node)
        val port = ports.firstOrNull { it.id() == portId } ?: return null
        val portIndex = ports.filter { it.direction() == port.direction() }.indexOf(port)
        val py = rect[1] + TITLE_H + PAD_Y + portIndex * PORT_SPACING + PORT_SPACING / 2
        val px = if (port.direction() == PortDirection.INPUT) rect[0] else rect[0] + rect[2]
        return Pair(px, py)
    }

    private fun drawNode(drawList: ImDrawList, node: GraphEditorModel.EdNode, index: Int) {
        val rect = nodeRect(node)
        val x = rect[0]
        val y = rect[1]
        val w = rect[2]
        val h = rect[3]
        val isSelected = node.id in selected

        drawList.addRectFilled(x, y, x + w, y + h, col(0.16f, 0.16f, 0.18f, 0.95f), 4f)
        drawList.addRectFilled(x, y, x + w, y + TITLE_H, col(0.22f, 0.24f, 0.28f, 1f), 4f)
        drawList.addRect(
            x, y, x + w, y + h,
            if (isSelected) col(0.3f, 0.7f, 1f, 1f) else col(0.35f, 0.35f, 0.4f, 1f),
            4f
        )
        drawList.addText(x + 6f, y + 3f, col(1f, 1f, 1f, 1f), displayName(node))

        // VFX 节点显示执行顺序徽标（nodes 列表顺序即执行顺序，1 起）
        if (node.typeId.startsWith("vfx.")) {
            val badgeW = 16f
            val badgeH = 14f
            val bx = x + w - badgeW - 5f
            val by = y + TITLE_H / 2f - badgeH / 2f
            drawList.addRectFilled(bx, by, bx + badgeW, by + badgeH, col(0.08f, 0.42f, 0.34f, 1f), 3f)
            drawList.addText(bx + 4f, by + 2f, col(1f, 1f, 1f, 1f), (index + 1).toString())
        }

        val connectingSource = connectingSource()
        for (port in model.portsFor(node)) {
            val pos = portScreenPos(node, port.id) ?: continue
            drawList.addCircleFilled(pos.first, pos.second, PORT_RADIUS, portColor(port.type()), 12)

            val highlight = portHighlight(node.id, port.id, port.direction(), port.type(), connectingSource)
            if (highlight != null) {
                drawList.addCircle(pos.first, pos.second, PORT_RADIUS + 3f, highlight, 12, 2f)
            } else if (hoverPort == Pair(node.id, port.id)) {
                drawList.addCircle(pos.first, pos.second, PORT_RADIUS + 3f, col(1f, 1f, 1f, 0.85f), 12, 1.5f)
            }
        }
    }

    private fun portHighlight(
        nodeId: String,
        portId: String,
        direction: PortDirection,
        type: ValueType,
        source: ConnectingSource?,
    ): Int? {
        if (source == null) return null
        if (source.nodeId == nodeId && source.portId == portId) return col(1f, 1f, 1f, 0.9f)
        val targetDirection = when (source.direction) {
            PortDirection.OUTPUT -> PortDirection.INPUT
            PortDirection.INPUT -> PortDirection.OUTPUT
        }
        if (direction != targetDirection) return null
        val (outType, inType) = when (source.direction) {
            PortDirection.OUTPUT -> Pair(source.type, type)
            PortDirection.INPUT -> Pair(type, source.type)
        }
        val compat = TypeConversions.INSTANCE.canConvert(outType, inType)
        return if (compat) col(0.3f, 0.9f, 0.4f, 1f) else col(0.9f, 0.3f, 0.3f, 1f)
    }

    private fun connectingSource(): ConnectingSource? {
        val c = connecting ?: return null
        val node = model.nodes[c.nodeId] ?: return null
        val spec = model.portSpec(node, c.portId) ?: return null
        return ConnectingSource(c.nodeId, c.portId, spec.direction(), spec.type())
    }

    private class ConnectingSource(
        val nodeId: String,
        val portId: String,
        val direction: PortDirection,
        val type: ValueType,
    )

    private fun displayName(node: GraphEditorModel.EdNode): String {
        val type = model.nodeType(node.typeId)
        return type?.displayName() ?: node.typeId
    }

    // ---- 边 ----

    private fun drawEdge(drawList: ImDrawList, edge: GraphEditorModel.EdEdge) {
        val from = portScreenPos(model.nodes[edge.fromNode], edge.fromPort) ?: return
        val to = portScreenPos(model.nodes[edge.toNode], edge.toPort) ?: return
        drawBezier(drawList, from.first, from.second, to.first, to.second, edgeColor())
    }

    private fun drawBezier(drawList: ImDrawList, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        val dx = (x2 - x1) * 0.5f
        drawList.addBezierCubic(
            x1, y1, x1 + dx, y1, x2 - dx, y2, x2, y2, color, 2f, 20
        )
    }

    // ---- 交互 ----

    private fun handleInteraction(canvasHovered: Boolean) {
        if (!canvasHovered) {
            connecting = null
            draggingNode = null
            draggingFrame = null
            resizingFrame = null
            draggingNote = null
            boxSelect = null
            return
        }

        val mouseX = ImGui.getMousePosX()
        val mouseY = ImGui.getMousePosY()

        // 左键按下：命中测试
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            val outPort = hitOutputPort(mouseX, mouseY)
            if (outPort != null) {
                connecting = PortDrag(outPort.first, outPort.second, DragKind.FROM_OUTPUT)
                return
            }
            val inPort = hitInputPort(mouseX, mouseY)
            if (inPort != null) {
                connecting = PortDrag(inPort.first, inPort.second, DragKind.FROM_INPUT)
                return
            }
            val note = hitNote(mouseX, mouseY)
            if (note != null) {
                val gx = camera.screenToGraphX(mouseX)
                val gy = camera.screenToGraphY(mouseY)
                draggingNote = NoteDrag(note.id, gx, gy, note.x, note.y)
                return
            }
            val node = hitNode(mouseX, mouseY)
            if (node != null) {
                if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
                    openSubGraphRequest = node.id
                }
                val ctrl = ImGui.getIO().keyMods.let { it and 1 != 0 }
                if (node.id !in selected && !ctrl) {
                    selected.clear()
                }
                selected.add(node.id)
                val drag = NodeDrag(camera.screenToGraphX(mouseX), camera.screenToGraphY(mouseY))
                for (id in selected) {
                    val n = model.nodes[id] ?: continue
                    drag.initialPositions[id] = Pair(n.x, n.y)
                }
                draggingNode = drag
                return
            }
            val frameHit = hitFrame(mouseX, mouseY)
            if (frameHit != null) {
                val (frame, onHandle) = frameHit
                val gx = camera.screenToGraphX(mouseX)
                val gy = camera.screenToGraphY(mouseY)
                if (onHandle) {
                    resizingFrame = ResizeDrag(frame.id, gx, gy, frame.w, frame.h)
                } else {
                    val drag = FrameDrag(frame.id, gx, gy, frame.x, frame.y)
                    for (n in model.nodes.values) {
                        if (frameContainsNode(frame, n)) {
                            drag.initialNodePositions[n.id] = Pair(n.x, n.y)
                        }
                    }
                    draggingFrame = drag
                }
                return
            }
            selected.clear()
            boxSelect = BoxSelect(mouseX, mouseY, mouseX, mouseY)
        }

        // 拖拽成组移动选中节点
        draggingNode?.let { drag ->
            if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
                val gx = camera.screenToGraphX(mouseX)
                val gy = camera.screenToGraphY(mouseY)
                val dx = gx - drag.startGraphX
                val dy = gy - drag.startGraphY
                for ((id, pos) in drag.initialPositions) {
                    val nx = if (snapEnabled) camera.snap(pos.first + dx) else pos.first + dx
                    val ny = if (snapEnabled) camera.snap(pos.second + dy) else pos.second + dy
                    model.moveNode(id, nx, ny)
                }
            }
        }

        // 拖拽移动分组 frame（含框内节点）
        draggingFrame?.let { drag ->
            if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
                val gx = camera.screenToGraphX(mouseX)
                val gy = camera.screenToGraphY(mouseY)
                val newX =
                    if (snapEnabled) camera.snap(drag.initialX + gx - drag.startGraphX) else drag.initialX + gx - drag.startGraphX
                val newY =
                    if (snapEnabled) camera.snap(drag.initialY + gy - drag.startGraphY) else drag.initialY + gy - drag.startGraphY
                model.moveFrame(drag.frameId, newX, newY)
                val dx = newX - drag.initialX
                val dy = newY - drag.initialY
                for ((id, pos) in drag.initialNodePositions) {
                    model.moveNode(id, pos.first + dx, pos.second + dy)
                }
            }
        }

        // 拖拽缩放分组 frame
        resizingFrame?.let { drag ->
            if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
                val gx = camera.screenToGraphX(mouseX)
                val gy = camera.screenToGraphY(mouseY)
                val newW = drag.initialW + gx - drag.startGraphX
                val newH = drag.initialH + gy - drag.startGraphY
                model.resizeFrame(drag.frameId, maxOf(newW, MIN_FRAME_SIZE), maxOf(newH, MIN_FRAME_SIZE))
            }
        }

        // 拖拽移动 sticky note
        draggingNote?.let { drag ->
            if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
                val gx = camera.screenToGraphX(mouseX)
                val gy = camera.screenToGraphY(mouseY)
                val nx =
                    if (snapEnabled) camera.snap(drag.initialX + gx - drag.startGraphX) else drag.initialX + gx - drag.startGraphX
                val ny =
                    if (snapEnabled) camera.snap(drag.initialY + gy - drag.startGraphY) else drag.initialY + gy - drag.startGraphY
                model.moveNote(drag.noteId, nx, ny)
            }
        }

        // 框选
        boxSelect?.let { box ->
            box.curX = mouseX
            box.curY = mouseY
        }

        // 释放左键
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            if (connecting != null) {
                finishConnecting(mouseX, mouseY)
            }
            draggingNode = null
            draggingFrame = null
            resizingFrame = null
            draggingNote = null
            boxSelect?.let { box ->
                applyBoxSelect(box)
            }
            boxSelect = null
        }

        // 右键：打开上下文菜单
        if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            connecting = null
            draggingNode = null
            draggingFrame = null
            resizingFrame = null
            draggingNote = null
            boxSelect = null
            val edge = hitEdge(mouseX, mouseY)
            if (edge != null) {
                contextRequest = ContextRequest(ContextRequest.Kind.EDGE, edge = edge)
                ImGui.openPopup(POPUP_EDGE)
                return
            }
            val note = hitNote(mouseX, mouseY)
            if (note != null) {
                contextRequest = ContextRequest(ContextRequest.Kind.NOTE, noteId = note.id)
                ImGui.openPopup(POPUP_NOTE)
                return
            }
            val node = hitNode(mouseX, mouseY)
            if (node != null) {
                if (node.id !in selected) {
                    selected.clear()
                    selected.add(node.id)
                }
                contextRequest = ContextRequest(ContextRequest.Kind.NODE, nodeId = node.id)
                ImGui.openPopup(POPUP_NODE)
                return
            }
            val frameHit = hitFrame(mouseX, mouseY)
            if (frameHit != null) {
                contextRequest = ContextRequest(ContextRequest.Kind.FRAME, frameId = frameHit.first.id)
                ImGui.openPopup(POPUP_FRAME)
                return
            }
            contextRequest = ContextRequest(ContextRequest.Kind.CANVAS)
            ImGui.openPopup(POPUP_CANVAS)
        }
    }

    private fun finishConnecting(mouseX: Float, mouseY: Float) {
        val c = connecting ?: return
        when (c.kind) {
            DragKind.FROM_OUTPUT -> {
                val target = hitInputPort(mouseX, mouseY)
                if (target != null) {
                    model.connect(c.nodeId, c.portId, target.first, target.second)
                }
            }

            DragKind.FROM_INPUT -> {
                val target = hitOutputPort(mouseX, mouseY)
                if (target != null) {
                    model.reconnect(target.first, target.second, c.nodeId, c.portId)
                }
            }
        }
        connecting = null
    }

    private fun applyBoxSelect(box: BoxSelect) {
        val minX = minOf(box.startX, box.curX)
        val maxX = maxOf(box.startX, box.curX)
        val minY = minOf(box.startY, box.curY)
        val maxY = maxOf(box.startY, box.curY)
        for (node in model.nodes.values) {
            val r = nodeRect(node)
            if (r[0] < maxX && r[0] + r[2] > minX && r[1] < maxY && r[1] + r[3] > minY) {
                selected.add(node.id)
            }
        }
    }

    private fun hitNode(mouseX: Float, mouseY: Float): GraphEditorModel.EdNode? {
        for (node in model.nodes.values.toList().asReversed()) {
            val r = nodeRect(node)
            if (mouseX in r[0]..(r[0] + r[2]) && mouseY in r[1]..(r[1] + r[3])) return node
        }
        return null
    }

    private fun computeHoveredPort(mouseX: Float, mouseY: Float): Pair<String, String>? {
        if (!ImGui.isWindowHovered()) return null
        hitOutputPort(mouseX, mouseY)?.let { return it }
        hitInputPort(mouseX, mouseY)?.let { return it }
        return null
    }

    private fun hitOutputPort(mouseX: Float, mouseY: Float): Pair<String, String>? {
        for (node in model.nodes.values) {
            for (port in model.portsFor(node)) {
                if (port.direction() != PortDirection.OUTPUT) continue
                val pos = portScreenPos(node, port.id) ?: continue
                if (dist(mouseX, mouseY, pos.first, pos.second) <= PORT_RADIUS + 3f) {
                    return Pair(node.id, port.id)
                }
            }
        }
        return null
    }

    private fun hitInputPort(mouseX: Float, mouseY: Float): Pair<String, String>? {
        for (node in model.nodes.values) {
            for (port in model.portsFor(node)) {
                if (port.direction() != PortDirection.INPUT) continue
                val pos = portScreenPos(node, port.id) ?: continue
                if (dist(mouseX, mouseY, pos.first, pos.second) <= PORT_RADIUS + 3f) {
                    return Pair(node.id, port.id)
                }
            }
        }
        return null
    }

    private fun hitEdge(mouseX: Float, mouseY: Float): GraphEditorModel.EdEdge? {
        for (edge in model.edges.values) {
            val from = portScreenPos(model.nodes[edge.fromNode], edge.fromPort) ?: continue
            val to = portScreenPos(model.nodes[edge.toNode], edge.toPort) ?: continue
            if (distToBezier(mouseX, mouseY, from.first, from.second, to.first, to.second) < 6f) {
                return edge
            }
        }
        return null
    }

    private fun distToBezier(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = (x2 - x1) * 0.5f
        var minDist = Float.MAX_VALUE
        for (i in 0..20) {
            val t = i / 20f
            val bx = bezier(x1, x1 + dx, x2 - dx, x2, t)
            val by = bezier(y1, y1, y2, y2, t)
            minDist = minOf(minDist, dist(px, py, bx, by))
        }
        return minDist
    }

    private fun bezier(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val u = 1f - t
        return u * u * u * p0 + 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t * p3
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    // ---- 颜色 ----

    private fun col(r: Float, g: Float, b: Float, a: Float): Int = ImGui.colorConvertFloat4ToU32(r, g, b, a)

    private fun edgeColor(): Int = col(0.7f, 0.7f, 0.75f, 0.9f)

    private fun portColor(type: ValueType): Int = when (type) {
        ValueType.FLOAT, ValueType.TIME -> col(0.55f, 0.85f, 0.55f, 1f)
        ValueType.VEC2, ValueType.VEC3, ValueType.VEC4, ValueType.COLOR -> col(0.85f, 0.75f, 0.45f, 1f)
        ValueType.SAMPLER -> col(0.6f, 0.65f, 0.9f, 1f)
        else -> col(0.75f, 0.75f, 0.75f, 1f)
    }

    /** 右键上下文菜单请求；宿主据此渲染弹窗。 */
    class ContextRequest(
        val kind: Kind,
        val nodeId: String? = null,
        val edge: GraphEditorModel.EdEdge? = null,
        val frameId: String? = null,
        val noteId: String? = null,
    ) {
        enum class Kind { CANVAS, NODE, EDGE, FRAME, NOTE }
    }

    companion object {
        const val POPUP_CANVAS = "##graph_canvas_ctx"
        const val POPUP_NODE = "##graph_node_ctx"
        const val POPUP_EDGE = "##graph_edge_ctx"
        const val POPUP_FRAME = "##graph_frame_ctx"
        const val POPUP_NOTE = "##graph_note_ctx"
        const val NODE_WIDTH = 160f
        const val TITLE_H = 20f
        const val PORT_SPACING = 22f
        const val PORT_RADIUS = 5f
        const val PAD_Y = 8f
        const val FRAME_PAD = 40f
        const val FRAME_TITLE_H = 24f
        const val FRAME_DEFAULT_W = 260f
        const val FRAME_DEFAULT_H = 180f
        const val MIN_FRAME_SIZE = 40f
        const val NOTE_MAX_LINES = 4
    }
}
