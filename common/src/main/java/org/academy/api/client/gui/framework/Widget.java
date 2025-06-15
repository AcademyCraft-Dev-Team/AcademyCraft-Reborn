package org.academy.api.client.gui.framework;

import net.minecraft.client.gui.GuiGraphics;

public interface Widget {
    float getX();

    float getY();

    float getZ();

    float getWidth();

    float getHeight();

    void setX(float x);

    void setY(float y);

    void setZ(float z);

    void setWidth(float width);

    void setHeight(float height);

    boolean isVisible();

    void setVisible(boolean visible);

    boolean isEnabled(); // Can the widget be interacted with?

    boolean isAbsoluteEnabled();

    void setEnabled(boolean enabled);

    boolean isHovered();

    void setHovered(boolean hovered);

    WidgetContainer getParent();

    void setParent(WidgetContainer parent);

    void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick);

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

    default void setFocused(boolean focused) {
    }

    default void onFocusGained() {
    }

    default void onFocusLost() {
    }

    default boolean isMouseOver(double checkX, double checkY) {
        float absX = getAbsoluteX();
        float absY = getAbsoluteY();
        return checkX >= absX && checkY >= absY && checkX < absX + getWidth() && checkY < absY + getHeight();
    }

    default boolean isAbsoluteMouseOver(double mouseX, double mouseY) {
        boolean parentMouseOver = true;
        if (getParent() != null) {
            parentMouseOver = getParent().isAbsoluteMouseOver(mouseX, mouseY);
        }
        boolean mouseOver = isMouseOver(mouseX, mouseY);
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