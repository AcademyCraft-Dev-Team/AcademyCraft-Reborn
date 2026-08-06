package org.academy.api.client.gui.editor

import com.google.gson.JsonObject
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.serialize.PropSpec
import org.academy.api.client.gui.serialize.PropType
import org.academy.api.client.gui.serialize.UiJson
import org.academy.api.client.gui.serialize.UiLayoutCodecs
import org.academy.api.client.gui.serialize.WidgetCodecRegistry
import org.academy.api.client.gui.serialize.WidgetNode
import org.academy.api.client.gui.serialize.WidgetSerializer
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.internal.client.gui.debug.UiDebugSession
import java.nio.file.Files

/**
 * UI 布局编辑器 - ImGui 编辑窗口 (独立于 ImGuiUIDebugger). 负责控件的增删改查:
 * 结构树, 属性编辑, 文件导入导出, JSON 文本. 只在 [UiLayoutEditorScreen] 打开时渲染.
 */
object UiLayoutImGuiEditor {
    private val SIZE_MODE_NAMES = arrayOf("FIXED", "MATCH_PARENT", "WRAP_CONTENT")
    private val VISIBILITY_NAMES = arrayOf("VISIBLE", "INVISIBLE", "GONE")
    private val FIELD_KEYS = mapOf(
        "file name" to "screen.academy.ui_debug.editor.field.file_name",
        "name" to "screen.academy.ui_debug.editor.field.name",
        "type" to "screen.academy.ui_debug.editor.field.type",
        "width_mode" to "screen.academy.ui_debug.editor.field.width_mode",
        "height_mode" to "screen.academy.ui_debug.editor.field.height_mode",
        "width" to "screen.academy.ui_debug.editor.field.width",
        "height" to "screen.academy.ui_debug.editor.field.height",
        "margin_left" to "screen.academy.ui_debug.editor.field.margin_left",
        "margin_top" to "screen.academy.ui_debug.editor.field.margin_top",
        "margin_right" to "screen.academy.ui_debug.editor.field.margin_right",
        "margin_bottom" to "screen.academy.ui_debug.editor.field.margin_bottom",
        "padding_left" to "screen.academy.ui_debug.editor.field.padding_left",
        "padding_top" to "screen.academy.ui_debug.editor.field.padding_top",
        "padding_right" to "screen.academy.ui_debug.editor.field.padding_right",
        "padding_bottom" to "screen.academy.ui_debug.editor.field.padding_bottom",
        "weight" to "screen.academy.ui_debug.editor.field.weight",
        "visibility" to "screen.academy.ui_debug.editor.field.visibility",
        "enabled" to "screen.academy.ui_debug.editor.field.enabled",
        "clickable" to "screen.academy.ui_debug.editor.field.clickable",
        "selected" to "screen.academy.ui_debug.editor.field.selected",
        "cover_all_prev" to "screen.academy.ui_debug.editor.field.cover_all_prev",
        "alpha" to "screen.academy.ui_debug.editor.field.alpha",
        "translation_x" to "screen.academy.ui_debug.editor.field.translation_x",
        "translation_y" to "screen.academy.ui_debug.editor.field.translation_y",
        "scale_x" to "screen.academy.ui_debug.editor.field.scale_x",
        "scale_y" to "screen.academy.ui_debug.editor.field.scale_y",
        "rotation" to "screen.academy.ui_debug.editor.field.rotation",
        "origin_x" to "screen.academy.ui_debug.editor.field.origin_x",
        "origin_y" to "screen.academy.ui_debug.editor.field.origin_y",
        "tooltip_text" to "screen.academy.ui_debug.editor.field.tooltip",
        "orientation" to "screen.academy.ui_debug.editor.field.orientation",
        "spacing" to "screen.academy.ui_debug.editor.field.spacing",
        "gravity" to "screen.academy.ui_debug.editor.field.gravity",
        "weight_sum" to "screen.academy.ui_debug.editor.field.weight_sum",
        "measure_all_children" to "screen.academy.ui_debug.editor.field.measure_all_children",
        "scroll_speed" to "screen.academy.ui_debug.editor.field.scroll_speed",
        "visible_item_count" to "screen.academy.ui_debug.editor.field.visible_item_count",
        "item_space" to "screen.academy.ui_debug.editor.field.item_space",
        "cyclic" to "screen.academy.ui_debug.editor.field.cyclic",
        "curtain" to "screen.academy.ui_debug.editor.field.curtain",
        "curtain_color" to "screen.academy.ui_debug.editor.field.curtain_color",
        "indicator" to "screen.academy.ui_debug.editor.field.indicator",
        "indicator_color" to "screen.academy.ui_debug.editor.field.indicator_color",
        "indicator_size" to "screen.academy.ui_debug.editor.field.indicator_size",
        "item_align" to "screen.academy.ui_debug.editor.field.item_align",
        "atmospheric" to "screen.academy.ui_debug.editor.field.atmospheric",
        "selected_scale_enabled" to "screen.academy.ui_debug.editor.field.selected_scale_enabled",
        "allow_reselect" to "screen.academy.ui_debug.editor.field.allow_reselect",
        "min" to "screen.academy.ui_debug.editor.field.min",
        "max" to "screen.academy.ui_debug.editor.field.max",
        "progress" to "screen.academy.ui_debug.editor.field.progress",
        "background_color" to "screen.academy.ui_debug.editor.field.background_color",
        "progress_color" to "screen.academy.ui_debug.editor.field.progress_color",
        "key_progress_increment" to "screen.academy.ui_debug.editor.field.key_progress_increment",
        "checked" to "screen.academy.ui_debug.editor.field.checked",
        "track_color" to "screen.academy.ui_debug.editor.field.track_color",
        "checked_track_color" to "screen.academy.ui_debug.editor.field.checked_track_color",
        "thumb_color" to "screen.academy.ui_debug.editor.field.thumb_color",
        "checked_thumb_color" to "screen.academy.ui_debug.editor.field.checked_thumb_color",
        "id" to "screen.academy.ui_debug.editor.field.id",
        "show_background" to "screen.academy.ui_debug.editor.field.show_background",
        "texture" to "screen.academy.ui_debug.editor.field.texture",
        "sheet_width" to "screen.academy.ui_debug.editor.field.sheet_width",
        "sheet_height" to "screen.academy.ui_debug.editor.field.sheet_height",
        "frame_width" to "screen.academy.ui_debug.editor.field.frame_width",
        "frame_height" to "screen.academy.ui_debug.editor.field.frame_height",
        "frame_count" to "screen.academy.ui_debug.editor.field.frame_count",
        "frame_index" to "screen.academy.ui_debug.editor.field.frame_index",
        "draw_line" to "screen.academy.ui_debug.editor.field.draw_line",
        "red" to "screen.academy.ui_debug.editor.field.red",
        "green" to "screen.academy.ui_debug.editor.field.green",
        "blue" to "screen.academy.ui_debug.editor.field.blue",
        "text" to "screen.academy.ui_debug.editor.field.text",
        "base_font_size" to "screen.academy.ui_debug.editor.field.base_font_size",
        "max_length" to "screen.academy.ui_debug.editor.field.max_length",
        "allow_line_break" to "screen.academy.ui_debug.editor.field.allow_line_break",
        "color" to "screen.academy.ui_debug.editor.field.color"
    )

