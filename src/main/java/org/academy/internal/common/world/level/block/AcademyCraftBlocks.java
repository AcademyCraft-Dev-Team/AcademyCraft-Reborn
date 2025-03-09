package org.academy.internal.common.world.level.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.academy.AcademyCraft;

import java.util.HashMap;
import java.util.Map;

public class AcademyCraftBlocks {
    public static final Map<ResourceLocation, Block> BLOCKS = new HashMap<>();
    public static final AbilityDeveloperBlock ABILITY_DEVELOPER_BLOCK = new AbilityDeveloperBlock(BlockBehaviour.Properties.of().noOcclusion());
    public static final RadioFrequencyEnergyOutputBridgeBlock RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK = new RadioFrequencyEnergyOutputBridgeBlock(BlockBehaviour.Properties.of());

    static {
        BLOCKS.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer_block"), ABILITY_DEVELOPER_BLOCK);
        BLOCKS.put(new ResourceLocation(AcademyCraft.MOD_ID, "radio_frequency_energy_output_bridge_block"), RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK);
    }

    private AcademyCraftBlocks() {
    }
}
