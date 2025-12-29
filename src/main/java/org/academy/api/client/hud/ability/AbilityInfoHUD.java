package org.academy.api.client.hud.ability;

import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

public final class AbilityInfoHUD {
    @Nullable
    private static AbilityInfoHUD INSTANCE;

    private AbilityInfoHUD() {
    }

    public static AbilityInfoHUD getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("AbilityInfoHUD has not been initialized.");
        return INSTANCE;
    }

    public static void initMain() {
        INSTANCE = new AbilityInfoHUD();
        NeoForge.EVENT_BUS.register(INSTANCE);
    }
}