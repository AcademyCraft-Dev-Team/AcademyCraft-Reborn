package org.academy.api.client.gui.serialize.codecs

import com.google.gson.JsonObject
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.serialize.PropSpec
import org.academy.api.client.gui.serialize.PropType
import org.academy.api.client.gui.serialize.WidgetCodec
import org.academy.api.client.gui.widget.BlendQuadWidget
import org.academy.api.client.gui.widget.ProgressBarWidget
import org.academy.api.client.gui.widget.RadioButtonWidget
import org.academy.api.client.gui.widget.ScrollBarWidget
import org.academy.api.client.gui.widget.SeekBarWidget
import org.academy.api.client.gui.widget.SpriteSheetWidget
import org.academy.api.client.gui.widget.ToggleButtonWidget

class ProgressBarCodec : WidgetCodec<ProgressBarWidget> {
    override val typeName = "progress_bar"
    override val widgetClass = ProgressBarWidget::class.java

    override fun create(props: JsonObject) = ProgressBarWidget()

    override fun encodeProps(widget: ProgressBarWidget) = JsonObject().apply {
        addProperty("min", widget.min)
        addProperty("max", widget.max)
        addProperty("progress", widget.progress)
        addProperty("background_color", widget.backgroundColor)
        addProperty("progress_color", widget.progressColor)
        addProperty("orientation", widget.orientation.name)
    }

    override fun decodeProps(widget: ProgressBarWidget, props: JsonObject) {
        props.get("min")?.asFloat?.let { widget.setMin(it) }
        props.get("max")?.asFloat?.let { widget.setMax(it) }
        props.get("progress")?.asFloat?.let { widget.setProgress(it) }
        props.get("background_color")?.asInt?.let { widget.setBackgroundColor(it) }
        props.get("progress_color")?.asInt?.let { widget.setProgressColor(it) }
        props.get("orientation")?.asString?.let { widget.setOrientation(Orientation.valueOf(it)) }
    }

    override val propertySchema = listOf(
        PropSpec("min", PropType.FLOAT, 0f, 1.0E9f),
        PropSpec("max", PropType.FLOAT, 0f, 1.0E9f),
        PropSpec("progress", PropType.FLOAT, 0f, 1.0E9f),
        PropSpec("background_color", PropType.COLOR),
        PropSpec("progress_color", PropType.COLOR),
        PropSpec("orientation", PropType.ENUM, options = listOf("HORIZONTAL", "VERTICAL"))
    )
}

class SeekBarCodec : WidgetCodec<SeekBarWidget> {
    override val typeName = "seek_bar"
    override val widgetClass = SeekBarWidget::class.java

    override fun create(props: JsonObject) = SeekBarWidget()

    override fun encodeProps(widget: SeekBarWidget) = JsonObject().apply {
        addProperty("min", widget.min)
        addProperty("max", widget.max)
        addProperty("progress", widget.progress)
        addProperty("background_color", widget.backgroundColor)
        addProperty("progress_color", widget.progressColor)
        addProperty("orientation", widget.orientation.name)
        addProperty("key_progress_increment", widget.keyProgressIncrement)
    }

    override fun decodeProps(widget: SeekBarWidget, props: JsonObject) {
        props.get("min")?.asFloat?.let { widget.setMin(it) }
        props.get("max")?.asFloat?.let { widget.setMax(it) }
        props.get("progress")?.asFloat?.let { widget.setProgress(it) }
        props.get("background_color")?.asInt?.let { widget.setBackgroundColor(it) }
        props.get("progress_color")?.asInt?.let { widget.setProgressColor(it) }
        props.get("orientation")?.asString?.let { widget.setOrientation(Orientation.valueOf(it)) }
        props.get("key_progress_increment")?.asInt?.let { widget.setKeyProgressIncrement(it) }
    }

    override val propertySchema = listOf(
        PropSpec("min", PropType.FLOAT, 0f, 1.0E9f),
        PropSpec("max", PropType.FLOAT, 0f, 1.0E9f),
        PropSpec("progress", PropType.FLOAT, 0f, 1.0E9f),
        PropSpec("background_color", PropType.COLOR),
        PropSpec("progress_color", PropType.COLOR),
        PropSpec("orientation", PropType.ENUM, options = listOf("HORIZONTAL", "VERTICAL")),
        PropSpec("key_progress_increment", PropType.INT, 0f, 1024f)
    )
}

