package org.academy.api.client.gui.serialize.codecs

import com.google.gson.JsonObject
import org.academy.api.client.gui.serialize.PropSpec
import org.academy.api.client.gui.serialize.PropType
import org.academy.api.client.gui.serialize.WidgetCodec
import org.academy.api.client.gui.widget.*

class LabelCodec : WidgetCodec<LabelWidget> {
    override val typeName = "label"
    override val widgetClass = LabelWidget::class.java

    override fun create(props: JsonObject) = LabelWidget(props.get("text")?.asString ?: "")

    override fun encodeProps(widget: LabelWidget) = JsonObject().apply {
        addProperty("text", widget.text)
        addProperty("base_font_size", widget.baseFontSize)
    }

    override fun decodeProps(widget: LabelWidget, props: JsonObject) {
        props.get("text")?.asString?.let { widget.text = it }
        props.get("base_font_size")?.asFloat?.let { widget.baseFontSize = it }
    }

    override val propertySchema = listOf(
        PropSpec("text", PropType.TEXT),
        PropSpec("base_font_size", PropType.FLOAT, 1f, 64f)
    )
}

class TextBoxCodec : WidgetCodec<TextBoxWidget> {
    override val typeName = "text_box"
    override val widgetClass = TextBoxWidget::class.java

    override fun create(props: JsonObject) = TextBoxWidget(props.get("max_length")?.asInt ?: 64)

    override fun encodeProps(widget: TextBoxWidget) = JsonObject().apply {
        addProperty("max_length", widget.getTextMaxLength())
        addProperty("text", widget.text)
        addProperty("allow_line_break", widget.allowLineBreak)
    }

    override fun decodeProps(widget: TextBoxWidget, props: JsonObject) {
        props.get("text")?.asString?.let { widget.text = it }
        props.get("allow_line_break")?.asBoolean?.let { widget.setAllowLineBreak(it) }
    }

    override val propertySchema = listOf(
        PropSpec("max_length", PropType.INT, 1f, 1024f),
        PropSpec("text", PropType.TEXT),
        PropSpec("allow_line_break", PropType.BOOLEAN)
    )
}

class ImageCodec : WidgetCodec<ImageWidget> {
    override val typeName = "image"
    override val widgetClass = ImageWidget::class.java

    override fun create(props: JsonObject) = ImageWidget()

    override fun encodeProps(widget: ImageWidget) = JsonObject().apply {
        encodeIdentifier(widget.getTextureLocation())?.let { addProperty("texture", it) }
        addProperty("u0", widget.u0)
        addProperty("v0", widget.v0)
        addProperty("u1", widget.u1)
        addProperty("v1", widget.v1)
        addProperty("u2", widget.u2)
        addProperty("v2", widget.v2)
        addProperty("u3", widget.u3)
        addProperty("v3", widget.v3)
        addProperty("red", widget.brightness)
        addProperty("green", widget.green)
        addProperty("blue", widget.blue)
    }

    override fun decodeProps(widget: ImageWidget, props: JsonObject) {
        props.get("texture")?.asString?.let { widget.setTexture(decodeIdentifier(it)) }
        val u0 = props.get("u0")?.asFloat ?: widget.u0
        val v0 = props.get("v0")?.asFloat ?: widget.v0
        val u1 = props.get("u1")?.asFloat ?: widget.u1
        val v1 = props.get("v1")?.asFloat ?: widget.v1
        val u2 = props.get("u2")?.asFloat ?: widget.u2
        val v2 = props.get("v2")?.asFloat ?: widget.v2
        val u3 = props.get("u3")?.asFloat ?: widget.u3
        val v3 = props.get("v3")?.asFloat ?: widget.v3
        widget.setUv(u0, v0, u1, v1, u2, v2, u3, v3)
        val r = props.get("red")?.asFloat ?: widget.brightness
        val g = props.get("green")?.asFloat ?: widget.green
        val b = props.get("blue")?.asFloat ?: widget.blue
        widget.setColor(r, g, b)
    }

    override val propertySchema = listOf(
        PropSpec("texture", PropType.IDENTIFIER),
        PropSpec("red", PropType.FLOAT, 0f, 1f),
        PropSpec("green", PropType.FLOAT, 0f, 1f),
        PropSpec("blue", PropType.FLOAT, 0f, 1f)
    )
}

class MonochromeImageCodec : WidgetCodec<MonochromeImageWidget> {
    override val typeName = "monochrome_image"
    override val widgetClass = MonochromeImageWidget::class.java

    override fun create(props: JsonObject) =
        MonochromeImageWidget(requireIdentifier(props.get("texture")?.asString, typeName))

    override fun encodeProps(widget: MonochromeImageWidget) = JsonObject().apply {
        encodeIdentifier(widget.getTextureLocation())?.let { addProperty("texture", it) }
    }

    override fun decodeProps(widget: MonochromeImageWidget, props: JsonObject) {
    }

    override val propertySchema = listOf(
        PropSpec("texture", PropType.IDENTIFIER)
    )
}

class ParallaxImageCodec : WidgetCodec<ParallaxImageWidget> {
    override val typeName = "parallax_image"
    override val widgetClass = ParallaxImageWidget::class.java

    override fun create(props: JsonObject) =
        ParallaxImageWidget(requireIdentifier(props.get("texture")?.asString, typeName))

    override fun encodeProps(widget: ParallaxImageWidget) = JsonObject().apply {
        encodeIdentifier(widget.getTextureLocation())?.let { addProperty("texture", it) }
    }

    override fun decodeProps(widget: ParallaxImageWidget, props: JsonObject) {
    }

    override val propertySchema = listOf(
        PropSpec("texture", PropType.IDENTIFIER)
    )
}

class FillCodec : WidgetCodec<FillWidget> {
    override val typeName = "fill"
    override val widgetClass = FillWidget::class.java

    override fun create(props: JsonObject) = FillWidget(props.get("color")?.asInt ?: 0)

    override fun encodeProps(widget: FillWidget) = JsonObject().apply {
        addProperty("color", widget.color)
    }

    override fun decodeProps(widget: FillWidget, props: JsonObject) {
        props.get("color")?.asInt?.let { widget.setColor(it) }
    }

    override val propertySchema = listOf(
        PropSpec("color", PropType.COLOR)
    )
}

class EmptyCodec : WidgetCodec<EmptyWidget> {
    override val typeName = "empty"
    override val widgetClass = EmptyWidget::class.java

    override fun create(props: JsonObject) = EmptyWidget()

    override fun encodeProps(widget: EmptyWidget) = JsonObject()

    override fun decodeProps(widget: EmptyWidget, props: JsonObject) {
    }

    override val propertySchema = emptyList<PropSpec>()
}

class ButtonCodec : WidgetCodec<ButtonWidget> {
    override val typeName = "button"
    override val widgetClass = ButtonWidget::class.java

    override fun create(props: JsonObject) = ButtonWidget()

    override fun encodeProps(widget: ButtonWidget) = JsonObject()

    override fun decodeProps(widget: ButtonWidget, props: JsonObject) {
    }

    override val propertySchema = emptyList<PropSpec>()
}
