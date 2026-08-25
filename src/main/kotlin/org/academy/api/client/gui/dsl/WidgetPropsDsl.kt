package org.academy.api.client.gui.dsl

import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.resources.Identifier
import org.academy.api.client.gui.animation.TimeInterpolator
import org.academy.api.client.gui.widget.DragBarWidget
import org.academy.api.client.gui.widget.ImageWidget
import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.PagerLayoutWidget
import org.academy.api.client.gui.widget.ParallaxImageWidget
import org.academy.api.client.gui.widget.ProgressBarWidget
import org.academy.api.client.gui.widget.RoundedRectWidget
import org.academy.api.client.gui.widget.ScrollBarWidget
import org.academy.api.client.gui.widget.ScrollPanelWidget
import org.academy.api.client.gui.widget.SeekBarWidget
import org.academy.api.client.gui.widget.TextBoxWidget
import org.academy.api.client.gui.widget.ToggleButtonWidget
import org.academy.api.client.gui.widget.WheelPickerWidget
import org.academy.api.client.gui.widget.Widget
import java.util.function.Consumer

/**
 * 控件属性 DSL 增强喵: 在控件上直接链式配置属性 (返回自身), 供工厂 lambda 之外
 * 的表达式风格使用. 属性本身仍是 Kotlin `var` 字段, 这些只是便捷扩展.
 */

// ============ ImageWidget ============

fun ImageWidget.texture(id: Identifier?): ImageWidget {
    textureIdentifier = id
    return this
}

fun ImageWidget.sampler(mode: FilterMode, useMipmap: Boolean): ImageWidget = setSampler(mode, useMipmap)

fun ImageWidget.uv(u0: Float, v0: Float, u1: Float, v1: Float): ImageWidget = setUv(u0, v0, u1, v1)

fun ImageWidget.uv(
    u0: Float, v0: Float, u1: Float, v1: Float,
    u2: Float, v2: Float, u3: Float, v3: Float
): ImageWidget = setUv(u0, v0, u1, v1, u2, v2, u3, v3)

fun ImageWidget.rgb(red: Float, green: Float, blue: Float): ImageWidget = setColor(red, green, blue)

fun ImageWidget.brightnessOf(value: Float): ImageWidget {
    brightness = value
    return this
}

// ============ LabelWidget ============

fun LabelWidget.rgb(red: Float, green: Float, blue: Float): LabelWidget {
    setRed(red)
    setGreen(green)
    setBlue(blue)
    return this
}

fun LabelWidget.withDropShadow(enabled: Boolean = true): LabelWidget {
    dropShadow = enabled
    return this
}

// ============ ProgressBarWidget ============

fun ProgressBarWidget.range(min: Float, max: Float): ProgressBarWidget {
    setMin(min)
    setMax(max)
    return this
}

fun ProgressBarWidget.value(value: Float): ProgressBarWidget {
    setProgress(value)
    return this
}

fun ProgressBarWidget.colors(background: Int, foreground: Int): ProgressBarWidget {
    backgroundColor = background
    setProgressColor(foreground)
    return this
}

// ============ RoundedRectWidget ============

fun RoundedRectWidget.border(width: Float, color: Int): RoundedRectWidget = setBorder(width, color)

fun RoundedRectWidget.shadow(
    color: Int,
    blur: Float,
    offsetX: Float = 0f,
    offsetY: Float = 0f
): RoundedRectWidget = setShadow(color, blur, offsetX, offsetY)

fun RoundedRectWidget.verticalGradient(from: Int, to: Int): RoundedRectWidget = setVerticalGradient(from, to)

fun RoundedRectWidget.horizontalGradient(from: Int, to: Int): RoundedRectWidget = setHorizontalGradient(from, to)

fun RoundedRectWidget.radialGradient(from: Int, to: Int): RoundedRectWidget = setRadialGradient(from, to)

// ============ ScrollPanelWidget ============

fun ScrollPanelWidget.scrollSpeed(value: Float): ScrollPanelWidget {
    setScrollSpeed(value)
    return this
}

// ============ TextBoxWidget ============

fun TextBoxWidget.enter(callback: Consumer<String>?): TextBoxWidget {
    setWhenEnter(callback)
    return this
}

