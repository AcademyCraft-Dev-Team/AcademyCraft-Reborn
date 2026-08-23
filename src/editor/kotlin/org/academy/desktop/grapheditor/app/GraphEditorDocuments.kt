package org.academy.desktop.grapheditor.app

import com.google.gson.JsonObject
import org.academy.api.client.render.graph.model.Graph
import org.academy.api.client.render.graph.registry.NodeRegistry
import org.academy.api.client.render.graph.serialize.JsonGraphCodec
import org.academy.api.client.render.graph.subgraph.SubGraphRegistry
import org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec
import org.academy.desktop.grapheditor.canvas.GraphEditorModel
import org.academy.desktop.grapheditor.container.VfxContainerModel
import org.academy.desktop.grapheditor.document.EditorMetadata
import java.nio.file.Path

/**
 * 多文档管理器（M19，ADR-022）：维护打开的图文档列表与活动索引。每文档独立模型
 * （独立 undo）。文档集合每次变更后重建共享 [SubGraphRegistry]（key = 文档名去扩展名），
 * 推给所有文档的模型，使 subgraph 节点端口动态派生并可在预览内联展开。
 */
class GraphEditorDocuments(private val registry: NodeRegistry) {
    val subGraphs = SubGraphRegistry()

    private val docs = mutableListOf<EditorDocument>()
    private var active = 0

    /** 文档集合变更（开/关/切）后的回调，宿主据此同步面板状态。 */
    var onChange: (() -> Unit)? = null

    /** 新建空白文档（模式为当前模式）并激活。 */
    fun newDoc(name: String, mode: GraphMode): EditorDocument {
        val model = GraphEditorModel(registry)
        model.reset()
        val containerModel = VfxContainerModel(registry)
        containerModel.reset()
        val doc = EditorDocument(name, null, model, containerModel, EditorMetadata(), mode, 1f, 0f, 0f)
        docs += doc
        active = docs.lastIndex
        rebuild()
        return doc
    }

    /** 兼容重载：以核心 [Graph]（扁平 schema）打开文档（非容器）。 */
    fun openDoc(path: Path, name: String, graph: Graph, metadata: EditorMetadata, mode: GraphMode): EditorDocument {
        val json = JsonGraphCodec(registry).encode(graph)
        return openDoc(path, name, json, false, metadata, mode)
    }

    /** 从磁盘图打开为新文档并激活；附带 sidecar 元数据。容器图（kind:"vfx"）加载进容器模型。 */
    fun openDoc(path: Path, name: String, json: JsonObject, isContainer: Boolean,
                metadata: EditorMetadata, mode: GraphMode): EditorDocument {
        val model = GraphEditorModel(registry)
        val containerModel = VfxContainerModel(registry)
        if (isContainer) {
            model.reset()
            containerModel.load(JsonVfxGraphCodec(registry).decode(json))
        } else {
            model.load(JsonGraphCodec(registry).decode(json))
            model.loadMetadata(metadata)
        }
        val doc = EditorDocument(name, path, model, containerModel, metadata, mode, 1f, 0f, 0f)
        docs += doc
        active = docs.lastIndex
        rebuild()
        return doc
    }

    /** 兼容重载：以核心 [Graph]（扁平 schema）就地重载（非容器）。 */
    fun reload(path: Path, name: String, graph: Graph, metadata: EditorMetadata, mode: GraphMode): EditorDocument? {
        val json = JsonGraphCodec(registry).encode(graph)
        return reload(path, name, json, false, metadata, mode)
    }

    /** 热重载（M21s）：就地替换指定路径文档的内容（保留标签页/相机，重置模型），返回 null 表示未打开。 */
    fun reload(path: Path, name: String, json: JsonObject, isContainer: Boolean,
               metadata: EditorMetadata, mode: GraphMode): EditorDocument? {
        val index = docs.indexOfFirst { it.path?.toAbsolutePath() == path.toAbsolutePath() }
        if (index < 0) {
            return null
        }
        val old = docs[index]
        val model = GraphEditorModel(registry)
        val containerModel = VfxContainerModel(registry)
        if (isContainer) {
            model.reset()
            containerModel.load(JsonVfxGraphCodec(registry).decode(json))
        } else {
            model.load(JsonGraphCodec(registry).decode(json))
            model.loadMetadata(metadata)
        }
        docs[index] = EditorDocument(name, path, model, containerModel, metadata, mode, old.cameraZoom, old.cameraPanX, old.cameraPanY)
        rebuild()
        return docs[index]
    }

    fun current(): EditorDocument = docs[active]

    fun list(): List<EditorDocument> = docs.toList()

    fun indexOf(doc: EditorDocument): Int = docs.indexOf(doc)

    fun activate(index: Int) {
        if (index in docs.indices && index != active) {
            active = index
            onChange?.invoke()
        }
    }

    /** 关闭文档；列表为空则新建一个空白文档。 */
    fun close(index: Int) {
        if (index !in docs.indices) return
        docs.removeAt(index)
        if (docs.isEmpty()) {
            newDoc("graph", GraphMode.SHADER)
            return
        }
        if (active >= docs.size) {
            active = docs.lastIndex
        }
        rebuild()
    }

    private fun rebuild() {
        subGraphs.clear()
        for (doc in docs) {
            subGraphs.register(subGraphId(doc.name), doc.model.toGraph())
        }
        for (doc in docs) {
            doc.model.setSubGraphRegistry(subGraphs)
        }
        onChange?.invoke()
    }

    /** 文档名/内容变化后重建子图注册表（改名/保存后调用）。 */
    fun refresh() {
        rebuild()
    }

    /** 文档名 → subgraph 注册 id（去 .json 扩展名）。 */
    companion object {
        fun subGraphId(name: String): String = name.removeSuffix(".json")
    }
}
