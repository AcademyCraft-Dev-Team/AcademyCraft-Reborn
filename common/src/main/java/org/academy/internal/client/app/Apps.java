package org.academy.internal.client.app;

import org.academy.api.client.hud.DataTerminalHUD;

import java.util.ArrayList;
import java.util.List;

public final class Apps {
    public static final List<DataTerminalHUD.App> APPS = new ArrayList<>();

    static {
        APPS.add(Settings.INSTANCE);
    }

    public static void register() {
        for (DataTerminalHUD.App app : APPS) {
            DataTerminalHUD.registerApp(app);
        }
    }

    private Apps() {
    }
}