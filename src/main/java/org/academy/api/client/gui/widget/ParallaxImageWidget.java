package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.gui.framework.WidgetRenderContext;
import org.academy.api.common.util.MathUtil;

public class ParallaxImageWidget extends ImageWidget {
    protected float parallaxFactorX = 0.5f;
    protected float parallaxFactorY = 0.5f;

    protected float imageToViewRatioWidth = 0.95f;
    protected float imageToViewRatioHeight = 0.95f;

    public ParallaxImageWidget(float x, float y, float width, float height, ResourceLocation texture) {
        super(x, y, width, height, texture);
    }

    @Override
    public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
        var window = Minecraft.getInstance().getWindow();
        var guiScale = context.pose().last().pose().m00();
        var screenWidth = window.getWidth() / guiScale;
        var screenHeight = window.getHeight() / guiScale;

        var anchorX = screenWidth / 2.0f;
        var anchorY = screenHeight / 2.0f;

        var deviationX = (float) ((mouseX - anchorX) / anchorX);
        var deviationY = (float) ((mouseY - anchorY) / anchorY);

        var motionX = MathUtil.clamp(deviationX * this.parallaxFactorX, -1.0f, 1.0f);
        var motionY = MathUtil.clamp(deviationY * this.parallaxFactorY, -1.0f, 1.0f);

        var maxUOffset = 1.0f - this.imageToViewRatioWidth;
        var maxVOffset = 1.0f - this.imageToViewRatioHeight;

        var uOffset = (motionX + 1.0f) / 2.0f * maxUOffset;
        var vOffset = (motionY + 1.0f) / 2.0f * maxVOffset;

        this.setTextureCoords(uOffset, vOffset, uOffset + this.imageToViewRatioWidth, vOffset + this.imageToViewRatioHeight);

        super.render(context, mouseX, mouseY, partialTick);
    }

    public ParallaxImageWidget setParallaxFactor(float parallaxFactorX, float parallaxFactorY) {
        this.parallaxFactorX = parallaxFactorX;
        this.parallaxFactorY = parallaxFactorY;
        return this;
    }

    public ParallaxImageWidget setImageToViewRatio(float imageToViewRatioWidth, float imageToViewRatioHeight) {
        this.imageToViewRatioWidth = MathUtil.clamp(imageToViewRatioWidth, 0.01f, 1.0f);
        this.imageToViewRatioHeight = MathUtil.clamp(imageToViewRatioHeight, 0.01f, 1.0f);
        return this;
    }
}