package org.academy.api.client.gui.serialize

import com.google.gson.JsonObject
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer

/**
 * 单个控件类型的编解码器. [create] 负责构造控件并初始化正确的 [org.academy.api.client.gui.widget.WidgetContainer.LayoutParams]
 * 子类, 这样 addChild 时 checkLayoutParams 直接通过, 不丢失 weight 等字段.
 */
interface WidgetCodec<T : Widget> {
    /** JSON 中使用的类型名, 如 "label" / "linear_layout". */
    val typeName: String

    val widgetClass: Class<T>

    fun create(props: JsonObject): T

    /** 仅序列化类型专属属性 (不含 layout/common). */
    fun encodeProps(widget: T): JsonObject

    fun decodeProps(widget: T, props: JsonObject)

    /** 编辑器属性面板的 schema. */
    val propertySchema: List<PropSpec>
}

object WidgetCodecRegistry {
    private val codecs: MutableList<WidgetCodec<*>> = ArrayList()
    private var byNameCache: Map<String, WidgetCodec<*>>? = null
    private var typesCache: List<String>? = null

    fun register(codec: WidgetCodec<*>) {
        codecs.add(codec)
        byNameCache = null
        typesCache = null
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Widget> byType(type: String): WidgetCodec<T>? {
        val cache = byNameCache ?: codecs.associateBy { it.typeName }.also { byNameCache = it }
        return cache[type] as WidgetCodec<T>?
    }

    /** 查找最具体的 codec (子类优先于父类). */
    @Suppress("UNCHECKED_CAST")
    fun <T : Widget> forWidget(widget: T): WidgetCodec<T>? {
        var best: WidgetCodec<*>? = null
        var bestDepth = Int.MAX_VALUE
        for (codec in codecs) {
            if (codec.widgetClass.isInstance(widget)) {
                var depth = 0
                var k: Class<*>? = widget.javaClass
                while (k != null && k != codec.widgetClass) {
                    depth++
                    k = k.superclass
                }
                if (depth < bestDepth) {
                    bestDepth = depth
                    best = codec
                }
            }
        }
        return best as WidgetCodec<T>?
    }

    fun types(): List<String> = typesCache ?: codecs.map { it.typeName }.also { typesCache = it }

    /** True when the type is a container that can host children widgets. */
    fun isContainerType(type: String): Boolean {
        val codec = byType<Widget>(type) ?: return false
        return WidgetContainer::class.java.isAssignableFrom(codec.widgetClass)
    }
}
