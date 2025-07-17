package org.academy.api.client.gui.framework;

import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.render.MatrixStack;

public interface Widget {
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

    boolean isAbsoluteEnabled();

    Widget setEnabled(boolean enabled);

    boolean isHovered();

    Widget setHovered(boolean hovered);

    WidgetContainer getParent();

    Widget setParent(WidgetContainer parent);

    void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick);

    float getAlpha();

    Widget setAlpha(float alpha);

    float getAbsoluteAlpha();

    default void mouseMoved(double mouseX, double mouseY) {
    }

    default boolean mousePressed(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    default boolean canFocus() {
        return false;
    }

    default boolean isFocused() {
        return false;
    }

    default Widget setFocused(boolean focused) {
        return this;
    }

    default void onFocusGained() {
    }

    default void onFocusLost() {
    }

    default boolean isMouseOver(double checkX, double checkY) {
        var absX = getAbsoluteX();
        var absY = getAbsoluteY();
        return checkX >= absX && checkY >= absY && checkX < absX + getWidth() && checkY < absY + getHeight();
    }

    default boolean isAbsoluteMouseOver(double mouseX, double mouseY) {
        var parentMouseOver = true;
        if (getParent() != null) {
            parentMouseOver = getParent().isAbsoluteMouseOver(mouseX, mouseY);
        }
        var mouseOver = isMouseOver(mouseX, mouseY);
        return parentMouseOver && mouseOver;
    }

    default float getAbsoluteX() {
        return getX() + (getParent() != null ? getParent().getAbsoluteX() : 0);
    }

    default float getAbsoluteY() {
        return getY() + (getParent() != null ? getParent().getAbsoluteY() : 0);
    }

    default float getAbsoluteZ() {
        return getZ() + (getParent() != null ? getParent().getAbsoluteZ() + 1 : 0);
    }
}