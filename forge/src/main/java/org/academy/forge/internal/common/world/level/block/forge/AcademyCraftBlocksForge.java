package org.academy.forge.internal.common.world.level.block.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.level.block.AcademyCraftBlocks;

public class AcademyCraftBlocksForge {
    public static final AbilityDeveloperBlockForge ABILITY_DEVELOPER_BLOCK = new AbilityDeveloperBlockForge(BlockBehaviour.Properties.of().noOcclusion());
    public static final RadioFrequencyEnergyOutputBridgeBlock RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK = new RadioFrequencyEnergyOutputBridgeBlock(BlockBehaviour.Properties.of());

    public static void init() {
        AcademyCraftBlocks.BLOCKS.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer_block"), ABILITY_DEVELOPER_BLOCK);
        AcademyCraftBlocks.BLOCKS.put(new ResourceLocation(AcademyCraft.MOD_ID, "radio_frequency_energy_output_bridge_block"), RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK);
    }

    private AcademyCraftBlocksForge() {
    }
}