    fun renderContent(screen: Screen) {
        val editor = screen as? UiLayoutEditorScreen ?: return
        UiLayoutCodecs.ensureRegistered()

        if (!ImGui.begin(tr("screen.academy.ui_debug.editor.title") + "##academy_layout_editor")) {
            ImGui.end()
            return
        }
        ImGui.setWindowSize(520f, 780f, ImGuiCond.FirstUseEver)
        renderToolbar(editor)
        ImGui.separator()
        renderTree(editor)
        ImGui.separator()
        renderProperties(editor)
        ImGui.separator()
        renderJsonSection(editor)
        ImGui.end()
    }

    // ============ 工具栏 / 文件 ============

    private fun renderToolbar(editor: UiLayoutEditorScreen) {
        if (editor.debugLayoutId != null) {
            ImGui.text(tr("screen.academy.ui_debug.editor.layout", editor.debugLayoutId))
            val state = UiDebugSession.status(editor.debugLayoutId)
            ImGui.sameLine()
            ImGui.textColored(
                if (state.dirty) 1f else 0.4f,
                if (state.dirty) 0.75f else 1f,
                0.4f,
                1f,
                tr(if (state.dirty) "screen.academy.ui_debug.status.modified" else "screen.academy.ui_debug.status.clean")
            )
            if (ImGui.button(tr("screen.academy.ui_debug.action.publish"))) UiDebugSession.publish()
            ImGui.sameLine()
            if (ImGui.button(tr("screen.academy.ui_debug.action.revert"))) editor.revertDebugDocument()
            ImGui.sameLine()
            if (ImGui.button(tr("screen.academy.ui_debug.action.reload"))) editor.reloadDebugDocument()
            ImGui.sameLine()
            val attached = UiDebugSession.attachedLayoutId == editor.debugLayoutId
            if (ImGui.button(tr(if (attached) {
                    "screen.academy.ui_debug.action.detach_live"
                } else {
                    "screen.academy.ui_debug.action.attach_live"
                }))) {
                UiDebugSession.attach(if (attached) null else editor.debugLayoutId)
            }
            editor.validationError?.let {
                ImGui.textColored(1f, 0.3f, 0.3f, 1f, it)
            }
            return
        }
        if (ImGui.button(tr("screen.academy.ui_debug.action.new"))) {
            editor.setDoc(WidgetNode("frame_layout", "root"))
        }
        ImGui.sameLine()
        if (ImGui.button(tr("screen.academy.ui_debug.action.save"))) saveDoc(editor)
        ImGui.sameLine()
        if (ImGui.button(tr("screen.academy.ui_debug.action.load"))) loadDoc(editor)

        val nameBuf = ImString(editor.fileName, 64)
        if (ImGui.inputText(localizedLabel("file name"), nameBuf)) editor.fileName = nameBuf.get()
        ImGui.textDisabled(tr("screen.academy.ui_debug.editor.layouts_dir", WidgetSerializer.layoutDir()))
    }

