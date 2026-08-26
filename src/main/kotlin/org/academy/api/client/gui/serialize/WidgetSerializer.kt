package org.academy.api.client.gui.serialize

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import net.minecraft.resources.Identifier
import org.academy.AcademyCraft
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.state.UiState
import org.academy.api.client.gui.state.bindProgress
import org.academy.api.client.gui.state.bindText
import org.academy.api.client.gui.state.bindVisible
import org.academy.api.client.gui.widget.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * 控件树 <-> JSON 的双向序列化器.
 *
 * 序列化范围: 布局属性 (LayoutParams), 控件通用属性, 类型专属属性, 子控件层级与顺序.
 * 不序列化: Drawable, 回调/行为, 瞬时状态 (measured/坐标/滚动位置/焦点).
 */
object WidgetSerializer {
    private val logger = AcademyCraft.getLogger()

    const val FORMAT_VERSION = 2

    /** 最早仍可解码的历史格式版本. */
    const val MIN_SUPPORTED_VERSION = 1

    // ============ 编码 (控件树 -> JSON) ============

    fun encode(root: WidgetContainer): JsonObject {
        UiLayoutCodecs.ensureRegistered()
        val out = JsonObject()
        out.addProperty("version", FORMAT_VERSION)
        out.add("root", encodeNode(root).toJson())
        return out
    }

    private fun encodeNode(widget: Widget): WidgetNode {
        val codec = WidgetCodecRegistry.forWidget(widget)
        if (codec == null) {
            logger.warn(
                "[UiLayout] No codec registered for widget '{}' of type '{}'; only layout/common will be serialized.",
                widget.name, widget.javaClass.simpleName
            )
        }
        val node = WidgetNode(
            type = codec?.typeName ?: widget.javaClass.simpleName,
            name = widget.name,
            layout = encodeLayout(widget.layoutParams),
            common = encodeCommon(widget),
            props = codec?.encodeProps(widget) ?: JsonObject()
        )
        if (widget is WidgetContainer) {
            for (child in widget.children.values) {
                node.children.add(encodeNode(child))
            }
        }
        return node
    }

    private fun encodeLayout(lp: WidgetContainer.LayoutParams): JsonObject {
        val o = JsonObject()
        o.addProperty("width_mode", lp.widthMode.name)
        o.addProperty("height_mode", lp.heightMode.name)
        o.addProperty("width", lp.width)
        o.addProperty("height", lp.height)
        o.addProperty("gravity", lp.gravity)
        o.addProperty("margin_left", lp.marginLeft)
        o.addProperty("margin_top", lp.marginTop)
        o.addProperty("margin_right", lp.marginRight)
        o.addProperty("margin_bottom", lp.marginBottom)
        o.addProperty("padding_left", lp.paddingLeft)
        o.addProperty("padding_top", lp.paddingTop)
        o.addProperty("padding_right", lp.paddingRight)
        o.addProperty("padding_bottom", lp.paddingBottom)
        if (lp is LinearLayoutWidget.LayoutParams) {
            o.addProperty("weight", lp.weight)
        }
        return o
    }

    private fun encodeCommon(widget: Widget): JsonObject {
        val o = JsonObject()
        o.addProperty("visibility", widget.visibility.name)
        o.addProperty("alpha", widget.alpha)
        o.addProperty("enabled", widget.isEnabled)
        o.addProperty("clickable", widget.isClickable)
        o.addProperty("selected", widget.isSelected)
        o.addProperty("cover_all_prev", widget.coverAllPrev)
        o.addProperty("translation_x", widget.translationX)
        o.addProperty("translation_y", widget.translationY)
        o.addProperty("scale_x", widget.scaleX)
        o.addProperty("scale_y", widget.scaleY)
        o.addProperty("rotation", widget.rotation)
        o.addProperty("origin_x", widget.originX)
        o.addProperty("origin_y", widget.originY)
        widget.tooltipText?.let { o.addProperty("tooltip_text", it.toString()) }
        return o
    }

    // ============ 解码 (JSON -> 控件树) ============

    fun decode(json: JsonObject, bindings: UiBindingContext? = null): Widget {
        return decode(json, bindings, null)
    }

    fun decode(json: JsonObject, bindings: UiBindingContext?, templates: UiTemplateRegistry?): Widget {
        validateVersion(json)
        UiLayoutCodecs.ensureRegistered()
        val rootObj = json.getAsJsonObject("root") ?: json
        val rootNode = WidgetNode.fromJson(rootObj)
        val widget = decodeNode(rootNode, bindings, templates)
        applyLayout(widget, rootNode.layout)
        return widget
    }

