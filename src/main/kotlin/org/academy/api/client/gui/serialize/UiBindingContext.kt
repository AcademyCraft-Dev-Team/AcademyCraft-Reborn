package org.academy.api.client.gui.serialize

import org.academy.api.client.gui.state.UiState

/**
 * JSON 布局绑定上下文喵. 把 v2 格式中的 `$path` 绑定路径解析到运行时的 [UiState].
 *
 * ```
 * val bindings = UiBindingContext()
 *     .register("player.name", playerNameState)
 *     .register("model.isUnlocked", unlockedState)
 * val widget = WidgetSerializer.decode(json, bindings)
 * ```
 */
class UiBindingContext {
    private val states: MutableMap<String, UiState<*>> = HashMap()
    private val repeatCounts: MutableMap<String, () -> Int> = HashMap()

    fun <T> register(path: String, state: UiState<T>): UiBindingContext {
        states[path] = state
        return this
    }

    fun resolve(path: String): UiState<*>? = states[path]

    /** 去掉 `$` 前缀并按上下文解析, 返回绑定到的 UiState. */
    fun resolveBinding(ref: String): UiState<*>? {
        val path = ref.removePrefix("$")
        return states[path]
    }

    /** 注册 repeat 数据源: 返回该源展开次数. */
    fun registerRepeatCount(source: String, count: () -> Int): UiBindingContext {
        repeatCounts[source.removePrefix("$")] = count
        return this
    }

    /** 解析 repeat 数据源展开次数; 未注册返回 null. */
    fun resolveRepeatCount(source: String): Int? = repeatCounts[source.removePrefix("$")]?.invoke()

    fun isEmpty(): Boolean = states.isEmpty() && repeatCounts.isEmpty()
}
