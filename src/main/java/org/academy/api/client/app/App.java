package org.academy.api.client.app;

import net.minecraft.resources.Identifier;
import org.academy.api.client.gui.widget.WidgetContext;

public interface App {
    WidgetContext context();

    String name();

    Identifier icon();
}