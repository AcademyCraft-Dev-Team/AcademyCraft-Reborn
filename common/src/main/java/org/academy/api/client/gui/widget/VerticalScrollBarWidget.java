package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;
import org.joml.Matrix4f;

public class VerticalScrollBarWidget extends DragBarWidget {
    public ScrollPanelWidget panel;

    public VerticalScrollBarWidget(ScrollPanelWidget panel, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.panel = panel;
    }

    @Override
    protected float getThumbSize() {
        float max = panel.getMaxScroll();
        if (max <= 0f) {
            return getHeight();
        }
        float contentHeight = max + panel.getHeight();
        float ratio = panel.getHeight() / contentHeight;
        return MathUtil.clamp(ratio * getHeight(), 16f, getHeight());
    }

    @Override
    protected float getThumbPosition() {
        float max = panel.getMaxScroll();
        if (max <= 0f) {
            return getY();
        }
        float track = getHeight() - getThumbSize();
        float ratio = panel.scrollOffset / max;
        return getY() + ratio * track;
    }

    @Override
    protected float getTrackSize() {
        return getHeight();
    }

    @Override
    protected float getMouseRelative(float mouseX, float mouseY) {
        return mouseY - getAbsoluteY();
    }

    @Override
    protected void updateTargetFromMouse(float mouse) {
        float max = panel.getMaxScroll();
        if (max <= 0f) return;
        float track = getTrackSize() - getThumbSize();
        float ratio = MathUtil.clamp((mouse - dragOffset) / track, 0f, 1f);
        panel.scrollTarget = ratio * max;
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource buffer = graphics.bufferSource();
        float x = getX();
        float y = getY();
        float z = getZ();
        float w = getWidth();
        float h = getHeight();
        matrix.translate(0, 0, z);

        float absoluteAlpha = getAbsoluteAlpha();
        int finalTrackColor = (getTrackColor() & 0x00FFFFFF) | ((int) (((getTrackColor() >> 24) & 0xFF) * absoluteAlpha) << 24);
        int finalThumbColor = (getThumbColor() & 0x00FFFFFF) | ((int) (((getThumbColor() >> 24) & 0xFF) * absoluteAlpha) << 24);

        if (showBackground) {
            RenderUtil.fill(matrix, x, y, x + w, y + h, finalTrackColor, buffer);
        }

        float thumbTop = getThumbPosition();
        float thumbHeight = getThumbSize();
        matrix.translate(0, 0, 1);
        RenderUtil.fill(matrix, x, thumbTop, x + w, thumbTop + thumbHeight, finalThumbColor, buffer);

        poseStack.popPose();
    }
}