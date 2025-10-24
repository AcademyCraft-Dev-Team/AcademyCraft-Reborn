package org.academy.api.client.gui.widget;

import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.gui.render.WidgetRenderContext;
import org.academy.api.common.util.MathUtil;

public class ParallaxImageWidget extends ImageWidget {
    protected float parallaxFactorX = 0.5f;
    protected float parallaxFactorY = 0.5f;

    protected float imageToViewRatioWidth = 0.9f;
    protected float imageToViewRatioHeight = 0.9f;

    public ParallaxImageWidget(float x, float y, float width, float height, ResourceLocation texture) {
        super(texture);
    }

    @Override
    public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
        var anchorX = getAbsoluteX() + width / 2.0f;
        var anchorY = getAbsoluteY() + height / 2.0f;

        var deviationX = (float) ((mouseX - anchorX) / anchorX);
        var deviationY = (float) ((mouseY - anchorY) / anchorY);

        var motionX = MathUtil.clamp(deviationX * parallaxFactorX, -1.0f, 1.0f);
        var motionY = MathUtil.clamp(deviationY * parallaxFactorY, -1.0f, 1.0f);

        var maxUOffset = 1.0f - imageToViewRatioWidth;
        var maxVOffset = 1.0f - imageToViewRatioHeight;

        var uOffset = (motionX + 1.0f) / 2.0f * maxUOffset;
        var vOffset = (motionY + 1.0f) / 2.0f * maxVOffset;

        setUv(uOffset, vOffset, uOffset + imageToViewRatioWidth, vOffset + imageToViewRatioHeight);

        super.render(context, mouseX, mouseY, partialTick);
    }

    public ParallaxImageWidget setParallaxFactor(float parallaxFactorX, float parallaxFactorY) {
        this.parallaxFactorX = parallaxFactorX;
        this.parallaxFactorY = parallaxFactorY;
        return this;
    }

    public ParallaxImageWidget setImageToViewRatio(float imageToViewRatioWidth, float imageToViewRatioHeight) {
        this.imageToViewRatioWidth = MathUtil.clamp(imageToViewRatioWidth, 0, 1);
        this.imageToViewRatioHeight = MathUtil.clamp(imageToViewRatioHeight, 0, 1);
        return this;
    }
}