package org.academy.api.client.gui.serialize

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Categories a property key falls into on a [WidgetNode]. */
fun kindOfKey(key: String): String = when (key) {
    "width_mode", "height_mode", "width", "height", "gravity",
    "margin_left", "margin_top", "margin_right", "margin_bottom",
    "padding_left", "padding_top", "padding_right", "padding_bottom", "weight" -> "layout"

    "visibility", "alpha", "enabled", "clickable", "selected", "cover_all_prev",
    "translation_x", "translation_y", "scale_x", "scale_y", "rotation",
    "origin_x", "origin_y", "tooltip_text" -> "common"

    else -> "props"
}

/** The JSON container a property key lives in (layout/common/props). */
fun WidgetNode.containerFor(key: String): JsonObject = when (kindOfKey(key)) {
    "layout" -> layout
    "common" -> common
    else -> props
}

/** Returns the value stored under [key] in the matching container, or null. */
fun WidgetNode.value(key: String): JsonElement? = containerFor(key).get(key)

/** Writes a typed value into the container that owns [key]. */
fun WidgetNode.setValue(key: String, type: PropType, value: String) {
    val target = containerFor(key)
    when (type) {
        PropType.FLOAT -> target.addProperty(key, value.trim().toFloat())
        PropType.INT -> target.addProperty(key, value.trim().toInt())
        PropType.BOOLEAN -> target.addProperty(key, value.toBoolean())
        PropType.COLOR -> target.addProperty(key, currentArgb(value))
        else -> target.addProperty(key, value)
    }
}

/** Parses `#AARRGGBB` or a raw int literal into an ARGB int. */
fun currentArgb(raw: String): Int {
    val s = raw.trim()
    if (s.startsWith("#")) return s.removePrefix("#").toLongOrNull(16)?.toInt() ?: 0
    return s.toLongOrNull()?.toInt() ?: 0
}

/** Renders a stored JSON value as an editable string (COLOR → `#AARRGGBB`). */
fun JsonElement?.asValueString(type: PropType): String {
    if (this == null) return ""
    if (!isJsonPrimitive) return toString()
    return when (type) {
        PropType.COLOR -> {
            val argb = asString.toLongOrNull()?.toInt()
            argb?.let { "#%08X".format(it) } ?: asString
        }

        else -> asString
    }
}

/**
 * 控件树的中立文档模型, 与运行时控件类解耦. 仅用于序列化/反序列化与编辑器.
 */
class WidgetNode(
    var type: String,
    var name: String,
    var layout: JsonObject = JsonObject(),
    var common: JsonObject = JsonObject(),
    var props: JsonObject = JsonObject(),
    val children: MutableList<WidgetNode> = ArrayList()
) {
    fun toJson(): JsonObject {
        val root = JsonObject()
        root.addProperty("type", type)
        root.addProperty("name", name)
        if (layout.size() > 0) root.add("layout", layout)
        if (common.size() > 0) root.add("common", common)
        if (props.size() > 0) root.add("props", props)
        if (children.isNotEmpty()) {
            val arr = JsonArray()
            for (child in children) arr.add(child.toJson())
            root.add("children", arr)
        }
        return root
    }

    fun findChild(name: String): WidgetNode? {
        if (this.name == name) return this
        for (child in children) {
            child.findChild(name)?.let { return it }
        }
        return null
    }

    companion object {
        fun fromJson(obj: JsonObject): WidgetNode {
            val type = obj.get("type")?.asString ?: error("Missing 'type' in widget node")
            val name = obj.get("name")?.asString ?: ""
            val node = WidgetNode(
                type,
                name,
                obj.getAsJsonObject("layout") ?: JsonObject(),
                obj.getAsJsonObject("common") ?: JsonObject(),
                obj.getAsJsonObject("props") ?: JsonObject()
            )
            obj.getAsJsonArray("children")?.forEach {
                node.children.add(fromJson(it.asJsonObject))
            }
            return node
        }
    }
}
