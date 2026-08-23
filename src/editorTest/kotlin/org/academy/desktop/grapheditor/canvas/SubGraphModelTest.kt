package org.academy.desktop.grapheditor.canvas

import org.academy.api.client.render.graph.model.Edge
import org.academy.api.client.render.graph.model.Graph
import org.academy.api.client.render.graph.model.GraphNode
import org.academy.api.client.render.graph.model.GraphParameter
import org.academy.api.client.render.graph.model.Port
import org.academy.api.client.render.graph.model.PortDirection
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry
import org.academy.api.client.render.graph.subgraph.SubGraphRegistry
import org.academy.api.client.render.graph.type.Value
import org.academy.api.client.render.graph.type.ValueType
import org.academy.api.client.render.shader.codegen.GlslNodeRegistry
import org.academy.api.client.render.shader.nodes.ShaderNodes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional

class SubGraphModelTest {

    private fun modelWithSubGraphs(): GraphEditorModel {
        val registry = SimpleNodeRegistry()
        ShaderNodes.registerAll(registry, GlslNodeRegistry())
        return GraphEditorModel(registry)
    }

    private fun subGraph(): Graph = Graph(
        "sub",
        listOf(
            GraphNode("p", "input.param_float", mapOf("param" to "x"), listOf(
                Port("out", "Out", PortDirection.OUTPUT, ValueType.FLOAT, Value.of(0f))
            ), 0f, 0f),
            GraphNode("o", "output.color", emptyMap(), listOf(
                Port("color", "Color", PortDirection.INPUT, ValueType.COLOR, Value.color(1f, 1f, 1f, 1f))
            ), 0f, 0f),
        ),
        listOf(Edge(Edge.PortRef("p", "out"), Edge.PortRef("o", "color"))),
        listOf(GraphParameter("x", "X", ValueType.FLOAT, Value.of(0f), Optional.empty())),
        listOf("o"),
    )

    @Test
    fun subgraphNodeDerivesDynamicPorts() {
        val model = modelWithSubGraphs()
        val subRegistry = SubGraphRegistry()
        subRegistry.register("sub", subGraph())
        model.setSubGraphRegistry(subRegistry)

        val node = model.addNode("subgraph", 0f, 0f)
        model.setProperty(node.id, "graph", "sub")

        val ports = model.portsFor(node)
        assertEquals(2, ports.size)
        assertTrue(ports.any { it.id() == "in0" && it.direction() == PortDirection.INPUT })
        assertTrue(ports.any { it.id() == "out" && it.direction() == PortDirection.OUTPUT })
    }

    @Test
    fun subgraphWithoutRegistryHasNoPorts() {
        val model = modelWithSubGraphs()
        val node = model.addNode("subgraph", 0f, 0f)
        model.setProperty(node.id, "graph", "sub")
        assertTrue(model.portsFor(node).isEmpty())
    }
}
