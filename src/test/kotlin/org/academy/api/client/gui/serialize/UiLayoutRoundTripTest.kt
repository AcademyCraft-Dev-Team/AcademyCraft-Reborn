package org.academy.api.client.gui.serialize

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.ButtonWidget
import org.academy.api.client.gui.widget.FillWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.ImageWidget
import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.ProgressBarWidget
import org.academy.api.client.gui.widget.RadioButtonWidget
import org.academy.api.client.gui.widget.RadioGroupWidget
import org.academy.api.client.gui.widget.ScrollBarWidget
import org.academy.api.client.gui.widget.ScrollPanelWidget
import org.academy.api.client.gui.widget.SeekBarWidget
import org.academy.api.client.gui.widget.SpriteSheetWidget
import org.academy.api.client.gui.widget.TextBoxWidget
import org.academy.api.client.gui.widget.ToggleButtonWidget
import org.academy.api.client.gui.widget.WheelPickerWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UiLayoutRoundTripTest {

    private fun buildSampleTree(): WidgetContainer {
        val root = FrameLayoutWidget().apply {
            name = "root"
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.9f
            translationX = 3f
            scaleY = 0.8f
        }

        val content = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 4f
            setGravity(Gravity.CENTER_HORIZONTAL)
            setWeightSum(2f)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .padding(8f)
                .margin(2f)
        }
        root.addChild("content", content)

        content.addChild("title", LabelWidget("Hello").apply {
            baseFontSize = 12f
            isEnabled = false
            tooltipText = "tooltip"
        })
        content.addChild("fill", FillWidget(0xFF112233.toInt()).apply {
            visibility = Widget.Visibility.INVISIBLE
        })
        content.addChild("image", ImageWidget().apply {
            setColor(1f, 0.5f, 0.25f)
            setUv(0.1f, 0.2f, 0.3f, 0.4f)
        })
        content.addChild("toggle", ToggleButtonWidget().apply {
            setChecked(true)
            setTrackColor(0xFF0000)
            setCheckedThumbColor(0xFFFFFF)
        })
        content.addChild("progress", ProgressBarWidget().apply {
            setMax(100f)
            setMin(0f)
            setProgress(42f)
            setOrientation(Orientation.VERTICAL)
            setProgressColor(0x00FF00)
        })
        content.addChild("seek", SeekBarWidget().apply {
            setMax(50f)
            setProgress(10f)
            setKeyProgressIncrement(5)
        })
        content.addChild("textbox", TextBoxWidget(32).apply {
            setAllowLineBreak(true)
            text = "abc"
        })

        val radioGroup = RadioGroupWidget().apply {
            allowReselect = true
        }
        radioGroup.addChild("opt1", RadioButtonWidget())
        radioGroup.addChild("opt2", RadioButtonWidget())
        content.addChild("radio_group", radioGroup)

        val button = ButtonWidget().apply {
            addChild("label", LabelWidget("Click"))
        }
        content.addChild("button", button)

        val picker = WheelPickerWidget().apply {
            visibleItemCount = 3
            isCyclic = true
            isCurtain = true
            isIndicator = true
            itemAlign = WheelPickerWidget.ItemAlign.LEFT
            addChild("item0", LabelWidget("0"))
            addChild("item1", LabelWidget("1"))
            addChild("item2", LabelWidget("2"))
        }
        content.addChild("picker", picker)

        val panel = ScrollPanelWidget(Orientation.VERTICAL).apply {
            setScrollSpeed(36f)
        }
        panel.addChild("content", LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            addChild("a", LabelWidget("A"))
            addChild("b", LabelWidget("B"))
        })
        root.addChild("scroll", panel)

        val bar = ScrollBarWidget(Orientation.VERTICAL).apply {
            setThumbColor(0x112233)
            setTrackColor(0x445566)
            setShowBackground(false)
        }
        root.addChild("bar", bar)

        val sprite = SpriteSheetWidget(
            Identifier.parse("academy:textures/gui/element/line.png"),
            Orientation.HORIZONTAL, 64, 16, 16, 16, 4
        )
        sprite.frameIndex = 2
        root.addChild("sprite", sprite)

        return root
    }

    @Test
    fun `round trip keeps layout structure and properties`() {
        val root = buildSampleTree()
        val first = WidgetSerializer.encode(root)

        val decoded = WidgetSerializer.decode(first)
        assertTrue(decoded is WidgetContainer)

        val second = WidgetSerializer.encode(decoded as WidgetContainer)
        assertEquals(first, second, "encode(decode(encode(w))) must equal encode(w)")
    }

    @Test
    fun `round trip through pretty json string`() {
        val root = buildSampleTree()
        val json = WidgetSerializer.toPrettyJson(root)
        assertTrue(json.contains('\n'), "output must be pretty printed")

        val decoded = WidgetSerializer.fromJsonString(json) as WidgetContainer
        val reparsed = WidgetSerializer.encode(decoded)
        assertEquals(WidgetSerializer.encode(root), reparsed)
    }

    @Test
    fun `decoded tree keeps hierarchy and names`() {
        val root = buildSampleTree()
        val decoded = WidgetSerializer.decode(WidgetSerializer.encode(root)) as WidgetContainer

        assertEquals("content", decoded.children["content"]?.name)
        val content = decoded.children["content"] as LinearLayoutWidget
        assertEquals(Orientation.VERTICAL, content.orientation)
        assertEquals(4f, content.spacing)
        assertEquals(Gravity.CENTER_HORIZONTAL, content.getLayoutGravity())
        assertEquals(2f, content.getLayoutWeightSum())

        val title = content.children["title"] as LabelWidget
        assertEquals("Hello", title.text)
        assertEquals(12f, title.baseFontSize)

        val textbox = content.children["textbox"] as TextBoxWidget
        assertEquals("abc", textbox.text)
        assertEquals(32, textbox.getTextMaxLength())

        val picker = content.children["picker"] as WheelPickerWidget
        assertEquals(3, picker.visibleItemCount)
        assertTrue(picker.isCyclic)
        assertEquals(WheelPickerWidget.ItemAlign.LEFT, picker.itemAlign)
        assertEquals(3, picker.children.size)

        val scrollPanel = decoded.children["scroll"] as ScrollPanelWidget
        assertEquals(Orientation.VERTICAL, scrollPanel.getPanelOrientation())
        assertEquals(36f, scrollPanel.getPanelScrollSpeed())
        assertEquals(1, scrollPanel.children.size)

        val sprite = decoded.children["sprite"] as SpriteSheetWidget
        assertEquals(2, sprite.frameIndex)
        assertEquals(4, sprite.getSpriteSheetFrameCount())
    }

    @Test
    fun `weight on linear layout child survives round trip`() {
        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
        }
        val weighted = LabelWidget("w").apply {
            layoutParams = LinearLayoutWidget.LayoutParams().weight(3f).height(0f)
        }
        column.addChild("weighted", weighted)

        val decoded = WidgetSerializer.decode(WidgetSerializer.encode(column)) as LinearLayoutWidget
        val lp = decoded.children["weighted"]!!.layoutParams as LinearLayoutWidget.LayoutParams
        assertEquals(3f, lp.weight)
        assertEquals(SizeMode.FIXED, lp.heightMode)
    }

    @Test
    fun `decoding a non-container root must not corrupt the shared NONE layout params`() {
        val json = """
            {
              "version": 1,
              "root": {
                "type": "label", "name": "root",
                "layout": { "width_mode": "FIXED", "width": 100, "gravity": 48 }
              }
            }
        """.trimIndent()

        val decoded = WidgetSerializer.fromJsonString(json)
        assertTrue(decoded is LabelWidget)
        assertEquals(100f, decoded.layoutParams.width)
        assertEquals(48, decoded.layoutParams.gravity)

        val none = WidgetContainer.LayoutParams.NONE
        assertEquals(SizeMode.WRAP_CONTENT, none.widthMode)
        assertEquals(0f, none.width)
        assertEquals(Gravity.TOP_LEFT, none.gravity)
    }

    @Test
    fun `scroll bar panel must be bound manually after load`() {
        val root = buildSampleTree()
        val decoded = WidgetSerializer.decode(WidgetSerializer.encode(root)) as WidgetContainer

        val bar = decoded.children["bar"] as ScrollBarWidget
        assertTrue(bar.panel == null, "panel must not be auto-wired by serialization")

        bar.panel = decoded.children["scroll"] as ScrollPanelWidget
        assertEquals(decoded.children["scroll"], bar.panel)
    }
}
