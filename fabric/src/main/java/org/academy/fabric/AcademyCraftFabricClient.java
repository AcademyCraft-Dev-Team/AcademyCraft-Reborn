package org.academy.fabric;

import dev.felnull.specialmodelloader.api.event.SpecialModelLoaderEvents;
import net.fabricmc.api.ClientModInitializer;
import org.academy.AcademyCraft;

public class AcademyCraftFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SpecialModelLoaderEvents.LOAD_SCOPE.register(location -> AcademyCraft.MOD_ID.equals(location.getNamespace()));
    }
}