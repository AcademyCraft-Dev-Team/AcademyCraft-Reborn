package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.gui.framework.Orientation;

public abstract class DragBarWidget extends AbstractWidget {
    public boolean showBackground = true;
    public boolean startDragging = false;
    public float dragOffset = 0f;
    protected int thumbColor = 0xFFAAAAAA;
    protected int trackColor = 0xFF202020;
    protected final Orientation orientation;

    public DragBarWidget(float x, float y, float width, float height, Orientation newOrientation) {
        super(x, y, width, height);
        orientation = newOrientation;
    }

    protected abstract float getThumbSize();

    protected abstract float getThumbPosition();

    protected float getTrackSize() {
        return orientation == Orientation.HORIZONTAL ? getWidth() : getHeight();
    }

    protected float getMouseRelative(float mouseX, float mouseY) {
        return orientation == Orientation.HORIZONTAL ? (mouseX - getAbsoluteX()) : (mouseY - getAbsoluteY());
    }

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

    public void setThumbColor(int newColor) {
        thumbColor = newColor;
    }

    public void setTrackColor(int newColor) {
        trackColor = newColor;
    }

    public int getThumbColor() {
        return thumbColor;
    }

    public int getTrackColor() {
        return trackColor;
    }
}