package org.academy.internal.client.gui.world;

import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.widget.ImageButtonWidget;
import org.academy.api.client.gui.widget.ImageWidget;
import org.academy.api.client.gui.widget.PanelWidget;
import org.academy.api.client.renderer.RenderTypes;
import org.jetbrains.annotations.NotNull;

public class WindGenWorldGUI {
    public static final float WIDTH = 640;
    public static final float HEIGHT = 400;
    public final AbstractContainerWidget rootContainer = new PanelWidget(0, 0, WIDTH, HEIGHT);
    public double mouseX, mouseY;

    public void render(@NotNull GuiGraphics guiGraphics, float partialTicks) {
        rootContainer.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    public void onInit() {
        var back = new ImageWidget(0, 0, WIDTH, HEIGHT, RenderTypes.RENDER_TYPE_SKILL_PANEL_INFO);
        rootContainer.addChild("back", back);
        var buttonWidget = new ImageButtonWidget(320 - 25, 200 - 25, 50, 50, RenderTypes.RENDER_TYPE_CURSOR, () -> {

        });
        rootContainer.addChild("button", buttonWidget);
    }

    public void mouseClicked() {
        rootContainer.mousePressed(mouseX, mouseY, 1);
    }
}