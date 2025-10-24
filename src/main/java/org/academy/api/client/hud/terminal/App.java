package org.academy.api.client.hud.terminal;

import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.gui.widget.AbstractWidgetContainer;

public interface App {
    ResourceLocation getIcon();

    String getName();

    AbstractWidgetContainer createUI(UIManager uiManager);
}