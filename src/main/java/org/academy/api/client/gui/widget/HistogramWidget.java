package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.command.FillRectDrawCommand;
import org.academy.api.client.gui.command.ImageDrawCommand;
import org.academy.api.client.gui.render.WidgetRenderContext;
import org.academy.api.client.gui.layout.MeasureSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistogramWidget extends AbstractWidget {
    private static final float DEFAULT_INTRINSIC_SIZE = 84.0f;

    protected boolean renderBack = true;
    private final List<Value> values;

    public HistogramWidget(List<Value> initialValues) {
        values = new ArrayList<>(initialValues);
    }

    public HistogramWidget() {
        this(Collections.emptyList());
    }

    @Override
    protected void onMeasure(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        var lp = getLayoutParams();
        var desiredWidth = DEFAULT_INTRINSIC_SIZE + lp.paddingLeft + lp.paddingRight;
        var desiredHeight = DEFAULT_INTRINSIC_SIZE + lp.paddingTop + lp.paddingBottom;

        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec)
        );
    }

    @Override
    public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        context.pose().pushPose();
        context.drawOrder().push();
        {
            var accumulatedAlpha = context.getAccumulatedAlpha() * getAlpha();

            if (renderBack) renderBackground(context, accumulatedAlpha);

            context.drawOrder().advance();
            renderBars(context, accumulatedAlpha);
        }
        context.drawOrder().pop();
        context.pose().popPose();
    }


    private void renderBackground(WidgetRenderContext context, float finalAlpha) {
        context.pose().pushPose();
        {
            context.pose().translate(5.0f, 0, 0.0f);

            var textureManager = Minecraft.getInstance().getTextureManager();
            var texture = textureManager.getTexture(Resource.Textures.HISTOGRAM).getTextureView();
            var command = new ImageDrawCommand(texture, getWidth(), getHeight(), 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, finalAlpha);
            context.submit(command);
        }
        context.pose().popPose();
    }

    private void renderBars(WidgetRenderContext context, float accumulatedAlpha) {
        for (var value : values) {
            var left = Math.max(0.0f, value.x);
            var right = Math.min(getWidth(), value.x + value.width);

            if (left >= right) continue;

            var barWidth = right - left;
            var barHeight = value.height;

            var bottom = getHeight() - 5;
            var top = bottom - barHeight;

            context.pose().pushPose();
            {
                context.pose().translate(left, top, 0.1f);
                var finalBarAlpha = value.alpha * accumulatedAlpha;
                var command = new FillRectDrawCommand(barWidth, barHeight, value.red, value.green, value.blue, finalBarAlpha);
                context.submit(command);
            }
            context.pose().popPose();
        }
    }

    public void setValues(List<Value> values) {
        this.values.clear();
        this.values.addAll(values);
    }

    public void addValue(Value value) {
        values.add(value);
    }

    public void clearValues() {
        values.clear();
    }

    public List<Value> getValues() {
        return Collections.unmodifiableList(values);
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
}