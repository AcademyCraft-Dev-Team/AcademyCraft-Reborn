package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.resource.TextureResources;

public class CursorWidget extends ImageWidget {
    public CursorWidget(float width, float height) {
        super(0, 0, width, height, TextureResources.RenderTypes.RENDER_TYPE_CURSOR);
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        setX((float) mouseX - getWidth() / 2);
        setY((float) mouseY - getHeight() / 2);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}