    private fun saveDoc(editor: UiLayoutEditorScreen) {
        try {
            val name = editor.fileName.trim().ifEmpty { "layout" }
            editor.fileName = name
            val file = WidgetSerializer.layoutDir().resolve("$name.json")
            Files.createDirectories(file.parent)
            Files.writeString(file, UiJson.GSON.toJson(editor.documentJson()))
        } catch (e: Exception) {
            ImGui.textColored(1f, 0.3f, 0.3f, 1f,
                tr("screen.academy.ui_debug.editor.save_failed", e.message ?: ""))
        }
    }

    private fun loadDoc(editor: UiLayoutEditorScreen) {
        try {
            val name = editor.fileName.trim().ifEmpty { "layout" }
            editor.fileName = name
            val file = WidgetSerializer.layoutDir().resolve("$name.json")
            val parsed = UiJson.GSON.fromJson(Files.readString(file), JsonObject::class.java)
                ?: return
            editor.setDoc(WidgetNode.fromJson(parsed.getAsJsonObject("root") ?: parsed))
        } catch (e: Exception) {
            ImGui.textColored(1f, 0.3f, 0.3f, 1f,
                tr("screen.academy.ui_debug.editor.load_failed", e.message ?: ""))
        }
    }

    // ============ 结构树 + 增删改查 ============

    private fun renderTree(editor: UiLayoutEditorScreen) {
        val doc = editor.readDoc()
        val selPath = editor.currentPath()

        if (ImGui.beginChild("tree", 0f, 300f)) {
            renderNode(editor, doc, emptyList(), selPath)
        }
        ImGui.endChild()

        if (editor.structureLocked) {
            ImGui.textDisabled(tr("screen.academy.ui_debug.editor.structure_locked"))
            return
        }
        ImGui.separator()
        if (ImGui.button(tr("screen.academy.ui_debug.action.add_child"))) addChild(editor)
        ImGui.sameLine()
        if (ImGui.button(tr("screen.academy.ui_debug.action.delete"))) deleteNode(editor)
        ImGui.sameLine()
        if (ImGui.button(tr("screen.academy.ui_debug.action.duplicate"))) duplicateNode(editor)
        ImGui.sameLine()
        if (ImGui.button(tr("screen.academy.ui_debug.action.move_up"))) reorderNode(editor, -1)
        ImGui.sameLine()
        if (ImGui.button(tr("screen.academy.ui_debug.action.move_down"))) reorderNode(editor, +1)
    }

