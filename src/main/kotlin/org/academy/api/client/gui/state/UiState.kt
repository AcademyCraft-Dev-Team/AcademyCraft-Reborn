package org.academy.api.client.gui.state

/**
 * 最小可观察状态持有器喵. 值变化时通知所有订阅者 (仅在值真的变化时触发).
 *
 * [observe] 默认立即用当前值回调一次; 返回的 lambda 用于退订.
 */
class UiState<T>(initial: T) {
    var value: T = initial
        set(v) {
            if (field != v) {
                field = v
                val snapshot = ArrayList(subscribers)
                for (subscriber in snapshot) subscriber(v)
            }
        }

    private val subscribers: MutableList<(T) -> Unit> = ArrayList()

    /** 订阅状态变化. [fireImmediately] 为 true 时先以当前值回调一次. 返回退订函数. */
    fun observe(fn: (T) -> Unit, fireImmediately: Boolean = true): () -> Unit {
        subscribers.add(fn)
        if (fireImmediately) fn(value)
        return { subscribers.remove(fn) }
    }

    /** 订阅并把生命周期交给 [scope] 管理 (scope 注销时自动退订). */
    fun observe(scope: StateScope, fn: (T) -> Unit): () -> Unit {
        return scope.track(observe(fn, true))
    }

    fun dispose() {
        subscribers.clear()
    }
}

/**
 * 状态订阅作用域. 用于把多个 UiState 的订阅统一绑定到某个生命周期
 * (例如组件挂载/卸载), 避免手动逐个退订.
 */
class StateScope {
    private val unsubscribers: MutableList<() -> Unit> = ArrayList()

    fun track(unsubscribe: () -> Unit): () -> Unit {
        unsubscribers.add(unsubscribe)
        return unsubscribe
    }

    fun clear() {
        for (unsubscribe in unsubscribers) unsubscribe()
        unsubscribers.clear()
    }
}

/** 便捷工厂: 创建一个 [UiState]. */
fun <T> uiState(initial: T): UiState<T> = UiState(initial)
