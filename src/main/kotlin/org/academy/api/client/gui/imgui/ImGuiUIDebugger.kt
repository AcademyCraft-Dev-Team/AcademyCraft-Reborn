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
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*

object ImGuiUIDebugger {
    private val SIZE_MODE_NAMES = arrayOf("FIXED", "MATCH_PARENT", "WRAP_CONTENT")
    private val ORIENTATION_NAMES = arrayOf("HORIZONTAL", "VERTICAL")
    private val VISIBILITY_NAMES = arrayOf("VISIBLE", "INVISIBLE", "GONE")

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
            if (ImGui.collapsingHeader("Transform")) {
                renderTransform(widget)
            }
            if (ImGui.collapsingHeader("Appearance")) {
                renderAppearance(widget)
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

        val coverAllPrev = ImBoolean(widget.coverAllPrev)
        if (ImGui.checkbox("CoverAllPrev", coverAllPrev)) {
            widget.coverAllPrev = coverAllPrev.get()
        }

        val enabled = ImBoolean(widget.isEnabled)
        if (ImGui.checkbox("Enabled", enabled)) {
            widget.isEnabled = enabled.get()
        }

        val clickable = ImBoolean(widget.isClickable)
        if (ImGui.checkbox("Clickable", clickable)) {
            widget.isClickable = clickable.get()
        }

        val selected = ImBoolean(widget.isSelected)
        if (ImGui.checkbox("Selected", selected)) {
            widget.isSelected = selected.get()
        }

        val currentVisibility = ImInt(widget.visibility.ordinal)
        if (ImGui.combo("Visibility", currentVisibility, VISIBILITY_NAMES)) {
            widget.visibility = Widget.Visibility.entries[currentVisibility.get()]
            widget.requestLayout()
        }

        if (ImGui.button("Force Focus")) {
            widget.parent?.focusedChild = widget
        }
        ImGui.sameLine()
        if (ImGui.button("Request Layout")) {
            widget.requestLayout()
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

    private fun renderTransform(widget: Widget) {
        var changed = false

        val translation = floatArrayOf(widget.translationX, widget.translationY)
        if (ImGui.dragFloat2("Translation", translation, 0.5f)) {
            widget.translationX = translation[0]
            widget.translationY = translation[1]
            changed = true
        }

        val scaleArr = floatArrayOf(widget.scaleX, widget.scaleY)
        if (ImGui.dragFloat2("Scale", scaleArr, 0.01f, 0.01f, 10.0f)) {
            widget.scaleX = scaleArr[0]
            widget.scaleY = scaleArr[1]
            changed = true
        }

        val uniformScale = floatArrayOf(widget.scale)
        if (ImGui.dragFloat("Uniform Scale", uniformScale, 0.01f, 0.01f, 10.0f)) {
            widget.scale = uniformScale[0]
            changed = true
        }

        val rotation = floatArrayOf(widget.rotation)
        if (ImGui.dragFloat("Rotation", rotation, 1.0f, -360.0f, 360.0f)) {
            widget.rotation = rotation[0]
            changed = true
        }

        val origin = floatArrayOf(widget.originX, widget.originY)
        if (ImGui.dragFloat2("Transform Origin", origin, 0.5f)) {
            widget.originX = origin[0]
            widget.originY = origin[1]
            changed = true
        }

        val scroll = floatArrayOf(widget.scrollX, widget.scrollY)
        if (ImGui.dragFloat2("Scroll", scroll, 0.5f)) {
            widget.scrollTo(scroll[0], scroll[1])
            changed = true
        }

        if (changed) {
            widget.invalidate()
        }
    }

    private fun renderAppearance(widget: Widget) {
        val alpha = floatArrayOf(widget.alpha)
        if (ImGui.sliderFloat("Alpha", alpha, 0.0f, 1.0f)) {
            widget.alpha = alpha[0]
        }

        val hasBackground = widget.background != null
        ImGui.text("Background: ${if (hasBackground) widget.background!!.javaClass.simpleName else "None"}")
        val hasForeground = widget.foreground != null
        ImGui.text("Foreground: ${if (hasForeground) widget.foreground!!.javaClass.simpleName else "None"}")
        ImGui.text("StateListAnimator: ${if (widget.stateListAnimator != null) "Present" else "None"}")
    }

    private fun renderWidgetSpecificProperties(widget: Widget) {
        if (widget is LabelWidget) {
            val textBuffer = ImString(widget.text, 256)
            if (ImGui.inputText("Text", textBuffer)) {
                widget.text = textBuffer.get()
            }

            val scale = floatArrayOf(widget.scale)
            if (ImGui.dragFloat("Font Scale", scale, 0.05f, 0.1f, 5.0f)) {
                widget.scale = scale[0]
            }
        }

        if (widget is ButtonWidget) {
            ImGui.text("OnClickListener: ${if (widget.onClickListener != null) "Set" else "None"}")
            val pressed = ImBoolean(widget.isPressed)
            ImGui.checkbox("Pressed State", pressed) // readonly effectively
        }

        if (widget is ImageWidget) {
            val rgb = floatArrayOf(widget.brightness, widget.green, widget.blue)
            if (ImGui.colorEdit3("Image Tint", rgb)) {
                widget.setColor(rgb[0], rgb[1], rgb[2])
            }

            if (ImGui.treeNode("UV Coordinates")) {
                val uv0 = floatArrayOf(widget.u0, widget.v0)
                val uv1 = floatArrayOf(widget.u1, widget.v1)
                val uv2 = floatArrayOf(widget.u2, widget.v2)
                val uv3 = floatArrayOf(widget.u3, widget.v3)

                var uvChanged = false
                if (ImGui.dragFloat2("Top-Left (u0,v0)", uv0, 0.001f)) uvChanged = true
                if (ImGui.dragFloat2("Bottom-Left (u1,v1)", uv1, 0.001f)) uvChanged = true
                if (ImGui.dragFloat2("Bottom-Right (u2,v2)", uv2, 0.001f)) uvChanged = true
                if (ImGui.dragFloat2("Top-Right (u3,v3)", uv3, 0.001f)) uvChanged = true

                if (uvChanged) {
                    widget.setUv(uv0[0], uv0[1], uv1[0], uv1[1], uv2[0], uv2[1], uv3[0], uv3[1])
                }
                if (ImGui.button("Rotate UV")) {
                    widget.rotateUv()
                }
                ImGui.treePop()
            }
        }

        if (widget is FillWidget) {
            val color = colorToFloat4(widget.color)
            if (ImGui.colorEdit4("Fill Color", color)) {
                widget.setColor(float4ToColor(color))
            }
        }

        if (widget is LinearLayoutWidget) {
            val currentOrientation = ImInt(widget.orientation.ordinal)
            if (ImGui.combo("Orientation", currentOrientation, ORIENTATION_NAMES)) {
                widget.orientation = if (currentOrientation.get() == 0) Orientation.HORIZONTAL else Orientation.VERTICAL
                widget.requestLayout()
            }

            val spacing = floatArrayOf(widget.spacing)
            if (ImGui.dragFloat("Spacing", spacing, 0.5f)) {
                widget.spacing = spacing[0]
                widget.requestLayout()
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
        ImGui.text(String.format("Measured Size (W/H): %.2f, %.2f", widget.measuredWidth, widget.measuredHeight))
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
        ImGui.text(String.format("Pressed: %b", widget.isPressed))
        ImGui.text(String.format("Selected: %b", widget.isSelected))
        ImGui.separator()
        val parent = widget.parent
        ImGui.text(String.format("Parent: %s", parent?.name ?: "None"))
        ImGui.text(String.format("Attached: %b", widget.isAttached()))
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
