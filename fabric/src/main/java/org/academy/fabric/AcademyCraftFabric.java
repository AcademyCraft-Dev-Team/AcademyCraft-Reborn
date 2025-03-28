package org.academy.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.academy.AcademyCraft;
import org.academy.fabric.internal.common.world.level.block.entity.fabric.AbilityDeveloperBlockEntityFabric;

public class AcademyCraftFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        AcademyCraftRegisterFabric.register();
        AcademyCraft.init();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            AbilityDeveloperBlockEntityFabric.intiServer();
        });
    }
}