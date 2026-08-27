package org.academy.desktop.uieditor

import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import org.academy.api.client.gui.editor.UiEditorDocument
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.serialize.*
import org.academy.api.client.gui.widget.Widget

/**
 * 检查器面板：结构节（类型/重命名/添加子级/删除/复制/移动）+ 布局节 + 通用节 + 类型专属属性节。
 * 属性编辑经 [UiEditorDocument] 的 mutation 通道落栈，预览交由宿主刷新。
 */
class WidgetInspectorPanel(private val getDoc: () -> UiEditorDocument) {
    private val doc: UiEditorDocument get() = getDoc()

    private val nameBuf = ImString(64)
    private var nameNode: WidgetNode? = null

    private val addTypeIdx = ImInt(0)

    fun render() {
        val node = doc.selectedNode
        ImGui.text("Inspector")
        ImGui.separator()
        if (node == null) {
            ImGui.textDisabled("Select a widget to edit")
            return
        }
        renderStructure(node)
        ImGui.separator()
        renderLayout(node)
        ImGui.separator()
        renderCommon(node)
        val props = WidgetCodecRegistry.byType<Widget>(node.type)?.propertySchema
        if (props != null && props.isNotEmpty()) {
            ImGui.separator()
            ImGui.text("Props")
            renderProps(props, node)
        }
    }

    // ============ structure ============

    private fun renderStructure(node: WidgetNode) {
        ImGui.text("Type: ${node.type}")
        val locked = doc.structureLocked

        if (node !== nameNode) {
            nameNode = node
            nameBuf.set(node.name)
        }
        if (ImGui.inputText("Name##name", nameBuf)) {
            if (ImGui.isItemDeactivatedAfterEdit()) {
                val t = nameBuf.get().trim()
                if (!locked && t.isNotEmpty() && t != node.name) doc.renameSelected(t)
            }
        }

        val types = WidgetCodecRegistry.types()
        if (types.isEmpty()) return
        addTypeIdx.set(addTypeIdx.get().coerceIn(0, types.size - 1))
        if (ImGui.combo("Add type##addtype", addTypeIdx, types.toTypedArray())) Unit
        ImGui.sameLine()
        if (ImGui.button("Add##addchild") && !locked) {
            if (!doc.addChild(types[addTypeIdx.get()])) ImGui.textColored(1f, 0.45f, 0.45f, 1f, "non-container")
        }

        if (ImGui.button("Delete##del") && !locked) doc.deleteSelected()
        ImGui.sameLine()
        if (ImGui.button("Duplicate##dup") && !locked) doc.duplicateSelected()
        ImGui.sameLine()
        if (ImGui.button("+\u2191##moveup") && !locked) doc.moveSelected(-1)
        ImGui.sameLine()
        if (ImGui.button("\u2193##movedown") && !locked) doc.moveSelected(1)
    }

    // ============ layout ============

    private fun renderLayout(node: WidgetNode) {
        ImGui.text("Layout")
        renderSizeRow(node, "width_mode", "width", "Width")
        renderSizeRow(node, "height_mode", "height", "Height")

        val current = node.value("gravity")?.asValueString(PropType.INT)?.toIntOrNull() ?: Gravity.CENTER
        val name = GRAVITY_NAMES.entries.firstOrNull { it.value == current }?.key ?: "CENTER"
        val idx = ImInt(GRAVITY_NAMES.keys.indexOf(name).coerceAtLeast(0))
        if (ImGui.combo("Gravity##gravity", idx, GRAVITY_NAMES.keys.toTypedArray())) {
            val selectedName = GRAVITY_NAMES.keys.elementAt(idx.get())
            editValue(node, "gravity", PropType.INT, GRAVITY_NAMES.getValue(selectedName).toString())
        }

        renderPropField(node, PropSpec("weight", PropType.FLOAT, -1f, 1024f))
        renderPropField(node, PropSpec("margin_left", PropType.FLOAT, -4096f, 4096f))
        renderPropField(node, PropSpec("margin_top", PropType.FLOAT, -4096f, 4096f))
        renderPropField(node, PropSpec("margin_right", PropType.FLOAT, -4096f, 4096f))
        renderPropField(node, PropSpec("margin_bottom", PropType.FLOAT, -4096f, 4096f))
        renderPropField(node, PropSpec("padding_left", PropType.FLOAT, 0f, 4096f))
        renderPropField(node, PropSpec("padding_top", PropType.FLOAT, 0f, 4096f))
        renderPropField(node, PropSpec("padding_right", PropType.FLOAT, 0f, 4096f))
        renderPropField(node, PropSpec("padding_bottom", PropType.FLOAT, 0f, 4096f))
    }

