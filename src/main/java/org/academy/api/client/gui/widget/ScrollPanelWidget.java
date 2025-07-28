package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.StencilUtil;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;

public class ScrollPanelWidget extends AbstractContainerWidget {
    public float scrollOffset;
    public float scrollTarget;
    public float scrollSpeed = 24f;

    public ScrollPanelWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollAmount) {
        if (!isAbsoluteEnabled() || !isAbsoluteMouseOver(mouseX, mouseY)) {
            return false;
        }

        if (super.mouseScrolled(mouseX, mouseY, scrollAmount)) {
            return true;
        }

        scrollTarget -= (float) (scrollAmount * scrollSpeed);
        scrollTarget = MathUtil.clamp(scrollTarget, 0, getMaxScroll());
        return true;
    }

    public float getMaxScroll() {
        float maxY = 0f;
        for (Widget child : getChildren().values()) {
            maxY = Math.max(maxY, child.getY() + child.getHeight());
        }
        return Math.max(0, maxY - getHeight());
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        scrollOffset = MathUtil.lerpStartEndFactor(scrollOffset, scrollTarget, ClientUtil.animationFactor(MathUtil.PI / 1.5f));

        stack.pushPose();
        bufferSource.endBatch();

        StencilUtil.beginDrawMask();
        RenderUtil.fill(stack, bufferSource, getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFFFFFFFF);
        bufferSource.endBatch();

        StencilUtil.useMask();
        stack.translate(0, -scrollOffset, 0);
        super.render(stack, bufferSource, mouseX, mouseY, partialTick);
        bufferSource.endBatch();

        StencilUtil.end();
        stack.popPose();
    }

    @Override
    public float getAbsoluteY() {
        return super.getAbsoluteY() - scrollOffset;
    }

    @Override
    public boolean isMouseOver(double checkX, double checkY) {
        var absX = super.getAbsoluteX();
        var absY = super.getAbsoluteY();
        return checkX >= absX && checkY >= absY && checkX < absX + getWidth() && checkY < absY + getHeight();
    }

    public void scrollToBottom() {
        scrollTarget = getMaxScroll();
    }
}