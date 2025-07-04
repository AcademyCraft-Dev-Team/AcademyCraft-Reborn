package org.academy.api.client.gui.widget;

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

    public ParallaxImageWidget(float x, float y, float width, float height, @NotNull RenderType renderType, float newAnchorX, float newAnchorY) {
        super(x, y, width, height, renderType);
        anchorX = newAnchorX;
        anchorY = newAnchorY;
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTicks) {
        graphics.pose().pushPose();
        var dx = (float) (((mouseX - anchorX) / anchorX) * scaleX);
        var dy = (float) (((mouseY - anchorY) / anchorY) * scaleY);

        dx = Math.max(-1f, Math.min(1f, dx));
        dy = Math.max(-1f, Math.min(1f, dy));

        var maxUOffset = (1f - parallaxWidth);
        var maxVOffset = (1f - parallaxHeight);

        var offsetU = (dx + 1f) / 2f * maxUOffset;
        var offsetV = (dy + 1f) / 2f * maxVOffset;

        u0 = offsetU;
        v0 = offsetV;
        u1 = u0 + parallaxWidth;
        v1 = v0 + parallaxHeight;
        super.render(graphics, mouseX, mouseY, partialTicks);
        graphics.pose().popPose();
    }
}