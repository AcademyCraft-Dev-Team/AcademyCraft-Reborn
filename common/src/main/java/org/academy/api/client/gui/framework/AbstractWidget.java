package org.academy.api.client.gui.framework;

public abstract class AbstractWidget implements Widget {
    protected float x, y, z, width, height;
    protected boolean visible = true;
    protected boolean enabled = true;
    protected WidgetContainer parent = null;
    protected boolean hovered = false;
    protected boolean focused = false;

    public AbstractWidget(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getY() {
        return y;
    }

    @Override
    public float getZ() {
        return z;
    }

    @Override
    public float getWidth() {
        return width;
    }

    @Override
    public float getHeight() {
        return height;
    }

    @Override
    public void setX(float x) {
        this.x = x;
    }

    @Override
    public void setY(float y) {
        this.y = y;
    }

    @Override
    public void setZ(float z) {
        this.z = z;
    }

    @Override
    public void setWidth(float width) {
        this.width = width;
    }

    @Override
    public void setHeight(float height) {
        this.height = height;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public WidgetContainer getParent() {
        return parent;
    }

    @Override
    public void setParent(WidgetContainer parent) {
        this.parent = parent;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void setFocused(boolean focused) {
        if (this.focused != focused && canFocus()) {
            this.focused = focused;
            if (focused) {
                onFocusGained();
            } else {
                onFocusLost();
            }
        }
    }

    @Override
    public boolean isHovered() {
        return hovered;
    }

    @Override
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    @Override
    public boolean isAbsoluteEnabled() {
        if (isEnabled()){
            if (parent != null) {
                return parent.isAbsoluteEnabled();
            } else {
                return true;
            }
        } else {
            return false;
        }
    }
}