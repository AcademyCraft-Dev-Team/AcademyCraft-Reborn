package org.academy.api.client.gui.imgui

import com.mojang.blaze3d.pipeline.RenderTarget
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import org.academy.AcademyCraft
import org.academy.api.client.gui.editor.UiLayoutEditorScreen
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.serialize.WidgetSerializer
import org.academy.api.client.gui.widget.*
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.*

object ImGuiUIDebugger {
    fun render(renderTarget: RenderTarget, root: WidgetContainer) {
        ImGuiUtilApi.render(renderTarget) { renderContent(root) }
    }

    /** ImGui 帧内的内容部分, 供 [org.academy.api.client.gui.screen.ScreenDispatcher] 与其他窗口共享同一帧. */
    fun renderContent(root: WidgetContainer, lockNames: Boolean = false, title: String? = null) {
        if (ImGui.begin((title ?: tr("screen.academy.ui_debug.inspector.title")) + "##academy_ui_inspector")) {
            ImGui.setWindowSize(450f, 700f, ImGuiCond.FirstUseEver)
            if (ImGui.button(tr("screen.academy.ui_debug.inspector.export_json"))) {
                exportLayout(root)
            }
            ImGui.sameLine()
            if (ImGui.button(tr("screen.academy.ui_debug.inspector.open_editor"))) {
                UiLayoutEditorScreen.open()
            }
            ImGui.separator()
            renderWidgetNode(root, root.hoveredWidget, lockNames)
        }
        ImGui.end()
    }

    private fun exportLayout(root: WidgetContainer) {
        try {
            val dir = WidgetSerializer.layoutDir().resolve("dump")
            Files.createDirectories(dir)
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
            val file = dir.resolve("layout-$stamp.json")
            WidgetSerializer.export(root, file)
            AcademyCraft.getLogger().info("[UiLayout] Exported current screen layout to {}", file)
        } catch (e: Exception) {
            AcademyCraft.getLogger().error("[UiLayout] Failed to export layout", e)
        }
    }

