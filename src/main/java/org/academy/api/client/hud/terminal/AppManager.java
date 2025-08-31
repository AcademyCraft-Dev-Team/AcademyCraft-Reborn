package org.academy.api.client.hud.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AppManager {
    private static final List<App> APP_LIST = new ArrayList<>();

    private AppManager() {
    }

    public static void registerApp(App app) {
        APP_LIST.add(app);
    }

    public static List<App> getApps() {
        return Collections.unmodifiableList(APP_LIST);
    }
}