package org.academy.api.client.gui.framework;

public abstract class AbstractWidget implements Widget {
    protected float x, y, z, width, height;
    protected boolean visible = true;
    protected boolean enabled = true;
    protected WidgetContainer parent = null;
    protected boolean hovered = false;
    protected boolean focused = false;
    protected float alpha = 1.0f;
    protected String name = "";

    public AbstractWidget(float newX, float newY, float newWidth, float newHeight) {
        x = newX;
        y = newY;
        width = newWidth;
        height = newHeight;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Widget setName(String newName) {
        name = newName;
        return this;
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
    public Widget setX(float newX) {
        x = newX;
        return this;
    }

    @Override
    public Widget setY(float newY) {
        y = newY;
        return this;
    }

    @Override
    public Widget setZ(float newZ) {
        z = newZ;
        return this;
    }

    @Override
    public Widget setWidth(float newWidth) {
        width = newWidth;
        return this;
    }

    @Override
    public Widget setHeight(float newHeight) {
        height = newHeight;
        return this;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public Widget setVisible(boolean newVisible) {
        visible = newVisible;
        return this;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Widget setEnabled(boolean newEnabled) {
        enabled = newEnabled;
        return this;
    }

    @Override
    public WidgetContainer getParent() {
        return parent;
    }

    @Override
    public Widget setParent(WidgetContainer newParent) {
        parent = newParent;
        return this;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public Widget setFocused(boolean newFocused) {
        if (focused != newFocused && canFocus()) {
            focused = newFocused;
            if (newFocused) {
                onFocusGained();
            } else {
                onFocusLost();
            }
        }
        return this;
    }

    @Override
    public boolean isHovered() {
        return hovered;
    }

    @Override
    public Widget setHovered(boolean newHovered) {
        hovered = newHovered;
        return this;
    }

    @Override
    public boolean isAbsoluteEnabled() {
        if (isEnabled()) {
            if (parent != null) {
                return parent.isAbsoluteEnabled();
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    public float getAlpha() {
        return alpha;
    }

    @Override
    public Widget setAlpha(float newAlpha) {
        alpha = newAlpha;
        return this;
    }

    @Override
    public float getAbsoluteAlpha() {
        if (getParent() != null) {
            return getAlpha() * getParent().getAbsoluteAlpha();
        }
        return getAlpha();
    }
}