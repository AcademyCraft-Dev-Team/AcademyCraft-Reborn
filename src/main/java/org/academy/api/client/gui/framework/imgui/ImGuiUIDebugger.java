package org.academy.api.client.gui.framework.imgui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.client.gui.framework.WidgetContainer;
import org.academy.api.client.gui.imgui.ImGuiUtilApi;

public final class ImGuiUIDebugger {

    private ImGuiUIDebugger() {
    }

    public static void render(RenderTarget renderTarget, WidgetContainer root) {
        ImGuiUtilApi.render(renderTarget, () -> {
            if (ImGui.begin("ImGui UI Debugger")) {
                ImGui.setWindowSize(800, 600, imgui.flag.ImGuiCond.FirstUseEver);
                renderWidgetNode(root);
                ImGui.end();
            }
        });
    }

    private static void renderWidgetNode(Widget widget) {
        var flags = (widget instanceof WidgetContainer container && !container.getChildren().isEmpty())
                ? ImGuiTreeNodeFlags.None
                : ImGuiTreeNodeFlags.Leaf;

        var nodeOpen = ImGui.treeNodeEx(widget.getName() + " (" + widget.getClass().getSimpleName() + ")", flags);

        if (nodeOpen) {
            renderWidgetProperties(widget);
            if (widget instanceof WidgetContainer container) {
                for (var child : container.getChildren().values()) {
                    renderWidgetNode(child);
                }
            }
            ImGui.treePop();
        }
    }

    private static void renderWidgetProperties(Widget widget) {
        var nameBuffer = new ImString(widget.getName(), 256);
        if (ImGui.inputText("Name", nameBuffer)) {
            widget.setName(nameBuffer.get());
        }

        var position = new float[]{widget.getX(), widget.getY(), widget.getZ()};
        if (ImGui.dragFloat3("Position (X/Y/Z)", position)) {
            widget.setX(position[0]);
            widget.setY(position[1]);
            widget.setZ(position[2]);
        }

        var size = new float[]{widget.getWidth(), widget.getHeight()};
        if (ImGui.dragFloat2("Size (Width/Height)", size)) {
            widget.setWidth(size[0]);
            widget.setHeight(size[1]);
        }

        var visible = new ImBoolean(widget.isVisible());
        if (ImGui.checkbox("Visible", visible)) {
            widget.setVisible(visible.get());
        }
        ImGui.sameLine();
        var enabled = new ImBoolean(widget.isEnabled());
        if (ImGui.checkbox("Enabled", enabled)) {
            widget.setEnabled(enabled.get());
        }
        ImGui.sameLine();
        var clickable = new ImBoolean(widget.isClickable());
        if (ImGui.checkbox("Clickable", clickable)) {
            widget.setClickable(clickable.get());
        }

        var alpha = new float[]{widget.getAlpha()};
        if (ImGui.sliderFloat("Alpha", alpha, 0.0f, 1.0f)) {
            widget.setAlpha(alpha[0]);
        }

        if (ImGui.collapsingHeader("Read-only Info")) {
            ImGui.text(String.format("Absolute X: %.2f", widget.getAbsoluteX()));
            ImGui.text(String.format("Absolute Y: %.2f", widget.getAbsoluteY()));
            ImGui.text(String.format("Absolute Alpha: %.2f", widget.getAbsoluteAlpha()));
            ImGui.text(String.format("Absolute Enabled: %b", widget.isAbsoluteEnabled()));
            ImGui.separator();
            ImGui.text(String.format("Focused: %b", widget.isFocused()));
            ImGui.text(String.format("Hovered: %b", widget.isHovered()));
            ImGui.text(String.format("Can Focus: %b", widget.canFocus()));
            ImGui.separator();
            var parent = widget.getParent();
            ImGui.text(String.format("Parent: %s", parent != null ? parent.getName() : "None"));
        }
    }
}