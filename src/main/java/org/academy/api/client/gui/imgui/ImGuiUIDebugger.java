package org.academy.api.client.gui.imgui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.util.ARGB;
import org.academy.api.client.gui.widget.Widget;
import org.academy.api.client.gui.widget.WidgetContainer;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.widget.FillWidget;
import org.academy.api.client.gui.widget.LabelWidget;
import org.academy.api.client.gui.widget.LinearLayoutWidget;
import org.jspecify.annotations.Nullable;

public final class ImGuiUIDebugger {
    private static final String[] SIZE_MODE_NAMES = {"FIXED", "MATCH_PARENT", "WRAP_CONTENT"};
    private static final String[] ORIENTATION_NAMES = {"HORIZONTAL", "VERTICAL"};

    private ImGuiUIDebugger() {
    }

    public static void render(RenderTarget renderTarget, WidgetContainer root) {
        ImGuiUtilApi.render(renderTarget, () -> {
            if (ImGui.begin("ImGui UI Debugger")) {
                ImGui.setWindowSize(450, 700, ImGuiCond.FirstUseEver);
                renderWidgetNode(root, root.getHoveredWidget());
            }
            ImGui.end();
        });
    }

    private static void renderWidgetNode(Widget widget, @Nullable Widget hoveredWidget) {
        var nodeFlags = ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.FramePadding;
        var isHovered = (widget == hoveredWidget);

        if (isHovered) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 1.0f, 0.0f, 1.0f);
        }

        var nodeOpen = ImGui.treeNodeEx(widget.getName() + " (" + widget.getClass().getSimpleName() + ")", nodeFlags);

        if (isHovered) {
            ImGui.popStyleColor();
        }

        if (nodeOpen) {
            ImGui.indent();
            if (ImGui.collapsingHeader("Basic Properties")) {
                renderBasicProperties(widget);
            }
            if (ImGui.collapsingHeader("Layout Properties")) {
                renderLayoutParams(widget);
            }
            if (ImGui.collapsingHeader("Widget-Specific Properties")) {
                renderWidgetSpecificProperties(widget);
            }
            if (ImGui.collapsingHeader("Read-only Info")) {
                renderReadOnlyInfo(widget);
            }
            ImGui.unindent();


            if (widget instanceof WidgetContainer container) {
                ImGui.separator();
                for (var child : container.getChildren().values()) {
                    renderWidgetNode(child, hoveredWidget);
                }
            }
            ImGui.treePop();
        }
    }

    private static void renderBasicProperties(Widget widget) {
        var nameBuffer = new ImString(widget.getName(), 256);
        if (ImGui.inputText("Name", nameBuffer)) {
            widget.setName(nameBuffer.get());
        }

        var zPos = new float[]{widget.getZ()};
        if (ImGui.dragFloat("Z", zPos, 0.1f)) {
            widget.setZ(zPos[0]);
        }

        var translation = new float[]{widget.getTranslationX(), widget.getTranslationY()};
        if (ImGui.dragFloat2("Translation (X/Y)", translation, 0.5f)) {
            widget.setTranslationX(translation[0]);
            widget.setTranslationY(translation[1]);
        }

        ImGui.sameLine();
        var enabled = new ImBoolean(widget.isEnabled());
        if (ImGui.checkbox("Enabled", enabled)) {
            widget.setEnabled(enabled.get());
        }

        var alpha = new float[]{widget.getAlpha()};
        if (ImGui.sliderFloat("Alpha", alpha, 0.0f, 1.0f)) {
            widget.setAlpha(alpha[0]);
        }

        if (ImGui.button("Force Focus") && widget.getParent() != null) {
            widget.getParent().setFocusedChild(widget);
        }
    }

    private static void renderLayoutParams(Widget widget) {
        var lp = widget.getLayoutParams();
        var changed = false;

        var currentWidthMode = new ImInt(lp.widthMode.ordinal());
        if (ImGui.combo("Width Mode", currentWidthMode, SIZE_MODE_NAMES)) {
            lp.widthMode = SizeMode.values()[currentWidthMode.get()];
            changed = true;
        }

        if (lp.widthMode == SizeMode.FIXED) {
            var width = new float[]{lp.width};
            if (ImGui.dragFloat("Fixed Width", width, 0.5f)) {
                lp.width = width[0];
                changed = true;
            }
        }

        var currentHeightMode = new ImInt(lp.heightMode.ordinal());
        if (ImGui.combo("Height Mode", currentHeightMode, SIZE_MODE_NAMES)) {
            lp.heightMode = SizeMode.values()[currentHeightMode.get()];
            changed = true;
        }

        if (lp.heightMode == SizeMode.FIXED) {
            var height = new float[]{lp.height};
            if (ImGui.dragFloat("Fixed Height", height, 0.5f)) {
                lp.height = height[0];
                changed = true;
            }
        }

        if (lp instanceof LinearLayoutWidget.LayoutParams linearLp) {
            var weight = new float[]{linearLp.weight};
            if (ImGui.dragFloat("Weight", weight, 0.1f, 0.0f, 10.0f)) {
                linearLp.weight = weight[0];
                changed = true;
            }
        }

        if (ImGui.treeNode("Gravity")) {
            var gravity = new ImInt(lp.gravity);
            ImGui.text("Horizontal:");
            ImGui.sameLine();
            changed |= gravityRadio("LEFT", gravity, Gravity.LEFT, Gravity.HORIZONTAL_GRAVITY_MASK);
            ImGui.sameLine();
            changed |= gravityRadio("CENTER_H", gravity, Gravity.CENTER_HORIZONTAL, Gravity.HORIZONTAL_GRAVITY_MASK);
            ImGui.sameLine();
            changed |= gravityRadio("RIGHT", gravity, Gravity.RIGHT, Gravity.HORIZONTAL_GRAVITY_MASK);

            ImGui.text("Vertical:  ");
            ImGui.sameLine();
            changed |= gravityRadio("TOP", gravity, Gravity.TOP, Gravity.VERTICAL_GRAVITY_MASK);
            ImGui.sameLine();
            changed |= gravityRadio("CENTER_V", gravity, Gravity.CENTER_VERTICAL, Gravity.VERTICAL_GRAVITY_MASK);
            ImGui.sameLine();
            changed |= gravityRadio("BOTTOM", gravity, Gravity.BOTTOM, Gravity.VERTICAL_GRAVITY_MASK);

            lp.gravity = gravity.get();
            ImGui.treePop();
        }

        var margin = new float[]{lp.marginTop, lp.marginRight, lp.marginBottom, lp.marginLeft};
        if (ImGui.dragFloat4("Margin (T/R/B/L)", margin, 0.5f)) {
            lp.marginTop = margin[0];
            lp.marginRight = margin[1];
            lp.marginBottom = margin[2];
            lp.marginLeft = margin[3];
            changed = true;
        }

        var padding = new float[]{lp.paddingTop, lp.paddingRight, lp.paddingBottom, lp.paddingLeft};
        if (ImGui.dragFloat4("Padding (T/R/B/L)", padding, 0.5f)) {
            lp.paddingTop = padding[0];
            lp.paddingRight = padding[1];
            lp.paddingBottom = padding[2];
            lp.paddingLeft = padding[3];
            changed = true;
        }

        if (changed) {
            widget.requestLayout();
        }
    }

    private static void renderWidgetSpecificProperties(Widget widget) {
        if (widget instanceof LabelWidget label) {
            var textBuffer = new ImString(label.getText(), 256);
            if (ImGui.inputText("Text", textBuffer)) {
                label.setText(textBuffer.get());
            }

            var color = colorToFloat4(label.getColor());
            if (ImGui.colorEdit4("Color", color)) {
                label.setColor(float4ToColor(color));
            }

            var scale = new float[]{label.getScale()};
            if (ImGui.dragFloat("Scale", scale, 0.05f, 0.1f, 5.0f)) {
                label.setScale(scale[0]);
            }
        }

        if (widget instanceof FillWidget fillWidget) {
            var color = colorToFloat4(fillWidget.getColor());
            if (ImGui.colorEdit4("Fill Color", color)) {
                fillWidget.setColor(float4ToColor(color));
            }
        }
    }

    private static void renderReadOnlyInfo(Widget widget) {
        ImGui.text(String.format("Class: %s", widget.getClass().getName()));
        ImGui.separator();
        ImGui.text(String.format("Layout Pos (X/Y): %.2f, %.2f", widget.getX(), widget.getY()));
        ImGui.text(String.format("Layout Size (W/H): %.2f, %.2f", widget.getWidth(), widget.getHeight()));
        ImGui.text(String.format("Visual Pos (X/Y): %.2f, %.2f", widget.getX() + widget.getTranslationX(), widget.getY() + widget.getTranslationY()));
        ImGui.separator();
        ImGui.text(String.format("Measured Size (W/H): %.2f, %.2f", widget.getMeasuredWidth(), widget.getMeasuredHeight()));
        ImGui.separator();
        ImGui.text(String.format("Absolute Layout Pos (X/Y): %.2f, %.2f", widget.getAbsoluteX(), widget.getAbsoluteY()));
        ImGui.text(String.format("Absolute Translation (X/Y): %.2f, %.2f", widget.getAbsoluteTranslationX(), widget.getAbsoluteTranslationY()));
        ImGui.text(String.format("Absolute Alpha: %.2f", widget.getAbsoluteAlpha()));
        ImGui.text(String.format("Absolute Enabled: %b", widget.isAbsoluteEnabled()));
        ImGui.separator();
        ImGui.text(String.format("Focused: %b", widget.isFocused()));
        ImGui.text(String.format("Hovered: %b", widget.isHovered()));
        ImGui.separator();
        var parent = widget.getParent();
        ImGui.text(String.format("Parent: %s", parent != null ? parent.getName() : "None"));
    }

    private static boolean checkboxFlags(String label, ImInt flags, int flagValue) {
        var isSet = new ImBoolean((flags.get() & flagValue) == flagValue);
        if (ImGui.checkbox(label, isSet)) {
            if (isSet.get()) {
                flags.set(flags.get() | flagValue);
            } else {
                flags.set(flags.get() & ~flagValue);
            }
            return true;
        }
        return false;
    }

    private static boolean gravityRadio(String label, ImInt flags, int flagValue, int mask) {
        if (ImGui.radioButton(label, (flags.get() & mask) == flagValue)) {
            flags.set((flags.get() & ~mask) | flagValue);
            return true;
        }
        return false;
    }

    private static float[] colorToFloat4(int color) {
        return new float[]{
                ARGB.red(color) / 255f,
                ARGB.green(color) / 255f,
                ARGB.blue(color) / 255f,
                ARGB.alpha(color) / 255f
        };
    }

    private static int float4ToColor(float[] color) {
        return ARGB.color(
                (int) (color[3] * 255),
                (int) (color[0] * 255),
                (int) (color[1] * 255),
                (int) (color[2] * 255)
        );
    }
}