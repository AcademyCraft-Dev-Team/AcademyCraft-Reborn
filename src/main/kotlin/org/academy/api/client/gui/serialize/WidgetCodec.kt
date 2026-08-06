package org.academy.api.client.gui.serialize

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject

/**
 * 属性编辑器 schema 的条目, 编辑器属性面板据此生成通用编辑控件.
 */
class PropSpec(
    val key: String,
    val type: PropType,
    val min: Float = 0f,
    val max: Float = 1f,
    val options: List<String> = emptyList()
)

enum class PropType {
    TEXT, FLOAT, INT, BOOLEAN, COLOR, ENUM, IDENTIFIER
}

object UiJson {
    /** 统一的 Gson 实例, 输出使用美化排版. */
    val GSON: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()
}

/** 从 [JsonObject] 安全读取可选值. */
internal fun JsonObject.optString(key: String): String? = get(key)?.asString
internal fun JsonObject.optFloat(key: String): Float? = get(key)?.asFloat
internal fun JsonObject.optInt(key: String): Int? = get(key)?.asInt
internal fun JsonObject.optBoolean(key: String): Boolean? = get(key)?.asBoolean
