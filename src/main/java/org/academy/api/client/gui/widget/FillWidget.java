package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FastColor;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.RenderUtil;
import org.jetbrains.annotations.NotNull;

public class FillWidget extends AbstractWidget {
    protected int color;

    public FillWidget(float x, float y, float width, float height, int color) {
        super(x, y, width, height);
        this.color = color;
    }

    @Override
    public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        var baseAlpha = FastColor.ARGB32.alpha(this.color);
        var finalAlpha = (int)(baseAlpha * getAbsoluteAlpha());

        if (finalAlpha < 1) {
            return;
        }

        var finalColor = (this.color & 0x00FFFFFF) | (finalAlpha << 24);

        RenderUtil.fill(stack, bufferSource, 0, 0,
                this.getWidth(), this.getHeight(), finalColor);

    }

    public int getColor() {
        return this.color;
    }

    @NotNull
    public FillWidget setColor(int color) {
        this.color = color;
        return this;
    }
}