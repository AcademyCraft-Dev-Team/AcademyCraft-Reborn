package org.academy.desktop.widgets

import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.serialize.PropSpec
import org.academy.api.client.gui.serialize.PropType
import org.academy.api.client.gui.serialize.currentArgb
import org.academy.api.client.gui.widget.ButtonWidget
import org.academy.api.client.gui.widget.FillWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.TextBoxWidget
import org.academy.api.client.gui.widget.ToggleButtonWidget
import org.academy.api.client.gui.widget.Widget

/**
 * Renders a property schema ([PropSpec] list) as editable rows using the
 * framework's own widgets. Each row shows the key and an editor appropriate to
 * the [PropType]. Edits are reported through [onChange]; malformed numeric/color
 * edits are reverted to the current value.
 */
class PropFormWidget : LinearLayoutWidget() {
    private var onChange: (key: String, value: String) -> Unit = { _, _ -> }
    private var getValue: (key: String) -> String = { _ -> "" }

    init {
        orientation = Orientation.VERTICAL
        isClickable = false
    }

    fun setForm(
        specs: List<PropSpec>,
        valueProvider: (key: String) -> String,
        changeHandler: (key: String, value: String) -> Unit
    ) {
        this.onChange = changeHandler
        this.getValue = valueProvider
        clearChildren()
        for (spec in specs) {
            addChild("row_${spec.key}", buildRow(spec))
        }
    }

    private fun buildRow(spec: PropSpec): Widget {
        val row = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            layoutParams = LinearLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.FIXED)
                .height(26f)
            spacing = 4f
        }
        val label = LabelWidget(spec.key).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(120f)
                .heightMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER_VERTICAL)
            baseFontSize = 12f
        }
        val editor = buildEditor(spec).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .heightMode(SizeMode.MATCH_PARENT)
        }
        row.addChild("label", label)
        row.addChild("editor", editor)
        return row
    }

    private fun buildEditor(spec: PropSpec): Widget {
        return when (spec.type) {
            PropType.BOOLEAN -> buildToggle(spec)
            PropType.ENUM -> buildEnum(spec)
            else -> buildTextField(spec)
        }
    }

    private fun buildToggle(spec: PropSpec): Widget {
        return ToggleButtonWidget().apply {
            isChecked = getValue(spec.key).toBooleanStrictOrNull() ?: false
            setOnCheckedChangeListener(object : ToggleButtonWidget.OnCheckedChangeListener {
                override fun onCheckedChanged(toggle: ToggleButtonWidget, isChecked: Boolean) {
                    onChange(spec.key, isChecked.toString())
                }
            })
        }
    }

    private fun buildEnum(spec: PropSpec): Widget {
        val options = spec.options.ifEmpty { listOf("true", "false") }
        val label = LabelWidget(getValue(spec.key).ifBlank { options.first() }).apply { baseFontSize = 12f }
        val button = ButtonWidget(label).apply { applyHoverState(this) }
        button.onClickListener = OnClickListener {
            val current = getValue(spec.key).ifBlank { options.first() }
            val index = options.indexOf(current)
            val next = options[(if (index < 0) 0 else (index + 1) % options.size)]
            label.text = next
            onChange(spec.key, next)
        }
        return button
    }

    private fun buildTextField(spec: PropSpec): Widget {
        val box = TextBoxWidget(256).apply {
            text = getValue(spec.key)
            placeholder = when (spec.type) {
                PropType.COLOR -> "#00000000"
                PropType.FLOAT, PropType.INT -> "0"
                else -> ""
            }
            baseFontSize = 12f
            background = ColorDrawable(0x40303030)
        }
        box.setOnFocusLost(Runnable {
            val raw = box.text
            val normalized = clampValue(spec, raw)
            if (normalized != null) {
                box.text = normalized
                onChange(spec.key, normalized)
            } else {
                box.text = getValue(spec.key)
            }
        })
        if (spec.type != PropType.COLOR) return box

        // Color row: a swatch preview next to the hex text field.
        val row = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT)
            spacing = 4f
        }
        val swatch = FillWidget(currentArgb(box.text)).apply {
            layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.FIXED, SizeMode.MATCH_PARENT).width(18f)
        }
        row.addChild("swatch", swatch)
        row.addChild("box", box.apply {
            layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).heightMode(SizeMode.MATCH_PARENT)
        })
        box.setOnFocusLost(Runnable {
            swatch.setColor(currentArgb(box.text))
            swatch.invalidate()
        })
        return row
    }

    private fun clampValue(spec: PropSpec, value: String): String? {
        return when (spec.type) {
            PropType.FLOAT -> {
                val f = value.trim().toFloatOrNull() ?: return null
                f.coerceIn(spec.min, spec.max).toString()
            }

            PropType.INT -> {
                val i = value.trim().toIntOrNull() ?: return null
                i.coerceIn(spec.min.toInt(), spec.max.toInt()).toString()
            }

            PropType.COLOR -> normalizeColor(value)

            else -> value
        }
    }

    private fun normalizeColor(raw: String): String? {
        val argb = currentArgb(raw)
        val s = raw.trim()
        if (s.startsWith("#")) {
            if (s.removePrefix("#").toLongOrNull(16) == null) return null
        } else if (s.toLongOrNull() == null) {
            return null
        }
        return "#%08X".format(argb)
    }
}