    private fun validateVersion(json: JsonObject) {
        val element = json.get("version") ?: return
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            throw IllegalArgumentException("Layout 'version' must be a number")
        }
        val version = element.asInt
        if (version < MIN_SUPPORTED_VERSION || version > FORMAT_VERSION) {
            throw IllegalArgumentException(
                "Unsupported layout format version $version (supported: $MIN_SUPPORTED_VERSION..$FORMAT_VERSION)"
            )
        }
    }

    private fun decodeNode(
        node: WidgetNode,
        bindings: UiBindingContext?,
        templates: UiTemplateRegistry?
    ): Widget {
        // v2 `include`：按模板名展开为实际控件。
        if (node.type == "include") {
            val template = templates?.resolve(node.template ?: "")
                ?: throw IllegalArgumentException("Unknown template '${node.template}'")
            val expanded = template.expand(node.props)
            expanded.name = node.name
            return decodeNode(expanded, bindings, templates)
        }

        val codec = WidgetCodecRegistry.byType<Widget>(node.type)
            ?: throw IllegalArgumentException("Unknown widget type '${node.type}'")
        val props = clampPropsToSchema(codec, node.props)
        val widget = codec.create(props)
        widget.name = node.name
        applyCommon(widget, node.common, bindings)
        codec.decodeProps(widget, props)
        if (widget is WidgetContainer) {
            val repeatCount = resolveRepeatCount(node, bindings)
            if (repeatCount > 0) {
                val item = node.repeatItem
                    ?: throw IllegalArgumentException("repeat without item on '${node.name}'")
                for (i in 0 until repeatCount) {
                    val copy = WidgetNode.fromJson(item.toJson())
                    copy.name = "${item.name}_$i"
                    val child = decodeNode(copy, bindings, templates)
                    widget.addChild(copy.name, child)
                    applyLayout(child, copy.layout)
                }
            }
            for (childNode in node.children) {
                val child = decodeNode(childNode, bindings, templates)
                // 先 addChild 让父容器把 layoutParams 校正为父容器的子类,
                // 再应用 layout (weight 等字段在子类里才存在, 不会被丢弃).
                widget.addChild(childNode.name, child)
                applyLayout(child, childNode.layout)
            }
        }
        return widget
    }

    private fun resolveRepeatCount(node: WidgetNode, bindings: UiBindingContext?): Int {
        node.repeatCount?.let { return it }
        node.repeatSource?.let { return bindings?.resolveRepeatCount(it) ?: 0 }
        return 0
    }

    /**
     * 以 [WidgetCodec.propertySchema] 为唯一事实来源, 将数值属性钳制到声明的 min/max 区间.
     * 仅处理 FLOAT/INT 类型; 其余属性原样透传.
     */
    private fun clampPropsToSchema(codec: WidgetCodec<*>, props: JsonObject): JsonObject {
        if (props.size() == 0 || codec.propertySchema.isEmpty()) return props
        val clamped = JsonObject()
        for ((key, value) in props.entrySet()) {
            val spec = codec.propertySchema.firstOrNull { it.key == key }
            val primitive = (value as? JsonPrimitive)?.takeIf { it.isNumber }
            if (spec != null && primitive != null &&
                (spec.type == PropType.FLOAT || spec.type == PropType.INT)
            ) {
                if (spec.type == PropType.FLOAT) {
                    clamped.addProperty(key, primitive.asFloat.coerceIn(spec.min, spec.max))
                } else {
                    clamped.addProperty(key, primitive.asInt.coerceIn(spec.min.toInt(), spec.max.toInt()))
                }
            } else {
                clamped.add(key, value)
            }
        }
        return clamped
    }

    private fun applyLayout(widget: Widget, layout: JsonObject) {
        // 防止直接修改共享的 LayoutParams.NONE 单例
        val lp = if (widget.layoutParams === WidgetContainer.LayoutParams.NONE)
            WidgetContainer.LayoutParams()
        else
            widget.layoutParams
        layout.optString("width_mode")?.let { lp.widthMode = SizeMode.valueOf(it) }
        layout.optString("height_mode")?.let { lp.heightMode = SizeMode.valueOf(it) }
        layout.optFloat("width")?.let { lp.width = it }
        layout.optFloat("height")?.let { lp.height = it }
        layout.optInt("gravity")?.let { lp.gravity = it }
        layout.optFloat("margin_left")?.let { lp.marginLeft = it }
        layout.optFloat("margin_top")?.let { lp.marginTop = it }
        layout.optFloat("margin_right")?.let { lp.marginRight = it }
        layout.optFloat("margin_bottom")?.let { lp.marginBottom = it }
        layout.optFloat("padding_left")?.let { lp.paddingLeft = it }
        layout.optFloat("padding_top")?.let { lp.paddingTop = it }
        layout.optFloat("padding_right")?.let { lp.paddingRight = it }
        layout.optFloat("padding_bottom")?.let { lp.paddingBottom = it }
        if (lp is LinearLayoutWidget.LayoutParams) {
            layout.optFloat("weight")?.let { lp.weight = it }
        }
        widget.layoutParams = lp
    }

    private fun applyCommon(widget: Widget, common: JsonObject, bindings: UiBindingContext? = null) {
        common.optString("visibility")?.let { widget.visibility = Widget.Visibility.valueOf(it) }
        common.optFloat("alpha")?.let { widget.alpha = it }
        common.optBoolean("enabled")?.let { widget.isEnabled = it }
        common.optBoolean("clickable")?.let { widget.isClickable = it }
        common.optBoolean("selected")?.let { widget.isSelected = it }
        common.optBoolean("cover_all_prev")?.let { widget.coverAllPrev = it }
        common.optFloat("translation_x")?.let { widget.translationX = it }
        common.optFloat("translation_y")?.let { widget.translationY = it }
        common.optFloat("scale_x")?.let { widget.scaleX = it }
        common.optFloat("scale_y")?.let { widget.scaleY = it }
        common.optFloat("rotation")?.let { widget.rotation = it }
        common.optFloat("origin_x")?.let { widget.originX = it }
        common.optFloat("origin_y")?.let { widget.originY = it }
        common.optString("tooltip_text")?.let { widget.tooltipText = it }
        bindings ?: return
        common.optString("bind_text")?.let { ref ->
            bindings.resolveBinding(ref)?.let { state ->
                @Suppress("UNCHECKED_CAST")
                (widget as? LabelWidget)?.bindText(state as UiState<String>)
            }
        }
        common.optString("visible_when")?.let { ref ->
            bindings.resolveBinding(ref)?.let { state ->
                @Suppress("UNCHECKED_CAST")
                widget.bindVisible(state as UiState<Boolean>)
            }
        }
        common.optString("progress_when")?.let { ref ->
            bindings.resolveBinding(ref)?.let { state ->
                @Suppress("UNCHECKED_CAST")
                (widget as? ProgressBarWidget)?.bindProgress(state as UiState<Float>)
            }
        }
    }

    // ============ 字符串 / 文件 I/O ============

    fun toPrettyJson(root: WidgetContainer): String = UiJson.GSON.toJson(encode(root))

    fun fromJsonString(json: String): Widget {
        return fromJsonString(json, null, null)
    }

    fun fromJsonString(json: String, bindings: UiBindingContext?): Widget {
        return fromJsonString(json, bindings, null)
    }

    fun fromJsonString(json: String, bindings: UiBindingContext?, templates: UiTemplateRegistry?): Widget {
        val element = UiJson.GSON.fromJson(json, JsonObject::class.java)
            ?: throw IllegalArgumentException("Invalid layout JSON")
        return decode(element, bindings, templates)
    }

    /** 可写布局目录: <gameDir>/academy/ui */
    fun layoutDir(): Path {
        return UiEnvironment.get().layoutDir()
    }

    fun export(root: WidgetContainer, file: Path) {
        Files.createDirectories(file.parent)
        Files.writeString(file, toPrettyJson(root))
    }

    fun import(file: Path): Widget {
        return import(file, null)
    }

    fun import(file: Path, bindings: UiBindingContext?): Widget {
        return fromJsonString(Files.readString(file), bindings)
    }

    /** 从 assets (只读) 加载布局. */
    fun loadLayout(identifier: Identifier): Widget {
        return loadLayout(identifier, null)
    }

    /** 从 assets (只读) 加载布局，并可传入绑定上下文解析 `$path`。 */
    fun loadLayout(identifier: Identifier, bindings: UiBindingContext?): Widget {
        val json = UiEnvironment.get().openResource(identifier.namespace, identifier.path)?.use { stream ->
            UiJson.GSON.fromJson(stream.reader(), JsonObject::class.java)
        } ?: throw IllegalArgumentException("Layout '$identifier' is empty")
        return decode(json, bindings)
    }
}
