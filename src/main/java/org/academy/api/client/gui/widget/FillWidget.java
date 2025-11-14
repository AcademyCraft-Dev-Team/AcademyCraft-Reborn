package org.academy.api.client.gui.widget;

import net.minecraft.util.ARGB;
import org.academy.api.client.gui.command.FillRectDrawCommand;
import org.academy.api.client.gui.render.RenderContext;

public class FillWidget extends AbstractWidget {
    protected int red;
    protected int green;
    protected int blue;
    protected int alpha;

    public FillWidget(int color) {
        setColor(color);
    }

    @Override
    public void render(RenderContext context, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        var lp = getLayoutParams();
        var paddedWidth = getWidth() - lp.paddingLeft - lp.paddingRight;
        var paddedHeight = getHeight() - lp.paddingTop - lp.paddingBottom;

        if (paddedWidth <= 0 || paddedHeight <= 0) return;

        var finalAlpha = alpha / 255f * getAlpha() * context.getAccumulatedAlpha();
        var finalRed = red / 255f;
        var finalGreen = green / 255f;
        var finalBlue = blue / 255f;

        context.pose().pushPose();
        {
            context.pose().translate(lp.paddingLeft, lp.paddingTop, 0);
            var command = new FillRectDrawCommand(paddedWidth, paddedHeight, finalRed, finalGreen, finalBlue, finalAlpha);
            context.submit(command);
        }
        context.pose().popPose();
    }

    public int getColor() {
        return ARGB.color(alpha, red, green, blue);
    }

    public FillWidget setColor(int color) {
        alpha = ARGB.alpha(color);
        red = ARGB.red(color);
        green = ARGB.green(color);
        blue = ARGB.blue(color);
        return this;
    }
}