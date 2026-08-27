package org.academy.desktop.grapheditor

import org.academy.api.client.render.graph.model.PortDirection
import org.academy.api.client.render.graph.registry.NodeType
import org.academy.api.client.render.graph.registry.PortSpec
import org.academy.api.client.render.graph.registry.PropertySpec
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry
import org.academy.api.client.render.graph.type.Value
import org.academy.api.client.render.graph.type.ValueType
import java.util.*

/**
 * editorTest 共享夹具：提供带常量/加法节点目录的 [SimpleNodeRegistry]。
 */
object EditorTestFixtures {
    fun constantType(): NodeType = NodeType(
        "input.constant", "input", "Constant",
        listOf(PortSpec("out", "Out", PortDirection.OUTPUT, ValueType.FLOAT, Value.of(0f))),
        listOf(PropertySpec("value", "Value", ValueType.FLOAT, Value.of(0f), Optional.empty()))
    )

    fun addType(): NodeType = NodeType(
        "math.add", "math", "Add",
        listOf(
            PortSpec("a", "A", PortDirection.INPUT, ValueType.FLOAT, Value.of(0f)),
            PortSpec("b", "B", PortDirection.INPUT, ValueType.FLOAT, Value.of(0f)),
            PortSpec("out", "Out", PortDirection.OUTPUT, ValueType.FLOAT, Value.of(0f))
        ),
        emptyList()
    )

    fun registry(): SimpleNodeRegistry {
        val registry = SimpleNodeRegistry()
        registry.register(constantType())
        registry.register(addType())
        return registry
    }
}