fun TextBoxWidget.onLostFocus(callback: Runnable?): TextBoxWidget {
    setOnFocusLost(callback)
    return this
}

fun TextBoxWidget.clearOnEnter(clear: Boolean): TextBoxWidget {
    setClearWhenEnter(clear)
    return this
}

fun TextBoxWidget.lineBreak(enabled: Boolean): TextBoxWidget {
    setAllowLineBreak(enabled)
    return this
}

// ============ ToggleButtonWidget ============

fun ToggleButtonWidget.checked(value: Boolean): ToggleButtonWidget {
    isChecked = value
    return this
}

// ============ SeekBarWidget ============

fun SeekBarWidget.keyIncrement(value: Int): SeekBarWidget {
    setKeyProgressIncrement(value)
    return this
}

fun SeekBarWidget.seekListener(listener: SeekBarWidget.OnSeekBarChangeListener?): SeekBarWidget {
    setOnSeekBarChangeListener(listener)
    return this
}

// ============ WheelPickerWidget ============

fun WheelPickerWidget.visibleItemCount(count: Int): WheelPickerWidget {
    visibleItemCount = count
    return this
}

fun WheelPickerWidget.cyclic(enabled: Boolean): WheelPickerWidget {
    isCyclic = enabled
    return this
}

fun WheelPickerWidget.curtain(enabled: Boolean): WheelPickerWidget {
    isCurtain = enabled
    return this
}

fun WheelPickerWidget.curtainColor(color: Int): WheelPickerWidget {
    this.curtainColor = color
    return this
}

fun WheelPickerWidget.indicator(enabled: Boolean): WheelPickerWidget {
    isIndicator = enabled
    return this
}

fun WheelPickerWidget.itemAlign(align: WheelPickerWidget.ItemAlign): WheelPickerWidget {
    this.itemAlign = align
    return this
}

fun WheelPickerWidget.atmospheric(enabled: Boolean): WheelPickerWidget {
    isAtmospheric = enabled
    return this
}

fun WheelPickerWidget.selectedScale(enabled: Boolean): WheelPickerWidget {
    isSelectedScaleEnabled = enabled
    return this
}

fun WheelPickerWidget.onItemSelected(
    listener: (picker: WheelPickerWidget, item: Widget?, position: Int) -> Unit
): WheelPickerWidget {
    onItemSelectedListener = object : WheelPickerWidget.OnItemSelectedListener {
        override fun onItemSelected(picker: WheelPickerWidget, item: Widget?, position: Int) {
            listener(picker, item, position)
        }
    }
    return this
}

// ============ PagerLayoutWidget ============

fun PagerLayoutWidget.page(index: Int, animate: Boolean = false): PagerLayoutWidget {
    if (animate) switchToPage(index) else jumpToPage(index)
    return this
}

fun PagerLayoutWidget.switchDuration(ms: Long): PagerLayoutWidget {
    pageSwitchDuration = ms
    return this
}

fun PagerLayoutWidget.interpolator(interpolator: TimeInterpolator): PagerLayoutWidget {
    pageSwitchInterpolator = interpolator
    return this
}

// ============ DragBarWidget / ScrollBarWidget ============

fun DragBarWidget.showBackground(visible: Boolean): DragBarWidget {
    setShowBackground(visible)
    return this
}

fun DragBarWidget.thumbColor(color: Int): DragBarWidget {
    setThumbColor(color)
    return this
}

fun DragBarWidget.trackColor(color: Int): DragBarWidget {
    setTrackColor(color)
    return this
}

fun ScrollBarWidget.panel(p: ScrollPanelWidget?): ScrollBarWidget {
    this.panel = p
    return this
}

// ============ ParallaxImageWidget ============

fun ParallaxImageWidget.parallaxFactor(x: Float, y: Float): ParallaxImageWidget = setParallaxFactor(x, y)

fun ParallaxImageWidget.imageToViewRatio(w: Float, h: Float): ParallaxImageWidget = setImageToViewRatio(w, h)

fun ParallaxImageWidget.parallax(enabled: Boolean): ParallaxImageWidget {
    parallaxEnabled = enabled
    return this
}

fun ParallaxImageWidget.rgb(red: Float, green: Float, blue: Float): ParallaxImageWidget {
    setColor(red, green, blue)
    return this
}
