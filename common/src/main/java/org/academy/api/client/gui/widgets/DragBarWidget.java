package org.academy.api.client.gui.widgets;

import org.academy.api.client.gui.framework.AbstractWidget;

public abstract class DragBarWidget extends AbstractWidget {
    public boolean showBackground = true;
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
        if (button == 0 && isHovered()) {
            dragOffset = getThumbSize() / 2f;
            updateTargetFromMouse(getMouseRelative((float) mouseX, (float) mouseY));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && isHovered()) {
            updateTargetFromMouse(getMouseRelative((float) mouseX, (float) mouseY));
            return true;
        }

        return false;
    }
}