class ToggleButtonCodec : WidgetCodec<ToggleButtonWidget> {
    override val typeName = "toggle_button"
    override val widgetClass = ToggleButtonWidget::class.java

    override fun create(props: JsonObject) = ToggleButtonWidget()

    override fun encodeProps(widget: ToggleButtonWidget) = JsonObject().apply {
        addProperty("checked", widget.isChecked)
        addProperty("track_color", widget.trackColor)
        addProperty("checked_track_color", widget.checkedTrackColor)
        addProperty("thumb_color", widget.thumbColor)
        addProperty("checked_thumb_color", widget.checkedThumbColor)
    }

    override fun decodeProps(widget: ToggleButtonWidget, props: JsonObject) {
        props.get("checked")?.asBoolean?.let { widget.setChecked(it) }
        props.get("track_color")?.asInt?.let { widget.setTrackColor(it) }
        props.get("checked_track_color")?.asInt?.let { widget.setCheckedTrackColor(it) }
        props.get("thumb_color")?.asInt?.let { widget.setThumbColor(it) }
        props.get("checked_thumb_color")?.asInt?.let { widget.setCheckedThumbColor(it) }
    }

    override val propertySchema = listOf(
        PropSpec("checked", PropType.BOOLEAN),
        PropSpec("track_color", PropType.COLOR),
        PropSpec("checked_track_color", PropType.COLOR),
        PropSpec("thumb_color", PropType.COLOR),
        PropSpec("checked_thumb_color", PropType.COLOR)
    )
}

class RadioButtonCodec : WidgetCodec<RadioButtonWidget> {
    override val typeName = "radio_button"
    override val widgetClass = RadioButtonWidget::class.java

    override fun create(props: JsonObject) = RadioButtonWidget()

    override fun encodeProps(widget: RadioButtonWidget) = JsonObject().apply {
        addProperty("id", widget.id)
    }

    override fun decodeProps(widget: RadioButtonWidget, props: JsonObject) {
        props.get("id")?.asInt?.let { widget.setId(it) }
    }

    override val propertySchema = listOf(
        PropSpec("id", PropType.INT, -1f, 65536f)
    )
}

class ScrollBarCodec : WidgetCodec<ScrollBarWidget> {
    override val typeName = "scroll_bar"
    override val widgetClass = ScrollBarWidget::class.java

    /**
     * panel 不参与序列化, 反序列化后需由调用方手动绑定.
     */
    override fun create(props: JsonObject): ScrollBarWidget {
        val orientation = props.get("orientation")?.asString?.let { Orientation.valueOf(it) }
            ?: Orientation.VERTICAL
        return ScrollBarWidget(orientation)
    }

    override fun encodeProps(widget: ScrollBarWidget) = JsonObject().apply {
        addProperty("orientation", widget.getDragBarOrientation().name)
        addProperty("show_background", widget.isShowBackground)
        addProperty("thumb_color", widget.thumbColor)
        addProperty("track_color", widget.trackColor)
    }

    override fun decodeProps(widget: ScrollBarWidget, props: JsonObject) {
        props.get("show_background")?.asBoolean?.let { widget.setShowBackground(it) }
        props.get("thumb_color")?.asInt?.let { widget.setThumbColor(it) }
        props.get("track_color")?.asInt?.let { widget.setTrackColor(it) }
    }

    override val propertySchema = listOf(
        PropSpec("orientation", PropType.ENUM, options = listOf("HORIZONTAL", "VERTICAL")),
        PropSpec("show_background", PropType.BOOLEAN),
        PropSpec("thumb_color", PropType.COLOR),
        PropSpec("track_color", PropType.COLOR)
    )
}

class SpriteSheetCodec : WidgetCodec<SpriteSheetWidget> {
    override val typeName = "sprite_sheet"
    override val widgetClass = SpriteSheetWidget::class.java

