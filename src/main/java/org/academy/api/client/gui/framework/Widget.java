package org.academy.api.client.gui.framework;

import org.academy.api.client.gui.event.InputEvent;
import org.academy.api.common.vanilla.Tickable;
import org.jetbrains.annotations.Nullable;

public interface Widget extends Tickable {
    void render(WidgetRenderContext renderContext, double mouseX, double mouseY, float partialTick);

    /**
     * The new unified entry point for all input events.
     * This method is responsible for dispatching the event to specific handlers
     * within the widget's implementation.
     * @param event The input event to be processed.
     */
    void dispatchEvent(InputEvent event);

    String getName();

    Widget setName(String name);

    float getX();

    float getY();

    float getZ();

    float getWidth();

    float getHeight();

    Widget setX(float x);

    Widget setY(float y);

    Widget setZ(float z);

    Widget setWidth(float width);

    Widget setHeight(float height);

    boolean isVisible();

    Widget setVisible(boolean visible);

    boolean isEnabled();

    Widget setEnabled(boolean enabled);

    @Nullable WidgetContainer getParent();

    Widget setParent(@Nullable WidgetContainer parent);

    boolean isFocused();

    Widget setFocused(boolean focused);

    boolean isHovered();

    void setHovered(boolean hovered);

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

    float getAbsoluteAlpha();

    boolean isAbsoluteEnabled();

    boolean isMouseOver(double mouseX, double mouseY);

    boolean canFocus();

    void onFocusGained();

    void onFocusLost();

    @Override
    default void tick() {
    }
}