package org.academy.fabric.internal.common.world.level.block.entity.fabric;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.academy.AcademyCraft;
import org.academy.fabric.internal.common.world.level.block.fabric.AcademyCraftBlocksFabric;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;
import team.reborn.energy.api.EnergyStorage;

public class BlockEntityTypesFabric {
    public static final BlockEntityType<AbilityDeveloperBlockEntityFabric> ABILITY_DEVELOPER = BlockEntityType.Builder.of(AbilityDeveloperBlockEntityFabric::new, AcademyCraftBlocksFabric.ABILITY_DEVELOPER_BLOCK).build(Util.fetchChoiceType(References.BLOCK_ENTITY, "ability_developer"));
    public static final BlockEntityType<RadioFrequencyEnergyOutputBridgeBlockEntity> RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE = BlockEntityType.Builder.of(RadioFrequencyEnergyOutputBridgeBlockEntity::new, AcademyCraftBlocksFabric.RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK).build(Util.fetchChoiceType(References.BLOCK_ENTITY, "radio_frequency_energy_output_bridge"));

    public static void init() {
        BlockEntityTypes.BLOCK_ENTITY_TYPES.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer"), ABILITY_DEVELOPER);
        BlockEntityTypes.BLOCK_ENTITY_TYPES.put(new ResourceLocation(AcademyCraft.MOD_ID, "radio_frequency_energy_output_bridge"), RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE);
        EnergyStorage.SIDED.registerForBlockEntity((radioFrequencyEnergyOutputBridgeBlockEntity, direction) -> radioFrequencyEnergyOutputBridgeBlockEntity.energyStorage, BlockEntityTypesFabric.RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE);
    }

    private BlockEntityTypesFabric() {
    }
}