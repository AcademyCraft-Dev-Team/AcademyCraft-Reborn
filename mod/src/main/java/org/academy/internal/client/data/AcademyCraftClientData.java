package org.academy.internal.client.data;

import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;
import java.util.Set;

@EventBusSubscriber(Dist.CLIENT)
public final class AcademyCraftClientData {
    private AcademyCraftClientData() {
    }

    @SubscribeEvent
    public static void dataSetup(GatherDataEvent.Client event) {
        event.createProvider(AcademyCraftModelProvider::new);

        var builder = new RegistrySetBuilder();
        builder.add(Registries.LOOT_TABLE, new LootTableProvider(
                Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(
                        AcademyCraftBlockLootProvider::new,
                        LootContextParamSets.BLOCK
                ))
        ));
        builder.add(Registries.ADVANCEMENT, new AdvancementProvider(
                List.of(AcademyCraftAdvancementProvider::new)
        ));
        builder.add(RecipeProvider.asBootstrap(AcademyCraftRecipeProvider::new));
        event.createReloadableRegistryObjects(builder);
    }
}
