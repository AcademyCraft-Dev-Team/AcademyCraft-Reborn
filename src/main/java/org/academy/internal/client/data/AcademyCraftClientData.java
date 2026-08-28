package org.academy.internal.client.data;

import net.minecraft.data.advancements.AdvancementProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;

public final class AcademyCraftClientData {
    private AcademyCraftClientData() {
    }

    public static void dataSetup(GatherDataEvent.Client event) {
        event.createProvider(AcademyCraftModelProvider::new);
        event.createProvider(output -> new AcademyCraftDatapackProvider(output, event.getLookupProvider()));
        event.createProvider(AcademyCraftRecipeProvider.Runner::new);
        event.createProvider((output, registries) -> new AdvancementProvider(
                output,
                registries,
                List.of(new AcademyCraftAdvancementProvider())
        ));
    }
}
