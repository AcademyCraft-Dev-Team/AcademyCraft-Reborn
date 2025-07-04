package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.client.renderer.RenderTypes;
import org.academy.api.client.util.RenderUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class HistogramWidget extends AbstractWidget {
    public final ImageWidget back;
    public boolean renderBack = true;
    private final List<Value> values;

    public HistogramWidget(float x, float y, float width, float height, List<Value> newInitialValues) {
        super(x, y, width, height);
        back = new ImageWidget(x + 5, y - 15, width, height, RenderTypes.RENDER_TYPE_HISTOGRAM);
        values = new ArrayList<>(Objects.requireNonNullElse(newInitialValues, Collections.emptyList()));
    }

    public HistogramWidget(float x, float y, float width, float height) {
        this(x, y, width, height, Collections.emptyList());
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        var finalAlpha = getAbsoluteAlpha();

        if (renderBack) {
            back.setAlpha(finalAlpha);
            back.render(graphics, mouseX - getX(), mouseY - getY(), partialTick);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(getX(), getY(), getZ());

        for (var value : values) {
            var left = value.x;
            var right = value.x + value.width;
            left = Math.max(0, left);
            right = Math.min(getWidth(), right);

            var bottom = getY() + getHeight() - 20;
            var top = bottom - value.height;

            var r = Math.min(255, Math.max(0, (int) (value.red * 255.0f)));
            var g = Math.min(255, Math.max(0, (int) (value.green * 255.0f)));
            var b = Math.min(255, Math.max(0, (int) (value.blue * 255.0f)));
            var a = Math.min(255, Math.max(0, (int) (value.alpha * finalAlpha * 255.0f)));
            var packedColor = (a << 24) | (r << 16) | (g << 8) | b;

            RenderUtil.fill(
                    graphics.pose().last().pose(),
                    left, top,
                    right, bottom,
                    packedColor,
                    graphics.bufferSource()
            );
        }

        graphics.pose().popPose();
    }

    public void setValues(List<Value> newValues) {
        values.clear();
        if (newValues != null) {
            values.addAll(newValues);
        }
    }

    public void addValue(Value newValue) {
        if (newValue != null) {
            values.add(newValue);
        }
    }

    public void clearValues() {
        values.clear();
    }

    public List<Value> getValues() {
        return values;
    }

    public static class Value {
        public float x;
        public float width;
        public float height;
        public float red;
        public float green;
        public float blue;
        public float alpha;

        public Value(float newX, float newWidth, float newHeight, float newRed, float newGreen, float newBlue, float newAlpha) {
            x = newX;
            width = newWidth;
            height = newHeight;
            red = newRed;
            green = newGreen;
            blue = newBlue;
            alpha = newAlpha;
        }

        public Value(float x, float width, float height) {
            this(x, width, height, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    @Override
    public Widget setWidth(float newWidth) {
        super.setWidth(newWidth);
        if (back != null) {
            back.setWidth(newWidth);
        }
        return this;
    }

    @Override
    public Widget setHeight(float newHeight) {
        super.setHeight(newHeight);
        if (back != null) {
            back.setHeight(newHeight);
        }
        return this;
    }
}