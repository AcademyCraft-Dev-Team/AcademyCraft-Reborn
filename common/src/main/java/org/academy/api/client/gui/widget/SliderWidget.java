package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;
import org.joml.Matrix4f;

import java.util.function.Consumer;

public class SliderWidget extends DragBarWidget {
    public float minValue;
    public float maxValue;
    private float currentValue;
    public Consumer<Float> onValueChanged;

    public SliderWidget(float x, float y, float width, float height, float minValue, float maxValue, float initialValue) {
        super(x, y, width, height);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.currentValue = MathUtil.clamp(initialValue, minValue, maxValue);
    }

    public float getValue() {
        return currentValue;
    }

    public void setValue(float value) {
        float newValue = MathUtil.clamp(value, minValue, maxValue);
        if (this.currentValue != newValue) {
            this.currentValue = newValue;
            if (onValueChanged != null) {
                onValueChanged.accept(this.currentValue);
            }
        }
    }

    @Override
    protected float getThumbSize() {
        return 8f;
    }

    @Override
    protected float getThumbPosition() {
        float track = getTrackSize() - getThumbSize();
        if (maxValue - minValue == 0) return getX();
        float ratio = (currentValue - minValue) / (maxValue - minValue);
        return getX() + ratio * track;
    }

    @Override
    protected float getTrackSize() {
        return getWidth();
    }

    @Override
    protected float getMouseRelative(float mouseX, float mouseY) {
        return mouseX - getAbsoluteX();
    }

    @Override
    protected void updateTargetFromMouse(float mouse) {
        float track = getTrackSize() - getThumbSize();
        if (track <= 0) return;
        float ratio = MathUtil.clamp((mouse - dragOffset) / track, 0f, 1f);
        setValue(minValue + ratio * (maxValue - minValue));
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        graphics.pose().pushPose();

        Matrix4f matrix = graphics.pose().last().pose();
        MultiBufferSource.BufferSource buffer = graphics.bufferSource();

        if (showBackground) {
            RenderUtil.fill(matrix, getX(), getY(), getX() + getWidth(), getY() + getHeight(), getTrackColor(), buffer);
        }

        float thumbLeft = getThumbPosition();
        float thumbWidth = getThumbSize();
        graphics.pose().translate(0, 0, 1);

        RenderUtil.fill(matrix, thumbLeft, getY(), thumbLeft + thumbWidth, getY() + getHeight(), getThumbColor(), buffer);

        graphics.pose().popPose();
    }
}