package org.academy.api.client.gui.framework;

import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.gui.event.InputEvent;
import org.academy.api.client.render.MatrixStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Widget {
    void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick);

    /**
     * The new unified entry point for all input events.
     * This method is responsible for dispatching the event to specific handlers
     * within the widget's implementation.
     * @param event The input event to be processed.
     */
    void dispatchEvent(@NotNull InputEvent event);

    @NotNull String getName();

    @NotNull Widget setName(String name);

    float getX();
    float getY();
    float getZ();
    float getWidth();
    float getHeight();

    @NotNull Widget setX(float x);

    @NotNull Widget setY(float y);

    @NotNull Widget setZ(float z);

    @NotNull Widget setWidth(float width);

    @NotNull Widget setHeight(float height);

    boolean isVisible();

    @NotNull Widget setVisible(boolean visible);

    boolean isEnabled();

    @NotNull Widget setEnabled(boolean enabled);

    @Nullable WidgetContainer getParent();

    @NotNull Widget setParent(@Nullable WidgetContainer parent);

    boolean isFocused();

    @NotNull Widget setFocused(boolean focused);

    boolean isHovered();

    float getAlpha();

    @NotNull Widget setAlpha(float alpha);

    float getScrollX();

    float getScrollY();

    void scrollTo(float x, float y);

    void scrollBy(float dx, float dy);

    boolean isClickable();

    @NotNull Widget setClickable(boolean clickable);

    float getAbsoluteX();

    float getAbsoluteY();
    float getAbsoluteAlpha();

    boolean isAbsoluteEnabled();

    boolean isMouseOver(double mouseX, double mouseY);

    boolean canFocus();

    void onFocusGained();

    void onFocusLost();
}