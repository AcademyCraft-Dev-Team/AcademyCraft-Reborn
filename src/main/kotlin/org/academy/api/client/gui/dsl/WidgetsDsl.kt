package org.academy.api.client.gui.dsl

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.widget.*

// ============ 名称生成 ============

/** 生成容器内唯一的默认子控件名: `base`, `base_1`, `base_2`... */
fun WidgetContainer.nextChildName(base: String): String {
    if (base !in children) return base
    var n = 1
    while ("${base}_$n" in children) n++
    return "${base}_$n"
}

// ============ 叶子控件工厂 ============

/** 创建并加入一个 [LabelWidget]. */
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

/** 创建并加入一个 [ImageWidget]. */
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

/** 创建并加入一个 [FillWidget]. */
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

/** 创建并加入一个磨砂玻璃面板 [BlurPanelWidget]. */
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

/** 创建并加入一个 SDF 圆角矩形控件 [RoundedRectWidget]. */
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

/** 创建并加入一个九宫格控件 [NineSliceWidget]. */
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

/** 创建并加入一个 [ButtonWidget]. */
fun WidgetContainer.button(name: String = nextChildName("button"), init: ButtonWidget.() -> Unit = {}): ButtonWidget {
    val widget = ButtonWidget()
    addChild(name, widget)
    widget.init()
    return widget
}

/** 创建并加入一个 [ToggleButtonWidget]. */
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

/** 创建并加入一个 [RadioButtonWidget]. */
fun WidgetContainer.radio(
    name: String = nextChildName("radio"),
    init: RadioButtonWidget.() -> Unit = {}
): RadioButtonWidget {
    val widget = RadioButtonWidget()
    addChild(name, widget)
    widget.init()
    return widget
}

/** 创建并加入一个 [ProgressBarWidget]. */
fun WidgetContainer.progress(
    name: String = nextChildName("progress"),
    init: ProgressBarWidget.() -> Unit = {}
): ProgressBarWidget {
    val widget = ProgressBarWidget()
    addChild(name, widget)
    widget.init()
    return widget
}

/** 创建并加入一个 [TextBoxWidget]. */
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

/** 创建并加入一个 [SeekBarWidget]. */
fun WidgetContainer.seekBar(
    name: String = nextChildName("seek_bar"),
    init: SeekBarWidget.() -> Unit = {}
): SeekBarWidget {
    val widget = SeekBarWidget()
    addChild(name, widget)
    widget.init()
    return widget
}

// ============ 独立构造器 (不加入任何父级, 供返回/传参使用) ============

/** 构造一个未挂载的纵向线性布局. */
fun standaloneColumn(spacing: Float = 0f, init: LinearLayoutWidget.() -> Unit = {}): LinearLayoutWidget {
    val widget = LinearLayoutWidget()
    widget.orientation = Orientation.VERTICAL
    widget.spacing = spacing
    widget.layoutParams = LinearLayoutWidget.LayoutParams()
    widget.init()
    return widget
}

/** 构造一个未挂载的横向线性布局. */
fun standaloneRow(spacing: Float = 0f, init: LinearLayoutWidget.() -> Unit = {}): LinearLayoutWidget {
    val widget = LinearLayoutWidget()
    widget.orientation = Orientation.HORIZONTAL
    widget.spacing = spacing
    widget.layoutParams = LinearLayoutWidget.LayoutParams()
    widget.init()
    return widget
}

/** 构造一个未挂载的帧布局. */
fun standaloneFrame(init: FrameLayoutWidget.() -> Unit = {}): FrameLayoutWidget {
    val widget = FrameLayoutWidget()
    widget.layoutParams = FrameLayoutWidget.LayoutParams()
    widget.init()
    return widget
}

// ============ 容器控件工厂 ============

/** 纵向线性布局. */
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

/** 横向线性布局. */
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

/** 锚点布局 (布局 v2). */
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

/** 宽高比约束布局. */
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

/** 滚动面板, [content] 为其唯一子内容. */
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

/** 单选组. */
fun WidgetContainer.radioGroup(
    name: String = nextChildName("radio_group"),
    init: RadioGroupWidget.() -> Unit = {}
): RadioGroupWidget {
    val widget = RadioGroupWidget()
    addChild(name, widget)
    widget.init()
    return widget
}

/** 为现有子控件赋予布局参数后加入父级 (等价 addChild + init). */
fun <T : Widget> WidgetContainer.add(
    name: String,
    widget: T,
    init: T.() -> Unit = {}
): T {
    addChild(name, widget)
    widget.init()
    return widget
}

/** 替换同名子控件并保留顺序, 赋予布局参数后执行 init (等价 replaceChild + init). */
fun <T : Widget> WidgetContainer.replace(
    name: String,
    widget: T,
    init: T.() -> Unit = {}
): T {
    replaceChild(name, widget)
    widget.init()
    return widget
}

// ============ 交互辅助 ============

/** 设置点击回调. */
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