    private fun renderSizeRow(node: WidgetNode, modeKey: String, valueKey: String, label: String) {
        val modes = listOf("MATCH_PARENT", "WRAP_CONTENT", "FIXED")
        val currentMode = node.value(modeKey)?.asString ?: "WRAP_CONTENT"
        val idx = ImInt(modes.indexOf(currentMode).coerceAtLeast(0))
        if (ImGui.combo("$label mode##$modeKey", idx, modes.toTypedArray())) {
            editValue(node, modeKey, PropType.TEXT, modes[idx.get()])
        }
        val currentValue = node.value(valueKey)?.asValueString(PropType.FLOAT)?.toFloatOrNull() ?: 0f
        val v = floatArrayOf(currentValue)
        if (ImGui.dragFloat("$label##$valueKey", v, 0.5f)) {
            editValue(node, valueKey, PropType.FLOAT, v[0].toString())
        }
    }

    // ============ common ============

    private fun renderCommon(node: WidgetNode) {
        for (spec in COMMON_FIELDS) renderPropField(node, spec)
    }

    // ============ props ============

    private fun renderProps(specs: List<PropSpec>, node: WidgetNode) {
        for (spec in specs) renderPropField(node, spec)
    }

    private fun renderPropField(node: WidgetNode, spec: PropSpec) {
        val id = spec.key
        val label = spec.key
        val current = node.value(id)?.asValueString(spec.type) ?: ""
        when (spec.type) {
            PropType.FLOAT -> {
                val v = floatArrayOf(current.toFloatOrNull() ?: 0f)
                if (ImGui.dragFloat("$label##$id", v, 0.01f, spec.min, spec.max)) {
                    if (v[0].toString() != current) editValue(node, id, PropType.FLOAT, v[0].toString())
                }
            }

            PropType.INT -> {
                val v = intArrayOf(current.toIntOrNull() ?: 0)
                if (ImGui.dragInt("$label##$id", v)) {
                    if (v[0].toString() != current) editValue(node, id, PropType.INT, v[0].toString())
                }
            }

            PropType.BOOLEAN -> {
                val flag = ImBoolean(current.toBoolean())
                if (ImGui.checkbox("$label##$id", flag)) {
                    editValue(node, id, PropType.BOOLEAN, flag.get().toString())
                }
            }

            PropType.COLOR -> {
                val c = parseArgb(current)
                if (ImGui.colorEdit4("$label##$id", c)) {
                    editValue(node, id, PropType.COLOR, argbToHex(c))
                }
            }

            PropType.ENUM -> {
                if (spec.options.isNotEmpty()) {
                    val options = spec.options
                    val idx = ImInt(options.indexOf(current).coerceAtLeast(0))
                    if (ImGui.combo("$label##$id", idx, options.toTypedArray())) {
                        editValue(node, id, PropType.ENUM, options[idx.get()])
                    }
                } else {
                    val buf = ImString(current, 256)
                    if (ImGui.inputText("$label##$id", buf)) editValue(node, id, PropType.TEXT, buf.get())
                }
            }

            else -> {
                val buf = ImString(current, 256)
                if (ImGui.inputText("$label##$id", buf)) editValue(node, id, PropType.TEXT, buf.get())
            }
        }
    }

    // ============ value helpers ============

    private fun editValue(node: WidgetNode, key: String, type: PropType, value: String) {
        val before = node.value(key)?.asValueString(type) ?: ""
        if (before == value) return
        node.setValue(key, type, value)
        doc.mutate { }
    }

    private fun parseArgb(raw: String): FloatArray {
        val s = raw.removePrefix("#")
        val argb = s.toLongOrNull(16)?.toInt() ?: raw.toLongOrNull()?.toInt() ?: 0xFFFFFFFF.toInt()
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return floatArrayOf(r, g, b, a)
    }

    private fun argbToHex(c: FloatArray): String {
        val a = (c[3] * 255f).toInt() and 0xFF
        val r = (c[0] * 255f).toInt() and 0xFF
        val g = (c[1] * 255f).toInt() and 0xFF
        val b = (c[2] * 255f).toInt() and 0xFF
        return "#%08X".format((a shl 24) or (r shl 16) or (g shl 8) or b)
    }

    companion object {
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

        // 重力选项：组合框索引 → 位掩码。
        private val GRAVITY_NAMES = linkedMapOf(
            "TOP_LEFT" to Gravity.TOP_LEFT,
            "TOP" to Gravity.TOP,
            "TOP_RIGHT" to Gravity.TOP_RIGHT,
            "LEFT" to Gravity.LEFT,
            "CENTER" to Gravity.CENTER,
            "RIGHT" to Gravity.RIGHT,
            "BOTTOM_LEFT" to Gravity.BOTTOM_LEFT,
            "BOTTOM" to Gravity.BOTTOM,
            "BOTTOM_RIGHT" to Gravity.BOTTOM_RIGHT,
            "FILL" to Gravity.FILL
        )
    }
}
