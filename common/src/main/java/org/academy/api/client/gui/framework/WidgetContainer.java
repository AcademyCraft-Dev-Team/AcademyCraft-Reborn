package org.academy.api.client.gui.framework;

import java.util.Map;

public interface WidgetContainer extends Widget, Iterable<Widget> {
    void addChild(String name, Widget child);

    void removeChild(String name);

    void clearChildren();

    Map<String, Widget> getChildren();

    /**
     * Finds the widget currently under the mouse cursor within this container.
     * Searches children in reverse order (topmost first).
     * @param mouseX Screen X coordinate.
     * @param mouseY Screen Y coordinate.
     * @return The widget under the mouse, or null if none.
     */
    Widget getWidgetAt(double mouseX, double mouseY);
}