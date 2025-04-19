package org.academy.internal.common.world.level.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.academy.AcademyCraft;

import java.util.HashMap;
import java.util.Map;

public class Blocks {
    public static final Map<ResourceLocation, Block> BLOCKS = new HashMap<>();
    public static final Block ADVANCED_WIRELESS_NODE_BLOCK = new AdvancedWirelessNodeBlock(BlockBehaviour.Properties.of());
    public static final Block WIND_GEN_BASE_BLOCK = new WindGenBaseBlock(BlockBehaviour.Properties.of());

    static {
        BLOCKS.put(new ResourceLocation(AcademyCraft.MOD_ID,"advanced_wireless_node_block"), ADVANCED_WIRELESS_NODE_BLOCK);
        BLOCKS.put(new ResourceLocation(AcademyCraft.MOD_ID,"wind_gen_base_block"), WIND_GEN_BASE_BLOCK);
    }

    private Blocks() {
    }
}