package org.academy.api.client.gui.widgets;

import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.AbstractWidget;

public class UnlockSliderWidget extends AbstractWidget {
    private float thumbX = getX();
    private boolean unlocked = false;

    public UnlockSliderWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;
        guiGraphics.pose().pushPose();

        int bgColor = 0xFF888888;
        guiGraphics.fill((int) getX(), (int) getY(),
                (int) (getX() + getWidth()), (int) (getY() + getHeight()), bgColor);

        int thumbColor = unlocked ? 0xFF00DD00 : 0xFFFFFFFF;
        guiGraphics.pose().translate(0, 0, 0.1f);
        guiGraphics.fill((int)getX(), (int) getY(),
                (int) (thumbX), (int) (getY() + getHeight()), thumbColor);
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        thumbX += dragX;

        thumbX = Math.max(getX(), Math.min(thumbX, getX() + getWidth()));

        if (thumbX > getX() + getWidth()) {
            unlocked = true;
        }

        if (unlocked) {
            onUnlocked();
        }

        return true;
    }

    protected void onUnlocked() {
        System.out.println("解锁成功！");
    }
}
