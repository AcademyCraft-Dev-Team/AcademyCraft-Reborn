package org.academy.desktop.grapheditor.app

import org.academy.api.client.render.graph.model.Graph
import org.academy.api.client.render.graph.model.GraphNode
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry
import org.academy.api.client.render.graph.serialize.JsonGraphCodec
import org.academy.api.client.render.graph.type.ValueType
import org.academy.api.client.render.shader.codegen.GlslNodeRegistry
import org.academy.api.client.render.shader.nodes.ShaderNodes
import org.academy.desktop.grapheditor.document.EditorMetadata
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

class GraphEditorDocumentsTest {
    private lateinit var registry: SimpleNodeRegistry

    @BeforeEach
    fun setUp() {
        registry = SimpleNodeRegistry()
        ShaderNodes.registerAll(registry, GlslNodeRegistry())
    }

    private fun graphWithNode(id: String): Graph {
        val node = GraphNode(id, "input.constant", mapOf("value" to "1.0"), listOf(), 0f, 0f)
        return Graph("t", listOf(node), listOf(), listOf(), listOf())
    }

    @Test
    fun newDocCreatesActiveBlankDocument() {
        val docs = GraphEditorDocuments(registry)
        val doc = docs.newDoc("a", GraphMode.SHADER)
        assertSame(doc, docs.current())
        assertEquals("a", doc.name)
        assertEquals(1, docs.list().size)
    }

    @Test
    fun openDocLoadsGraphAndMetadata() {
        val docs = GraphEditorDocuments(registry)
        val meta = EditorMetadata()
        meta.panelVisibility["Inspector"] = false
        val doc = docs.openDoc(Path.of("a.json"), "a", graphWithNode("n"), meta, GraphMode.VFX)
        assertEquals("n", doc.model.nodes.keys.first())
        assertEquals(GraphMode.VFX, doc.mode)
        assertEquals(false, doc.metadata.panelVisibility["Inspector"])
    }

    @Test
    fun documentsKeepIndependentModelsAndUndo() {
        val docs = GraphEditorDocuments(registry)
        val a = docs.newDoc("a", GraphMode.SHADER)
        a.model.addNode("input.constant", 0f, 0f)
        val b = docs.newDoc("b", GraphMode.SHADER)
        // b 是空白文档，模型实例不同，undo 互不影响
        assertNotSame(a.model, b.model)
        assertEquals(1, a.model.nodes.size)
        assertEquals(0, b.model.nodes.size)
        docs.activate(0)
        assertSame(a, docs.current())
        docs.activate(1)
        assertSame(b, docs.current())
    }

    @Test
    fun closeRemovesAndFallsBackToFreshDoc() {
        val docs = GraphEditorDocuments(registry)
        docs.newDoc("a", GraphMode.SHADER)
        docs.newDoc("b", GraphMode.SHADER)
        docs.close(0)
        assertEquals(1, docs.list().size)
        assertEquals("b", docs.current().name)
        docs.close(0)
        // 清空后自动新建空白文档
        assertEquals(1, docs.list().size)
        assertSame(docs.list()[0], docs.current())
    }

    @Test
    fun subgraphRegistryWiredAcrossDocuments() {
        val docs = GraphEditorDocuments(registry)
        // 子图文档 "sub" 含一个输出节点（subgraph 端口按参数 + out 派生）
        val subGraph = graphWithNode("subnode")
        docs.openDoc(Path.of("sub.json"), "sub", subGraph, EditorMetadata(), GraphMode.SHADER)
        val main = docs.newDoc("main", GraphMode.SHADER)
        val node = main.model.addNode("subgraph", 0f, 0f)
        main.model.setProperty(node.id, "graph", "sub")
        // 子图注册表已推给 main 模型 → 动态端口出现
        val ports = main.model.portsFor(node)
        assertEquals(1, ports.size)
        assertEquals("out", ports[0].id())
        assertEquals(ValueType.COLOR, ports[0].type())
    }

    @Test
    fun refreshReRegistersAfterRename() {
        val docs = GraphEditorDocuments(registry)
        docs.newDoc("old", GraphMode.SHADER)
        val doc = docs.current()
        doc.name = "new"
        docs.refresh()
        assertEquals("new", GraphEditorDocuments.subGraphId(doc.name))
        assertEquals(doc.model.toGraph(), docs.subGraphs.find("new"))
    }

    @Test
    fun reloadReplacesContentInPlaceKeepingTab() {
        val docs = GraphEditorDocuments(registry)
        val path = Path.of("a.json")
        docs.openDoc(path, "a", graphWithNode("first"), EditorMetadata(), GraphMode.SHADER)

        val replaced = docs.reload(path, "a", graphWithNode("second"), EditorMetadata(), GraphMode.VFX)
        assertNotNull(replaced)
        // 就地替换：标签页数不变、同一索引，内容/模式已更新
        assertEquals(1, docs.list().size)
        assertSame(replaced, docs.list()[0])
        assertEquals("second", docs.current().model.nodes.keys.first())
        assertEquals(GraphMode.VFX, docs.current().mode)
        // 未打开的文件返回 null
        assertNull(
            docs.reload(Path.of("nope.json"), "nope", graphWithNode("x"), EditorMetadata(), GraphMode.SHADER)
        )
    }

    @Test
    fun codecRoundTripsThroughDocuments() {
        val codec = JsonGraphCodec(registry)
        val docs = GraphEditorDocuments(registry)
        val doc = docs.newDoc("t", GraphMode.SHADER)
        doc.model.addNode("input.constant", 1f, 2f)
        val encoded = codec.encode(doc.model.toGraph())
        val decoded = codec.decode(encoded)
        assertEquals(1, decoded.nodes().size)
    }
}
