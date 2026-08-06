package org.academy.api.client.gui.serialize

import com.google.gson.JsonArray
import com.google.gson.JsonObject

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
