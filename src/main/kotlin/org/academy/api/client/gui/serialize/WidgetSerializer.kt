package org.academy.api.client.gui.serialize

import com.google.gson.JsonObject
import net.minecraft.resources.Identifier
import org.academy.AcademyCraft
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
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

    const val FORMAT_VERSION = 1

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

    fun decode(json: JsonObject): Widget {
        UiLayoutCodecs.ensureRegistered()
        val rootObj = json.getAsJsonObject("root") ?: json
        val rootNode = WidgetNode.fromJson(rootObj)
        val widget = decodeNode(rootNode)
        applyLayout(widget, rootNode.layout)
        return widget
    }

    private fun decodeNode(node: WidgetNode): Widget {
        val codec = WidgetCodecRegistry.byType<Widget>(node.type)
            ?: throw IllegalArgumentException("Unknown widget type '${node.type}'")
        val widget = codec.create(node.props)
        widget.name = node.name
        applyCommon(widget, node.common)
        codec.decodeProps(widget, node.props)
        if (widget is WidgetContainer) {
            for (childNode in node.children) {
                val child = decodeNode(childNode)
                // 先 addChild 让父容器把 layoutParams 校正为父容器的子类,
                // 再应用 layout (weight 等字段在子类里才存在, 不会被丢弃).
                widget.addChild(childNode.name, child)
                applyLayout(child, childNode.layout)
            }
        }
        return widget
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

    private fun applyCommon(widget: Widget, common: JsonObject) {
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
    }

    // ============ 字符串 / 文件 I/O ============

    fun toPrettyJson(root: WidgetContainer): String = UiJson.GSON.toJson(encode(root))

    fun fromJsonString(json: String): Widget {
        val element = UiJson.GSON.fromJson(json, JsonObject::class.java)
            ?: throw IllegalArgumentException("Invalid layout JSON")
        return decode(element)
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
        return fromJsonString(Files.readString(file))
    }

    /** 从 assets (只读) 加载布局. */
    fun loadLayout(identifier: Identifier): Widget {
        val json = UiEnvironment.get().openResource(identifier.namespace, identifier.path)?.use { stream ->
            UiJson.GSON.fromJson(stream.reader(), JsonObject::class.java)
        } ?: throw IllegalArgumentException("Layout '$identifier' is empty")
        return decode(json)
    }
}
