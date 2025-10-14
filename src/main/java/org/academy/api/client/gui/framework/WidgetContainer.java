package org.academy.api.client.gui.framework;

import java.util.Map;
import java.util.NoSuchElementException;

public interface WidgetContainer extends Widget {
    void addChild(String name, Widget child);

    void removeChild(String name);

    void clearChildren();

    Map<String, Widget> getChildren();

    @Deprecated(forRemoval = true)
    @SuppressWarnings("unchecked")
    default <T extends Widget> T getChildUnSafe(String name) {
        if (!getChildren().containsKey(name)) {
            throw new NoSuchElementException("No such child: " + name);
        }
        return (T) getChildren().get(name);
    }
}