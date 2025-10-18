package org.academy.api.client.gui.widget;

import net.minecraft.util.ARGB;
import org.academy.api.client.gui.command.FillRectDrawCommand;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.gui.framework.WidgetRenderContext;

public class FillWidget extends AbstractWidget {
    protected int color;

    public FillWidget(float x, float y, float width, float height, int color) {
        super(x, y, width, height);
        this.color = color;
    }

    @Override
    public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        var colorAlpha = ARGB.alpha(color) / 255.0f * getAlpha();
        var finalAlpha = colorAlpha * context.getAccumulatedAlpha();

        if (finalAlpha <= 1.0f / 255.0f) return;

        context.pose().pushPose();
        context.pose().translate(getX(), getY(), getZ());

        var red = ARGB.red(color) / 255.0f;
        var green = ARGB.green(color) / 255.0f;
        var blue = ARGB.blue(color) / 255.0f;

        var command = new FillRectDrawCommand(getWidth(), getHeight(), red, green, blue, finalAlpha);
        context.submit(command);

        context.pose().popPose();
    }

    public int getColor() {
        return color;
    }

    public FillWidget setColor(int color) {
        this.color = color;
        return this;
    }
}