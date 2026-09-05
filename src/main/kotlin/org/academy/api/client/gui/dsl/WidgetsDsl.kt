package org.academy.api.client.gui.dsl

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.texture.TextureSource
import org.academy.api.client.gui.widget.*

// ============ 名称生成 ============

/** 生成容器内唯一的默认子控件名: `base`, `base_1`, `base_2`... */
fun WidgetContainer.nextChildName(base: String): String {
    if (base !in children) return base
    var n = 1
    while ("${base}_$n" in children) n++
    return "${base}_$n"
}

fun WidgetContainer.label(
    text: String,
    name: String = nextChildName("label"),
    init: LabelWidget.() -> Unit = {}
): LabelWidget {
    val widget = LabelWidget(text)
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.image(
    texture: Identifier? = null,
    name: String = nextChildName("image"),
    init: ImageWidget.() -> Unit = {}
): ImageWidget {
    val widget = ImageWidget(texture)
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.image(
    texture: TextureSource,
    name: String = nextChildName("image"),
    init: ImageWidget.() -> Unit = {}
): ImageWidget {
    val widget = ImageWidget().setTextureSource(texture)
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.fill(
    color: Int,
    name: String = nextChildName("fill"),
    init: FillWidget.() -> Unit = {}
): FillWidget {
    val widget = FillWidget(color)
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.blurPanel(
    radius: Float = 8f,
    name: String = nextChildName("blur_panel"),
    init: BlurPanelWidget.() -> Unit = {}
): BlurPanelWidget {
    val widget = BlurPanelWidget(radius)
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.roundedRect(
    fillColor: Int = 0xFFFFFFFF.toInt(),
    radius: Float = 4f,
    name: String = nextChildName("rounded_rect"),
    init: RoundedRectWidget.() -> Unit = {}
): RoundedRectWidget {
    val widget = RoundedRectWidget(fillColor, radius)
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.nineSlice(
    texture: Identifier,
    left: Float = 0f,
    right: Float = 0f,
    top: Float = 0f,
    bottom: Float = 0f,
    name: String = nextChildName("nine_slice"),
    init: NineSliceWidget.() -> Unit = {}
): NineSliceWidget {
    val widget = NineSliceWidget(texture, left, right, top, bottom)
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.button(name: String = nextChildName("button"), init: ButtonWidget.() -> Unit = {}): ButtonWidget {
    val widget = ButtonWidget()
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.toggle(
    checked: Boolean = false,
    name: String = nextChildName("toggle"),
    init: ToggleButtonWidget.() -> Unit = {}
): ToggleButtonWidget {
    val widget = ToggleButtonWidget()
    widget.isChecked = checked
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.radio(
    name: String = nextChildName("radio"),
    init: RadioButtonWidget.() -> Unit = {}
): RadioButtonWidget {
    val widget = RadioButtonWidget()
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.progress(
    name: String = nextChildName("progress"),
    init: ProgressBarWidget.() -> Unit = {}
): ProgressBarWidget {
    val widget = ProgressBarWidget()
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.textBox(
    maxLength: Int = 32,
    name: String = nextChildName("text_box"),
    init: TextBoxWidget.() -> Unit = {}
): TextBoxWidget {
    val widget = TextBoxWidget(maxLength)
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.seekBar(
    name: String = nextChildName("seek_bar"),
    init: SeekBarWidget.() -> Unit = {}
): SeekBarWidget {
    val widget = SeekBarWidget()
    addChild(name, widget)
    widget.init()
    return widget
}

fun standaloneColumn(spacing: Float = 0f, init: LinearLayoutWidget.() -> Unit = {}): LinearLayoutWidget {
    val widget = LinearLayoutWidget()
    widget.orientation = Orientation.VERTICAL
    widget.spacing = spacing
    widget.layoutParams = LinearLayoutWidget.LayoutParams()
    widget.init()
    return widget
}

fun standaloneRow(spacing: Float = 0f, init: LinearLayoutWidget.() -> Unit = {}): LinearLayoutWidget {
    val widget = LinearLayoutWidget()
    widget.orientation = Orientation.HORIZONTAL
    widget.spacing = spacing
    widget.layoutParams = LinearLayoutWidget.LayoutParams()
    widget.init()
    return widget
}

fun standaloneFrame(init: FrameLayoutWidget.() -> Unit = {}): FrameLayoutWidget {
    val widget = FrameLayoutWidget()
    widget.layoutParams = FrameLayoutWidget.LayoutParams()
    widget.init()
    return widget
}

fun WidgetContainer.column(
    name: String = nextChildName("column"),
    spacing: Float = 0f,
    init: LinearLayoutWidget.() -> Unit = {}
): LinearLayoutWidget {
    val widget = LinearLayoutWidget()
    widget.orientation = Orientation.VERTICAL
    widget.spacing = spacing
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.row(
    name: String = nextChildName("row"),
    spacing: Float = 0f,
    init: LinearLayoutWidget.() -> Unit = {}
): LinearLayoutWidget {
    val widget = LinearLayoutWidget()
    widget.orientation = Orientation.HORIZONTAL
    widget.spacing = spacing
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.anchor(
    name: String = nextChildName("anchor"),
    init: AnchorLayoutWidget.() -> Unit = {}
): AnchorLayoutWidget {
    val widget = AnchorLayoutWidget()
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.frame(
    name: String = nextChildName("frame"),
    init: FrameLayoutWidget.() -> Unit = {}
): FrameLayoutWidget {
    val widget = FrameLayoutWidget()
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.grid(
    columns: Int,
    name: String = nextChildName("grid"),
    rows: Int = 0,
    init: GridLayoutWidget.() -> Unit = {}
): GridLayoutWidget {
    val widget = GridLayoutWidget()
    widget.columns = columns
    widget.rows = rows
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.wrap(
    name: String = nextChildName("wrap"),
    init: WrapLayoutWidget.() -> Unit = {}
): WrapLayoutWidget {
    val widget = WrapLayoutWidget()
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.dock(
    name: String = nextChildName("dock"),
    init: DockLayoutWidget.() -> Unit = {}
): DockLayoutWidget {
    val widget = DockLayoutWidget()
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.stack(
    name: String = nextChildName("stack"),
    init: StackLayoutWidget.() -> Unit = {}
): StackLayoutWidget {
    val widget = StackLayoutWidget()
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.aspect(
    ratio: Float,
    name: String = nextChildName("aspect"),
    init: AspectLayoutWidget.() -> Unit = {}
): AspectLayoutWidget {
    val widget = AspectLayoutWidget(ratio)
    addChild(name, widget)
    widget.init()
    return widget
}

fun WidgetContainer.scrollPanel(
    orientation: Orientation? = Orientation.VERTICAL,
    name: String = nextChildName("scroll"),
    content: Widget? = null,
    init: ScrollPanelWidget.() -> Unit = {}
): ScrollPanelWidget {
    val widget = ScrollPanelWidget(orientation)
    addChild(name, widget)
    if (content != null) widget.setContent(content)
    widget.init()
    return widget
}

fun WidgetContainer.radioGroup(
    name: String = nextChildName("radio_group"),
    init: RadioGroupWidget.() -> Unit = {}
): RadioGroupWidget {
    val widget = RadioGroupWidget()
    addChild(name, widget)
    widget.init()
    return widget
}

fun <T : Widget> WidgetContainer.add(
    name: String,
    widget: T,
    init: T.() -> Unit = {}
): T {
    addChild(name, widget)
    widget.init()
    return widget
}

fun <T : Widget> WidgetContainer.replace(
    name: String,
    widget: T,
    init: T.() -> Unit = {}
): T {
    replaceChild(name, widget)
    widget.init()
    return widget
}

fun ButtonWidget.onClick(handler: () -> Unit): ButtonWidget {
    onClickListener = OnClickListener { handler() }
    return this
}

fun BlurPanelWidget.onClick(handler: () -> Unit): BlurPanelWidget {
    onClick = handler
    return this
}

fun ToggleButtonWidget.onCheckedChange(handler: (Boolean) -> Unit): ToggleButtonWidget {
    onCheckedChangeListener =
        object : ToggleButtonWidget.OnCheckedChangeListener {
            override fun onCheckedChanged(toggle: ToggleButtonWidget, isChecked: Boolean) {
                handler(isChecked)
            }
        }
    return this
}
