package org.academy.api.client.gui.state

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.texture.TextureSource
import org.academy.api.client.gui.widget.ImageWidget
import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.ProgressBarWidget
import org.academy.api.client.gui.widget.Widget

fun <T> Widget.bindState(state: UiState<T>, writer: Widget.(T) -> Unit) {
    val widget: Widget = this
    val unsubscribe = state.observe(fn = { value -> with(widget) { writer(value) } })
    widget.addOnDetach { unsubscribe() }
}

fun LabelWidget.bindText(state: UiState<String>): LabelWidget {
    bindState(state) { text = it }
    return this
}

fun Widget.bindVisible(state: UiState<Boolean>): Widget {
    bindState(state) { visibility = if (it) Widget.Visibility.VISIBLE else Widget.Visibility.INVISIBLE }
    return this
}

fun ProgressBarWidget.bindProgress(state: UiState<Float>): ProgressBarWidget {
    bindState(state) { setProgress(it) }
    return this
}

fun Widget.bindEnabled(state: UiState<Boolean>): Widget {
    bindState(state) { isEnabled = it }
    return this
}

fun Widget.bindAlpha(state: UiState<Float>): Widget {
    bindState(state) { alpha = it }
    return this
}

fun Widget.bindText(state: UiState<String>, writer: Widget.(String) -> Unit): Widget {
    bindState(state) { writer(it) }
    return this
}

fun ImageWidget.bindTextureSource(state: UiState<TextureSource?>): ImageWidget {
    bindState(state) { setTextureSource(it) }
    return this
}

fun ImageWidget.bindIdentifier(state: UiState<Identifier?>): ImageWidget {
    bindState(state) { setTexture(it) }
    return this
}
