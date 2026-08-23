package org.academy.desktop.grapheditor.container

import imgui.ImGui
import imgui.ImDrawList
import imgui.flag.ImDrawFlags
import imgui.flag.ImGuiMouseButton
import org.academy.api.client.render.graph.model.PortDirection
import org.academy.api.client.render.vfxgraph.model.VfxContextType
import org.academy.desktop.grapheditor.canvas.Camera2D

/**
 * VFX 容器画布（M26）：渲染 [VfxContainerModel] 的 context 容器框（内部 block 垂直排列）、
 * 自由算子、flow/data 贝塞尔连线，支持平移/缩放、拖拽移动、连线、框选、右键菜单请求。
 *
 * <p>与扁平 [NodeCanvas] 平行（SHADER 模式用扁平，VFX 模式用容器）。执行顺序由 context 内
 * blocks 列表序决定（画布上不显示顺序徽标——由容器结构可视化）。</p>
 */
class VfxContainerCanvas(
    private val containerRef: VfxContainerModelRef,
    private val camera: Camera2D,
) {
    private val model: VfxContainerModel get() = containerRef.model

    val selected: MutableSet<String> = mutableSetOf()
    val selectedContext: MutableSet<String> = mutableSetOf()

    var canvasRect: FloatArray = floatArrayOf(0f, 0f, 0f, 0f)
        private set

    var topInset = 0f

    var contextRequest: ContextRequest? = null
        private set

    /** CANVAS 右键菜单委托（Add Context/Block/Operator 等由宿主渲染，需 registry）。 */
    var canvasPalette: (() -> Unit)? = null

    fun clearContextRequest() {
        contextRequest = null
    }

    private var connecting: Drag? = null
    private var dragging: Drag? = null
    private var boxSelect: BoxSelect? = null
    private var hoverPort: Pair<String, String>? = null

    private enum class DragKind { FLOW, BLOCK_FLOW, DATA_FROM, DATA_TO }

    private class Drag(val contextId: String?, val nodeId: String, val portId: String, val kind: DragKind)
    private class BoxSelect(val startX: Float, val startY: Float, var curX: Float, var curY: Float)

    fun render() {
        val drawList = ImGui.getWindowDrawList()
        val canvasHovered = ImGui.isWindowHovered()
        val mouseX = ImGui.getMousePosX()
        val mouseY = ImGui.getMousePosY()
        val originX = ImGui.getWindowPosX()
        val originY = ImGui.getWindowPosY() + topInset
        val sizeX = ImGui.getWindowSizeX()
        val sizeY = ImGui.getWindowSizeY() - topInset
        canvasRect = floatArrayOf(originX, originY, sizeX, sizeY)

        ImGui.pushClipRect(originX, originY, originX + sizeX, originY + sizeY, true)
        try {
            handlePanZoom(canvasHovered)
            drawGrid(drawList)

            for (ctx in model.contexts.values) {
                drawContext(drawList, ctx)
            }

            for (edge in model.flowEdges) {
                drawFlowEdge(drawList, edge)
            }

            for (edge in model.blockFlows) {
                drawBlockFlowEdge(drawList, edge)
            }

            for (edge in model.dataEdges) {
                drawDataEdge(drawList, edge)
            }

            connecting?.let { c ->
                val from = connectingSourcePos(c) ?: return@let
                drawBezier(drawList, from.first, from.second, mouseX, mouseY, edgeColor())
            }

            if (boxSelect != null) {
                val b = boxSelect!!
                drawList.addRectFilled(b.startX, b.startY, b.curX, b.curY, col(0.3f, 0.5f, 1f, 0.15f))
                drawList.addRect(b.startX, b.startY, b.curX, b.curY, col(0.4f, 0.6f, 1f, 0.8f))
            }

            hoverPort = computeHoveredPort(mouseX, mouseY)
            renderPortTooltip()

            for (op in model.operators.values) {
                drawOperator(drawList, op)
            }

            handleInteraction(canvasHovered, mouseX, mouseY)
        } finally {
            ImGui.popClipRect()
        }
        renderContextMenus()
    }

    // ---- 绘制 ----

    private fun drawContext(drawList: ImDrawList, ctx: VfxContainerModel.EdContext) {
        val x = camera.graphToScreenX(ctx.x)
        val y = camera.graphToScreenY(ctx.y)
        val headerH = 24f
        val blockH = 26f
        val pad = 6f
        val w = 200f * camera.zoom
        val bodyH = pad + ctx.blocks.size * blockH + pad
        val isSelected = ctx.id in selectedContext

        val header = when (ctx.type) {
            VfxContextType.SPAWN -> col(0.15f, 0.45f, 0.35f, 1f)
            VfxContextType.INITIALIZE -> col(0.25f, 0.4f, 0.55f, 1f)
            VfxContextType.UPDATE -> col(0.55f, 0.4f, 0.2f, 1f)
            VfxContextType.OUTPUT -> col(0.5f, 0.2f, 0.4f, 1f)
        }
        drawList.addRectFilled(x, y, x + w, y + headerH + bodyH, col(0.13f, 0.13f, 0.16f, 0.95f), 6f)
        drawList.addRectFilled(x, y, x + w, y + headerH, header, 6f, ImDrawFlags.RoundCornersTop)
        drawList.addRect(
            x, y, x + w, y + headerH + bodyH,
            if (isSelected) col(0.3f, 0.7f, 1f, 1f) else col(0.4f, 0.4f, 0.45f, 1f),
            6f,
        )
        drawList.addText(x + 6f, y + 4f, col(1f, 1f, 1f, 1f), ctx.type.name)

        var blockY = y + headerH + pad
        for (block in ctx.blocks.values) {
            val selected = block.id in selected
            drawList.addRectFilled(x + 4f, blockY, x + w - 4f, blockY + blockH, col(0.2f, 0.2f, 0.24f, 0.9f), 3f)
            if (selected) {
                drawList.addRect(x + 4f, blockY, x + w - 4f, blockY + blockH, col(0.3f, 0.7f, 1f, 1f), 3f)
            }
            drawList.addText(x + 10f, blockY + 6f, col(0.85f, 0.85f, 0.9f, 1f), displayName(block.typeId))
            // 数据输入端口（左侧黄点）：块上部；批次输入端口（左侧绿点）：块下部——错开避免重叠
            val isInit = blockCategory(block.typeId) == "init"
            val hasDataPort = model.firstInputPort(block.id) != null
            if (hasDataPort) {
                val py = if (isInit) blockY + blockH / 2f - 5f else blockY + blockH / 2f
                drawPorts(drawList, block.id, x, py, isInput = true)
            }
            // 批次 flow 端口（M28b）：spawn 块右缘输出、init 块左缘输入（绿，与 context flow 同色系）；
            // arc 发射块（vfx.block.arc_*）虽为 spawn 类但不产粒子批次，不画批次端口（M22）
            when (blockCategory(block.typeId)) {
                "spawn" -> if (!isArc(block.typeId)) {
                    drawBatchPort(drawList, block.id, x + w, blockY + blockH / 2f, "@batchOut")
                }
                "init" -> drawBatchPort(drawList, block.id, x, blockY + blockH / 2f + 7f, "@batchIn")
                else -> Unit
            }
            blockY += blockH
        }
        // context 右缘 flow 输出端口
        drawFlowPort(drawList, x + w, y + headerH / 2f, ctx.id, isOutput = true)
        drawFlowPort(drawList, x, y + headerH / 2f, ctx.id, isOutput = false)
    }

    private fun drawFlowPort(drawList: ImDrawList, px: Float, py: Float, contextId: String, isOutput: Boolean) {
        val portId = if (isOutput) "@flow" else "@flowIn"
        val highlight = hoverPort == Pair(contextId, portId)
        drawList.addCircleFilled(px, py, 5f, col(0.5f, 0.9f, 0.7f, 1f), 12)
        if (highlight) drawList.addCircle(px, py, 8f, col(1f, 1f, 1f, 0.9f), 12, 2f)
    }

    private fun drawPorts(drawList: ImDrawList, nodeId: String, px: Float, py: Float, isInput: Boolean) {
        val color = if (isInput) col(0.6f, 0.7f, 0.9f, 1f) else col(0.85f, 0.75f, 0.45f, 1f)
        val portId = if (isInput) "@in" else "out"
        val highlight = hoverPort == Pair(nodeId, portId)
        drawList.addCircleFilled(px, py, 5f, color, 12)
        if (highlight) drawList.addCircle(px, py, 8f, col(1f, 1f, 1f, 0.9f), 12, 2f)
    }

    /** 批次 flow 端口（M28b）：spawn 输出 / init 输入，绿（与 context flow 同色系）。 */
    private fun drawBatchPort(drawList: ImDrawList, nodeId: String, px: Float, py: Float, portId: String) {
        val highlight = hoverPort == Pair(nodeId, portId)
        drawList.addCircleFilled(px, py, 4f, col(0.5f, 0.9f, 0.7f, 1f), 10)
        if (highlight) drawList.addCircle(px, py, 7f, col(1f, 1f, 1f, 0.9f), 10, 2f)
    }

    private fun drawOperator(drawList: ImDrawList, op: VfxContainerModel.EdOperator) {
        val x = camera.graphToScreenX(op.x)
        val y = camera.graphToScreenY(op.y)
        val w = 160f * camera.zoom
        val h = 40f * camera.zoom
        val selected = op.id in selected
        drawList.addRectFilled(x, y, x + w, y + h, col(0.18f, 0.18f, 0.22f, 0.95f), 4f)
        drawList.addRect(
            x, y, x + w, y + h,
            if (selected) col(0.3f, 0.7f, 1f, 1f) else col(0.35f, 0.35f, 0.4f, 1f),
            4f,
        )
        drawList.addText(x + 6f, y + 4f, col(1f, 1f, 1f, 1f), displayName(op.typeId))
        // 输出端口（右缘）
        drawPorts(drawList, op.id, x + w, y + h / 2f, isInput = false)
    }

    private fun drawDataEdge(drawList: ImDrawList, edge: VfxContainerModel.EdDataEdge) {
        val from = dataPortPos(edge.fromNode, edge.fromPort, isInput = false) ?: return
        val to = dataPortPos(edge.toNode, edge.toPort, isInput = true) ?: return
        drawBezier(drawList, from.first, from.second, to.first, to.second, edgeColor())
    }

    /** context 间 flow 连线：上游输出端口 → 下游输入端口（批次语义，M26）。
     *  选中任一端 context 时高亮，让「哪个 spawn 喂哪个 init」一目了然。 */
    private fun drawFlowEdge(drawList: ImDrawList, edge: VfxContainerModel.EdFlowEdge) {
        val from = flowPortPos(edge.fromContext, isOutput = true) ?: return
        val to = flowPortPos(edge.toContext, isOutput = false) ?: return
        val highlighted = edge.fromContext in selectedContext || edge.toContext in selectedContext
        drawBezier(drawList, from.first, from.second, to.first, to.second,
            if (highlighted) flowHighlightColor() else flowColor(), if (highlighted) 4f else 2f)
    }

    /** 块级批次 flow 连线（M28b）：spawn 块批次输出 → init 块批次输入。 */
    private fun drawBlockFlowEdge(drawList: ImDrawList, edge: VfxContainerModel.EdBlockFlowEdge) {
        val from = blockBatchPortPos(edge.fromBlock, "@batchOut") ?: return
        val to = blockBatchPortPos(edge.toBlock, "@batchIn") ?: return
        val highlighted = edge.fromBlock in selected || edge.toBlock in selected
        drawBezier(drawList, from.first, from.second, to.first, to.second,
            if (highlighted) flowHighlightColor() else flowColor(), if (highlighted) 4f else 2f)
    }

    private fun drawBezier(drawList: ImDrawList, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, thickness: Float = 2f) {
        val dx = (x2 - x1) * 0.5f
        drawList.addBezierCubic(x1, y1, x1 + dx, y1, x2 - dx, y2, x2, y2, color, thickness, 20)
    }

    // ---- 坐标 ----

    private fun contextSize(ctx: VfxContainerModel.EdContext): Float {
        return 24f + 6f + ctx.blocks.size * 26f + 6f
    }

    private fun flowPortPos(contextId: String, isOutput: Boolean): Pair<Float, Float>? {
        val ctx = model.contexts[contextId] ?: return null
        val x = camera.graphToScreenX(ctx.x)
        val y = camera.graphToScreenY(ctx.y)
        val w = 200f * camera.zoom
        return if (isOutput) Pair(x + w, y + 12f) else Pair(x, y + 12f)
    }

    private fun blockPortPos(nodeId: String): Pair<Float, Float>? {
        val contextId = model.contextOf(nodeId) ?: return null
        val ctx = model.contexts[contextId] ?: return null
        val block = ctx.blocks[nodeId] ?: return null
        val x = camera.graphToScreenX(ctx.x)
        val y = camera.graphToScreenY(ctx.y)
        val headerH = 24f
        val blockH = 26f
        val pad = 6f
        val index = ctx.blocks.keys.indexOf(nodeId)
        if (index < 0) return null
        // init 块数据输入端口上移（与批次输入错开）
        val offset = if (blockCategory(block.typeId) == "init") -5f else 0f
        return Pair(x, y + headerH + pad + index * blockH + blockH / 2f + offset)
    }

    /** 块批次 flow 端口位置（与绘制一致）：spawn 右缘、init 左缘偏移 7px）。 */
    private fun blockBatchPortPos(nodeId: String, portId: String): Pair<Float, Float>? {
        val contextId = model.contextOf(nodeId) ?: return null
        val ctx = model.contexts[contextId] ?: return null
        val block = ctx.blocks[nodeId] ?: return null
        val x = camera.graphToScreenX(ctx.x)
        val y = camera.graphToScreenY(ctx.y)
        val headerH = 24f
        val blockH = 26f
        val pad = 6f
        val index = ctx.blocks.keys.indexOf(nodeId)
        if (index < 0) return null
        val py = y + headerH + pad + index * blockH + blockH / 2f + (if (portId == "@batchIn") 7f else 0f)
        return if (portId == "@batchOut") Pair(x + 200f * camera.zoom, py) else Pair(x, py)
    }

    private fun operatorPortPos(nodeId: String, isInput: Boolean): Pair<Float, Float>? {
        val op = model.operators[nodeId] ?: return null
        val x = camera.graphToScreenX(op.x)
        val y = camera.graphToScreenY(op.y)
        val w = 160f * camera.zoom
        val h = 40f * camera.zoom
        return if (isInput) Pair(x, y + h / 2f) else Pair(x + w, y + h / 2f)
    }

    private fun dataPortPos(nodeId: String, portId: String, isInput: Boolean): Pair<Float, Float>? {
        if (portId == "@flow") return flowPortPos(nodeId, isOutput = true)
        if (portId == "@flowIn") return flowPortPos(nodeId, isOutput = false)
        val op = model.operators[nodeId]
        return if (op != null) {
            operatorPortPos(nodeId, isInput)
        } else {
            blockPortPos(nodeId)
        }
    }

    private fun connectingSourcePos(c: Drag): Pair<Float, Float>? {
        return when (c.kind) {
            DragKind.FLOW -> flowPortPos(c.contextId ?: return null, isOutput = true)
            DragKind.BLOCK_FLOW -> blockBatchPortPos(c.nodeId, "@batchOut")
            DragKind.DATA_FROM -> dataPortPos(c.nodeId, c.portId, isInput = false)
            DragKind.DATA_TO -> dataPortPos(c.nodeId, c.portId, isInput = true)
        }
    }

    // ---- 交互 ----

    private fun handlePanZoom(canvasHovered: Boolean) {
        if (!canvasHovered) return
        val io = ImGui.getIO()
        if (ImGui.isMouseDragging(ImGuiMouseButton.Right) || ImGui.isMouseDragging(ImGuiMouseButton.Middle)) {
            camera.panX += io.getMouseDeltaX()
            camera.panY += io.getMouseDeltaY()
        }
        val wheel = io.getMouseWheel()
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

    /** 取景所有 context/算子包围盒。 */
    fun frameAll() {
        val xs = model.contexts.values.map { it.x } + model.operators.values.map { it.x }
        val ys = model.contexts.values.map { it.y } + model.operators.values.map { it.y }
        if (xs.isEmpty()) {
            camera.zoom = 1f
            camera.panX = 0f
            camera.panY = 0f
            return
        }
        val minX = xs.min() - 20f
        val minY = ys.min() - 20f
        val maxX = (model.contexts.values.map { it.x + 200f } + model.operators.values.map { it.x + 160f }).max() + 20f
        val maxY = (model.contexts.values.map { it.y + 120f } + model.operators.values.map { it.y + 40f }).max() + 20f
        camera.frameToBounds(minX, minY, maxX, maxY,
            ImGui.getWindowPosX(), ImGui.getWindowPosY() + topInset,
            ImGui.getWindowSizeX(), ImGui.getWindowSizeY() - topInset)
    }

    private fun handleInteraction(canvasHovered: Boolean, mouseX: Float, mouseY: Float) {
        if (!canvasHovered) {
            connecting = null
            dragging = null
            boxSelect = null
            return
        }

        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            val batchOut = hitBlockBatchPort(mouseX, mouseY, "@batchOut")
            if (batchOut != null) {
                connecting = Drag(null, batchOut, "@batchOut", DragKind.BLOCK_FLOW)
                return
            }
            val flowOut = hitFlowPort(mouseX, mouseY, isOutput = true)
            if (flowOut != null) {
                connecting = Drag(flowOut, "", "@flow", DragKind.FLOW)
                return
            }
            val opOut = hitOperatorOutputPort(mouseX, mouseY)
            if (opOut != null) {
                connecting = Drag(null, opOut, "out", DragKind.DATA_FROM)
                return
            }
            // 块输入端口仅作数据线落点（接收算子输出），不启动连线——从输入端口拖出会让用户误以为
            // 块可连块（数据流只允许 算子输出 → 块输入；spawn/init 顺序由 context flow 边表达）
            val blockIn = hitBlockPort(mouseX, mouseY)
            if (blockIn != null) {
                return
            }
            val opNode = hitOperatorNode(mouseX, mouseY)
            if (opNode != null) {
                if (opNode !in selected) {
                    selected.clear()
                    selectedContext.clear()
                    selected.add(opNode)
                }
                dragging = Drag(null, opNode, "", DragKind.DATA_FROM)
                return
            }
            // 点击块：选中该块（优先于 context，因为块在 context 框内）
            val block = hitBlock(mouseX, mouseY)
            if (block != null) {
                selected.clear()
                selectedContext.clear()
                selected.add(block)
                return
            }
            val ctx = hitContext(mouseX, mouseY)
            if (ctx != null) {
                if (ctx !in selectedContext) {
                    selected.clear()
                    selectedContext.clear()
                    selectedContext.add(ctx)
                }
                dragging = Drag(ctx, "", "", DragKind.FLOW)
                return
            }
            selected.clear()
            selectedContext.clear()
            boxSelect = BoxSelect(mouseX, mouseY, mouseX, mouseY)
        }

        dragging?.let { d ->
            if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
                val gx = camera.screenToGraphX(mouseX)
                val gy = camera.screenToGraphY(mouseY)
                if (d.nodeId.isNotEmpty() && model.operators.containsKey(d.nodeId)) {
                    model.moveOperator(d.nodeId, camera.snap(gx), camera.snap(gy))
                } else if (d.contextId != null) {
                    model.moveContext(d.contextId, camera.snap(gx), camera.snap(gy))
                }
            }
        }

        boxSelect?.let { box -> box.curX = mouseX; box.curY = mouseY }

        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            if (connecting != null) {
                finishConnecting(mouseX, mouseY)
            }
            dragging = null
            boxSelect?.let { b -> applyBoxSelect(b) }
            boxSelect = null
        }

        if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            connecting = null
            dragging = null
            boxSelect = null
            // 边命中优先：flow / blockFlow / dataEdge 均可右键断线
            val edgeHit = hitAnyEdge(mouseX, mouseY)
            if (edgeHit != null) {
                contextRequest = ContextRequest(ContextRequest.Kind.EDGE, edge = edgeHit)
                ImGui.openPopup(POPUP_EDGE)
                return
            }
            val opNode = hitOperatorNode(mouseX, mouseY)
            if (opNode != null) {
                if (opNode !in selected) {
                    selected.clear()
                    selectedContext.clear()
                    selected.add(opNode)
                }
                contextRequest = ContextRequest(ContextRequest.Kind.NODE, nodeId = opNode)
                ImGui.openPopup(POPUP_NODE)
                return
            }
            val block = hitBlock(mouseX, mouseY)
            if (block != null) {
                selected.clear()
                selectedContext.clear()
                selected.add(block)
                contextRequest = ContextRequest(ContextRequest.Kind.BLOCK, nodeId = block)
                ImGui.openPopup(POPUP_BLOCK)
                return
            }
            val ctx = hitContext(mouseX, mouseY)
            if (ctx != null) {
                contextRequest = ContextRequest(ContextRequest.Kind.CONTEXT, contextId = ctx)
                ImGui.openPopup(POPUP_CONTEXT)
                return
            }
            contextRequest = ContextRequest(ContextRequest.Kind.CANVAS)
            ImGui.openPopup(POPUP_CANVAS)
        }
    }

    /** 渲染右键弹窗（画布内直接渲染，保证 id/时机一致）。
     *  请求在弹窗打开期间存活（beginPopup 每帧重绘菜单）：仅当弹窗已关闭（beginPopup false 且
     *  isPopupOpen false）才清除请求，否则菜单只存活一帧、点击无响应。 */
    private fun renderContextMenus() {
        val req = contextRequest
        if (req == null) return
        when (req.kind) {
            ContextRequest.Kind.EDGE -> {
                if (ImGui.beginPopup(POPUP_EDGE)) {
                    val edge = req.edge ?: return
                    if (ImGui.menuItem("Disconnect")) {
                        if (edge.flowFrom != null && edge.flowTo != null) {
                            model.disconnectFlow(edge.flowFrom, edge.flowTo)
                        } else if (edge.blockFrom != null && edge.blockTo != null) {
                            model.disconnectBlockFlow(edge.blockFrom, edge.blockTo)
                        } else if (edge.dataTo != null && edge.dataToPort != null) {
                            model.disconnectData(edge.dataTo, edge.dataToPort)
                        }
                    }
                    ImGui.endPopup()
                } else if (!ImGui.isPopupOpen(POPUP_EDGE)) {
                    contextRequest = null
                }
            }
            ContextRequest.Kind.BLOCK -> {
                if (ImGui.beginPopup(POPUP_BLOCK)) {
                    val blockId = req.nodeId ?: return
                    if (ImGui.menuItem(if (model.outputs.contains(blockId)) "Remove from Outputs" else "Set as Output")) {
                        model.setOutput(blockId)
                    }
                    val flows = model.blockFlows.filter { it.fromBlock == blockId || it.toBlock == blockId }
                    if (flows.isNotEmpty() && ImGui.menuItem("Disconnect Batch Flow (${flows.size})")) {
                        for (f in flows.toList()) model.disconnectBlockFlow(f.fromBlock, f.toBlock)
                    }
                    if (ImGui.menuItem("Delete")) {
                        model.contextOf(blockId)?.let { model.removeBlock(it, blockId) }
                        selected.remove(blockId)
                    }
                    ImGui.endPopup()
                } else if (!ImGui.isPopupOpen(POPUP_BLOCK)) {
                    contextRequest = null
                }
            }
            ContextRequest.Kind.NODE -> {
                if (ImGui.beginPopup(POPUP_NODE)) {
                    val nodeId = req.nodeId ?: return
                    if (ImGui.menuItem("Delete")) {
                        model.removeOperator(nodeId)
                        selected.remove(nodeId)
                    }
                    ImGui.endPopup()
                } else if (!ImGui.isPopupOpen(POPUP_NODE)) {
                    contextRequest = null
                }
            }
            ContextRequest.Kind.CONTEXT -> {
                if (ImGui.beginPopup(POPUP_CONTEXT)) {
                    val ctxId = req.contextId ?: return
                    if (ImGui.menuItem("Delete Context")) {
                        model.removeContext(ctxId)
                        selectedContext.remove(ctxId)
                    }
                    ImGui.endPopup()
                } else if (!ImGui.isPopupOpen(POPUP_CONTEXT)) {
                    contextRequest = null
                }
            }
            ContextRequest.Kind.CANVAS -> {
                if (ImGui.beginPopup(POPUP_CANVAS)) {
                    canvasPalette?.invoke()
                    ImGui.separator()
                    if (ImGui.menuItem("Frame All")) frameAll()
                    ImGui.endPopup()
                } else if (!ImGui.isPopupOpen(POPUP_CANVAS)) {
                    contextRequest = null
                }
            }
        }
    }

    /** 命中任意连线（右键断线用）：flow → blockFlow → dataEdge 优先级。 */
    private fun hitAnyEdge(mouseX: Float, mouseY: Float): ContextRequest.EdgeTarget? {
        for (edge in model.flowEdges) {
            val from = flowPortPos(edge.fromContext, true) ?: continue
            val to = flowPortPos(edge.toContext, false) ?: continue
            if (distToBezier(mouseX, mouseY, from.first, from.second, to.first, to.second) < 6f) {
                return ContextRequest.EdgeTarget(flowFrom = edge.fromContext, flowTo = edge.toContext)
            }
        }
        for (edge in model.blockFlows) {
            val from = blockBatchPortPos(edge.fromBlock, "@batchOut") ?: continue
            val to = blockBatchPortPos(edge.toBlock, "@batchIn") ?: continue
            if (distToBezier(mouseX, mouseY, from.first, from.second, to.first, to.second) < 6f) {
                return ContextRequest.EdgeTarget(blockFrom = edge.fromBlock, blockTo = edge.toBlock)
            }
        }
        for (edge in model.dataEdges) {
            val from = dataPortPos(edge.fromNode, edge.fromPort, false) ?: continue
            val to = dataPortPos(edge.toNode, edge.toPort, true) ?: continue
            if (distToBezier(mouseX, mouseY, from.first, from.second, to.first, to.second) < 6f) {
                return ContextRequest.EdgeTarget(dataTo = edge.toNode, dataToPort = edge.toPort)
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

    private fun finishConnecting(mouseX: Float, mouseY: Float) {
        val c = connecting ?: return
        when (c.kind) {
            DragKind.FLOW -> {
                val target = hitFlowPort(mouseX, mouseY, isOutput = false)
                if (target != null) model.connectFlow(c.contextId ?: return, target)
            }
            DragKind.BLOCK_FLOW -> {
                // spawn 块批次输出（c.nodeId）→ init 块批次输入
                val target = hitBlockBatchPort(mouseX, mouseY, "@batchIn")
                if (target != null) model.connectBlockFlow(c.nodeId, target)
            }
            DragKind.DATA_FROM -> {
                // 算子输出端口（c.nodeId）→ 块输入端口：源/目标均解析真实端口 id
                val target = hitBlockPort(mouseX, mouseY)
                val sourcePort = model.firstOutputPort(c.nodeId)
                val targetPort = target?.let { model.firstInputPort(it) }
                if (target != null && sourcePort != null && targetPort != null) {
                    model.connectData(c.nodeId, sourcePort, target, targetPort)
                }
            }
            DragKind.DATA_TO -> {
                // 块输入端口不再启动连线（仅作落点），此分支不应触发
            }
        }
        connecting = null
    }

    private fun applyBoxSelect(box: BoxSelect) {
        val minX = minOf(box.startX, box.curX)
        val maxX = maxOf(box.startX, box.curX)
        val minY = minOf(box.startY, box.curY)
        val maxY = maxOf(box.startY, box.curY)
        for (op in model.operators.values) {
            val x = camera.graphToScreenX(op.x)
            val y = camera.graphToScreenY(op.y)
            if (x < maxX && x + 160f * camera.zoom > minX && y < maxY && y + 40f * camera.zoom > minY) {
                selected.add(op.id)
            }
        }
    }

    // ---- 命中测试 ----

    private fun hitContext(mouseX: Float, mouseY: Float): String? {
        for (ctx in model.contexts.values) {
            val x = camera.graphToScreenX(ctx.x)
            val y = camera.graphToScreenY(ctx.y)
            val w = 200f * camera.zoom
            val h = contextSize(ctx)
            if (mouseX in x..(x + w) && mouseY in y..(y + h)) return ctx.id
        }
        return null
    }

    private fun hitBlock(mouseX: Float, mouseY: Float): String? {
        for (ctx in model.contexts.values) {
            val x = camera.graphToScreenX(ctx.x)
            val headerH = 24f
            val blockH = 26f
            val pad = 6f
            var blockY = camera.graphToScreenY(ctx.y) + headerH + pad
            for (block in ctx.blocks.values) {
                if (mouseX in x..(x + 200f * camera.zoom) && mouseY in blockY..(blockY + blockH)) {
                    return block.id
                }
                blockY += blockH
            }
        }
        return null
    }

    private fun hitOperatorNode(mouseX: Float, mouseY: Float): String? {
        for (op in model.operators.values) {
            val x = camera.graphToScreenX(op.x)
            val y = camera.graphToScreenY(op.y)
            if (mouseX in x..(x + 160f * camera.zoom) && mouseY in y..(y + 40f * camera.zoom)) return op.id
        }
        return null
    }

    private fun hitFlowPort(mouseX: Float, mouseY: Float, isOutput: Boolean): String? {
        for (ctx in model.contexts.values) {
            val pos = flowPortPos(ctx.id, isOutput) ?: continue
            if (dist(mouseX, mouseY, pos.first, pos.second) <= 8f) return ctx.id
        }
        return null
    }

    private fun hitOperatorOutputPort(mouseX: Float, mouseY: Float): String? {
        for (op in model.operators.values) {
            val pos = operatorPortPos(op.id, isInput = false) ?: continue
            if (dist(mouseX, mouseY, pos.first, pos.second) <= 8f) return op.id
        }
        return null
    }

    private fun hitBlockPort(mouseX: Float, mouseY: Float): String? {
        for (ctx in model.contexts.values) {
            for (block in ctx.blocks.values) {
                if (model.firstInputPort(block.id) == null) continue
                val pos = blockPortPos(block.id) ?: continue
                if (dist(mouseX, mouseY, pos.first, pos.second) <= 8f) return block.id
            }
        }
        return null
    }

    /** 命中块批次 flow 端口：spawn 输出（@batchOut）或 init 输入（@batchIn）。arc 发射块无批次端口。 */
    private fun hitBlockBatchPort(mouseX: Float, mouseY: Float, portId: String): String? {
        for (ctx in model.contexts.values) {
            for (block in ctx.blocks.values) {
                val cat = blockCategory(block.typeId)
                if (portId == "@batchOut" && (cat != "spawn" || isArc(block.typeId))) continue
                if (portId == "@batchIn" && cat != "init") continue
                val pos = blockBatchPortPos(block.id, portId) ?: continue
                if (dist(mouseX, mouseY, pos.first, pos.second) <= 8f) return block.id
            }
        }
        return null
    }

    private fun computeHoveredPort(mouseX: Float, mouseY: Float): Pair<String, String>? {
        if (!ImGui.isWindowHovered()) return null
        for (ctx in model.contexts.values) {
            val out = flowPortPos(ctx.id, true)
            if (out != null && dist(mouseX, mouseY, out.first, out.second) <= 8f) return Pair(ctx.id, "@flow")
            val inPos = flowPortPos(ctx.id, false)
            if (inPos != null && dist(mouseX, mouseY, inPos.first, inPos.second) <= 8f) return Pair(ctx.id, "@flowIn")
        }
        for (op in model.operators.values) {
            val pos = operatorPortPos(op.id, false)
            if (pos != null && dist(mouseX, mouseY, pos.first, pos.second) <= 8f) return Pair(op.id, "out")
        }
        for (ctx in model.contexts.values) {
            for (block in ctx.blocks.values) {
                val cat = blockCategory(block.typeId)
                if (cat == "spawn" && !isArc(block.typeId)) {
                    val pos = blockBatchPortPos(block.id, "@batchOut")
                    if (pos != null && dist(mouseX, mouseY, pos.first, pos.second) <= 8f) return Pair(block.id, "@batchOut")
                } else if (cat == "init") {
                    val pos = blockBatchPortPos(block.id, "@batchIn")
                    if (pos != null && dist(mouseX, mouseY, pos.first, pos.second) <= 8f) return Pair(block.id, "@batchIn")
                }
                if (model.firstInputPort(block.id) == null) continue
                val pos = blockPortPos(block.id)
                if (pos != null && dist(mouseX, mouseY, pos.first, pos.second) <= 8f) return Pair(block.id, "@in")
            }
        }
        return null
    }

    /** hover 端口时提示其数据流语义：让「spawn 喂哪个 init / 块接收谁的数据」可见。 */
    private fun renderPortTooltip() {
        val hp = hoverPort ?: return
        if (hp.second == "@flow") {
            val upstream = model.flowEdges.filter { it.fromContext == hp.first }.map { it.toContext }
            val downstream = model.flowEdges.filter { it.toContext == hp.first }.map { it.fromContext }
            ImGui.setTooltip("Flow out → sends particle batches to: ${upstream.joinToString { nameOfContext(it) }}")
            return
        }
        if (hp.second == "@flowIn") {
            val upstream = model.flowEdges.filter { it.toContext == hp.first }.map { it.fromContext }
            ImGui.setTooltip("Flow in ← receives batches from: ${upstream.joinToString { nameOfContext(it) }}")
            return
        }
        if (hp.second == "@batchOut") {
            val targets = model.blockFlows.filter { it.fromBlock == hp.first }.map { it.toBlock }
            val targetName = targets.joinToString { displayName(model.findBlock(it)?.typeId ?: it) }
            ImGui.setTooltip("Batch out → feeds spawn's particles to init: ${if (targetName.isEmpty()) "not wired yet" else targetName}")
            return
        }
        if (hp.second == "@batchIn") {
            val sources = model.blockFlows.filter { it.toBlock == hp.first }.map { it.fromBlock }
            val sourceName = sources.joinToString { displayName(model.findBlock(it)?.typeId ?: it) }
            ImGui.setTooltip("Batch in ← processes particles from spawn: ${if (sourceName.isEmpty()) "not wired (use context flow or a block flow line)" else sourceName}")
            return
        }
        if (hp.second == "@in") {
            val block = model.findBlock(hp.first)
            val contextId = block?.let { model.contextOf(it.id) }
            val upstream = contextId?.let { c -> model.flowEdges.filter { it.toContext == c }.map { it.fromContext } } ?: emptyList()
            val port = block?.let { model.firstInputPort(it.id) }
            val source = model.dataEdges.firstOrNull { it.toNode == hp.first }?.fromNode
            val sourceName = source?.let { s ->
                val op = model.operators[s]
                if (op != null) "operator '${displayName(op.typeId)}'" else null
            }
            ImGui.setTooltip(
                (if (sourceName != null) "Input '$port' ← $sourceName" else "Input '$port' (property default)") +
                    (if (upstream.isNotEmpty()) "\nContext receives particles from: ${upstream.joinToString { nameOfContext(it) }}" else "")
            )
            return
        }
        if (hp.second == "out") {
            ImGui.setTooltip("Output → connect to a block input to drive it")
        }
    }

    private fun nameOfContext(contextId: String): String {
        val ctx = model.contexts[contextId] ?: return contextId
        return ctx.type.name
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    // ---- 工具 ----

    private fun displayName(typeId: String): String {
        return model.nodeType(typeId)?.displayName() ?: typeId
    }

    /** 块目录 category（spawn/init/update/...），用于批次 flow 端口判定。 */
    private fun blockCategory(typeId: String): String {
        return model.nodeType(typeId)?.category() ?: ""
    }

    /** 电弧发射块（M22）：虽为 spawn 类但不产粒子批次，不参与批次 flow 端口。 */
    private fun isArc(typeId: String): Boolean {
        return typeId.startsWith("vfx.block.arc_")
    }

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

    private fun col(r: Float, g: Float, b: Float, a: Float): Int {
        return (((a * 255).toInt() and 0xFF) shl 24) or
            (((r * 255).toInt() and 0xFF) shl 16) or
            (((g * 255).toInt() and 0xFF) shl 8) or
            ((b * 255).toInt() and 0xFF)
    }

    private fun edgeColor(): Int = col(0.7f, 0.7f, 0.75f, 0.9f)

    /** flow 连线色（绿，与 flow 端口色一致）。 */
    private fun flowColor(): Int = col(0.5f, 0.9f, 0.7f, 0.9f)

    /** 选中 context 时的高亮 flow 色。 */
    private fun flowHighlightColor(): Int = col(0.9f, 1f, 0.6f, 1f)

    companion object {
        const val POPUP_CANVAS = "##container_canvas_ctx"
        const val POPUP_CONTEXT = "##container_ctx_ctx"
        const val POPUP_BLOCK = "##container_block_ctx"
        const val POPUP_NODE = "##container_node_ctx"
        const val POPUP_EDGE = "##container_edge_ctx"
    }
}

class ContextRequest(
    val kind: Kind,
    val nodeId: String? = null,
    val contextId: String? = null,
    val edge: EdgeTarget? = null,
) {
    enum class Kind { CANVAS, NODE, BLOCK, CONTEXT, EDGE }

    /** 右键命中的边目标（用于断线）。 */
    class EdgeTarget(
        val flowFrom: String? = null,
        val flowTo: String? = null,
        val blockFrom: String? = null,
        val blockTo: String? = null,
        val dataTo: String? = null,
        val dataToPort: String? = null,
    )
}
