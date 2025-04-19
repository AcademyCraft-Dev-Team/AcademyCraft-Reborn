package org.academy.api.client.gui.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.NotNull;

public class ParallaxImageWidget extends ImageWidget {
    public float anchorX;
    public float anchorY;
    public float parallaxWidth = 0.95f;
    public float parallaxHeight = 0.95f;
    public float scaleX = -1.0f;
    public float scaleY = -1.0f;

    public ParallaxImageWidget(float x, float y, float width, float height, @NotNull RenderType renderType, float anchorX, float anchorY) {
        super(x, y, width, height, renderType);
        this.anchorX = anchorX;
        this.anchorY = anchorY;
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTicks) {
        guiGraphics.pose().pushPose();
        float dx = (float) (((mouseX - anchorX) / anchorX) * scaleX);
        float dy = (float) (((mouseY - anchorY) / anchorY) * scaleY);

        dx = Math.max(-1f, Math.min(1f, dx));
        dy = Math.max(-1f, Math.min(1f, dy));

        float maxUOffset = (1f - parallaxWidth);
        float maxVOffset = (1f - parallaxHeight);

        float offsetU = (dx + 1f) / 2f * maxUOffset;
        float offsetV = (dy + 1f) / 2f * maxVOffset;

        u0 = offsetU;
        v0 = offsetV;
        u1 = u0 + parallaxWidth;
        v1 = v0 + parallaxHeight;
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        guiGraphics.pose().popPose();
    }
}