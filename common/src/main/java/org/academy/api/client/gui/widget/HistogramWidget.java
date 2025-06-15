package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.AbstractWidget;
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

    public HistogramWidget(float x, float y, float width, float height, List<Value> initialValues) {
        super(x, y, width, height);
        this.back = new ImageWidget(x + 5, y - 15, width, height, RenderTypes.RENDER_TYPE_HISTOGRAM);
        this.values = new ArrayList<>(Objects.requireNonNullElse(initialValues, Collections.emptyList()));
    }

    public HistogramWidget(float x, float y, float width, float height) {
        this(x, y, width, height, Collections.emptyList());
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;
        if (renderBack) {
            back.render(guiGraphics, mouseX - getX(), mouseY - getY(), partialTick);
        }
        
        if (animation != null) {
            animation.beforeRender(guiGraphics, mouseX, mouseY, partialTick);
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(getX(), getY(), getZ());

        for (Value value : values) {
            float left = value.x;
            float right = value.x + value.width;
            left = Math.max(0, left);
            right = Math.min(getWidth(), right);

            float bottom = getY() + getHeight() - 20;
            float top = bottom - value.height;

            int r = Math.min(255, Math.max(0, (int) (value.red * 255.0f)));
            int g = Math.min(255, Math.max(0, (int) (value.green * 255.0f)));
            int b = Math.min(255, Math.max(0, (int) (value.blue * 255.0f)));
            int a = Math.min(255, Math.max(0, (int) (value.alpha * 255.0f)));
            int packedColor = (a << 24) | (r << 16) | (g << 8) | b;

            RenderUtil.fill(
                    guiGraphics.pose().last().pose(),
                    left, top,
                    right, bottom,
                    packedColor,
                    guiGraphics.bufferSource()
            );
        }

        guiGraphics.pose().popPose();

        if (animation != null) {
            animation.afterRender(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    public void setValues(List<Value> newValues) {
        this.values.clear();
        if (newValues != null) {
            this.values.addAll(newValues);
        }
    }

    public void addValue(Value value) {
        if (value != null) {
            this.values.add(value);
        }
    }

    public void clearValues() {
        this.values.clear();
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

        public Value(float x, float width, float height, float red, float green, float blue, float alpha) {
            this.x = x;
            this.width = width;
            this.height = height;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
        }
        
        public Value(float x, float width, float height) {
            this(x, width, height, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    @Override
    public void setWidth(float width) {
        super.setWidth(width);
        if (this.back != null) {
            this.back.setWidth(width);
        }
    }

    @Override
    public void setHeight(float height) {
        super.setHeight(height);
        if (this.back != null) {
            this.back.setHeight(height);
        }
    }
}