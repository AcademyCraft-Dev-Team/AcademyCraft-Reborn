package org.academy.api.client.gui.editor

import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.serialize.WidgetNode
import org.academy.api.client.gui.serialize.WidgetSerializer
import org.academy.api.client.gui.widget.AbstractWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.internal.client.gui.debug.UiDebugSession

class UiLayoutEditorScreen(
    initialDoc: JsonObject? = null,
    val debugLayoutId: String? = null,
    val structureLocked: Boolean = false
) : UiScreen(Component.literal("UI Layout Editor")) {
    private val lock = Any()

    private var docNode: WidgetNode = initialDoc?.let { WidgetNode.fromJson(it.getAsJsonObject("root") ?: it) }
        ?: WidgetNode("frame_layout", "root")

    private var selectedPath: List<String> = emptyList()
    private var selectedNode: WidgetNode? = null

    @Volatile
    private var dirty = true

    private lateinit var previewHost: FrameLayoutWidget
    private var previewRoot: Widget? = null

    // ImGui 侧共享状态
    @Volatile
    var fileName: String = "layout"

    @Volatile
    var jsonText: String = ""

    @Volatile
    var validationError: String? = null

    override fun isPauseScreen(): Boolean = false

    override fun onInit() {
        previewHost = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            background = ColorDrawable(0x33000000)
        }
        root.addChild("preview", previewHost)
        rebuildPreview()
        dirty = false
    }

    override fun tick() {
        if (dirty) {
            dirty = false
            rebuildPreview()
        }
        super.tick()
    }

    // ============ 文档访问 (线程安全) ============

    fun readDoc(): WidgetNode = synchronized(lock) { docNode }

    fun currentNode(): WidgetNode? = synchronized(lock) { selectedNode }

    fun currentPath(): List<String> = synchronized(lock) { selectedPath }

    fun documentJson(): JsonObject {
        val o = JsonObject()
        o.addProperty("version", WidgetSerializer.FORMAT_VERSION)
        o.add("root", readDoc().toJson())
        return o
    }

    /** 在锁内修改文档, 随后刷新预览. */
    fun mutateDoc(action: (WidgetNode) -> Unit) {
        synchronized(lock) {
            action(docNode)
            selectedNode = if (selectedPath.isEmpty()) null else findNodeByPath(docNode, selectedPath)
            dirty = true
            updateDebugDocument()
        }
    }

    fun setDoc(node: WidgetNode) {
        synchronized(lock) {
            docNode = node
            selectedPath = emptyList()
            selectedNode = null
            dirty = true
            updateDebugDocument()
        }
    }

    fun setSelection(path: List<String>) {
        synchronized(lock) {
            selectedPath = path
            selectedNode = findNodeByPath(docNode, path)
            dirty = true
        }
    }

    fun notifyChanged() {
        dirty = true
        synchronized(lock) { updateDebugDocument() }
    }

    fun renameSelectedNode(newName: String) {
        synchronized(lock) {
            val sel = findNodeByPath(docNode, selectedPath) ?: return
            sel.name = newName
            if (selectedPath.isNotEmpty()) {
                selectedPath = selectedPath.dropLast(1) + newName
            }
            dirty = true
            updateDebugDocument()
        }
    }

    fun revertDebugDocument() {
        val id = debugLayoutId ?: return
        UiDebugSession.revert(id)
        setDoc(WidgetNode.fromJson(UiDebugSession.documentJson(id).getAsJsonObject("root")))
    }

    fun reloadDebugDocument() {
        val id = debugLayoutId ?: return
        UiDebugSession.reload(id)
        setDoc(WidgetNode.fromJson(UiDebugSession.documentJson(id).getAsJsonObject("root")))
    }

    private fun updateDebugDocument() {
        val id = debugLayoutId ?: return
        val result = UiDebugSession.update(id, documentJson())
        validationError = result.error
    }

    // ============ 预览 ============

    private fun rebuildPreview() {
        val path = synchronized(lock) { selectedPath }
        synchronized(lock) {
            try {
                val decoded = WidgetSerializer.decode(documentJson())
                previewHost.clearChildren()
                previewRoot = decoded
                previewHost.addChild("preview", decoded)
                if (path.isNotEmpty()) {
                    val target = findWidgetByPath(decoded, path)
                    previewHost.addChild("selection", SelectionBorderWidget { target })
                }
            } catch (_: Exception) {
                previewRoot = null
            }
        }
    }

    private fun findWidgetByPath(w: Widget, path: List<String>): Widget? {
        if (path.isEmpty()) return w
        if (w !is WidgetContainer) return null
        val child = w.children[path[0]] ?: return null
        return findWidgetByPath(child, path.drop(1))
    }

    private fun findNodeByPath(node: WidgetNode, path: List<String>): WidgetNode? {
        if (path.isEmpty()) return node
        val child = node.children.firstOrNull { it.name == path[0] } ?: return null
        return findNodeByPath(child, path.drop(1))
    }

    private class SelectionBorderWidget(private val target: () -> Widget?) : AbstractWidget() {
        override fun renderInternal(context: RenderContext) {
            val t = target() ?: return
            val host = parent ?: return
            val dx = t.getAbsoluteX() - host.getAbsoluteX()
            val dy = t.getAbsoluteY() - host.getAbsoluteY()
            val thickness = 1f
            val r = 0.4f
            val g = 0.8f
            val b = 1f
            val a = 0.9f
            context.pose().pushPose()
            context.pose().translate(dx, dy)
            context.submit(FillRectDrawCommand(t.width, thickness, r, g, b, a))
            context.pose().pushPose()
            context.pose().translate(0f, t.height - thickness)
            context.submit(FillRectDrawCommand(t.width, thickness, r, g, b, a))
            context.pose().popPose()
            context.submit(FillRectDrawCommand(thickness, t.height, r, g, b, a))
            context.pose().pushPose()
            context.pose().translate(t.width - thickness, 0f)
            context.submit(FillRectDrawCommand(thickness, t.height, r, g, b, a))
            context.pose().popPose()
            context.pose().popPose()
        }
    }

    override fun mouseClicked(e: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (e.button() == 0) {
            selectWidgetAt(e.x(), e.y())
        }
        return super.mouseClicked(e, isDoubleClick)
    }

    private fun selectWidgetAt(mouseX: Double, mouseY: Double) {
        val rootW = previewRoot ?: return
        val hit = findDeepestWidgetAt(rootW, mouseX, mouseY) ?: return
        if (hit === rootW) return

        val path = mutableListOf<String>()
        var cur: Widget? = hit
        while (cur != null && cur !== rootW) {
            path.add(cur.name)
            cur = cur.parent
        }
        path.reverse()
        synchronized(lock) {
            if (findNodeByPath(docNode, path) != null) {
                setSelection(path)
            }
        }
    }

    private fun findDeepestWidgetAt(w: Widget, mouseX: Double, mouseY: Double): Widget? {
        if (!w.isVisible() || !w.isMouseOver(mouseX, mouseY)) return null
        if (w is WidgetContainer) {
            for (child in w.children.values.toList().asReversed()) {
                val result = findDeepestWidgetAt(child, mouseX, mouseY)
                if (result != null) return result
            }
        }
        return w
    }

    companion object {
        fun open(json: JsonObject? = null) {
            Minecraft.getInstance().execute { Minecraft.getInstance().gui.setScreen(UiLayoutEditorScreen(json)) }
        }

        fun openDebug(layoutId: String) {
            val json = UiDebugSession.documentJson(layoutId)
            Minecraft.getInstance().execute {
                Minecraft.getInstance().gui.setScreen(UiLayoutEditorScreen(json, layoutId, true))
            }
        }
    }
}
