package org.academy.api.client.gui.dsl

import org.academy.api.client.gui.widget.WidgetContainer

/**
 * 有状态 UI 组件基类喵.
 *
 * 组件以 [props] 参数化, [build] 在挂载时构建控件子树并加入 [host].
 * 父容器 `addChild` 会触发附加/分离生命周期, 组件据此管理 [onMount]/[onUnmount].
 * 无状态组件应优先使用 [WidgetContainer] 上的 DSL 工厂函数 (更短).
 */
abstract class UiComponent<P>(open val props: P) {
    /** 组件是否已挂载到控件树. */
    var isMounted: Boolean = false
        private set

    protected var host: WidgetContainer? = null
        private set

    /** 挂载组件到 [host]. 同一组件只允许挂载一次. */
    fun mount(host: WidgetContainer): UiComponent<P> {
        check(!isMounted) { "UiComponent is already mounted" }
        this.host = host
        isMounted = true
        onMount()
        build(host)
        return this
    }

    /** 由外部在父容器 `removeChild` 前调用以释放资源. */
    fun unmount() {
        if (!isMounted) return
        onUnmount()
        host = null
        isMounted = false
    }

    open fun onMount() {}
    open fun onUnmount() {}
    open fun onPropsChanged(next: P) {}

    abstract fun build(host: WidgetContainer)
}
