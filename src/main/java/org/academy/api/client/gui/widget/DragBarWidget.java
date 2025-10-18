package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.gui.framework.Orientation;

public abstract class DragBarWidget extends AbstractWidget {
    protected boolean showBackground = true;
    protected float dragOffset = 0f;
    protected int thumbColor = 0xFFAAAAAA;
    protected int trackColor = 0xFF202020;
    protected final Orientation orientation;
    private boolean isDragging = false;

    public DragBarWidget(float x, float y, float width, float height, Orientation orientation) {
        super(x, y, width, height);
        this.orientation = orientation;
        clickable = true;
    }

    protected abstract float getThumbSize();

    protected abstract float getThumbPosition();

    protected abstract void updateTargetFromMouse(float mouse);

    protected float getTrackSize() {
        return orientation == Orientation.HORIZONTAL ? getWidth() : getHeight();
    }

    protected float getMouseRelative(float mouseX, float mouseY) {
        return orientation == Orientation.HORIZONTAL ? (mouseX - getAbsoluteX()) : (mouseY - getAbsoluteY());
    }

    @Override
    protected void onMousePressed(MouseEvent event) {
        if (isMouseOver(event.getX(), event.getY()) && event.getButton() == 0) {
            isDragging = true;
            dragOffset = getThumbSize() / 2f;
            updateTargetFromMouse(getMouseRelative((float) event.getX(), (float) event.getY()));
            event.consume();
        }
    }

    @Override
    protected void onMouseReleased(MouseEvent event) {
        isDragging = false;
        super.onMouseReleased(event);
    }

    @Override
    protected void onMouseDragged(MouseEvent event) {
        if (isDragging && event.getButton() == 0) {
            updateTargetFromMouse(getMouseRelative((float) event.getX(), (float) event.getY()));
            event.consume();
        }
    }

    public DragBarWidget setThumbColor(int color) {
        thumbColor = color;
        return this;
    }

    public DragBarWidget setTrackColor(int color) {
        trackColor = color;
        return this;
    }

    public DragBarWidget setShowBackground(boolean show) {
        showBackground = show;
        return this;
    }

    public int getThumbColor() {
        return thumbColor;
    }

    public int getTrackColor() {
        return trackColor;
    }

    public boolean isShowBackground() {
        return showBackground;
    }

    @Override
    public boolean canFocus() {
        return true;
    }
}