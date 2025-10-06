package org.academy.internal.client.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.level.levelgen.feature.Features;
import org.academy.internal.common.world.level.levelgen.feature.ImagiphaseLakeFeature;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class AcademyCraftDatapackProvider extends DatapackBuiltinEntriesProvider {
    public static final ResourceKey<ConfiguredFeature<?, ?>> IMAG_PHASE_LAKE = ResourceKey.create(Registries.CONFIGURED_FEATURE, AcademyCraft.academy("imag_phase_lake"));
    public static final ResourceKey<PlacedFeature> IMAG_PHASE_LAKE_PLACE = ResourceKey.create(Registries.PLACED_FEATURE, AcademyCraft.academy("imag_phase_lake"));
    public static final ResourceKey<BiomeModifier> IMAG_PHASE_LAKE_MODIFIER = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            AcademyCraft.academy("imag_phase_lake_modifier")
    );
    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.CONFIGURED_FEATURE, context -> context.register(
                    IMAG_PHASE_LAKE,
                    new ConfiguredFeature<>(
                            Features.IMAG_PHASE_LAKE.get(),
                            new ImagiphaseLakeFeature.Configuration(
                                    UniformInt.of(5, 10),
                                    UniformInt.of(5, 10)
                            )
                    )
            ))
            .add(Registries.PLACED_FEATURE, context ->
                    context.register(
                            IMAG_PHASE_LAKE_PLACE,
                            new PlacedFeature(
                                    context.lookup(Registries.CONFIGURED_FEATURE).getOrThrow(IMAG_PHASE_LAKE),
                                    List.of(
                                            BiomeFilter.biome()
                                    )
                            )
                    )
            )
            .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, bootstrap -> {
                var biomes = bootstrap.lookup(Registries.BIOME);
                var placedFeatures = bootstrap.lookup(Registries.PLACED_FEATURE);
                bootstrap.register(IMAG_PHASE_LAKE_MODIFIER,
                        new BiomeModifiers.AddFeaturesBiomeModifier(
                                HolderSet.direct(biomes.getOrThrow(Biomes.DEEP_DARK)),
                                HolderSet.direct(placedFeatures.getOrThrow(IMAG_PHASE_LAKE_PLACE)),
                                GenerationStep.Decoration.LAKES
                        )
                );
            });

    public AcademyCraftDatapackProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, BUILDER, Set.of(AcademyCraft.MOD_ID));
    }
}