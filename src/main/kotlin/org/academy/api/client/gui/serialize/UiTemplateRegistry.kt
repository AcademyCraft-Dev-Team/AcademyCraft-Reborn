package org.academy.api.client.gui.serialize

import com.google.gson.JsonObject

/**
 * 模板展开函数: 接收 include 的 [params] 并返回展开后的 [WidgetNode] 子树.
 * 模板作者在函数内自行处理参数 (或经 [UiTemplateRegistry] 的 `$param` 替换).
 */
fun interface UiTemplate {
    fun expand(params: JsonObject): WidgetNode
}

/**
 * 布局模板注册表 (v2 `include`/`repeat` 的支撑) 喵.
 *
 * - 函数式模板: `registry.register("skill_row") { params -> buildNode(params) }`
 * - JSON 模板: `registry.register("skill_row", rawNodeJson)`, 展开时把 `$param.key`
 *   替换为 params 中的字符串值.
 */
class UiTemplateRegistry {
    private val templates: MutableMap<String, UiTemplate> = LinkedHashMap()

    fun register(name: String, template: UiTemplate): UiTemplateRegistry {
        templates[name] = template
        return this
    }

    fun register(name: String, templateNode: WidgetNode): UiTemplateRegistry {
        templates[name] = UiTemplate { params -> substituteParams(cloneNode(templateNode), params) }
        return this
    }

    fun resolve(name: String): UiTemplate? = templates[name]

    fun names(): Set<String> = templates.keys

    fun isEmpty(): Boolean = templates.isEmpty()

    /** 递归克隆节点树 (注册原始节点时避免共享引用). */
    private fun cloneNode(node: WidgetNode): WidgetNode {
        val copy = WidgetNode(
            node.type, node.name,
            node.layout.deepCopy(),
            node.common.deepCopy(),
            node.props.deepCopy()
        )
        node.children.forEach { copy.children.add(cloneNode(it)) }
        copy.template = node.template
        copy.repeatCount = node.repeatCount
        copy.repeatSource = node.repeatSource
        node.repeatItem?.let { copy.repeatItem = cloneNode(it) }
        return copy
    }

    /** 把模板节点中的 `$param.key` 替换为 params 的值. */
    private fun substituteParams(node: WidgetNode, params: JsonObject): WidgetNode {
        substituteIn(node.layout, params)
        substituteIn(node.common, params)
        substituteIn(node.props, params)
        node.children.forEach { substituteParams(it, params) }
        node.repeatItem?.let { substituteParams(it, params) }
        return node
    }

    private fun substituteIn(json: JsonObject, params: JsonObject) {
        for (entry in json.entrySet().toList()) {
            val value = entry.value
            if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                val replaced = replaceParams(value.asString, params)
                entry.setValue(com.google.gson.JsonPrimitive(replaced))
            } else if (value.isJsonObject) {
                substituteIn(value.asJsonObject, params)
            }
        }
    }

    private fun replaceParams(text: String, params: JsonObject): String {
        if (!text.contains("\$param.")) return text
        var result = text
        val matcher = Regex("\\\$param\\.([A-Za-z0-9_.]+)").findAll(text)
        for (match in matcher) {
            val key = match.groupValues[1]
            val replacement = params.get(key)?.asString ?: match.value
            result = result.replace(match.value, replacement)
        }
        return result
    }
}
