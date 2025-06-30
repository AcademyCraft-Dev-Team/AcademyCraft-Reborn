package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.Widget;

public class LayeredPanelWidget extends PanelWidget {
    public LayeredPanelWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        beforeRender(graphics, mouseX, mouseY, partialTick);
        if (!isVisible()) return;

        graphics.pose().pushPose();
        graphics.pose().translate(getX(), getY(), getZ());

        RenderSystem.enablePolygonOffset();
        int layer = 0;
        for (Widget child : getChildren().values()) {
            layer++;
            RenderSystem.polygonOffset(10, layer);
            child.render(graphics, mouseX, mouseY, partialTick);
        }
        RenderSystem.disablePolygonOffset();

        graphics.pose().popPose();
        afterRender(graphics, mouseX, mouseY, partialTick);
    }
}