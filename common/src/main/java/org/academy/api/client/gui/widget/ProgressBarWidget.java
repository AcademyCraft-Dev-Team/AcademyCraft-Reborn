package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.util.RenderUtil;

import java.util.function.Supplier;

public class ProgressBarWidget extends AbstractWidget {
    private Supplier<Float> progressSupplier;
    public boolean backgroundVisible = true;
    public int backgroundColor = 0x20000000;
    public int progressBarColor = 0xFFfcd932;

    public ProgressBarWidget(float x, float y, float width, float height, Supplier<Float> progressSupplier) {
        super(x, y, width, height);
        this.progressSupplier = progressSupplier;
    }

    public void setProgressSupplier(Supplier<Float> progressSupplier) {
        this.progressSupplier = progressSupplier;
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        if (progressSupplier != null) {
            float progress = progressSupplier.get();
            if (progress < 0.0f) progress = 0.0f;
            if (progress > 1.0f) progress = 1.0f;

            if (backgroundVisible) {
                RenderUtil.fill(
                        guiGraphics.pose().last().pose(),
                        this.getX(), this.getY(),
                        this.getX() + this.getWidth(), this.getY() + this.getHeight(),
                        backgroundColor, guiGraphics.bufferSource()
                );
            }

            float progressWidth = this.getWidth() * progress;
            RenderUtil.fill(
                    guiGraphics.pose().last().pose(),
                    this.getX(), this.getY(),
                    this.getX() + progressWidth, this.getY() + this.getHeight(),
                    progressBarColor,
                    guiGraphics.bufferSource()
            );
        }
    }
}