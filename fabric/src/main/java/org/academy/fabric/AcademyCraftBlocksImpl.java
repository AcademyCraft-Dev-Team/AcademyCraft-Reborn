package org.academy.fabric;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.level.block.fabric.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.fabric.RadioFrequencyEnergyOutputBridgeBlock;

import java.util.Map;

public class AcademyCraftBlocksImpl {
    public static final AbilityDeveloperBlock ABILITY_DEVELOPER_BLOCK = new AbilityDeveloperBlock(BlockBehaviour.Properties.of().noOcclusion());
    public static final RadioFrequencyEnergyOutputBridgeBlock RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK = new RadioFrequencyEnergyOutputBridgeBlock(BlockBehaviour.Properties.of());

    public static void init(Map<ResourceLocation, Block> blockMap) {
        blockMap.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer_block"), ABILITY_DEVELOPER_BLOCK);
        blockMap.put(new ResourceLocation(AcademyCraft.MOD_ID, "radio_frequency_energy_output_bridge_block"), RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK);
    }

    private AcademyCraftBlocksImpl() {
    }
}