package org.academy.api.client.gui.drawable;

import net.minecraft.util.ARGB;
import org.academy.api.client.gui.command.FillRectDrawCommand;
import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.widget.Widget;

public class ColorDrawable implements Drawable {
    private int color;

    public ColorDrawable(int color) {
        this.color = color;
    }

    @Override
    public void draw(RenderContext context, Widget widget) {
        var lp = widget.getLayoutParams();
        var paddedWidth = widget.getWidth() - lp.paddingLeft - lp.paddingRight;
        var paddedHeight = widget.getHeight() - lp.paddingTop - lp.paddingBottom;

        if (paddedWidth <= 0 || paddedHeight <= 0) return;

        var baseAlpha = ARGB.alpha(color) / 255.0f;
        var finalAlpha = baseAlpha * widget.getAbsoluteAlpha();

        if (finalAlpha <= 0) return;

        var r = ARGB.red(color) / 255.0f;
        var g = ARGB.green(color) / 255.0f;
        var b = ARGB.blue(color) / 255.0f;

        context.pose().pushPose();
        context.pose().translate(lp.paddingLeft, lp.paddingTop, 0);

        var command = new FillRectDrawCommand(paddedWidth, paddedHeight, r, g, b, finalAlpha);
        context.submit(command);

        context.pose().popPose();
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }
}