    private fun renderNode(editor: UiLayoutEditorScreen, node: WidgetNode, path: List<String>, selPath: List<String>) {
        val isSel = path == selPath
        val id = path.joinToString("/").ifEmpty { "/" }
        val label = "${node.type}  ${node.name.ifEmpty { "<unnamed>" }}##$id"
        if (ImGui.selectable(label, isSel)) {
            editor.setSelection(path)
        }
        if (node.children.isNotEmpty()) {
            ImGui.indent()
            for (child in node.children) {
                renderNode(editor, child, path + listOf(child.name), selPath)
            }
            ImGui.unindent()
        }
    }

    private fun findNodeByPath(node: WidgetNode, path: List<String>): WidgetNode? {
        if (path.isEmpty()) return node
        val child = node.children.firstOrNull { it.name == path[0] } ?: return null
        return findNodeByPath(child, path.drop(1))
    }

    private fun uniqueName(parent: WidgetNode, base: String): String {
        var name = base
        var i = 1
        val used = parent.children.map { it.name }.toSet()
        while (name in used) {
            name = "${base}_$i"
            i++
        }
        return name
    }

    private fun addChild(editor: UiLayoutEditorScreen) {
        val path = editor.currentPath()
        var newName = "child"
        var targetPath = path
        editor.mutateDoc { doc ->
            var target = if (path.isEmpty()) doc else findNodeByPath(doc, path) ?: doc
            val codec = WidgetCodecRegistry.byType<Widget>(target.type)
            val isContainer = codec != null && WidgetContainer::class.java.isAssignableFrom(codec.widgetClass)
            if (!isContainer) {
                // 非容器类型无法承载子控件, 改为加到其父节点
                targetPath = path.dropLast(1)
                target = findNodeByPath(doc, targetPath) ?: doc
            }
            newName = uniqueName(target, "child")
            target.children.add(WidgetNode("label", newName))
        }
        editor.setSelection(targetPath + listOf(newName))
    }

    private fun deleteNode(editor: UiLayoutEditorScreen) {
        val path = editor.currentPath()
        if (path.isEmpty()) return
        editor.mutateDoc { doc ->
            val parent = findNodeByPath(doc, path.dropLast(1)) ?: return@mutateDoc
            parent.children.removeAll { it.name == path.last() }
        }
        editor.setSelection(emptyList())
    }

    private fun duplicateNode(editor: UiLayoutEditorScreen) {
        val path = editor.currentPath()
        if (path.isEmpty()) return
        editor.mutateDoc { doc ->
            val parent = findNodeByPath(doc, path.dropLast(1)) ?: return@mutateDoc
            val idx = parent.children.indexOfFirst { it.name == path.last() }
            if (idx < 0) return@mutateDoc
            val copy = WidgetNode.fromJson(parent.children[idx].toJson())
            copy.name = uniqueName(parent, path.last())
            parent.children.add(idx + 1, copy)
        }
    }

    private fun reorderNode(editor: UiLayoutEditorScreen, delta: Int) {
        val path = editor.currentPath()
        if (path.isEmpty()) return
        editor.mutateDoc { doc ->
            val parent = findNodeByPath(doc, path.dropLast(1)) ?: return@mutateDoc
            val idx = parent.children.indexOfFirst { it.name == path.last() }
            val newIdx = idx + delta
            if (idx < 0 || newIdx !in parent.children.indices) return@mutateDoc
            val node = parent.children.removeAt(idx)
            parent.children.add(newIdx, node)
        }
    }

    // ============ 属性面板 ============

