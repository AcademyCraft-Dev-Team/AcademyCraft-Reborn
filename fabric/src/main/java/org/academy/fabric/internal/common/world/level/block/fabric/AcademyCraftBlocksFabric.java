package org.academy.fabric.internal.common.world.level.block.fabric;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.level.block.Blocks;

public class AcademyCraftBlocksFabric {
    public static final AbilityDeveloperBlockFabric ABILITY_DEVELOPER_BLOCK = new AbilityDeveloperBlockFabric(BlockBehaviour.Properties.of());
    public static final RadioFrequencyEnergyOutputBridgeBlock RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK = new RadioFrequencyEnergyOutputBridgeBlock(BlockBehaviour.Properties.of());

    private AcademyCraftBlocksFabric() {
    }

    public static void init() {
        Blocks.BLOCKS.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer_block"), ABILITY_DEVELOPER_BLOCK);
        Blocks.BLOCKS.put(new ResourceLocation(AcademyCraft.MOD_ID, "radio_frequency_energy_output_bridge_block"), RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK);
    }
}