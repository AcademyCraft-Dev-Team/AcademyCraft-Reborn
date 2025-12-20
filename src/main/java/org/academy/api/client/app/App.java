package org.academy.api.client.app;

import net.minecraft.resources.Identifier;
import org.academy.api.client.gui.widget.Widget;

public interface App {
    Widget content();

    String name();

    Identifier icon();
}