    private fun renderProperties(editor: UiLayoutEditorScreen) {
        val node = editor.currentNode() ?: run {
            ImGui.text(tr("screen.academy.ui_debug.editor.no_selection"))
            return
        }

        if (editor.structureLocked) {
            ImGui.textDisabled(tr("screen.academy.ui_debug.editor.node_name", node.name))
            ImGui.textDisabled(tr("screen.academy.ui_debug.editor.node_type", node.type))
        } else {
            val nameBuf = ImString(node.name, 64)
            if (ImGui.inputText(localizedLabel("name"), nameBuf)) editor.renameSelectedNode(nameBuf.get())

            val types = WidgetCodecRegistry.types().toTypedArray()
            val typeIdx = ImInt(types.indexOf(node.type).coerceAtLeast(0))
            if (ImGui.combo(localizedLabel("type"), typeIdx, types)) {
                changeType(editor, types[typeIdx.get()])
            }
        }

        if (ImGui.collapsingHeader(tr("screen.academy.ui_debug.editor.section.layout"))) {
            val layout = node.layout
            enumField(editor, "width_mode##layout", layout, "width_mode", SIZE_MODE_NAMES)
            enumField(editor, "height_mode##layout", layout, "height_mode", SIZE_MODE_NAMES)
            floatField(editor, "width##layout", layout, "width", -1.0E6f, 1.0E6f)
            floatField(editor, "height##layout", layout, "height", -1.0E6f, 1.0E6f)
            gravityField(editor, layout)
            floatField(editor, "margin_left##layout", layout, "margin_left", -1.0E6f, 1.0E6f)
            floatField(editor, "margin_top##layout", layout, "margin_top", -1.0E6f, 1.0E6f)
            floatField(editor, "margin_right##layout", layout, "margin_right", -1.0E6f, 1.0E6f)
            floatField(editor, "margin_bottom##layout", layout, "margin_bottom", -1.0E6f, 1.0E6f)
            floatField(editor, "padding_left##layout", layout, "padding_left", -1.0E6f, 1.0E6f)
            floatField(editor, "padding_top##layout", layout, "padding_top", -1.0E6f, 1.0E6f)
            floatField(editor, "padding_right##layout", layout, "padding_right", -1.0E6f, 1.0E6f)
            floatField(editor, "padding_bottom##layout", layout, "padding_bottom", -1.0E6f, 1.0E6f)
            floatField(editor, "weight##layout", layout, "weight", -1f, 1.0E6f)
        }

        if (ImGui.collapsingHeader(tr("screen.academy.ui_debug.editor.section.common"))) {
            val common = node.common
            enumField(editor, "visibility##common", common, "visibility", VISIBILITY_NAMES)
            boolField(editor, "enabled##common", common, "enabled")
            boolField(editor, "clickable##common", common, "clickable")
            boolField(editor, "selected##common", common, "selected")
            boolField(editor, "cover_all_prev##common", common, "cover_all_prev")
            floatField(editor, "alpha##common", common, "alpha", 0f, 1f)
            floatField(editor, "translation_x##common", common, "translation_x", -1.0E6f, 1.0E6f)
            floatField(editor, "translation_y##common", common, "translation_y", -1.0E6f, 1.0E6f)
            floatField(editor, "scale_x##common", common, "scale_x", 0f, 1.0E3f)
            floatField(editor, "scale_y##common", common, "scale_y", 0f, 1.0E3f)
            floatField(editor, "rotation##common", common, "rotation", -360f, 360f)
            floatField(editor, "origin_x##common", common, "origin_x", 0f, 1f)
            floatField(editor, "origin_y##common", common, "origin_y", 0f, 1f)
            textField(editor, "tooltip_text##common", common, "tooltip_text")
        }

        if (ImGui.collapsingHeader(tr("screen.academy.ui_debug.editor.section.properties"))) {
            val codec = WidgetCodecRegistry.byType<Widget>(node.type)
            if (codec != null) {
                for (spec in codec.propertySchema) {
                    renderPropField(editor, spec, node.props)
                }
            } else {
                ImGui.textDisabled(tr("screen.academy.ui_debug.editor.no_codec"))
            }
        }
    }

    private fun changeType(editor: UiLayoutEditorScreen, newType: String) {
        val path = editor.currentPath()
        if (path.isEmpty()) return
        editor.mutateDoc { doc ->
            val sel = findNodeByPath(doc, path) ?: return@mutateDoc
            sel.type = newType
            sel.props = JsonObject()
        }
    }

    private fun renderPropField(editor: UiLayoutEditorScreen, spec: PropSpec, props: JsonObject) {
        val label = "${spec.key}##props"
        when (spec.type) {
            PropType.TEXT -> textField(editor, label, props, spec.key)
            PropType.IDENTIFIER -> textField(editor, label, props, spec.key)
            PropType.FLOAT -> floatField(editor, label, props, spec.key, spec.min, spec.max)
            PropType.INT -> intField(editor, label, props, spec.key, spec.min.toInt(), spec.max.toInt())
            PropType.BOOLEAN -> boolField(editor, label, props, spec.key)
            PropType.COLOR -> colorField(editor, label, props, spec.key)
            PropType.ENUM -> enumField(editor, label, props, spec.key, spec.options.toTypedArray())
        }
    }

