package org.academy.fabric;

import net.fabricmc.api.ModInitializer;

public class AcademyCraftFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        AcademyCraftRegisterFabric.register();
    }
}