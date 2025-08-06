package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FastColor;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.RenderUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * A widget that displays progress as a horizontal bar. The progress value is
 * dynamically obtained from a provided Supplier.
 */
public class ProgressBarWidget extends AbstractWidget {
    protected Supplier<Float> progressSupplier;
    protected boolean backgroundVisible = true;
    protected int backgroundColor = 0x20000000;
    protected int progressBarColor = 0xFFfcd932;

    public ProgressBarWidget(float x, float y, float width, float height, @NotNull Supplier<Float> progressSupplier) {
        super(x, y, width, height);
        this.progressSupplier = progressSupplier;
    }

    @Override
    public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!this.isVisible()) return;

        float progress = this.progressSupplier.get();
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        stack.pushPose();

        float absoluteAlpha = getAbsoluteAlpha();

        if (this.backgroundVisible) {
            int finalBackgroundColor = applyAlpha(this.backgroundColor, absoluteAlpha);
            RenderUtil.fill(stack, bufferSource, 0, 0, this.getWidth(), this.getHeight(), finalBackgroundColor);
        }

        float progressWidth = this.getWidth() * progress;
        if (progressWidth > 0) {
            int finalProgressBarColor = applyAlpha(this.progressBarColor, absoluteAlpha);
            RenderUtil.fill(stack, bufferSource, 0, 0, progressWidth, this.getHeight(), finalProgressBarColor);
        }

        stack.popPose();
    }

    private int applyAlpha(int color, float alpha) {
        int baseAlpha = FastColor.ARGB32.alpha(color);
        int finalAlpha = (int) (baseAlpha * alpha);
        return (color & 0x00FFFFFF) | (finalAlpha << 24);
    }

    public void setProgressSupplier(@NotNull Supplier<Float> progressSupplier) {
        this.progressSupplier = progressSupplier;
    }

    @NotNull
    public ProgressBarWidget setBackgroundVisible(boolean visible) {
        this.backgroundVisible = visible;
        return this;
    }

    @NotNull
    public ProgressBarWidget setBackgroundColor(int color) {
        this.backgroundColor = color;
        return this;
    }

    @NotNull
    public ProgressBarWidget setProgressBarColor(int color) {
        this.progressBarColor = color;
        return this;
    }
}