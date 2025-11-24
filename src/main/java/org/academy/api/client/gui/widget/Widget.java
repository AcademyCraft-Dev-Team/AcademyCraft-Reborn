package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.animation.Animator;
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

    boolean isVisible();

    Widget setVisible(boolean visible);

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

    float getAlpha();

    Widget setAlpha(float alpha);

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
}