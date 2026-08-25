package org.academy.api.client.gui.editor

import com.google.gson.JsonObject
import org.academy.api.client.gui.serialize.PropType
import org.academy.api.client.gui.serialize.UiJson
import org.academy.api.client.gui.serialize.WidgetCodecRegistry
import org.academy.api.client.gui.serialize.WidgetNode
import org.academy.api.client.gui.serialize.WidgetSerializer
import org.academy.api.client.gui.serialize.setValue
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque

/**
 * 布局编辑器的文档模型：中性的 [WidgetNode] 树 + 选中状态 + undo/redo，与渲染宿主解耦。
 * 游戏内编辑器与桌面编辑器共用；改动经 [onMutated] 通知宿主（如游戏内推给 UiDebugSession）。
 */
class UiEditorDocument(
    var fileName: String,
    var root: WidgetNode,
) {
    private val undoStack = ArrayDeque<WidgetNode>()
    private val redoStack = ArrayDeque<WidgetNode>()
    private val maxUndoDepth = 50

    /** debug 模式锁定名称/类型/结构编辑（游戏内实时布局编辑器）。 */
    var structureLocked: Boolean = false

    /** 每次内容性改动（mutate/undo/redo/replaceRoot）后回调（宿主可推送运行时草稿）。 */
    var onMutated: (() -> Unit)? = null

    var selectedPath: List<String> = emptyList()

    var dirty: Boolean = false
        private set

    var error: String? = null
        private set

    /** Reports a validation error surfaced by the view layer (e.g. preview decode). */
    fun reportError(message: String?) {
        error = message
    }

    val selectedNode: WidgetNode?
        get() = findNodeByPath(root, selectedPath)

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun documentJson(): JsonObject {
        val o = JsonObject()
        o.addProperty("version", WidgetSerializer.FORMAT_VERSION)
        o.add("root", root.toJson())
        return o
    }

    fun prettyJson(): String = UiJson.GSON.toJson(documentJson())

    fun setSelection(path: List<String>) {
        selectedPath = path
    }

    fun mutate(action: (WidgetNode) -> Unit) {
        pushUndo()
        action(root)
        dirty = true
        error = null
        onMutated?.invoke()
    }

    /** 整体替换文档（打开/加载/revert 用），不清空历史。 */
    fun replaceRoot(node: WidgetNode) {
        root = node
        selectedPath = emptyList()
        dirty = true
        error = null
        onMutated?.invoke()
    }

    fun undo(): Boolean {
        val snapshot = undoStack.pollLast() ?: return false
        redoStack.addLast(root)
        root = snapshot
        selectedPath = emptyList()
        dirty = true
        error = null
        onMutated?.invoke()
        return true
    }

    fun redo(): Boolean {
        val snapshot = redoStack.pollLast() ?: return false
        undoStack.addLast(root)
        root = snapshot
        selectedPath = emptyList()
        dirty = true
        error = null
        onMutated?.invoke()
        return true
    }

    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
    }

    // ============ structure operations ============

    fun renameSelected(newName: String) {
        val sel = selectedNode ?: return
        if (newName.isBlank()) return
        mutate {
            sel.name = newName
            if (selectedPath.isNotEmpty()) {
                selectedPath = selectedPath.dropLast(1) + newName
            }
        }
    }

    /** Adds a child of [type] to the selected container (or the root). */
    fun addChild(type: String): Boolean {
        val parent = selectedNode ?: root
        if (!canHostChildren(parent)) return false
        val name = uniqueName(parent, type)
        mutate {
            parent.children.add(WidgetNode(type, name))
            setSelection(selectionFor(parent, name))
        }
        return true
    }

    fun deleteSelected() {
        val parent = parentOf(selectedPath) ?: return
        mutate {
            parent.children.removeAll { it.name == selectedPath.last() }
            setSelection(emptyList())
        }
    }

    fun duplicateSelected(): Boolean {
        val name = selectedPath.lastOrNull() ?: return false
        val parent = parentOf(selectedPath) ?: return false
        if (!canHostChildren(parent)) return false
        mutate {
            val index = parent.children.indexOfFirst { it.name == name }
            if (index >= 0) {
                val copy = WidgetNode.fromJson(parent.children[index].toJson())
                copy.name = uniqueName(parent, name)
                parent.children.add(index + 1, copy)
                setSelection(selectionFor(parent, copy.name))
            }
        }
        return true
    }

    fun moveSelected(delta: Int) {
        val name = selectedPath.lastOrNull() ?: return
        val parent = parentOf(selectedPath) ?: return
        mutate {
            val index = parent.children.indexOfFirst { it.name == name }
            val target = index + delta
            if (index >= 0 && target >= 0 && target < parent.children.size) {
                val node = parent.children.removeAt(index)
                parent.children.add(target, node)
                setSelection(selectionFor(parent, name))
            }
        }
    }

    /** 单次撤销地把 [name] 移动到 [containerPath] 容器的 [targetIndex] 位置。 */
    fun moveChildTo(containerPath: List<String>, name: String, targetIndex: Int): Boolean {
        val container = findNodeByPath(root, containerPath) ?: return false
        val from = container.children.indexOfFirst { it.name == name }
        if (from < 0) return false
        mutate {
            val node = container.children.removeAt(from)
            val to = targetIndex.coerceIn(0, container.children.size)
            container.children.add(to, node)
            setSelection(containerPath + name)
        }
        return true
    }

    /** 单次撤销地把 [path] 节点的 [key] 属性改为 [value]（类型经 [type] 编码）。 */
    fun setNodeProperty(path: List<String>, key: String, type: PropType, value: String) = editNode(path) {
        it.setValue(key, type, value)
    }

    /** 单次撤销地对 [path] 节点执行 [action]（属性/结构修改用）。 */
    fun editNode(path: List<String>, action: (WidgetNode) -> Unit) {
        val node = findNodeByPath(root, path) ?: return
        mutate { action(node) }
    }

    /** Serializes the selected node to a JSON string (for clipboard). */
    fun copyNode(): String? {
        val node = selectedNode ?: return null
        return UiJson.GSON.toJson(node.toJson())
    }

    /** Parses a node JSON string and adds it as a child of the selected container. */
    fun pasteNode(json: String): Boolean {
        return try {
            val obj = UiJson.GSON.fromJson(json, JsonObject::class.java)
            val node = WidgetNode.fromJson(obj)
            val parent = selectedNode ?: root
            if (!canHostChildren(parent)) return false
            val name = uniqueName(parent, node.name.ifBlank { node.type })
            node.name = name
            mutate {
                parent.children.add(node)
                setSelection(selectionFor(parent, name))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Replaces the whole document from raw JSON text; returns an error message or null. */
    fun applyJsonText(text: String): String? {
        return try {
            val obj = UiJson.GSON.fromJson(text, JsonObject::class.java)
                ?: throw IllegalArgumentException("not a JSON object")
            WidgetSerializer.decode(obj)
            mutate {
                root = WidgetNode.fromJson(obj.getAsJsonObject("root") ?: obj)
                setSelection(emptyList())
            }
            null
        } catch (e: Exception) {
            "Invalid JSON: ${e.message}"
        }
    }

    // ============ validation / persistence ============

    /** Revalidates the document by decoding it through [WidgetSerializer]. */
    fun validate() {
        error = try {
            WidgetSerializer.decode(documentJson())
            null
        } catch (e: Exception) {
            "Invalid layout: ${e.message}"
        }
    }

    /** Writes the pretty JSON to [dir]/<fileName>.json (creating dirs). */
    fun saveTo(dir: Path): Path {
        Files.createDirectories(dir)
        val file = dir.resolve("$fileName.json")
        Files.writeString(file, prettyJson())
        dirty = false
        return file
    }

    /** Reads a layout file and returns the document, or null on failure. */
    fun loadFrom(dir: Path, name: String): UiEditorDocument? {
        return try {
            val obj = UiJson.GSON.fromJson(Files.readString(dir.resolve("$name.json")), JsonObject::class.java)
            UiEditorDocument(name, WidgetNode.fromJson(obj.getAsJsonObject("root") ?: obj)).also {
                it.dirty = false
            }
        } catch (e: Exception) {
            null
        }
    }

    // ============ internals ============

    private fun pushUndo() {
        undoStack.addLast(WidgetNode.fromJson(root.toJson()))
        if (undoStack.size > maxUndoDepth) undoStack.removeFirst()
        redoStack.clear()
    }

    /** The root frame is always a container; other nodes only if their codec says so. */
    private fun canHostChildren(node: WidgetNode): Boolean {
        if (node === root) return true
        return WidgetCodecRegistry.isContainerType(node.type)
    }

    private fun uniqueName(parent: WidgetNode, base: String): String {
        val used = parent.children.map { it.name }.toHashSet()
        if (base !in used) return base
        var i = 1
        while ("${base}_$i" in used) i++
        return "${base}_$i"
    }

    private fun parentOf(path: List<String>): WidgetNode? {
        if (path.isEmpty()) return null
        if (path.size == 1) return root
        return findNodeByPath(root, path.dropLast(1))
    }

    private fun selectionFor(parent: WidgetNode, name: String): List<String> {
        val parentPath = if (parent === root) emptyList() else pathOf(root, parent)
        return parentPath + name
    }

    private fun pathOf(node: WidgetNode, target: WidgetNode): List<String> {
        if (node === target) return emptyList()
        for (child in node.children) {
            if (child === target) return listOf(child.name)
            val sub = pathOf(child, target)
            if (sub.isNotEmpty()) return listOf(child.name) + sub
        }
        return emptyList()
    }

    companion object {
        fun findNodeByPath(node: WidgetNode, path: List<String>): WidgetNode? {
            if (path.isEmpty()) return node
            val child = node.children.firstOrNull { it.name == path[0] } ?: return null
            return findNodeByPath(child, path.drop(1))
        }
    }
}
