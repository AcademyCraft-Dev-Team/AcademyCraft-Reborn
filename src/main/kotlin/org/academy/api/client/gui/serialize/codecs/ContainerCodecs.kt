package org.academy.api.client.gui.serialize.codecs

import com.google.gson.JsonObject
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.serialize.PropSpec
import org.academy.api.client.gui.serialize.PropType
import org.academy.api.client.gui.serialize.WidgetCodec
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.RadioGroupWidget
import org.academy.api.client.gui.widget.ScrollPanelWidget
import org.academy.api.client.gui.widget.WheelPickerWidget

class LinearLayoutCodec : WidgetCodec<LinearLayoutWidget> {
    override val typeName = "linear_layout"
    override val widgetClass = LinearLayoutWidget::class.java

    override fun create(props: JsonObject) = LinearLayoutWidget().also {
        it.layoutParams = LinearLayoutWidget.LayoutParams()
    }

    override fun encodeProps(widget: LinearLayoutWidget) = JsonObject().apply {
        addProperty("orientation", widget.orientation.name)
        addProperty("spacing", widget.spacing)
        addProperty("gravity", widget.getLayoutGravity())
        addProperty("weight_sum", widget.getLayoutWeightSum())
    }

    override fun decodeProps(widget: LinearLayoutWidget, props: JsonObject) {
        props.get("orientation")?.asString?.let { widget.orientation = Orientation.valueOf(it) }
        props.get("spacing")?.asFloat?.let { widget.spacing = it }
        props.get("gravity")?.asInt?.let { widget.setGravity(it) }
        props.get("weight_sum")?.asFloat?.let { widget.setWeightSum(it) }
    }

    override val propertySchema = listOf(
        PropSpec(
            "orientation", PropType.ENUM,
            options = listOf("HORIZONTAL", "VERTICAL")
        ),
        PropSpec("spacing", PropType.FLOAT, 0f, 256f),
        PropSpec("gravity", PropType.INT, Int.MIN_VALUE.toFloat(), Int.MAX_VALUE.toFloat()),
        PropSpec("weight_sum", PropType.FLOAT, -1f, 1024f)
    )
}

class FrameLayoutCodec : WidgetCodec<FrameLayoutWidget> {
    override val typeName = "frame_layout"
    override val widgetClass = FrameLayoutWidget::class.java

    override fun create(props: JsonObject) = FrameLayoutWidget().also {
        it.layoutParams = FrameLayoutWidget.LayoutParams()
    }

    override fun encodeProps(widget: FrameLayoutWidget) = JsonObject().apply {
        addProperty("measure_all_children", widget.measureAllChildren)
    }

    override fun decodeProps(widget: FrameLayoutWidget, props: JsonObject) {
        props.get("measure_all_children")?.asBoolean?.let { widget.measureAllChildren = it }
    }

    override val propertySchema = listOf(
        PropSpec("measure_all_children", PropType.BOOLEAN)
    )
}

class ScrollPanelCodec : WidgetCodec<ScrollPanelWidget> {
    override val typeName = "scroll_panel"
    override val widgetClass = ScrollPanelWidget::class.java

    override fun create(props: JsonObject): ScrollPanelWidget {
        val orientation = props.get("orientation")?.asString?.let { Orientation.valueOf(it) }
        return ScrollPanelWidget(orientation)
    }

    override fun encodeProps(widget: ScrollPanelWidget) = JsonObject().apply {
        widget.getPanelOrientation()?.let { addProperty("orientation", it.name) }
        addProperty("scroll_speed", widget.getPanelScrollSpeed())
    }

    override fun decodeProps(widget: ScrollPanelWidget, props: JsonObject) {
        props.get("scroll_speed")?.asFloat?.let { widget.setScrollSpeed(it) }
    }

    override val propertySchema = listOf(
        PropSpec("orientation", PropType.ENUM, options = listOf("HORIZONTAL", "VERTICAL")),
        PropSpec("scroll_speed", PropType.FLOAT, 0.5f, 512f)
    )
}

class WheelPickerCodec : WidgetCodec<WheelPickerWidget> {
    override val typeName = "wheel_picker"
    override val widgetClass = WheelPickerWidget::class.java

    override fun create(props: JsonObject) = WheelPickerWidget()

    override fun encodeProps(widget: WheelPickerWidget) = JsonObject().apply {
        addProperty("visible_item_count", widget.visibleItemCount)
        addProperty("item_space", widget.itemSpace)
        addProperty("cyclic", widget.isCyclic)
        addProperty("curtain", widget.isCurtain)
        addProperty("curtain_color", widget.curtainColor)
        addProperty("indicator", widget.isIndicator)
        addProperty("indicator_color", widget.indicatorColor)
        addProperty("indicator_size", widget.indicatorSize)
        addProperty("item_align", widget.itemAlign.name)
        addProperty("atmospheric", widget.isAtmospheric)
        addProperty("selected_scale_enabled", widget.isSelectedScaleEnabled)
    }

    override fun decodeProps(widget: WheelPickerWidget, props: JsonObject) {
        props.get("visible_item_count")?.asInt?.let { widget.visibleItemCount = it }
        props.get("item_space")?.asFloat?.let { widget.itemSpace = it }
        props.get("cyclic")?.asBoolean?.let { widget.isCyclic = it }
        props.get("curtain")?.asBoolean?.let { widget.isCurtain = it }
        props.get("curtain_color")?.asInt?.let { widget.curtainColor = it }
        props.get("indicator")?.asBoolean?.let { widget.isIndicator = it }
        props.get("indicator_color")?.asInt?.let { widget.indicatorColor = it }
        props.get("indicator_size")?.asFloat?.let { widget.indicatorSize = it }
        props.get("item_align")?.asString?.let { widget.itemAlign = WheelPickerWidget.ItemAlign.valueOf(it) }
        props.get("atmospheric")?.asBoolean?.let { widget.isAtmospheric = it }
        props.get("selected_scale_enabled")?.asBoolean?.let { widget.isSelectedScaleEnabled = it }
    }

    override val propertySchema = listOf(
        PropSpec("visible_item_count", PropType.INT, 1f, 32f),
        PropSpec("item_space", PropType.FLOAT, 0f, 256f),
        PropSpec("cyclic", PropType.BOOLEAN),
        PropSpec("curtain", PropType.BOOLEAN),
        PropSpec("curtain_color", PropType.COLOR),
        PropSpec("indicator", PropType.BOOLEAN),
        PropSpec("indicator_color", PropType.COLOR),
        PropSpec("indicator_size", PropType.FLOAT, 0f, 64f),
        PropSpec("item_align", PropType.ENUM, options = listOf("CENTER", "LEFT", "RIGHT")),
        PropSpec("atmospheric", PropType.BOOLEAN),
        PropSpec("selected_scale_enabled", PropType.BOOLEAN)
    )
}

class RadioGroupCodec : WidgetCodec<RadioGroupWidget> {
    override val typeName = "radio_group"
    override val widgetClass = RadioGroupWidget::class.java

    override fun create(props: JsonObject) = RadioGroupWidget()

    override fun encodeProps(widget: RadioGroupWidget) = JsonObject().apply {
        addProperty("allow_reselect", widget.allowReselect)
    }

    override fun decodeProps(widget: RadioGroupWidget, props: JsonObject) {
        props.get("allow_reselect")?.asBoolean?.let { widget.allowReselect = it }
    }

    override val propertySchema = listOf(
        PropSpec("allow_reselect", PropType.BOOLEAN)
    )
}
