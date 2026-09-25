package org.academy.api.client.gui.util

import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.TextInputWidget
import org.academy.api.client.gui.widget.TextWidget
import org.academy.api.client.gui.widget.WidgetContainer
import java.util.*

fun WidgetContainer.infoArea(
    left: Float,
    top: Float,
    init: LinearLayoutWidget.() -> Unit = {}
): LinearLayoutWidget {
    val infoArea = frame("area_info") {
        lp {
            margin(left, top, 0f, 0f)
            sizeMode(SizeMode.WRAP_CONTENT)
        }
        blendQuad("back") {
            matchParent()
            alpha = 0.5f
        }
        startAnimation(
            ObjectAnimator.ofFloat({ value -> alpha = value }, 0f, 1f).setDuration(600)
        )
        startAnimation(
            ObjectAnimator.ofFloat({ value -> translationY = value }, 20f, 0f)
                .setDuration(600)
                .setInterpolator(EasingFunctions.EASE_OUT_CUBIC)
        )
    }
    return infoArea.column("info", spacing = 2f) {
        lp {
            marginTop(6.5f)
            marginBottom(6.5f)
            sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
        }
        init()
    }
}

fun WidgetContainer.infoRow(
    labelText: String,
    iconName: String,
    iconColor: Int,
    value: String,
    name: String = nextChildName("info_row"),
    valueName: String = labelText.lowercase(Locale.getDefault()) + "_value_label",
    init: TextWidget.() -> Unit = {}
): TextWidget {
    val prefix = labelText.lowercase(Locale.getDefault())
    lateinit var valueLabel: TextWidget
    val row = standaloneRow(spacing = 4f) {
        lp {
            width(128f)
            heightMode(SizeMode.WRAP_CONTENT)
            padding(8f, 0f, 10f, 0f)
        }
        fill(iconColor, iconName) {
            lp {
                gravity(Gravity.CENTER_VERTICAL)
                size(6f, 6f)
            }
        }
        text(labelText, prefix + "_label") {
            lp { gravity(Gravity.CENTER_VERTICAL) }
        }
        empty("empty") {
            lp {
                weight(1f)
                heightMode(SizeMode.MATCH_PARENT)
            }
        }
        valueLabel = text(value, valueName) {
            lp { gravity(Gravity.CENTER_VERTICAL) }
            init()
        }
    }
    add(name, row)
    return valueLabel
}

fun WidgetContainer.attributeRow(
    labelText: String,
    name: String = nextChildName("attribute_row"),
    value: WidgetContainer.() -> Unit
): LinearLayoutWidget {
    val row = standaloneRow {
        lp {
            width(128f)
            heightMode(SizeMode.WRAP_CONTENT)
            padding(10f, 0f)
        }
        text(labelText, labelText + "_label") {
            lp {
                gravity(Gravity.CENTER_VERTICAL)
            }
        }
        empty("empty") {
            lp {
                weight(1f)
                heightMode(SizeMode.MATCH_PARENT)
            }
        }
    }
    value(row)
    add(name, row)
    return row
}

fun WidgetContainer.inputRow(
    maxLength: Int = 12,
    name: String = nextChildName("text_box"),
    init: TextInputWidget.() -> Unit = {}
): LinearLayoutWidget {
    val row = standaloneRow {
        text("[", "bracket_left") {
            lp { gravity(Gravity.CENTER_VERTICAL) }
        }
        textBox(maxLength, name) {
            init()
            lp {
                width(48f)
                heightMode(SizeMode.MATCH_PARENT)
                gravity(Gravity.CENTER)
            }
        }
        text("]", "bracket_right") {
            lp { gravity(Gravity.CENTER_VERTICAL) }
        }
    }
    add(nextChildName("input_row"), row)
    return row
}

