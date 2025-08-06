package org.academy.api.client.gui.framework;

import org.academy.api.client.gui.event.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractWidget implements Widget {
    protected float x, y, z, width, height;
    protected boolean visible = true;
    protected boolean enabled = true;
    protected WidgetContainer parent = null;
    protected boolean hovered = false;
    protected boolean focused = false;
    protected boolean clickable = false;
    protected float alpha = 1.0f;
    protected float scrollX = 0f;
    protected float scrollY = 0f;
    protected String name = "";

    public AbstractWidget(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public void dispatchEvent(@NotNull InputEvent event) {
        if (!isAbsoluteEnabled() || !isVisible()) {
            return;
        }

        switch (event.getType()) {
            case MOUSE_PRESSED -> onMousePressed((MouseEvent) event);
            case MOUSE_RELEASED -> onMouseReleased((MouseEvent) event);
            case MOUSE_MOVED -> onMouseMoved((MouseEvent) event);
            case MOUSE_SCROLLED -> onMouseScrolled((ScrollEvent) event);
            case MOUSE_DRAGGED -> onMouseDragged((MouseEvent) event);
            case KEY_PRESSED -> onKeyPressed((KeyEvent) event);
            case KEY_RELEASED -> onKeyReleased((KeyEvent) event);
            case CHAR_TYPED -> onCharTyped((CharTypedEvent) event);
        }
    }

    protected void onMousePressed(@NotNull MouseEvent event) {
        if (isClickable() && isMouseOver(event.getX(), event.getY())) {
            event.consume();
        }
    }

    protected void onMouseReleased(@NotNull MouseEvent event) {
        if (isMouseOver(event.getX(), event.getY())) {
            event.consume();
        }
    }

    protected void onMouseMoved(@NotNull MouseEvent event) {
    }

    protected void onMouseScrolled(@NotNull ScrollEvent event) {
    }

    protected void onMouseDragged(@NotNull MouseEvent event) {
    }

    protected void onKeyPressed(@NotNull KeyEvent event) {
    }

    protected void onKeyReleased(@NotNull KeyEvent event) {
    }

    protected void onCharTyped(@NotNull CharTypedEvent event) {
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public @NotNull Widget setName(String name) {
        this.name = name;
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
    public @NotNull Widget setX(float x) {
        this.x = x;
        return this;
    }
    @Override
    public @NotNull Widget setY(float y) {
        this.y = y;
        return this;
    }
    @Override
    public @NotNull Widget setZ(float z) {
        this.z = z;
        return this;
    }
    @Override
    public @NotNull Widget setWidth(float width) {
        this.width = width;
        return this;
    }
    @Override
    public @NotNull Widget setHeight(float height) {
        this.height = height;
        return this;
    }
    @Override
    public boolean isVisible() {
        return visible;
    }
    @Override
    public @NotNull Widget setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    @Override
    public @NotNull Widget setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public @Nullable WidgetContainer getParent() {
        return parent;
    }
    @Override
    public @NotNull Widget setParent(@Nullable WidgetContainer parent) {
        this.parent = parent;
        return this;
    }
    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public @NotNull Widget setFocused(boolean focused) {
        if (this.focused != focused && canFocus()) {
            this.focused = focused;
            if (focused) onFocusGained();
            else onFocusLost();
        }
        return this;
    }
    @Override
    public boolean isHovered() {
        return hovered;
    }

    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    @Override
    public float getAlpha() {
        return alpha;
    }
    @Override
    public @NotNull Widget setAlpha(float alpha) {
        this.alpha = alpha;
        return this;
    }
    @Override
    public float getScrollX() {
        return scrollX;
    }

    @Override
    public float getScrollY() {
        return scrollY;
    }

    @Override
    public void scrollTo(float x, float y) {
        this.scrollX = x;
        this.scrollY = y;
    }

    @Override
    public void scrollBy(float dx, float dy) {
        this.scrollX += dx;
        this.scrollY += dy;
    }

    @Override
    public boolean isClickable() {
        return clickable;
    }

    @Override
    public @NotNull Widget setClickable(boolean clickable) {
        this.clickable = clickable;
        return this;
    }

    @Override
    public float getAbsoluteX() {
        var currentX = x;
        var p = parent;
        while (p != null) {
            currentX += p.getX();
            currentX -= p.getScrollX();
            p = p.getParent();
        }
        return currentX;
    }

    @Override
    public float getAbsoluteY() {
        var currentY = y;
        var p = parent;
        while (p != null) {
            currentY += p.getY();
            currentY -= p.getScrollY();
            p = p.getParent();
        }
        return currentY;
    }

    @Override
    public float getAbsoluteAlpha() {
        var currentAlpha = alpha;
        var p = parent;
        while (p != null) {
            currentAlpha *= p.getAlpha();
            p = p.getParent();
        }
        return currentAlpha;
    }

    @Override
    public boolean isAbsoluteEnabled() {
        var current = (Widget) this;
        while (current != null) {
            if (!current.isEnabled()) return false;
            current = current.getParent();
        }
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        var absX = getAbsoluteX();
        var absY = getAbsoluteY();
        return mouseX >= absX && mouseY >= absY && mouseX < absX + width && mouseY < absY + height;
    }

    @Override
    public boolean canFocus() {
        return false;
    }
    @Override
    public void onFocusGained() {
    }

    @Override
    public void onFocusLost() {
    }
}