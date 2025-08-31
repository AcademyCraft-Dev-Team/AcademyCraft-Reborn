package org.academy.api.client.gui.widget;

import net.minecraft.util.ARGB;
import org.academy.api.client.gui.command.FillRectDrawCommand;
import org.academy.api.client.gui.framework.WidgetRenderContext;
import org.academy.api.client.gui.framework.AbstractWidget;

public class FillWidget extends AbstractWidget {
    protected int color;

    public FillWidget(float x, float y, float width, float height, int color) {
        super(x, y, width, height);
        this.color = color;
    }

    @Override
    public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
        if (!isVisible())
            return;

        var colorAlpha = ARGB.alpha(this.color) / 255.0f;
        var finalAlpha = colorAlpha * context.getAccumulatedAlpha();

        if (finalAlpha <= 1.0f / 255.0f)
            return;

        context.pose().pushPose();
        context.pose().translate(this.getX(), this.getY(), this.getZ());

        var red = ARGB.red(this.color) / 255.0f;
        var green = ARGB.green(this.color) / 255.0f;
        var blue = ARGB.blue(this.color) / 255.0f;

        var command = new FillRectDrawCommand(this.getWidth(), this.getHeight(), red, green, blue, finalAlpha);
        context.submit(command);

        context.pose().popPose();
    }

    public int getColor() {
        return this.color;
    }

    public FillWidget setColor(int color) {
        this.color = color;
        return this;
    }
}