    // ============ 通用字段编辑 ============

    private fun axisRadio(gravity: ImInt, mask: Int, label: String, value: Int): Boolean {
        if (ImGui.radioButton(label, (gravity.get() and mask) == value)) {
            gravity.set((gravity.get() and mask.inv()) or value)
            return true
        }
        return false
    }

    /** 分轴 (水平/垂直) 单选按钮编辑 gravity 位标志. */
    private fun gravityField(editor: UiLayoutEditorScreen, obj: JsonObject) {
        val old = obj.get("gravity")?.asInt ?: Gravity.TOP_LEFT
        val gravity = ImInt(old)
        var changed = false

        ImGui.text(tr("screen.academy.ui_debug.editor.gravity.horizontal"))
        ImGui.sameLine()
        changed = axisRadio(gravity, Gravity.HORIZONTAL_GRAVITY_MASK,
            tr("screen.academy.ui_debug.direction.left") + "##gh", Gravity.LEFT) || changed
        ImGui.sameLine()
        changed = axisRadio(gravity, Gravity.HORIZONTAL_GRAVITY_MASK,
            tr("screen.academy.ui_debug.direction.center") + "##gh", Gravity.CENTER_HORIZONTAL) || changed
        ImGui.sameLine()
        changed = axisRadio(gravity, Gravity.HORIZONTAL_GRAVITY_MASK,
            tr("screen.academy.ui_debug.direction.right") + "##gh", Gravity.RIGHT) || changed
        ImGui.sameLine()
        changed = axisRadio(gravity, Gravity.HORIZONTAL_GRAVITY_MASK,
            tr("screen.academy.ui_debug.direction.fill") + "##gh", Gravity.FILL_HORIZONTAL) || changed

        ImGui.text(tr("screen.academy.ui_debug.editor.gravity.vertical"))
        ImGui.sameLine()
        changed = axisRadio(gravity, Gravity.VERTICAL_GRAVITY_MASK,
            tr("screen.academy.ui_debug.direction.top") + "##gv", Gravity.TOP) || changed
        ImGui.sameLine()
        changed = axisRadio(gravity, Gravity.VERTICAL_GRAVITY_MASK,
            tr("screen.academy.ui_debug.direction.center") + "##gv", Gravity.CENTER_VERTICAL) || changed
        ImGui.sameLine()
        changed = axisRadio(gravity, Gravity.VERTICAL_GRAVITY_MASK,
            tr("screen.academy.ui_debug.direction.bottom") + "##gv", Gravity.BOTTOM) || changed
        ImGui.sameLine()
        changed = axisRadio(gravity, Gravity.VERTICAL_GRAVITY_MASK,
            tr("screen.academy.ui_debug.direction.fill") + "##gv", Gravity.FILL_VERTICAL) || changed

        if (changed) {
            obj.addProperty("gravity", gravity.get())
            editor.notifyChanged()
        }
    }

    private fun enumField(
        editor: UiLayoutEditorScreen,
        label: String,
        obj: JsonObject,
        key: String,
        options: Array<String>
    ) {
        if (options.isEmpty()) return
        var idx = obj.get(key)?.asString?.let { options.indexOf(it) } ?: -1
        if (idx < 0) idx = 0
        val ref = ImInt(idx)
        if (ImGui.combo(localizedLabel(label), ref, localizedOptions(options))) {
            obj.addProperty(key, options[ref.get()])
            editor.notifyChanged()
        }
    }

    private fun floatField(
        editor: UiLayoutEditorScreen,
        label: String,
        obj: JsonObject,
        key: String,
        min: Float,
        max: Float
    ) {
        val v = obj.get(key)?.asFloat ?: 0f
        val ref = floatArrayOf(v)
        if (ImGui.dragFloat(localizedLabel(label), ref, 0.1f, min, max)) {
            obj.addProperty(key, ref[0])
            editor.notifyChanged()
        }
    }

    private fun intField(
        editor: UiLayoutEditorScreen,
        label: String,
        obj: JsonObject,
        key: String,
        min: Int,
        max: Int
    ) {
        val v = obj.get(key)?.asInt ?: 0
        val ref = intArrayOf(v)
        if (ImGui.dragInt(localizedLabel(label), ref, 1f, min, max)) {
            obj.addProperty(key, ref[0])
            editor.notifyChanged()
        }
    }

