package org.academy.api.client.gui.widget;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.academy.api.client.gui.command.DrawCommand;
import org.academy.api.client.gui.command.FillRectDrawCommand;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.render.RenderContext;

public class ProgressBarWidget extends AbstractWidget {
    protected float max = 100;
    protected float min = 0;
    protected float progress = 0;

    protected int backgroundColor = 0x40000000;
    protected int progressColor = 0xFFFFFFFF;

    protected Orientation orientation = Orientation.HORIZONTAL;

    public ProgressBarWidget() {
    }

    @Override
    protected void renderInternal(RenderContext context) {
        super.renderInternal(context);

        var width = getWidth();
        var height = getHeight();

        if (width <= 0 || height <= 0) return;

        var bgRed = ARGB.red(backgroundColor) / 255.0f;
        var bgGreen = ARGB.green(backgroundColor) / 255.0f;
        var bgBlue = ARGB.blue(backgroundColor) / 255.0f;
        var bgAlpha = ARGB.alpha(backgroundColor) / 255.0f;
        context.submit(generateBackDrawCommand(width, height, bgRed, bgGreen, bgBlue, bgAlpha * context.getAccumulatedAlpha()));

        var range = max - min;
        var ratio = range > 0 ? (progress - min) / range : 0.0f;
        ratio = Mth.clamp(ratio, 0.0f, 1.0f);

        var fgRed = ARGB.red(progressColor) / 255.0f;
        var fgGreen = ARGB.green(progressColor) / 255.0f;
        var fgBlue = ARGB.blue(progressColor) / 255.0f;
        var fgAlpha = ARGB.alpha(progressColor) / 255.0f;

        if (orientation == Orientation.HORIZONTAL) {
            var progressWidth = width * ratio;
            if (progressWidth > 0) {
                context.submit(generateProgressDrawCommand(progressWidth, height, fgRed, fgGreen, fgBlue, fgAlpha * context.getAccumulatedAlpha()));
            }
        } else {
            var progressHeight = height * ratio;
            if (progressHeight > 0) {
                context.pose().pushPose();
                context.pose().translate(0, height - progressHeight, 0.1f);
                context.submit(generateProgressDrawCommand(width, progressHeight, fgRed, fgGreen, fgBlue, fgAlpha * context.getAccumulatedAlpha()));
                context.pose().popPose();
            }
        }
    }

    protected DrawCommand generateBackDrawCommand(
            float width, float height,
            float red, float green, float blue, float alpha
    ) {
        return new FillRectDrawCommand(width, height, red, green, blue, alpha);
    }

    protected DrawCommand generateProgressDrawCommand(
            float width, float height,
            float red, float green, float blue, float alpha
    ) {
        return new FillRectDrawCommand(width, height, red, green, blue, alpha);
    }

    public float getMax() {
        return max;
    }

    public ProgressBarWidget setMax(float max) {
        if (max < min) {
            max = min;
        }
        if (this.max != max) {
            this.max = max;
            setProgress(progress);
        }
        return this;
    }

    public float getMin() {
        return min;
    }

    public ProgressBarWidget setMin(float min) {
        if (min > max) {
            min = max;
        }
        if (this.min != min) {
            this.min = min;
            setProgress(progress);
        }
        return this;
    }

    public float getProgress() {
        return progress;
    }

    public ProgressBarWidget setProgress(float progress) {
        var clampedProgress = Mth.clamp(progress, min, max);
        if (this.progress != clampedProgress) {
            this.progress = clampedProgress;
        }
        return this;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public ProgressBarWidget setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
        return this;
    }

    public int getProgressColor() {
        return progressColor;
    }

    public ProgressBarWidget setProgressColor(int progressColor) {
        this.progressColor = progressColor;
        return this;
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public ProgressBarWidget setOrientation(Orientation orientation) {
        this.orientation = orientation;
        requestLayout();
        return this;
    }
}