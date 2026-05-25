package org.academy.api.client.gui.imgui

import com.mojang.blaze3d.pipeline.RenderTarget
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import net.minecraft.util.ARGB
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*

object ImGuiUIDebugger {
    private val SIZE_MODE_NAMES = arrayOf("FIXED", "MATCH_PARENT", "WRAP_CONTENT")
    private val ORIENTATION_NAMES = arrayOf("HORIZONTAL", "VERTICAL")

    fun render(renderTarget: RenderTarget, root: WidgetContainer) {
        ImGuiUtilApi.render(renderTarget) {
            if (ImGui.begin("ImGui UI Debugger")) {
                ImGui.setWindowSize(450f, 700f, ImGuiCond.FirstUseEver)
                renderWidgetNode(root, root.hoveredWidget)
            }
            ImGui.end()
        }
    }

    private fun renderWidgetNode(widget: Widget, hoveredWidget: Widget?) {
        val nodeFlags = ImGuiTreeNodeFlags.DefaultOpen or ImGuiTreeNodeFlags.FramePadding
        val isHovered = (widget === hoveredWidget)

        if (isHovered) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 1.0f, 0.0f, 1.0f)
        }

        val nodeOpen = ImGui.treeNodeEx(widget.name + " (" + widget.javaClass.getSimpleName() + ")", nodeFlags)

        if (isHovered) {
            ImGui.popStyleColor()
        }

        if (nodeOpen) {
            ImGui.indent()
            if (ImGui.collapsingHeader("Basic Properties")) {
                renderBasicProperties(widget)
            }
            if (ImGui.collapsingHeader("Layout Properties")) {
                renderLayoutParams(widget)
            }
            if (ImGui.collapsingHeader("Widget-Specific Properties")) {
                renderWidgetSpecificProperties(widget)
            }
            if (ImGui.collapsingHeader("Read-only Info")) {
                renderReadOnlyInfo(widget)
            }
            ImGui.unindent()


            if (widget is WidgetContainer) {
                ImGui.separator()
                for (child in widget.children.values) {
                    renderWidgetNode(child, hoveredWidget)
                }
            }
            ImGui.treePop()
        }
    }

    private fun renderBasicProperties(widget: Widget) {
        val nameBuffer = ImString(widget.name, 256)
        if (ImGui.inputText("Name", nameBuffer)) {
            widget.name = nameBuffer.get()
        }

        val zPos = floatArrayOf(widget.z)
        if (ImGui.dragFloat("Z", zPos, 0.1f)) {
            widget.z = zPos[0]
        }

        val translation = floatArrayOf(widget.translationX, widget.translationY)
        if (ImGui.dragFloat2("Translation (X/Y)", translation, 0.5f)) {
            widget.translationX = translation[0]
            widget.translationY = translation[1]
        }

        ImGui.sameLine()
        val enabled = ImBoolean(widget.isEnabled)
        if (ImGui.checkbox("Enabled", enabled)) {
            widget.isEnabled = enabled.get()
        }

        val alpha = floatArrayOf(widget.alpha)
        if (ImGui.sliderFloat("Alpha", alpha, 0.0f, 1.0f)) {
            widget.alpha = alpha[0]
        }

        if (ImGui.button("Force Focus")) {
            widget.parent?.focusedChild = widget
        }
    }

    private fun renderLayoutParams(widget: Widget) {
        val lp = widget.layoutParams
        var changed = false

        val currentWidthMode = ImInt(lp.widthMode.ordinal)
        if (ImGui.combo("Width Mode", currentWidthMode, SIZE_MODE_NAMES)) {
            lp.widthMode = SizeMode.entries[currentWidthMode.get()]
            changed = true
        }

        if (lp.widthMode == SizeMode.FIXED) {
            val width = floatArrayOf(lp.width)
            if (ImGui.dragFloat("Fixed Width", width, 0.5f)) {
                lp.width = width[0]
                changed = true
            }
        }

        val currentHeightMode = ImInt(lp.heightMode.ordinal)
        if (ImGui.combo("Height Mode", currentHeightMode, SIZE_MODE_NAMES)) {
            lp.heightMode = SizeMode.entries[currentHeightMode.get()]
            changed = true
        }

        if (lp.heightMode == SizeMode.FIXED) {
            val height = floatArrayOf(lp.height)
            if (ImGui.dragFloat("Fixed Height", height, 0.5f)) {
                lp.height = height[0]
                changed = true
            }
        }

        if (lp is LinearLayoutWidget.LayoutParams) {
            val weight = floatArrayOf(lp.weight)
            if (ImGui.dragFloat("Weight", weight, 0.1f, 0.0f, 10.0f)) {
                lp.weight = weight[0]
                changed = true
            }
        }

        if (ImGui.treeNode("Gravity")) {
            val gravity = ImInt(lp.gravity)
            ImGui.text("Horizontal:")
            ImGui.sameLine()
            changed = changed or gravityRadio("LEFT", gravity, Gravity.LEFT, Gravity.HORIZONTAL_GRAVITY_MASK)
            ImGui.sameLine()
            changed =
                changed or gravityRadio("CENTER_H", gravity, Gravity.CENTER_HORIZONTAL, Gravity.HORIZONTAL_GRAVITY_MASK)
            ImGui.sameLine()
            changed = changed or gravityRadio("RIGHT", gravity, Gravity.RIGHT, Gravity.HORIZONTAL_GRAVITY_MASK)

            ImGui.text("Vertical:  ")
            ImGui.sameLine()
            changed = changed or gravityRadio("TOP", gravity, Gravity.TOP, Gravity.VERTICAL_GRAVITY_MASK)
            ImGui.sameLine()
            changed =
                changed or gravityRadio("CENTER_V", gravity, Gravity.CENTER_VERTICAL, Gravity.VERTICAL_GRAVITY_MASK)
            ImGui.sameLine()
            changed = changed or gravityRadio("BOTTOM", gravity, Gravity.BOTTOM, Gravity.VERTICAL_GRAVITY_MASK)

            lp.gravity = gravity.get()
            ImGui.treePop()
        }

        val margin = floatArrayOf(lp.marginTop, lp.marginRight, lp.marginBottom, lp.marginLeft)
        if (ImGui.dragFloat4("Margin (T/R/B/L)", margin, 0.5f)) {
            lp.marginTop = margin[0]
            lp.marginRight = margin[1]
            lp.marginBottom = margin[2]
            lp.marginLeft = margin[3]
            changed = true
        }

        val padding = floatArrayOf(lp.paddingTop, lp.paddingRight, lp.paddingBottom, lp.paddingLeft)
        if (ImGui.dragFloat4("Padding (T/R/B/L)", padding, 0.5f)) {
            lp.paddingTop = padding[0]
            lp.paddingRight = padding[1]
            lp.paddingBottom = padding[2]
            lp.paddingLeft = padding[3]
            changed = true
        }

        if (changed) {
            widget.requestLayout()
        }
    }

    private fun renderWidgetSpecificProperties(widget: Widget) {
        if (widget is LabelWidget) {
            val textBuffer = ImString(widget.text, 256)
            if (ImGui.inputText("Text", textBuffer)) {
                widget.text = textBuffer.get()
            }

            val scale = floatArrayOf(widget.scale)
            if (ImGui.dragFloat("Scale", scale, 0.05f, 0.1f, 5.0f)) {
                widget.scale = scale[0]
            }
        }

        if (widget is FillWidget) {
            val color = colorToFloat4(widget.color)
            if (ImGui.colorEdit4("Fill Color", color)) {
                widget.setColor(float4ToColor(color))
            }
        }
    }

    private fun renderReadOnlyInfo(widget: Widget) {
        ImGui.text(String.format("Class: %s", widget.javaClass.getName()))
        ImGui.separator()
        ImGui.text(String.format("Layout Pos (X/Y): %.2f, %.2f", widget.x, widget.y))
        ImGui.text(String.format("Layout Size (W/H): %.2f, %.2f", widget.width, widget.height))
        ImGui.text(
            String.format(
                "Visual Pos (X/Y): %.2f, %.2f",
                widget.x + widget.translationX,
                widget.y + widget.translationY
            )
        )
        ImGui.separator()
        ImGui.text(
            String.format(
                "Measured Size (W/H): %.2f, %.2f",
                widget.measuredWidth,
                widget.measuredHeight
            )
        )
        ImGui.separator()
        ImGui.text(String.format("Absolute Layout Pos (X/Y): %.2f, %.2f", widget.getAbsoluteX(), widget.getAbsoluteY()))
        ImGui.text(
            String.format(
                "Absolute Translation (X/Y): %.2f, %.2f",
                widget.getAbsoluteTranslationX(),
                widget.getAbsoluteTranslationY()
            )
        )
        ImGui.text(String.format("Absolute Alpha: %.2f", widget.getAbsoluteAlpha()))
        ImGui.text(String.format("Absolute Enabled: %b", widget.isAbsoluteEnabled()))
        ImGui.separator()
        ImGui.text(String.format("Focused: %b", widget.isFocused))
        ImGui.text(String.format("Hovered: %b", widget.isHovered))
        ImGui.separator()
        val parent = widget.parent
        ImGui.text(String.format("Parent: %s", parent?.name ?: "None"))
    }

    private fun checkboxFlags(label: String, flags: ImInt, flagValue: Int): Boolean {
        val isSet = ImBoolean((flags.get() and flagValue) == flagValue)
        if (ImGui.checkbox(label, isSet)) {
            if (isSet.get()) {
                flags.set(flags.get() or flagValue)
            } else {
                flags.set(flags.get() and flagValue.inv())
            }
            return true
        }
        return false
    }

    private fun gravityRadio(label: String, flags: ImInt, flagValue: Int, mask: Int): Boolean {
        if (ImGui.radioButton(label, (flags.get() and mask) == flagValue)) {
            flags.set((flags.get() and mask.inv()) or flagValue)
            return true
        }
        return false
    }

    private fun colorToFloat4(color: Int): FloatArray {
        return floatArrayOf(
            ARGB.red(color) / 255f,
            ARGB.green(color) / 255f,
            ARGB.blue(color) / 255f,
            ARGB.alpha(color) / 255f
        )
    }

    private fun float4ToColor(color: FloatArray): Int {
        return ARGB.color(
            (color[3] * 255).toInt(),
            (color[0] * 255).toInt(),
            (color[1] * 255).toInt(),
            (color[2] * 255).toInt()
        )
    }
}