    /** 以 ARGB 十六进制字符串编辑颜色. */
    private fun colorField(editor: UiLayoutEditorScreen, label: String, obj: JsonObject, key: String) {
        val v = obj.get(key)?.asInt ?: 0xFFFFFFFF.toInt()
        val ref = ImString(String.format("%08X", v), 16)
        if (ImGui.inputText(localizedLabel(label), ref)) {
            val parsed = runCatching { java.lang.Long.parseLong(ref.get().removePrefix("0x"), 16).toInt() }.getOrNull()
            if (parsed != null) {
                obj.addProperty(key, parsed)
                editor.notifyChanged()
            }
        }
    }

    private fun boolField(editor: UiLayoutEditorScreen, label: String, obj: JsonObject, key: String) {
        val b = obj.get(key)?.asBoolean ?: false
        val ref = ImBoolean(b)
        if (ImGui.checkbox(localizedLabel(label), ref)) {
            obj.addProperty(key, ref.get())
            editor.notifyChanged()
        }
    }

    private fun textField(editor: UiLayoutEditorScreen, label: String, obj: JsonObject, key: String) {
        val v = obj.get(key)?.asString ?: ""
        val ref = ImString(v, 1024)
        if (ImGui.inputText(localizedLabel(label), ref)) {
            obj.addProperty(key, ref.get())
            editor.notifyChanged()
        }
    }

    // ============ JSON 文本 ============

    private fun renderJsonSection(editor: UiLayoutEditorScreen) {
        if (editor.structureLocked) return
        if (!ImGui.collapsingHeader(tr("screen.academy.ui_debug.editor.section.json"))) return

        if (ImGui.button(tr("screen.academy.ui_debug.editor.load_json_text"))) {
            editor.jsonText = UiJson.GSON.toJson(editor.documentJson())
        }
        ImGui.sameLine()
        if (ImGui.button(tr("screen.academy.ui_debug.editor.apply_json"))) {
            try {
                val parsed = UiJson.GSON.fromJson(editor.jsonText, JsonObject::class.java)
                    ?: throw IllegalArgumentException("empty json")
                editor.setDoc(WidgetNode.fromJson(parsed.getAsJsonObject("root") ?: parsed))
            } catch (e: Exception) {
                ImGui.textColored(1f, 0.3f, 0.3f, 1f,
                    tr("screen.academy.ui_debug.editor.json_error", e.message ?: ""))
            }
        }
        val text = ImString(editor.jsonText, 131072)
        if (ImGui.inputTextMultiline("##json", text, 0f, 220f)) {
            editor.jsonText = text.get()
        }
    }

    private fun localizedLabel(label: String): String {
        val split = label.split("##", limit = 2)
        val visible = FIELD_KEYS[split[0]]?.let { tr(it) } ?: split[0]
        return if (split.size == 2) "$visible##${split[1]}" else visible
    }

    private fun localizedOptions(options: Array<String>): Array<String> = when {
        options.contentEquals(SIZE_MODE_NAMES) -> arrayOf(
            tr("screen.academy.ui_debug.size_mode.fixed"),
            tr("screen.academy.ui_debug.size_mode.match_parent"),
            tr("screen.academy.ui_debug.size_mode.wrap_content")
        )
        options.contentEquals(VISIBILITY_NAMES) -> arrayOf(
            tr("screen.academy.ui_debug.visibility.visible"),
            tr("screen.academy.ui_debug.visibility.invisible"),
            tr("screen.academy.ui_debug.visibility.gone")
        )
        options.contentEquals(arrayOf("HORIZONTAL", "VERTICAL")) -> arrayOf(
            tr("screen.academy.ui_debug.orientation.horizontal"),
            tr("screen.academy.ui_debug.orientation.vertical")
        )
        options.contentEquals(arrayOf("CENTER", "LEFT", "RIGHT")) -> arrayOf(
            tr("screen.academy.ui_debug.direction.center"),
            tr("screen.academy.ui_debug.direction.left"),
            tr("screen.academy.ui_debug.direction.right")
        )
        else -> options
    }

    private fun tr(key: String, vararg args: Any): String = Component.translatable(key, *args).string
}
