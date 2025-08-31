package org.academy.api.client.hud.terminal;

import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.gui.framework.AbstractContainerWidget;

public interface App {
    ResourceLocation getIcon();

    String getName();

    AbstractContainerWidget createUI(UIManager uiManager);
}