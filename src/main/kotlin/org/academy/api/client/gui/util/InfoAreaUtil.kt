package org.academy.api.client.gui.util

import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.RenderRoot
import org.academy.api.client.gui.widget.*
import java.util.*

object InfoAreaUtil {
    fun create(ui: RenderRoot, left: Float, top: Float): LinearLayoutWidget {
        val infoArea = FrameLayoutWidget()
        infoArea.layoutParams = FrameLayoutWidget.LayoutParams()
            .margin(left, top, 0f, 0f)
            .sizeMode(SizeMode.WRAP_CONTENT)
        ui.root.addChild("area_info", infoArea)
        infoArea.startAnimation(
            ObjectAnimator.ofFloat(
                { alpha: Float? -> infoArea.alpha = alpha!! },
                0f,
                1f
            ).setDuration(600)
        )
        infoArea.startAnimation(ObjectAnimator.ofFloat({ translationY ->
            infoArea.translationY = translationY

        }, 20f, 0f).setDuration(600).setInterpolator(EasingFunctions.EASE_OUT_CUBIC))
        run {
            val back = BlendQuadWidget()
            back.alpha = 0.5f
            back.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
            infoArea.addChild("back", back)

            val info = LinearLayoutWidget()
            info.setOrientation(Orientation.VERTICAL)
            info.layoutParams = FrameLayoutWidget.LayoutParams()
                .marginTop(6.5f)
                .marginBottom(6.5f)
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
            info.setSpacing(2f)
            infoArea.addChild("info", info)
            return info
        }
    }

    fun createInfoRow(
        labelText: String,
        iconName: String,
        iconColor: Int,
        valueLabel: LabelWidget
    ): LinearLayoutWidget {
        val layout = LinearLayoutWidget()
        layout.setOrientation(Orientation.HORIZONTAL)
        layout.setSpacing(4f)
        layout.layoutParams = WidgetContainer.LayoutParams()
            .width(128f)
            .heightMode(SizeMode.WRAP_CONTENT)
            .padding(6.5f, 0f, 10f, 0f)
        run {
            val icon = FillWidget(iconColor)
            icon.layoutParams = LinearLayoutWidget.LayoutParams()
                .gravity(Gravity.CENTER_VERTICAL)
                .size(6.5f, 6.5f)
            layout.addChild(iconName, icon)

            val label = LabelWidget(labelText)
            label.scale = 0.75f
            label.layoutParams = LinearLayoutWidget.LayoutParams()
                .gravity(Gravity.CENTER_VERTICAL)
            layout.addChild(labelText.lowercase(Locale.getDefault()) + "_label", label)

            val empty = EmptyWidget()
            empty.layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .heightMode(SizeMode.MATCH_PARENT)
            layout.addChild("empty", empty)

            valueLabel.scale = 0.75f
            layout.addChild(labelText.lowercase(Locale.getDefault()) + "_value_label", valueLabel)
        }
        return layout
    }

    fun createAttributeRow(labelText: String, valueWidget: Widget): LinearLayoutWidget {
        val layout = LinearLayoutWidget()
        layout.setOrientation(Orientation.HORIZONTAL)
        layout.layoutParams = WidgetContainer.LayoutParams()
            .width(128f)
            .heightMode(SizeMode.WRAP_CONTENT)
            .padding(10f, 0f)
        run {
            val label = LabelWidget(labelText)
            label.layoutParams = LinearLayoutWidget.LayoutParams()
                .height(LabelWidget.DEFAULT_BASE_FONT_SIZE)
                .gravity(Gravity.CENTER_VERTICAL)
            layout.addChild(labelText + "_label", label)

            val empty = EmptyWidget()
            empty.layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .heightMode(SizeMode.MATCH_PARENT)
            layout.addChild("empty", empty)
            layout.addChild("value_widget", valueWidget)
        }
        return layout
    }

    fun createInputRow(textBox: TextBoxWidget): LinearLayoutWidget {
        val layout = LinearLayoutWidget()
        layout.setOrientation(Orientation.HORIZONTAL)
        run {
            val leftBracket = LabelWidget("[")
            leftBracket.layoutParams = LinearLayoutWidget.LayoutParams()
                .gravity(Gravity.CENTER_VERTICAL)
            layout.addChild("bracket_left", leftBracket)

            textBox.layoutParams = LinearLayoutWidget.LayoutParams()
                .width(48f)
                .heightMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER)
            layout.addChild("textbox", textBox)

            val rightBracket = LabelWidget("]")
            rightBracket.layoutParams = LinearLayoutWidget.LayoutParams()
                .gravity(Gravity.CENTER_VERTICAL)
            layout.addChild("bracket_right", rightBracket)
        }
        return layout
    }
}