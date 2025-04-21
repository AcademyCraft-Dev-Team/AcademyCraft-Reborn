package org.academy.api.client.gui.widgets;

import org.academy.api.client.gui.framework.AbstractContainerWidget;

/**
 * A simple container that just holds children and delegates events.
 * Can be extended for scrolling, specific layouts, backgrounds etc.
 */
public class PanelWidget extends AbstractContainerWidget {
    public boolean shouldFocus = false;

    public PanelWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    @Override
    public boolean shouldFocus() {
        return shouldFocus;
    }
}