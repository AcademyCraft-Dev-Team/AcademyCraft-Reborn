package org.academy.api.client.gui.widget

import net.minecraft.client.input.PreeditEvent

/**
 * 进程级唯一的文本输入焦点 / IME 目标协调者。
 *
 * 原生 IME 只有一路 preedit，所以必须有一个唯一目标。控件聚焦时 [acquire]、失焦时
 * [release]，取代原先散落在控件内的静态字段以及控件内的 NeoForge 事件。
 */
object TextInputFocus {
    @Volatile
    private var target: TextInputWidget? = null

    fun acquire(widget: TextInputWidget) {
        target = widget
    }

    fun release(widget: TextInputWidget) {
        if (target === widget) target = null
    }

    fun isActive(): Boolean = target != null

    /** 当前焦点是否位于 [root] 子树内，用于把容器事件拦截限定在本屏幕。 */
    fun isActiveWithin(root: Widget): Boolean {
        var current: Widget? = target
        while (current != null) {
            if (current === root) return true
            current = current.parent
        }
        return false
    }

    fun handlePreedit(event: PreeditEvent?): Boolean = target?.updatePreedit(event) == true
}
