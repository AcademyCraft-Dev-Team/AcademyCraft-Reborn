package org.academy.fabric;

import net.fabricmc.api.ModInitializer;
import org.academy.AcademyCraft;

public class AcademyCraftFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        AcademyCraftRegisterFabric.register();
        AcademyCraft.init();
    }
}