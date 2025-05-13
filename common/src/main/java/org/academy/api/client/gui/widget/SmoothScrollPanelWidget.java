package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.common.util.MathUtil;

public class SmoothScrollPanelWidget extends AbstractContainerWidget {
    public float scrollOffset;
    public float scrollTarget;
    public float scrollSpeed = 24f;

    public SmoothScrollPanelWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollAmount) {
        scrollTarget -= (float) (scrollAmount * scrollSpeed);
        scrollTarget = MathUtil.clamp(scrollTarget, 0, getMaxScroll());
        return true;
    }

    public float getMaxScroll() {
        float maxY = 0f;
        for (Widget child : children.values()) {
            maxY = Math.max(maxY, child.getY() + child.getHeight());
        }
        return Math.max(0, maxY - getHeight());
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        scrollOffset = MathUtil.lerpStartEndFactor(scrollOffset, scrollTarget, MathUtil.magicAnimationFactor(0.25f, partialTick));

        guiGraphics.pose().pushPose();
        guiGraphics.flush();

        int x0 = (int) getAbsoluteX();
        int y0 = (int) super.getAbsoluteY();
        int x1 = x0 + (int) getWidth();
        int y1 = y0 + (int) getHeight();

        guiGraphics.enableScissor(x0, y0, x1, y1);
        guiGraphics.pose().translate(0, -scrollOffset, 0);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.flush();
        guiGraphics.disableScissor();
        guiGraphics.pose().popPose();
    }

    @Override
    public float getAbsoluteY() {
        return super.getAbsoluteY() - scrollOffset;
    }

    @Override
    public boolean isMouseOver(double checkX, double checkY) {
        float absX = super.getAbsoluteX();
        float absY = super.getAbsoluteY();
        return checkX >= absX && checkY >= absY && checkX < absX + getWidth() && checkY < absY + getHeight();
    }

    public void scrollToBottom() {
        scrollTarget = getMaxScroll();
    }
}