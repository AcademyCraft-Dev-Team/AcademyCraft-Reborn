package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.academy.api.client.gui.render.RenderContext;

public class ParallaxImageWidget extends ImageWidget {
    protected float parallaxFactorX = 0.5f;
    protected float parallaxFactorY = 0.5f;

    protected float imageToViewRatioWidth = 0.9f;
    protected float imageToViewRatioHeight = 0.9f;

    public ParallaxImageWidget(float x, float y, float width, float height, ResourceLocation texture) {
        super(texture);
    }

    @Override
    public void render(RenderContext context) {
        var anchorX = getAbsoluteX() + width / 2.0f;
        var anchorY = getAbsoluteY() + height / 2.0f;

        var mc = Minecraft.getInstance();
        var mh = mc.mouseHandler;
        var w = mc.getWindow();
        var mouseX = mh.getScaledXPos(w);
        var mouseY = mh.getScaledYPos(w);

        var deviationX = (float) ((mouseX - anchorX) / anchorX);
        var deviationY = (float) ((mouseY - anchorY) / anchorY);

        var motionX = Mth.clamp(deviationX * parallaxFactorX, -1.0f, 1.0f);
        var motionY = Mth.clamp(deviationY * parallaxFactorY, -1.0f, 1.0f);

        var maxUOffset = 1.0f - imageToViewRatioWidth;
        var maxVOffset = 1.0f - imageToViewRatioHeight;

        var uOffset = (motionX + 1.0f) / 2.0f * maxUOffset;
        var vOffset = (motionY + 1.0f) / 2.0f * maxVOffset;

        setUv(uOffset, vOffset, uOffset + imageToViewRatioWidth, vOffset + imageToViewRatioHeight);

        super.render(context);
    }

    public ParallaxImageWidget setParallaxFactor(float parallaxFactorX, float parallaxFactorY) {
        this.parallaxFactorX = parallaxFactorX;
        this.parallaxFactorY = parallaxFactorY;
        return this;
    }

    public ParallaxImageWidget setImageToViewRatio(float imageToViewRatioWidth, float imageToViewRatioHeight) {
        this.imageToViewRatioWidth = Mth.clamp(imageToViewRatioWidth, (float) 0, (float) 1);
        this.imageToViewRatioHeight = Mth.clamp(imageToViewRatioHeight, (float) 0, (float) 1);
        return this;
    }
}