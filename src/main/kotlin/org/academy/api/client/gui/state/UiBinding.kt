package org.academy.api.client.gui.state

import org.academy.api.client.gui.widget.Widget

fun <T> Widget.bindState(state: UiState<T>, writer: Widget.(T) -> Unit) {
    val widget: Widget = this
    val unsubscribe = state.observe(fn = { value -> with(widget) { writer(value) } })
    widget.addOnDetach { unsubscribe() }
}
