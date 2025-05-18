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

    /**
     * Gets the parent container, if any.
     * @return The parent WidgetContainer, or null if this is a root widget.
     */
    WidgetContainer getParent();

    /**
     * Sets the parent container. Should generally only be called by the container itself.
     * @param parent The parent container.
     */
    void setParent(WidgetContainer parent);

    void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick);

    default void mouseMoved(double mouseX, double mouseY) {
    }

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
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

    /**
     * Checks if the given screen coordinates are within the bounds of this widget.
     * Considers parent positions if applicable (absolute screen coordinates).
     * @param checkX Screen X coordinate.
     * @param checkY Screen Y coordinate.
     * @return True if the point is within the widget's bounds.
     */
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

    /**
     * Calculates the absolute X coordinate on the screen.
     * @return Absolute X coordinate.
     */
    default float getAbsoluteX() {
        return getX() + (getParent() != null ? getParent().getAbsoluteX() : 0);
    }

    /**
     * Calculates the absolute Y coordinate on the screen.
     * @return Absolute Y coordinate.
     */
    default float getAbsoluteY() {
        return getY() + (getParent() != null ? getParent().getAbsoluteY() : 0);
    }

    default float getAbsoluteZ() {
        return getZ() + (getParent() != null ? getParent().getAbsoluteZ() + 1 : 0);
    }
}