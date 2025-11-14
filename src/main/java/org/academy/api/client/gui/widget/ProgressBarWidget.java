package org.academy.api.client.gui.widget;

import net.minecraft.util.ARGB;
import org.academy.api.client.gui.command.FillRectDrawCommand;
import org.academy.api.client.gui.render.RenderContext;

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

    public ProgressBarWidget(float x, float y, float width, float height, Supplier<Float> progressSupplier) {
        this.progressSupplier = progressSupplier;
    }

    @Override
    public void render(RenderContext context, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        var progress = progressSupplier.get();
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        context.pose().pushPose();
        context.pose().translate(getX(), getY(), getZ());

        var absoluteAlpha = context.getAccumulatedAlpha() * getAlpha();

        if (backgroundVisible) {
            var command = new FillRectDrawCommand(getWidth(), getHeight(),
                    ARGB.redFloat(backgroundColor),
                    ARGB.greenFloat(backgroundColor),
                    ARGB.blueFloat(backgroundColor),
                    ARGB.alphaFloat(backgroundColor) * absoluteAlpha
            );
            context.submit(command);
        }

        var progressWidth = getWidth() * progress;
        progressWidth = Math.max(0.0f, Math.min(getWidth(), progressWidth));
        context.pose().translate(0, 0, 0.1f);
        var command = new FillRectDrawCommand(progressWidth, getHeight(),
                ARGB.redFloat(progressBarColor),
                ARGB.greenFloat(progressBarColor),
                ARGB.blueFloat(progressBarColor),
                ARGB.alphaFloat(progressBarColor) * absoluteAlpha
        );
        context.submit(command);

        context.pose().popPose();
    }

    public void setProgressSupplier(Supplier<Float> progressSupplier) {
        this.progressSupplier = progressSupplier;
    }

    public ProgressBarWidget setBackgroundVisible(boolean visible) {
        backgroundVisible = visible;
        return this;
    }

    public ProgressBarWidget setBackgroundColor(int color) {
        backgroundColor = color;
        return this;
    }

    public ProgressBarWidget setProgressBarColor(int color) {
        progressBarColor = color;
        return this;
    }
}