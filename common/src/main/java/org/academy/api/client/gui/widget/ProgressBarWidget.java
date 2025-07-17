package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.RenderUtil;

import java.util.function.Supplier;

public class ProgressBarWidget extends AbstractWidget {
    private Supplier<Float> progressSupplier;
    public boolean backgroundVisible = true;
    public int backgroundColor = 0x20000000;
    public int progressBarColor = 0xFFfcd932;

    public ProgressBarWidget(float x, float y, float width, float height, Supplier<Float> newProgressSupplier) {
        super(x, y, width, height);
        progressSupplier = newProgressSupplier;
    }

    public void setProgressSupplier(Supplier<Float> newProgressSupplier) {
        progressSupplier = newProgressSupplier;
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (progressSupplier != null) {
            var progress = progressSupplier.get();
            if (progress < 0.0f) progress = 0.0f;
            if (progress > 1.0f) progress = 1.0f;

            var absoluteAlpha = getAbsoluteAlpha();
            var finalBackgroundColor = (backgroundColor & 0x00FFFFFF) | ((int) (((backgroundColor >> 24) & 0xFF) * absoluteAlpha) << 24);
            var finalProgressBarColor = (progressBarColor & 0x00FFFFFF) | ((int) (((progressBarColor >> 24) & 0xFF) * absoluteAlpha) << 24);

            if (backgroundVisible) {
                RenderUtil.fill(
                        stack,
                        bufferSource,
                        getX(), getY(),
                        getX() + getWidth(), getY() + getHeight(),
                        finalBackgroundColor
                );
            }

            var progressWidth = getWidth() * progress;
            RenderUtil.fill(
                    stack,
                    bufferSource,
                    getX(), getY(),
                    getX() + progressWidth, getY() + getHeight(),
                    finalProgressBarColor
            );
        }
    }
}