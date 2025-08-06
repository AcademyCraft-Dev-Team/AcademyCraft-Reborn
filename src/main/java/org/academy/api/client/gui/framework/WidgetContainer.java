package org.academy.api.client.gui.framework;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.NoSuchElementException;

public interface WidgetContainer extends Widget {
    void addChild(String name, Widget child);

    void removeChild(String name);

    void clearChildren();

    @NotNull
    Map<String, Widget> getChildren();

    @SuppressWarnings("unchecked")
    @NotNull
    default <T extends Widget> T getChildUnSafe(String name) {
        if (!getChildren().containsKey(name)) {
            throw new NoSuchElementException("No such child: " + name);
        }
        return (T) getChildren().get(name);
    }
}