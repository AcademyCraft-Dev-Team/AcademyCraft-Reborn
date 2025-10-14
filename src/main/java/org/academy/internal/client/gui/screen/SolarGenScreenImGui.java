package org.academy.internal.client.gui.screen;

import imgui.ImGui;
import imgui.type.ImBoolean;
import net.minecraft.client.Minecraft;
import org.academy.internal.client.gui.imgui.ImGuiUtilInternal;

public final class SolarGenScreenImGui {
    private static ImBoolean showDemoWindow = new ImBoolean(false);

    public static void render() {
        final var mainRenderTarget = Minecraft.getInstance().getMainRenderTarget();
        ImGuiUtilInternal.render(mainRenderTarget, () -> {
            if (ImGui.begin("Hello, World!")) {
                ImGui.setWindowSize(800, 600);
                ImGui.checkbox("Show Demo Window", showDemoWindow);
                ImGui.end();
            }

            if (showDemoWindow.get()) ImGui.showDemoWindow();
        });
    }

    private SolarGenScreenImGui() {
    }
}