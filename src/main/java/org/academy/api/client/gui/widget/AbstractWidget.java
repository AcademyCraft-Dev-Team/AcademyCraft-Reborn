package org.academy.api.client.gui.widget;

import com.mojang.math.Axis;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.AnimatorListener;
import org.academy.api.client.gui.animation.StateListAnimator;
import org.academy.api.client.gui.drawable.Drawable;
import org.academy.api.client.gui.event.*;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.render.RenderContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractWidget implements Widget {
    protected WidgetContainer.LayoutParams layoutParams = WidgetContainer.LayoutParams.NONE;
    protected float measuredWidth;
    protected float measuredHeight;

    protected float x = 0, y = 0, z = 0, width = 0, height = 0;
    protected float translationX = 0;
    protected float translationY = 0;
    protected float scaleX = 1.0f;
    protected float scaleY = 1.0f;
    protected float rotation = 0.0f;
    protected float originX = 0.5f;
    protected float originY = 0.5f;

    protected Visibility visibility = Visibility.VISIBLE;
    protected boolean enabled = true;
    @Nullable
    protected WidgetContainer parent = null;
    protected boolean hovered = false;
    protected boolean focused = false;
    protected boolean pressed = false;
    protected boolean selected = false;
    protected boolean clickable = false;
    protected float alpha = 1.0f;
    protected float scrollX = 0f;
    protected float scrollY = 0f;
    protected String name = "";

    private boolean isAttached = false;
    private final List<Animator> attachedAnimators = new ArrayList<>();

    @Nullable
    protected Drawable background = null;
    @Nullable
    protected Drawable foreground = null;

    @Nullable
    private StateListAnimator stateListAnimator;

    @Override
    public void render(RenderContext context) {
        if (!isVisible()) return;

        var pivotX = width * originX;
        var pivotY = height * originY;

        var hasTransform = scaleX != 1.0f || scaleY != 1.0f || rotation != 0.0f;

        context.pose().pushPose();
        if (hasTransform) {
            context.pose().translate(pivotX, pivotY, 0);
            if (rotation != 0.0f) {
                context.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
            }
            if (scaleX != 1.0f || scaleY != 1.0f) {
                context.pose().scale(scaleX, scaleY, 1.0f);
            }
            context.pose().translate(-pivotX, -pivotY, 0);
        }

        renderInternal(context);

        context.pose().popPose();
    }

    protected void renderInternal(RenderContext context) {
        if (background != null) {
            background.draw(context, this);
        }
        if (foreground != null) {
            foreground.draw(context, this);
        }
    }

    @Override
    public void dispatchEvent(InputEvent event) {
        if (!isAbsoluteEnabled() || visibility != Visibility.VISIBLE) {
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
            pressed = true;
            updateStateAnimator();
            event.consume();
        }
    }

    protected void onMouseReleased(MouseEvent event) {
        pressed = false;
        updateStateAnimator();
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
        if (visibility == Visibility.GONE) {
            setMeasuredDimension(0, 0);
            return;
        }
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
        return switch (specMode) {
            case EXACTLY -> specSize;
            case AT_MOST -> Math.min(desiredSize, specSize);
            default -> desiredSize;
        };
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
    public Visibility getVisibility() {
        return visibility;
    }

    @Override
    public Widget setVisibility(Visibility visibility) {
        if (this.visibility != visibility) {
            this.visibility = visibility;
            if (parent != null) {
                parent.requestLayout();
            }
        }
        return this;
    }

    @Override
    public boolean isVisible() {
        return visibility == Visibility.VISIBLE;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Widget setEnabled(boolean enabled) {
        this.enabled = enabled;
        updateStateAnimator();
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
            updateStateAnimator();
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
        updateStateAnimator();
    }

    @Override
    public boolean isPressed() {
        return pressed;
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public void setSelected(boolean selected) {
        this.selected = selected;
        updateStateAnimator();
    }

    @Override
    public int getWidgetState() {
        var state = State.NONE;
        if (!enabled) state |= State.DISABLED;
        if (hovered) state |= State.HOVERED;
        if (pressed) state |= State.PRESSED;
        if (focused) state |= State.FOCUSED;
        if (selected) state |= State.SELECTED;
        return state;
    }

    @Override
    public void setStateListAnimator(@Nullable StateListAnimator animator) {
        stateListAnimator = animator;
        if (isAttached && animator != null) {
            animator.jumpToCurrentState();
            updateStateAnimator();
        }
    }

    @Override
    public @Nullable StateListAnimator getStateListAnimator() {
        return stateListAnimator;
    }

    private void updateStateAnimator() {
        if (stateListAnimator != null) {
            stateListAnimator.setState(getWidgetState());
        }
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
    public float getScaleX() {
        return scaleX;
    }

    @Override
    public Widget setScaleX(float scaleX) {
        this.scaleX = scaleX;
        return this;
    }

    @Override
    public float getScaleY() {
        return scaleY;
    }

    @Override
    public Widget setScaleY(float scaleY) {
        this.scaleY = scaleY;
        return this;
    }

    @Override
    public Widget setScale(float scale) {
        scaleX = scale;
        scaleY = scale;
        return this;
    }

    @Override
    public float getScale() {
        return scaleX;
    }

    @Override
    public float getRotation() {
        return rotation;
    }

    @Override
    public Widget setRotation(float rotation) {
        this.rotation = rotation;
        return this;
    }

    @Override
    public float getOriginX() {
        return originX;
    }

    @Override
    public Widget setOriginX(float originX) {
        this.originX = originX;
        return this;
    }

    @Override
    public float getOriginY() {
        return originY;
    }

    @Override
    public Widget setOriginY(float originY) {
        this.originY = originY;
        return this;
    }

    @Override
    public Widget setOrigin(float originX, float originY) {
        this.originX = originX;
        this.originY = originY;
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

    @Override
    public void setBackground(@Nullable Drawable background) {
        this.background = background;
    }

    @Override
    @Nullable
    public Drawable getBackground() {
        return background;
    }

    @Override
    public void setForeground(@Nullable Drawable foreground) {
        this.foreground = foreground;
    }

    @Override
    @Nullable
    public Drawable getForeground() {
        return foreground;
    }

    @Override
    public boolean isAttached() {
        return isAttached;
    }

    @Override
    public void dispatchAttached() {
        if (isAttached) return;
        isAttached = true;
        onAttached();
        if (stateListAnimator != null) {
            stateListAnimator.jumpToCurrentState();
            updateStateAnimator();
        }
    }

    @Override
    public void dispatchDetached() {
        if (!isAttached) return;
        onDetached();
        isAttached = false;
    }

    @Override
    public void onAttached() {
    }

    @Override
    public void onDetached() {
        cancelAnimations();
    }

    @Override
    public void startAnimation(Animator animator) {
        attachedAnimators.add(animator);
        animator.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                attachedAnimators.remove(animation);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                attachedAnimators.remove(animation);
            }
        });
        animator.start();
    }

    @Override
    public void cancelAnimations() {
        for (var anim : new ArrayList<>(attachedAnimators)) anim.cancel();
        attachedAnimators.clear();
    }
}