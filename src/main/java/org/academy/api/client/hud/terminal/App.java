package org.academy.api.client.hud.terminal;

import net.minecraft.resources.Identifier;
import org.academy.api.client.gui.widget.AbstractWidgetContainer;

public interface App {
    Identifier getIcon();

    String getName();

    AbstractWidgetContainer createUI(UIManager uiManager);
}