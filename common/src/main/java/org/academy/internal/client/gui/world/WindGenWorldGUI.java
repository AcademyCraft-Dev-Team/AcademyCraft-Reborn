package org.academy.internal.client.gui.world;

import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.widget.ImageButtonWidget;
import org.academy.api.client.gui.widget.ImageWidget;
import org.academy.api.client.gui.widget.PanelWidget;
import org.academy.api.client.resource.TextureResources;
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
        PanelWidget mainPanel = new PanelWidget(0, 0, WIDTH, HEIGHT);
        rootContainer.addChild("main_panel", mainPanel);
        {
            ImageWidget back = new ImageWidget(0, 0, WIDTH, HEIGHT, TextureResources.RenderTypes.RENDER_TYPE_SKILL_PANEL_INFO);
            mainPanel.addChild("back", back);
            ImageButtonWidget buttonWidget = new ImageButtonWidget(320, 200, 50, 50, TextureResources.RenderTypes.RENDER_TYPE_ICON_NODE, new Runnable() {
                @Override
                public void run() {

                }
            });
            mainPanel.addChild("button", buttonWidget);
        }
    }

    public void mouseClicked() {
        rootContainer.mouseClicked(mouseX, mouseY, 1);
    }
}