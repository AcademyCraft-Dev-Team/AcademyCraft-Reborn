package org.academy.api.client.gui.event;

import org.academy.api.client.gui.widget.Widget;

@FunctionalInterface
public interface OnClickListener {
    void onClick(Widget source);
}