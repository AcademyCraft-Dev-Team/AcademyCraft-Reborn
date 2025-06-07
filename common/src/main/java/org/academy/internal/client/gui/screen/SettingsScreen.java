package org.academy.internal.client.gui.screen;

import net.minecraft.network.chat.Component;
import org.academy.api.client.gui.framework.CGuiScreen;

public class SettingsScreen extends CGuiScreen {
    public SettingsScreen() {
        super(Component.literal("Settings"));
    }

    @Override
    protected void onInit() {
    }
}