    private fun renderWidgetNode(widget: Widget, hoveredWidget: Widget?, lockNames: Boolean) {
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
            if (ImGui.collapsingHeader(tr("screen.academy.ui_debug.inspector.section.basic"))) {
                renderBasicProperties(widget, lockNames)
            }
            if (ImGui.collapsingHeader(tr("screen.academy.ui_debug.inspector.section.layout"))) {
                renderLayoutParams(widget)
            }
            if (ImGui.collapsingHeader(tr("screen.academy.ui_debug.inspector.section.transform"))) {
                renderTransform(widget)
            }
            if (ImGui.collapsingHeader(tr("screen.academy.ui_debug.inspector.section.appearance"))) {
                renderAppearance(widget)
            }
            if (ImGui.collapsingHeader(tr("screen.academy.ui_debug.inspector.section.widget"))) {
                renderWidgetSpecificProperties(widget)
            }
            if (ImGui.collapsingHeader(tr("screen.academy.ui_debug.inspector.section.read_only"))) {
                renderReadOnlyInfo(widget)
            }
            ImGui.unindent()

            if (widget is WidgetContainer) {
                ImGui.separator()
                for (child in widget.children.values) {
                    renderWidgetNode(child, hoveredWidget, lockNames)
                }
            }
            ImGui.treePop()
        }
    }

    private fun renderBasicProperties(widget: Widget, lockNames: Boolean) {
        if (lockNames) {
            ImGui.textDisabled(tr("screen.academy.ui_debug.inspector.name_value", widget.name))
        } else {
            val nameBuffer = ImString(widget.name, 256)
            if (ImGui.inputText(label("screen.academy.ui_debug.inspector.name", "name"), nameBuffer)) {
                widget.name = nameBuffer.get()
            }
        }

        val coverAllPrev = ImBoolean(widget.coverAllPrev)
        if (ImGui.checkbox(label("screen.academy.ui_debug.inspector.cover_previous", "cover_previous"), coverAllPrev)) {
            widget.coverAllPrev = coverAllPrev.get()
        }

        val enabled = ImBoolean(widget.isEnabled)
        if (ImGui.checkbox(label("screen.academy.ui_debug.inspector.enabled", "enabled"), enabled)) {
            widget.isEnabled = enabled.get()
        }

        val clickable = ImBoolean(widget.isClickable)
        if (ImGui.checkbox(label("screen.academy.ui_debug.inspector.clickable", "clickable"), clickable)) {
            widget.isClickable = clickable.get()
        }

        val selected = ImBoolean(widget.isSelected)
        if (ImGui.checkbox(label("screen.academy.ui_debug.inspector.selected", "selected"), selected)) {
            widget.isSelected = selected.get()
        }

        val currentVisibility = ImInt(widget.visibility.ordinal)
        if (ImGui.combo(
                label("screen.academy.ui_debug.inspector.visibility", "visibility"), currentVisibility,
                visibilityNames()
            )
        ) {
            widget.visibility = Widget.Visibility.entries[currentVisibility.get()]
            widget.requestLayout()
        }

        if (ImGui.button(tr("screen.academy.ui_debug.inspector.force_focus"))) {
            widget.parent?.focusedChild = widget
        }
        ImGui.sameLine()
        if (ImGui.button(tr("screen.academy.ui_debug.inspector.request_layout"))) {
            widget.requestLayout()
        }
    }

    private fun renderLayoutParams(widget: Widget) {
        val lp = widget.layoutParams
        var changed = false

        val currentWidthMode = ImInt(lp.widthMode.ordinal)
        if (ImGui.combo(
                label("screen.academy.ui_debug.inspector.width_mode", "width_mode"), currentWidthMode,
                sizeModeNames()
            )
        ) {
            lp.widthMode = SizeMode.entries[currentWidthMode.get()]
            changed = true
        }

        if (lp.widthMode == SizeMode.FIXED) {
            val width = floatArrayOf(lp.width)
            if (ImGui.dragFloat(label("screen.academy.ui_debug.inspector.fixed_width", "fixed_width"), width, 0.5f)) {
                lp.width = width[0]
                changed = true
            }
        }

        val currentHeightMode = ImInt(lp.heightMode.ordinal)
        if (ImGui.combo(
                label("screen.academy.ui_debug.inspector.height_mode", "height_mode"), currentHeightMode,
                sizeModeNames()
            )
        ) {
            lp.heightMode = SizeMode.entries[currentHeightMode.get()]
            changed = true
        }

        if (lp.heightMode == SizeMode.FIXED) {
            val height = floatArrayOf(lp.height)
            if (ImGui.dragFloat(
                    label("screen.academy.ui_debug.inspector.fixed_height", "fixed_height"),
                    height,
                    0.5f
                )
            ) {
                lp.height = height[0]
                changed = true
            }
        }

        if (lp is LinearLayoutWidget.LayoutParams) {
            val weight = floatArrayOf(lp.weight)
            if (ImGui.dragFloat(
                    label("screen.academy.ui_debug.inspector.weight", "weight"),
                    weight,
                    0.1f,
                    0.0f,
                    10.0f
                )
            ) {
                lp.weight = weight[0]
                changed = true
            }
        }

        if (ImGui.treeNode(tr("screen.academy.ui_debug.inspector.gravity") + "##gravity")) {
            val gravity = ImInt(lp.gravity)
            ImGui.text(tr("screen.academy.ui_debug.inspector.horizontal"))
            ImGui.sameLine()
            changed = changed or gravityRadio(
                tr("screen.academy.ui_debug.direction.left") + "##left", gravity,
                Gravity.LEFT, Gravity.HORIZONTAL_GRAVITY_MASK
            )
            ImGui.sameLine()
            changed =
                changed or gravityRadio(
                    tr("screen.academy.ui_debug.direction.center") + "##center_h", gravity,
                    Gravity.CENTER_HORIZONTAL, Gravity.HORIZONTAL_GRAVITY_MASK
                )
            ImGui.sameLine()
            changed = changed or gravityRadio(
                tr("screen.academy.ui_debug.direction.right") + "##right", gravity,
                Gravity.RIGHT, Gravity.HORIZONTAL_GRAVITY_MASK
            )

            ImGui.text(tr("screen.academy.ui_debug.inspector.vertical"))
            ImGui.sameLine()
            changed = changed or gravityRadio(
                tr("screen.academy.ui_debug.direction.top") + "##top", gravity,
                Gravity.TOP, Gravity.VERTICAL_GRAVITY_MASK
            )
            ImGui.sameLine()
            changed =
                changed or gravityRadio(
                    tr("screen.academy.ui_debug.direction.center") + "##center_v", gravity,
                    Gravity.CENTER_VERTICAL, Gravity.VERTICAL_GRAVITY_MASK
                )
            ImGui.sameLine()
            changed = changed or gravityRadio(
                tr("screen.academy.ui_debug.direction.bottom") + "##bottom", gravity,
                Gravity.BOTTOM, Gravity.VERTICAL_GRAVITY_MASK
            )

            lp.gravity = gravity.get()
            ImGui.treePop()
        }

        val margin = floatArrayOf(lp.marginTop, lp.marginRight, lp.marginBottom, lp.marginLeft)
        if (ImGui.dragFloat4(label("screen.academy.ui_debug.inspector.margin", "margin"), margin, 0.5f)) {
            lp.marginTop = margin[0]
            lp.marginRight = margin[1]
            lp.marginBottom = margin[2]
            lp.marginLeft = margin[3]
            changed = true
        }

        val padding = floatArrayOf(lp.paddingTop, lp.paddingRight, lp.paddingBottom, lp.paddingLeft)
        if (ImGui.dragFloat4(label("screen.academy.ui_debug.inspector.padding", "padding"), padding, 0.5f)) {
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
        if (ImGui.dragFloat2(
                label("screen.academy.ui_debug.inspector.translation", "translation"),
                translation,
                0.5f
            )
        ) {
            widget.translationX = translation[0]
            widget.translationY = translation[1]
            changed = true
        }

        val scaleArr = floatArrayOf(widget.scaleX, widget.scaleY)
        if (ImGui.dragFloat2(
                label("screen.academy.ui_debug.inspector.scale", "scale"),
                scaleArr,
                0.01f,
                0.01f,
                10.0f
            )
        ) {
            widget.scaleX = scaleArr[0]
            widget.scaleY = scaleArr[1]
            changed = true
        }

        val uniformScale = floatArrayOf(widget.scale)
        if (ImGui.dragFloat(
                label("screen.academy.ui_debug.inspector.uniform_scale", "uniform_scale"),
                uniformScale, 0.01f, 0.01f, 10.0f
            )
        ) {
            widget.scale = uniformScale[0]
            changed = true
        }

        val rotation = floatArrayOf(widget.rotation)
        if (ImGui.dragFloat(
                label("screen.academy.ui_debug.inspector.rotation", "rotation"),
                rotation, 1.0f, -360.0f, 360.0f
            )
        ) {
            widget.rotation = rotation[0]
            changed = true
        }

        val origin = floatArrayOf(widget.originX, widget.originY)
        if (ImGui.dragFloat2(label("screen.academy.ui_debug.inspector.origin", "origin"), origin, 0.5f)) {
            widget.originX = origin[0]
            widget.originY = origin[1]
            changed = true
        }

        val scroll = floatArrayOf(widget.scrollX, widget.scrollY)
        if (ImGui.dragFloat2(label("screen.academy.ui_debug.inspector.scroll", "scroll"), scroll, 0.5f)) {
            widget.scrollTo(scroll[0], scroll[1])
            changed = true
        }

        if (changed) {
            widget.invalidate()
        }
    }

    private fun renderAppearance(widget: Widget) {
        val alpha = floatArrayOf(widget.alpha)
        if (ImGui.sliderFloat(label("screen.academy.ui_debug.inspector.alpha", "alpha"), alpha, 0.0f, 1.0f)) {
            widget.alpha = alpha[0]
        }

        val hasBackground = widget.background != null
        ImGui.text(
            tr(
                "screen.academy.ui_debug.inspector.background",
                if (hasBackground) widget.background!!.javaClass.simpleName else tr("screen.academy.ui_debug.value.none")
            )
        )
        val hasForeground = widget.foreground != null
        ImGui.text(
            tr(
                "screen.academy.ui_debug.inspector.foreground",
                if (hasForeground) widget.foreground!!.javaClass.simpleName else tr("screen.academy.ui_debug.value.none")
            )
        )
        ImGui.text(
            tr(
                "screen.academy.ui_debug.inspector.animator",
                tr(if (widget.stateListAnimator != null) "screen.academy.ui_debug.value.present" else "screen.academy.ui_debug.value.none")
            )
        )
    }

    private fun renderWidgetSpecificProperties(widget: Widget) {
        if (widget is LabelWidget) {
            val textBuffer = ImString(widget.text, 256)
            if (ImGui.inputText(label("screen.academy.ui_debug.inspector.text", "text"), textBuffer)) {
                widget.text = textBuffer.get()
            }

            val scale = floatArrayOf(widget.scale)
            if (ImGui.dragFloat(
                    label("screen.academy.ui_debug.inspector.font_scale", "font_scale"),
                    scale, 0.05f, 0.1f, 5.0f
                )
            ) {
                widget.scale = scale[0]
            }
        }

        if (widget is ButtonWidget) {
            ImGui.text(
                tr(
                    "screen.academy.ui_debug.inspector.click_listener",
                    tr(if (widget.onClickListener != null) "screen.academy.ui_debug.value.set" else "screen.academy.ui_debug.value.none")
                )
            )
            val pressed = ImBoolean(widget.isPressed)
            ImGui.checkbox(label("screen.academy.ui_debug.inspector.pressed_state", "pressed_state"), pressed)
        }

        if (widget is ImageWidget) {
            val rgb = floatArrayOf(widget.brightness, widget.green, widget.blue)
            if (ImGui.colorEdit3(label("screen.academy.ui_debug.inspector.image_tint", "image_tint"), rgb)) {
                widget.setColor(rgb[0], rgb[1], rgb[2])
            }

            if (ImGui.treeNode(tr("screen.academy.ui_debug.inspector.uv") + "##uv")) {
                val uv0 = floatArrayOf(widget.u0, widget.v0)
                val uv1 = floatArrayOf(widget.u1, widget.v1)
                val uv2 = floatArrayOf(widget.u2, widget.v2)
                val uv3 = floatArrayOf(widget.u3, widget.v3)

                var uvChanged = false
                if (ImGui.dragFloat2(
                        label("screen.academy.ui_debug.inspector.uv_top_left", "uv0"),
                        uv0,
                        0.001f
                    )
                ) uvChanged = true
                if (ImGui.dragFloat2(
                        label("screen.academy.ui_debug.inspector.uv_bottom_left", "uv1"),
                        uv1,
                        0.001f
                    )
                ) uvChanged = true
                if (ImGui.dragFloat2(
                        label("screen.academy.ui_debug.inspector.uv_bottom_right", "uv2"),
                        uv2,
                        0.001f
                    )
                ) uvChanged = true
                if (ImGui.dragFloat2(
                        label("screen.academy.ui_debug.inspector.uv_top_right", "uv3"),
                        uv3,
                        0.001f
                    )
                ) uvChanged = true

                if (uvChanged) {
                    widget.setUv(uv0[0], uv0[1], uv1[0], uv1[1], uv2[0], uv2[1], uv3[0], uv3[1])
                }
                if (ImGui.button(tr("screen.academy.ui_debug.inspector.rotate_uv"))) {
                    widget.rotateUv()
                }
                ImGui.treePop()
            }
        }

        if (widget is FillWidget) {
            val color = colorToFloat4(widget.color)
            if (ImGui.colorEdit4(label("screen.academy.ui_debug.inspector.fill_color", "fill_color"), color)) {
                widget.setColor(float4ToColor(color))
            }
        }

        if (widget is LinearLayoutWidget) {
            val currentOrientation = ImInt(widget.orientation.ordinal)
            if (ImGui.combo(
                    label("screen.academy.ui_debug.inspector.orientation", "orientation"),
                    currentOrientation, orientationNames()
                )
            ) {
                widget.orientation = if (currentOrientation.get() == 0) Orientation.HORIZONTAL else Orientation.VERTICAL
                widget.requestLayout()
            }

            val spacing = floatArrayOf(widget.spacing)
            if (ImGui.dragFloat(label("screen.academy.ui_debug.inspector.spacing", "spacing"), spacing, 0.5f)) {
                widget.spacing = spacing[0]
                widget.requestLayout()
            }
        }
    }

    private fun renderReadOnlyInfo(widget: Widget) {
        ImGui.text(tr("screen.academy.ui_debug.inspector.info.class", widget.javaClass.name))
        ImGui.separator()
        ImGui.text(tr("screen.academy.ui_debug.inspector.info.layout_position", format(widget.x), format(widget.y)))
        ImGui.text(
            tr(
                "screen.academy.ui_debug.inspector.info.layout_size",
                format(widget.width),
                format(widget.height)
            )
        )
        ImGui.text(
            tr(
                "screen.academy.ui_debug.inspector.info.visual_position",
                format(widget.x + widget.translationX), format(widget.y + widget.translationY)
            )
        )
        ImGui.separator()
        ImGui.text(
            tr(
                "screen.academy.ui_debug.inspector.info.measured_size",
                format(widget.measuredWidth), format(widget.measuredHeight)
            )
        )
        ImGui.separator()
        ImGui.text(
            tr(
                "screen.academy.ui_debug.inspector.info.absolute_position",
                format(widget.getAbsoluteX()), format(widget.getAbsoluteY())
            )
        )
        ImGui.text(
            tr(
                "screen.academy.ui_debug.inspector.info.absolute_translation",
                format(widget.getAbsoluteTranslationX()), format(widget.getAbsoluteTranslationY())
            )
        )
        ImGui.text(tr("screen.academy.ui_debug.inspector.info.absolute_alpha", format(widget.getAbsoluteAlpha())))
        ImGui.text(
            tr(
                "screen.academy.ui_debug.inspector.info.absolute_enabled",
                booleanText(widget.isAbsoluteEnabled())
            )
        )
        ImGui.separator()
        ImGui.text(tr("screen.academy.ui_debug.inspector.info.focused", booleanText(widget.isFocused)))
        ImGui.text(tr("screen.academy.ui_debug.inspector.info.hovered", booleanText(widget.isHovered)))
        ImGui.text(tr("screen.academy.ui_debug.inspector.info.pressed", booleanText(widget.isPressed)))
        ImGui.text(tr("screen.academy.ui_debug.inspector.info.selected", booleanText(widget.isSelected)))
        ImGui.separator()
        val parent = widget.parent
        ImGui.text(
            tr(
                "screen.academy.ui_debug.inspector.info.parent",
                parent?.name ?: tr("screen.academy.ui_debug.value.none")
            )
        )
        ImGui.text(tr("screen.academy.ui_debug.inspector.info.attached", booleanText(widget.isAttached())))
    }

    private fun sizeModeNames() = arrayOf(
        tr("screen.academy.ui_debug.size_mode.fixed"),
        tr("screen.academy.ui_debug.size_mode.match_parent"),
        tr("screen.academy.ui_debug.size_mode.wrap_content")
    )

    private fun orientationNames() = arrayOf(
        tr("screen.academy.ui_debug.orientation.horizontal"),
        tr("screen.academy.ui_debug.orientation.vertical")
    )

    private fun visibilityNames() = arrayOf(
        tr("screen.academy.ui_debug.visibility.visible"),
        tr("screen.academy.ui_debug.visibility.invisible"),
        tr("screen.academy.ui_debug.visibility.gone")
    )

    private fun booleanText(value: Boolean): String = tr(
        if (value) "screen.academy.ui_debug.value.yes" else "screen.academy.ui_debug.value.no"
    )

    private fun format(value: Float): String = String.format(Locale.ROOT, "%.2f", value)

    private fun label(key: String, id: String): String = tr(key) + "##$id"

    private fun tr(key: String, vararg args: Any): String = Component.translatable(key, *args).string

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
