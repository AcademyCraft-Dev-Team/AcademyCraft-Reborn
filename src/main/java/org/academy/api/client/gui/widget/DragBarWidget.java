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
        this.clickable = true;
    }

    protected abstract float getThumbSize();

    protected abstract float getThumbPosition();

    protected abstract void updateTargetFromMouse(float mouse);

    protected float getTrackSize() {
        return this.orientation == Orientation.HORIZONTAL ? this.getWidth() : this.getHeight();
    }

    protected float getMouseRelative(float mouseX, float mouseY) {
        return this.orientation == Orientation.HORIZONTAL ? (mouseX - this.getAbsoluteX()) : (mouseY - this.getAbsoluteY());
    }

    @Override
    protected void onMousePressed(MouseEvent event) {
        if (isMouseOver(event.getX(), event.getY()) && event.getButton() == 0) {
            this.isDragging = true;
            this.dragOffset = this.getThumbSize() / 2f;
            this.updateTargetFromMouse(this.getMouseRelative((float) event.getX(), (float) event.getY()));
            event.consume();
        }
    }

    @Override
    protected void onMouseReleased(MouseEvent event) {
        this.isDragging = false;
        super.onMouseReleased(event);
    }

    @Override
    protected void onMouseDragged(MouseEvent event) {
        if (this.isDragging && event.getButton() == 0) {
            this.updateTargetFromMouse(this.getMouseRelative((float) event.getX(), (float) event.getY()));
            event.consume();
        }
    }

    public DragBarWidget setThumbColor(int color) {
        this.thumbColor = color;
        return this;
    }

    public DragBarWidget setTrackColor(int color) {
        this.trackColor = color;
        return this;
    }

    public DragBarWidget setShowBackground(boolean show) {
        this.showBackground = show;
        return this;
    }

    public int getThumbColor() {
        return this.thumbColor;
    }

    public int getTrackColor() {
        return this.trackColor;
    }

    public boolean isShowBackground() {
        return this.showBackground;
    }

    @Override
    public boolean canFocus() {
        return true;
    }
}