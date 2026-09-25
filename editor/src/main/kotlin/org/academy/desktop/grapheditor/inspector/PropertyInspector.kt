package org.academy.desktop.grapheditor.inspector

import imgui.ImGui
import imgui.type.ImInt
import imgui.type.ImString
import org.academy.api.client.render.graph.model.GraphParameter
import org.academy.api.client.render.graph.type.Value
import org.academy.api.client.render.graph.type.ValueType
import org.academy.desktop.grapheditor.canvas.GraphEditorModel
import org.academy.desktop.grapheditor.canvas.GraphEditorModelRef
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.*

/**
 * 属性面板：编辑选中节点的属性与黑板参数。
 * M12-01 增强：参数类型补全（VEC2/VEC4/SAMPLER/CURVE/GRADIENT）、FLOAT 范围编辑、分组（sidecar）。
 */
class PropertyInspector(
    private val modelRef: GraphEditorModelRef,
) {
    private val model: GraphEditorModel get() = modelRef.model

    /** 黑板参数分组（引用宿主 EditorMetadata.paramGroups，随加载/新建替换）。 */
    var paramGroups: MutableMap<String, String> = mutableMapOf()

    private val newParamName = ImString(32)
    private val newParamType = ImInt(0)
    private val paramTypes = arrayOf("FLOAT", "VEC2", "VEC3", "VEC4", "COLOR", "SAMPLER", "CURVE", "GRADIENT")

    /** CURVE/GRADIENT 参数「Edit」回调；宿主据此打开曲线/渐变编辑器。 */
    var onEditCurve: ((String) -> Unit)? = null
    var onEditGradient: ((String) -> Unit)? = null

    fun render(selected: Set<String>) {
        ImGui.text("Inspector")
        ImGui.separator()

        if (selected.size == 1) {
            val node = model.nodes[selected.first()]
            if (node != null) renderNode(node)
        } else if (selected.size > 1) {
            ImGui.textDisabled("${selected.size} nodes selected")
        }

        ImGui.separator()
        renderParameters()
    }

    private fun renderNode(node: GraphEditorModel.EdNode) {
        val type = model.nodeType(node.typeId)
        ImGui.text("${type?.displayName() ?: node.typeId}  [${node.id}]")

        if (ImGui.button("Set as Output##output")) {
            model.setOutput(node.id)
        }
        ImGui.sameLine()
        if (ImGui.button("Delete##delete")) {
            model.removeNode(node.id)
        }

        for (spec in type?.properties() ?: emptyList()) {
            val current = node.properties[spec.id()] ?: valueToString(spec.defaultValue())
            val updated = editProperty("${spec.name()}##${node.id}_${spec.id()}", spec.type(), current)
            if (updated != null) {
                model.setProperty(node.id, spec.id(), updated)
            }
        }
    }

    private fun editProperty(label: String, type: ValueType, current: String): String? = when (type) {
        ValueType.FLOAT -> {
            val v = floatArrayOf(current.toFloatOrNull() ?: 0f)
            if (ImGui.dragFloat(label, v, 0.01f)) v[0].toString() else null
        }

        ValueType.VEC2 -> {
            val arr = parseVec2(current)
            if (ImGui.dragFloat2(label, arr)) "${arr[0]},${arr[1]}" else null
        }

        ValueType.COLOR -> {
            val arr = parseColor(current)
            if (ImGui.colorEdit4(label, arr)) "${arr[0]},${arr[1]},${arr[2]},${arr[3]}" else null
        }

        else -> {
            val buf = ImString(current, 128)
            if (ImGui.inputText(label, buf)) buf.get() else null
        }
    }

    private fun renderParameters() {
        ImGui.text("Parameters")

        for (i in model.parameters.indices) {
            val p = model.parameters[i]
            val group = paramGroups[p.id()] ?: ""
            val groupBuf = ImString(group, 32)
            if (ImGui.inputText("group##${i}_${p.id()}", groupBuf)) {
                if (groupBuf.get().isBlank()) paramGroups.remove(p.id())
                else paramGroups[p.id()] = groupBuf.get()
            }
            ImGui.text("${p.name()}  (${p.type()})")
            ImGui.sameLine()
            if (ImGui.button("x##remove_$i")) {
                model.removeParameter(i)
                continue
            }

            when (p.type()) {
                ValueType.CURVE -> {
                    if (ImGui.button("Edit Curve...##$i")) onEditCurve?.invoke(p.id())
                }

                ValueType.GRADIENT -> {
                    if (ImGui.button("Edit Gradient...##$i")) onEditGradient?.invoke(p.id())
                }

                ValueType.FLOAT -> {
                    val updated = editValue("##param_${i}_${p.id()}", p.defaultValue())
                    if (updated != null) {
                        model.replaceParameter(i, p.withDefault(updated))
                    }
                    renderRangeEdit(i, p)
                }

                else -> {
                    val updated = editValue("##param_${i}_${p.id()}", p.defaultValue())
                    if (updated != null) {
                        model.replaceParameter(i, p.withDefault(updated))
                    }
                }
            }
        }

        ImGui.separator()
        ImGui.inputText("Name##newparam", newParamName)
        ImGui.combo("Type##newparam", newParamType, paramTypes)
        if (ImGui.button("Add Parameter##add")) {
            val name = newParamName.get().trim().ifEmpty { "param" }
            val type = ValueType.valueOf(paramTypes[newParamType.get()])
            val id = name.lowercase().replace(Regex("[^a-z0-9_]"), "_")
            val def = when (type) {
                ValueType.VEC2 -> Value.of(Vector2f(0f))
                ValueType.VEC3 -> Value.of(Vector3f(0f))
                ValueType.VEC4 -> Value.of(Vector4f(0f))
                ValueType.COLOR -> Value.color(1f, 1f, 1f, 1f)
                ValueType.SAMPLER -> Value.sampler("minecraft:textures/block/stone.png")
                ValueType.CURVE -> Value.curve(
                    org.academy.api.client.render.graph.type.Curve(
                        listOf(
                            org.academy.api.client.render.graph.type.Curve.Keyframe.linear(0f, 0f),
                            org.academy.api.client.render.graph.type.Curve.Keyframe.linear(1f, 1f),
                        )
                    )
                )

                ValueType.GRADIENT -> Value.gradient(
                    org.academy.api.client.render.graph.type.Gradient(
                        listOf(
                            org.academy.api.client.render.graph.type.Gradient.ColorStop(0f, 0f, 0f, 0f, 1f),
                            org.academy.api.client.render.graph.type.Gradient.ColorStop(1f, 1f, 1f, 1f, 1f),
                        )
                    )
                )

                else -> Value.of(0f)
            }
            model.addParameter(GraphParameter(id, name, type, def, Optional.empty()))
        }
    }

    private fun renderRangeEdit(index: Int, p: GraphParameter) {
        val range = p.range()
        val arr = floatArrayOf(
            range.map { it.min().toFloat() }.orElse(0f),
            range.map { it.max().toFloat() }.orElse(1f),
        )
        if (ImGui.dragFloat2("Range##range_$index", arr, 0.1f)) {
            model.replaceParameter(
                index,
                p.withRange(Optional.of(GraphParameter.Range(arr[0].toDouble(), arr[1].toDouble())))
            )
        }
    }

    private fun editValue(label: String, value: Value): Value? = when (value.type()) {
        ValueType.FLOAT -> {
            val v = floatArrayOf(value.asFloat())
            if (ImGui.dragFloat(label, v, 0.01f)) Value.of(v[0]) else null
        }

        ValueType.VEC2 -> {
            val v = floatArrayOf(value.asVec2().x, value.asVec2().y)
            if (ImGui.dragFloat2(label, v, 0.01f)) Value.of(Vector2f(v[0], v[1])) else null
        }

        ValueType.VEC3 -> {
            val v = floatArrayOf(value.asVec3().x, value.asVec3().y, value.asVec3().z)
            if (ImGui.dragFloat3(label, v, 0.01f)) Value.of(Vector3f(v[0], v[1], v[2])) else null
        }

        ValueType.VEC4 -> {
            val v = floatArrayOf(value.asVec4().x, value.asVec4().y, value.asVec4().z, value.asVec4().w)
            if (ImGui.dragFloat4(label, v, 0.01f)) Value.of(Vector4f(v[0], v[1], v[2], v[3])) else null
        }

        ValueType.COLOR -> {
            val v = floatArrayOf(value.asColor().x, value.asColor().y, value.asColor().z, value.asColor().w)
            if (ImGui.colorEdit4(label, v)) Value.color(v[0], v[1], v[2], v[3]) else null
        }

        ValueType.SAMPLER -> {
            val buf = ImString(value.asSampler(), 256)
            if (ImGui.inputText(label, buf)) Value.sampler(buf.get()) else null
        }

        else -> null
    }

    private fun valueToString(value: Value): String = when (value.type()) {
        ValueType.FLOAT -> value.asFloat().toString()
        ValueType.VEC2 -> "${value.asVec2().x},${value.asVec2().y}"
        ValueType.COLOR -> {
            val c = value.asColor()
            "${c.x},${c.y},${c.z},${c.w}"
        }

        else -> ""
    }

    private fun parseColor(csv: String): FloatArray {
        val parts = csv.split(",").map { it.trim().toFloatOrNull() ?: 1f }
        return floatArrayOf(
            parts.getOrElse(0) { 1f }, parts.getOrElse(1) { 1f },
            parts.getOrElse(2) { 1f }, parts.getOrElse(3) { 1f }
        )
    }

    private fun parseVec2(csv: String): FloatArray {
        val parts = csv.split(",").map { it.trim().toFloatOrNull() ?: 0f }
        return floatArrayOf(parts.getOrElse(0) { 0f }, parts.getOrElse(1) { 0f })
    }
}

private fun GraphParameter.withDefault(newDefault: Value): GraphParameter =
    GraphParameter(id(), name(), type(), newDefault, range())

private fun GraphParameter.withRange(newRange: Optional<GraphParameter.Range>): GraphParameter =
    GraphParameter(id(), name(), type(), defaultValue(), newRange)