    override fun create(props: JsonObject): SpriteSheetWidget {
        return SpriteSheetWidget(
            requireIdentifier(props.get("texture")?.asString, typeName),
            props.get("orientation")?.asString?.let { Orientation.valueOf(it) } ?: Orientation.HORIZONTAL,
            props.get("sheet_width")?.asInt ?: 1,
            props.get("sheet_height")?.asInt ?: 1,
            props.get("frame_width")?.asInt ?: 1,
            props.get("frame_height")?.asInt ?: 1,
            props.get("frame_count")?.asInt ?: 1
        )
    }

    override fun encodeProps(widget: SpriteSheetWidget) = JsonObject().apply {
        encodeIdentifier(widget.getTextureLocation())?.let { addProperty("texture", it) }
        addProperty("orientation", widget.getSpriteSheetOrientation().name)
        addProperty("sheet_width", widget.getSpriteSheetWidth())
        addProperty("sheet_height", widget.getSpriteSheetHeight())
        addProperty("frame_width", widget.getSpriteSheetFrameWidth())
        addProperty("frame_height", widget.getSpriteSheetFrameHeight())
        addProperty("frame_count", widget.getSpriteSheetFrameCount())
        addProperty("frame_index", widget.frameIndex)
    }

    override fun decodeProps(widget: SpriteSheetWidget, props: JsonObject) {
        val idx = props.get("frame_index")?.asInt
        if (idx != null) {
            widget.frameIndex = idx.coerceIn(0, widget.getSpriteSheetFrameCount() - 1)
        }
    }

    override val propertySchema = listOf(
        PropSpec("texture", PropType.IDENTIFIER),
        PropSpec("orientation", PropType.ENUM, options = listOf("HORIZONTAL", "VERTICAL")),
        PropSpec("sheet_width", PropType.INT, 1f, 4096f),
        PropSpec("sheet_height", PropType.INT, 1f, 4096f),
        PropSpec("frame_width", PropType.INT, 1f, 4096f),
        PropSpec("frame_height", PropType.INT, 1f, 4096f),
        PropSpec("frame_count", PropType.INT, 1f, 4096f),
        PropSpec("frame_index", PropType.INT, 0f, 4096f)
    )
}

class BlendQuadCodec : WidgetCodec<BlendQuadWidget> {
    override val typeName = "blend_quad"
    override val widgetClass = BlendQuadWidget::class.java

    override fun create(props: JsonObject) = BlendQuadWidget()

    override fun encodeProps(widget: BlendQuadWidget) = JsonObject().apply {
        addProperty("margin_left", widget.marginLeft)
        addProperty("margin_top", widget.marginTop)
        addProperty("margin_right", widget.marginRight)
        addProperty("margin_bottom", widget.marginBottom)
        addProperty("draw_line", widget.drawLine)
        addProperty("red", widget.red)
        addProperty("green", widget.green)
        addProperty("blue", widget.blue)
    }

    override fun decodeProps(widget: BlendQuadWidget, props: JsonObject) {
        props.get("margin_left")?.asFloat?.let { widget.marginLeft = it }
        props.get("margin_top")?.asFloat?.let { widget.marginTop = it }
        props.get("margin_right")?.asFloat?.let { widget.marginRight = it }
        props.get("margin_bottom")?.asFloat?.let { widget.marginBottom = it }
        props.get("draw_line")?.asBoolean?.let { widget.drawLine = it }
        props.get("red")?.asFloat?.let { widget.red = it }
        props.get("green")?.asFloat?.let { widget.green = it }
        props.get("blue")?.asFloat?.let { widget.blue = it }
    }

    override val propertySchema = listOf(
        PropSpec("margin_left", PropType.FLOAT, 0f, 256f),
        PropSpec("margin_top", PropType.FLOAT, 0f, 256f),
        PropSpec("margin_right", PropType.FLOAT, 0f, 256f),
        PropSpec("margin_bottom", PropType.FLOAT, 0f, 256f),
        PropSpec("draw_line", PropType.BOOLEAN),
        PropSpec("red", PropType.FLOAT, 0f, 1f),
        PropSpec("green", PropType.FLOAT, 0f, 1f),
        PropSpec("blue", PropType.FLOAT, 0f, 1f)
    )
}
