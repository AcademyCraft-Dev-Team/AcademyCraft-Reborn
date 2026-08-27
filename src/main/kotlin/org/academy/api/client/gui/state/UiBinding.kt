package org.academy.api.client.gui.state

import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.ProgressBarWidget
import org.academy.api.client.gui.widget.Widget

/**
 * 响应式属性绑定扩展喵. 把 [UiState] 订阅到控件属性上,
 * 状态变化时自动更新并 invalidate (生命周期由控件分离自动清理).
 */

/**
 * 订阅 [state]，变化时以 [writer] 更新控件，并在控件分离时自动退订。
 * [UiState.observe] 默认立即回调一次当前值，因此绑定后控件即为初始状态。
 */
fun <T> Widget.bindState(state: UiState<T>, writer: Widget.(T) -> Unit) {
    val widget: Widget = this
    val unsubscribe = state.observe(fn = { value -> with(widget) { writer(value) } })
    widget.addOnDetach { unsubscribe() }
}

/** 绑定文本: 状态变化时更新 [LabelWidget.text]. */
fun LabelWidget.bindText(state: UiState<String>): LabelWidget {
    bindState(state) { text = it }
    return this
}

/** 绑定可见性: true=VISIBLE, false=INVISIBLE. */
fun Widget.bindVisible(state: UiState<Boolean>): Widget {
    bindState(state) { visibility = if (it) Widget.Visibility.VISIBLE else Widget.Visibility.INVISIBLE }
    return this
}

/** 绑定可见性: 状态变化时更新 [ProgressBarWidget] 进度. */
fun ProgressBarWidget.bindProgress(state: UiState<Float>): ProgressBarWidget {
    bindState(state) { setProgress(it) }
    return this
}

/** 绑定启用状态. */
fun Widget.bindEnabled(state: UiState<Boolean>): Widget {
    bindState(state) { isEnabled = it }
    return this
}

/** 绑定透明度. */
fun Widget.bindAlpha(state: UiState<Float>): Widget {
    bindState(state) { alpha = it }
    return this
}

/** 绑定文本内容到任意 writer (例如只读标签 + 格式化). */
fun Widget.bindText(state: UiState<String>, writer: Widget.(String) -> Unit): Widget {
    bindState(state) { writer(it) }
    return this
}
