package org.academy.internal.client.gui.screen;

import imgui.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.academy.api.client.gui.imgui.ImGuiUtilApi;

public class SolarGenScreen extends Screen {
    public SolarGenScreen() {
        super(Component.empty());
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        final var mainRenderTarget = Minecraft.getInstance().getMainRenderTarget();
        ImGuiUtilApi.render(mainRenderTarget, () -> {
            if (ImGui.begin("Hello, World!")) {
                ImGui.setWindowSize(800, 600);
                ImGui.end();
            }

            ImGui.showDemoWindow();
        });
    }
}