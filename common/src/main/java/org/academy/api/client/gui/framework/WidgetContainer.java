package org.academy.api.client.gui.framework;

import java.util.Map;

public interface WidgetContainer extends Widget, Iterable<Widget> {
    void addChild(String name, Widget child);

    void removeChild(String name);

    void clearChildren();

    Map<String, Widget> getChildren();
}