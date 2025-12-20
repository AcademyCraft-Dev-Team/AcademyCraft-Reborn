package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.StateListAnimator;
import org.academy.api.client.gui.drawable.Drawable;
import org.academy.api.client.gui.event.InputEvent;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.common.vanilla.Tickable;
import org.jspecify.annotations.Nullable;

public interface Widget extends Tickable {
    void render(RenderContext context);

    void dispatchEvent(InputEvent event);

    void measure(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec);

    void layout(float left, float top, float right, float bottom);

    void requestLayout();

    WidgetContainer.LayoutParams getLayoutParams();

    Widget setLayoutParams(WidgetContainer.LayoutParams params);

    float getMeasuredWidth();

    float getMeasuredHeight();

    String getName();

    Widget setName(String name);

    float getX();

    float getY();

    float getZ();

    float getWidth();

    float getHeight();

    Widget setZ(float z);

    Widget setWidth(float width);

    Widget setHeight(float height);

    float getTranslationX();

    Widget setTranslationX(float translationX);

    float getTranslationY();

    Widget setTranslationY(float translationY);

    Visibility getVisibility();

    Widget setVisibility(Visibility visibility);

    boolean isVisible();

    boolean isEnabled();

    Widget setEnabled(boolean enabled);

    @Nullable
    WidgetContainer getParent();

    Widget setParent(@Nullable WidgetContainer parent);

    boolean isFocused();

    Widget setFocused(boolean focused);

    boolean isHovered();

    void setHovered(boolean hovered);

    boolean isPressed();

    boolean isSelected();

    void setSelected(boolean selected);

    int getWidgetState();

    void setStateListAnimator(@Nullable StateListAnimator animator);

    @Nullable
    StateListAnimator getStateListAnimator();

    float getAlpha();

    Widget setAlpha(float alpha);

    float getScaleX();

    Widget setScaleX(float scaleX);

    float getScaleY();

    Widget setScaleY(float scaleY);

    Widget setScale(float scale);

    float getScale();

    float getRotation();

    Widget setRotation(float rotation);

    float getOriginX();

    Widget setOriginX(float originX);

    float getOriginY();

    Widget setOriginY(float originY);

    Widget setOrigin(float originX, float originY);

    float getScrollX();

    float getScrollY();

    void scrollTo(float x, float y);

    void scrollBy(float dx, float dy);

    boolean isClickable();

    Widget setClickable(boolean clickable);

    float getAbsoluteX();

    float getAbsoluteY();

    float getAbsoluteTranslationX();

    float getAbsoluteTranslationY();

    float getAbsoluteAlpha();

    boolean isAbsoluteEnabled();

    boolean isMouseOver(double mouseX, double mouseY);

    boolean canFocus();

    void onFocusGained();

    void onFocusLost();

    @Override
    default void tick() {
    }

    void setBackground(@Nullable Drawable background);

    @Nullable
    Drawable getBackground();

    void setForeground(@Nullable Drawable foreground);

    @Nullable
    Drawable getForeground();

    void onAttached();

    void onDetached();

    void dispatchAttached();

    void dispatchDetached();

    boolean isAttached();

    void startAnimation(Animator animator);

    void cancelAnimations();

    enum Visibility {
        VISIBLE,
        INVISIBLE,
        GONE
    }

    class State {
        public static final int NONE = 0;
        public static final int DISABLED = 1;
        public static final int HOVERED = 1 << 1;
        public static final int PRESSED = 1 << 2;
        public static final int FOCUSED = 1 << 3;
        public static final int CHECKED = 1 << 4;
        public static final int SELECTED = 1 << 5;

        private State() {
        }
    }
}