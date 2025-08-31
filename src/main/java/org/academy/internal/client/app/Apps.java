package org.academy.internal.client.app;

import org.academy.api.client.hud.terminal.App;
import org.academy.api.client.hud.terminal.AppManager;
import org.academy.api.client.hud.terminal.apps.SettingsApp;

import java.util.ArrayList;
import java.util.List;

public final class Apps {
    public static final List<App> APPS = new ArrayList<>();

    static {
        APPS.add(SettingsApp.INSTANCE);
    }

    public static void register() {
        for (var app : APPS) {
            AppManager.registerApp(app);
        }
    }

    private Apps() {
    }
}