package org.academy.api.client.gui.animation;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.common.util.MathUtil;
import org.jetbrains.annotations.NotNull;

public class AnimationTopToBottom implements Animation {
    public final Widget widget;
    public final float originHeight;
    public float animationTime = 0.25f;
    public float currentHeight;
    public boolean startAlpha = false;
    public boolean alpha = true;
    public boolean finished = false;
    public float alphaTime = 0.25f;
    public float currentAlpha = 0;
    public final float[] originColor;
    private float[] previousColor = null;

    public AnimationTopToBottom(@NotNull Widget widget) {
        this.widget = widget;
        originHeight = widget.getHeight();
        originColor = RenderSystem.getShaderColor().clone();
    }

    @Override
    public void beforeRender(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        guiGraphics.flush();

        if (alpha && previousColor == null) {
            previousColor = RenderSystem.getShaderColor().clone();
        }

        float factorHeight = MathUtil.animationFactor(animationTime, partialTick);
        currentHeight = MathUtil.lerpStartEndFactor(currentHeight, originHeight, factorHeight);
        widget.setHeight(currentHeight);

        if ((originHeight - currentHeight) / originHeight < alphaTime) {
            startAlpha = true;
        }

        finished = (originHeight - currentHeight) / originHeight < 0.01f;

        if (alpha) {
            if (startAlpha) {
                float factorAlpha = MathUtil.animationFactor(alphaTime, partialTick);
                currentAlpha = MathUtil.lerpStartEndFactor(currentAlpha, 1, factorAlpha);
            }
            RenderSystem.setShaderColor(originColor[0], originColor[1], originColor[2], currentAlpha);
        }
    }

    @Override
    public void afterRender(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        guiGraphics.flush();
        if (alpha && previousColor != null) {
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            previousColor = null;
        }
    }
}