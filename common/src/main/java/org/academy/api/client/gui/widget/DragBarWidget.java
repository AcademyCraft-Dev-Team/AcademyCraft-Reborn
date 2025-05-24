package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.framework.AbstractWidget;

public abstract class DragBarWidget extends AbstractWidget {
    public boolean showBackground = true;
    public boolean startDragging = false;
    public float dragOffset = 0f;

    public DragBarWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    protected abstract float getThumbSize();

    protected abstract float getThumbPosition();

    protected abstract float getTrackSize();

    protected abstract float getMouseRelative(float mouseX, float mouseY);

    protected abstract void updateTargetFromMouse(float mouse);

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
}