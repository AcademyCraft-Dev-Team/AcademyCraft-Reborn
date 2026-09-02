package org.academy.internal.client.data;

import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;
import java.util.Set;

public final class AcademyCraftClientData {
    private AcademyCraftClientData() {
    }

    public static void dataSetup(GatherDataEvent.Client event) {
        event.createProvider(AcademyCraftModelProvider::new);
        event.createProvider((output, registries) -> new LootTableProvider(
                output,
                Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(
                        AcademyCraftBlockLootProvider::new,
                        LootContextParamSets.BLOCK
                )),
                registries
        ));
        event.createProvider(output -> new AcademyCraftDatapackProvider(output, event.getLookupProvider()));
        event.createProvider(AcademyCraftRecipeProvider.Runner::new);
        event.createProvider((output, registries) -> new AdvancementProvider(
                output,
                registries,
                List.of(new AcademyCraftAdvancementProvider())
        ));
    }
}
