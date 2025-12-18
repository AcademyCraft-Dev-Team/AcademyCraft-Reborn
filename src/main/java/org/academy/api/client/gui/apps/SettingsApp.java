package org.academy.api.client.gui.apps;

import net.minecraft.resources.Identifier;
import org.academy.api.client.gui.widget.FrameLayoutWidget;
import org.jspecify.annotations.Nullable;

public class SettingsApp extends AbstractApp {
    public SettingsApp(@Nullable Identifier iconRes) {
        super("Settings", iconRes);
    }

    @Override
    protected void initAppContent(FrameLayoutWidget content) {

    }
}
