package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.event.*;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.render.WidgetRenderContext;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractWidget implements Widget {
    protected WidgetContainer.LayoutParams layoutParams = WidgetContainer.LayoutParams.NONE;
    protected float measuredWidth;
    protected float measuredHeight;

    protected float x = 0, y = 0, z = 0, width = 0, height = 0;
    protected float translationX = 0;
    protected float translationY = 0;
    protected boolean visible = true;
    protected boolean enabled = true;
    @Nullable
    protected WidgetContainer parent = null;
    protected boolean hovered = false;
    protected boolean focused = false;
    protected boolean clickable = false;
    protected float alpha = 1.0f;
    protected float scrollX = 0f;
    protected float scrollY = 0f;
    protected String name = "";

    @Override
    public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
    }

    @Override
    public void dispatchEvent(InputEvent event) {
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

    protected void onMousePressed(MouseEvent event) {
        if (isClickable() && isMouseOver(event.getX(), event.getY())) {
            event.consume();
        }
    }

    protected void onMouseReleased(MouseEvent event) {
        if (isMouseOver(event.getX(), event.getY())) {
            event.consume();
        }
    }

    protected void onMouseMoved(MouseEvent event) {
    }

    protected void onMouseScrolled(ScrollEvent event) {
    }

    protected void onMouseDragged(MouseEvent event) {
    }

    protected void onKeyPressed(KeyEvent event) {
    }

    protected void onKeyReleased(KeyEvent event) {
    }

    protected void onCharTyped(CharTypedEvent event) {
    }

    @Override
    public void measure(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    protected void onMeasure(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        var desiredWidth = layoutParams.paddingLeft + layoutParams.paddingRight;
        var desiredHeight = layoutParams.paddingTop + layoutParams.paddingBottom;

        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec)
        );
    }

    protected final void setMeasuredDimension(float measuredWidth, float measuredHeight) {
        this.measuredWidth = measuredWidth;
        this.measuredHeight = measuredHeight;
    }

    protected static float resolveSize(float desiredSize, MeasureSpec spec) {
        var specMode = spec.getMode();
        var specSize = spec.getSize();
        switch (specMode) {
            case EXACTLY:
                return specSize;
            case AT_MOST:
                return Math.min(desiredSize, specSize);
            case UNSPECIFIED:
            default:
                return desiredSize;
        }
    }

    @Override
    public void layout(float left, float top, float right, float bottom) {
        x = left;
        y = top;
        width = right - left;
        height = bottom - top;
    }

    @Override
    public void requestLayout() {
        if (parent != null) {
            parent.requestLayout();
        }
    }

    @Override
    public WidgetContainer.LayoutParams getLayoutParams() {
        return layoutParams;
    }

    @Override
    public Widget setLayoutParams(WidgetContainer.LayoutParams params) {
        layoutParams = params;
        requestLayout();
        return this;
    }

    @Override
    public float getMeasuredWidth() {
        return measuredWidth;
    }

    @Override
    public float getMeasuredHeight() {
        return measuredHeight;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Widget setName(String name) {
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
    public Widget setZ(float z) {
        this.z = z;
        return this;
    }

    @Override
    public Widget setWidth(float width) {
        if (layoutParams.width != width || layoutParams.widthMode != SizeMode.FIXED) {
            layoutParams.width = width;
            layoutParams.widthMode = SizeMode.FIXED;
            requestLayout();
        }
        return this;
    }

    @Override
    public Widget setHeight(float height) {
        if (layoutParams.height != height || layoutParams.heightMode != SizeMode.FIXED) {
            layoutParams.height = height;
            layoutParams.heightMode = SizeMode.FIXED;
            requestLayout();
        }
        return this;
    }

    @Override
    public float getTranslationX() {
        return translationX;
    }

    @Override
    public Widget setTranslationX(float translationX) {
        this.translationX = translationX;
        return this;
    }

    @Override
    public float getTranslationY() {
        return translationY;
    }

    @Override
    public Widget setTranslationY(float translationY) {
        this.translationY = translationY;
        return this;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public Widget setVisible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            if (parent != null) {
                parent.requestLayout();
            }
        }
        return this;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Widget setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public @Nullable WidgetContainer getParent() {
        return parent;
    }

    @Override
    public Widget setParent(@Nullable WidgetContainer parent) {
        this.parent = parent;
        return this;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public Widget setFocused(boolean focused) {
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

    @Override
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    @Override
    public float getAlpha() {
        return alpha;
    }

    @Override
    public Widget setAlpha(float alpha) {
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
        scrollX = x;
        scrollY = y;
    }

    @Override
    public void scrollBy(float dx, float dy) {
        scrollX += dx;
        scrollY += dy;
    }

    @Override
    public boolean isClickable() {
        return clickable;
    }

    @Override
    public Widget setClickable(boolean clickable) {
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
    public float getAbsoluteTranslationX() {
        var currentTX = translationX;
        var p = parent;
        while (p != null) {
            currentTX += p.getTranslationX();
            p = p.getParent();
        }
        return currentTX;
    }

    @Override
    public float getAbsoluteTranslationY() {
        var currentTY = translationY;
        var p = parent;
        while (p != null) {
            currentTY += p.getTranslationY();
            p = p.getParent();
        }
        return currentTY;
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
        var visualX = getAbsoluteX() + getAbsoluteTranslationX();
        var visualY = getAbsoluteY() + getAbsoluteTranslationY();
        return mouseX >= visualX && mouseY >= visualY && mouseX < visualX + width && mouseY < visualY + height;
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