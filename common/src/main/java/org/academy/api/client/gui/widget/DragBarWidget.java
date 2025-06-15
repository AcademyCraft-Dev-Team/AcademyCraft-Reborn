package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.framework.AbstractWidget;

public abstract class DragBarWidget extends AbstractWidget {
    public boolean showBackground = true;
    public boolean startDragging = false;
    public float dragOffset = 0f;
    protected int thumbColor = 0xFFAAAAAA;
    protected int trackColor = 0xFF202020;

    public DragBarWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    protected abstract float getThumbSize();

    protected abstract float getThumbPosition();

    protected abstract float getTrackSize();

    protected abstract float getMouseRelative(float mouseX, float mouseY);

    protected abstract void updateTargetFromMouse(float mouse);

    @Override
    public boolean mousePressed(double mouseX, double mouseY, int button) {
        if (isHovered()) {
            startDragging = true;
            dragOffset = getThumbSize() / 2f;
            updateTargetFromMouse(getMouseRelative((float) mouseX, (float) mouseY));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        startDragging = false;
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (startDragging) {
            updateTargetFromMouse(getMouseRelative((float) mouseX, (float) mouseY));
            return true;
        }
        return false;
    }

    public void setThumbColor(int color) {
        this.thumbColor = color;
    }

    public int getThumbColor() {
        return thumbColor;
    }

    public void setTrackColor(int color) {
        this.trackColor = color;
    }

    public int getTrackColor() {
        return trackColor;
    }
}