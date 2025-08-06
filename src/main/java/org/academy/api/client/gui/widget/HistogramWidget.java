package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.render.RenderTypes;
import org.academy.api.client.util.RenderUtil;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class HistogramWidget extends AbstractWidget {
    public boolean renderBack = true;
    private final List<Value> values;

    public HistogramWidget(float x, float y, float width, float height, List<Value> initialValues) {
        super(x, y, width, height);
        this.values = new ArrayList<>(Objects.requireNonNullElse(initialValues, Collections.emptyList()));
    }

    public HistogramWidget(float x, float y, float width, float height) {
        this(x, y, width, height, Collections.emptyList());
    }

    @Override
    public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        stack.pushPose();
        stack.translate(this.getX(), this.getY(), this.getZ());

        float finalAlpha = getAbsoluteAlpha();
        if (finalAlpha < 1.0f / 255.0f) {
            stack.popPose();
            return;
        }

        if (renderBack) {
            this.renderBackground(bufferSource, stack.lastMatrix(), finalAlpha);
        }

        this.renderBars(bufferSource, stack);

        stack.popPose();
    }

    private void renderBackground(MultiBufferSource.BufferSource bufferSource, Matrix4f matrix, float alpha) {
        var consumer = bufferSource.getBuffer(RenderTypes.HISTOGRAM);
        addPositionColorTexQuad(consumer, matrix, 5, -15, this.getWidth(), this.getHeight(), 1f, 1f, 1f, alpha);
        bufferSource.endBatch(RenderTypes.HISTOGRAM);
    }

    private void renderBars(MultiBufferSource.BufferSource bufferSource, MatrixStack stack) {
        for (var value : values) {
            var left = Math.max(0, value.x);
            var right = Math.min(this.getWidth(), value.x + value.width);

            if (left >= right) continue;

            var bottom = this.getHeight() - 20;
            var top = bottom - value.height;

            var r = (int) (value.red * 255.0f);
            var g = (int) (value.green * 255.0f);
            var b = (int) (value.blue * 255.0f);
            var a = (int) (value.alpha * getAbsoluteAlpha() * 255.0f);
            var packedColor = (a << 24) | (r << 16) | (g << 8) | b;

            RenderUtil.fill(
                    stack,
                    bufferSource,
                    left, top,
                    right - left, bottom - top,
                    packedColor
            );
        }
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
        return Collections.unmodifiableList(values);
    }

    private static void addPositionColorTexQuad(VertexConsumer consumer, Matrix4f matrix, float x, float y, float width, float height, float r, float g, float b, float a) {
        float x2 = x + width;
        float y2 = y + height;
        consumer.addVertex(matrix, x, y, 0).setColor(r, g, b, a).setUv(0, 0);
        consumer.addVertex(matrix, x, y2, 0).setColor(r, g, b, a).setUv(0, 1);
        consumer.addVertex(matrix, x2, y2, 0).setColor(r, g, b, a).setUv(1, 1);
        consumer.addVertex(matrix, x2, y, 0).setColor(r, g, b, a).setUv(1, 0);
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