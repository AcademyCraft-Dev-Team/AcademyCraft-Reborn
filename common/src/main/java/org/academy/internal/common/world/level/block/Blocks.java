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
    public static final Block WIND_GEN_TOP_BLOCK = new WindGenTopBlock();
    public static final Block WIND_GEN_PILLAR_BLOCK = new WindGenPillarBlock();
    public static final MultiBlock ABILITY_DEVELOPER_BLOCK = new AbilityDeveloperBlock();

    static {
        BLOCKS.put(new ResourceLocation(AcademyCraft.MOD_ID,"advanced_wireless_node_block"), ADVANCED_WIRELESS_NODE_BLOCK);
        BLOCKS.put(new ResourceLocation(AcademyCraft.MOD_ID,"wind_gen_base_block"), WIND_GEN_BASE_BLOCK);
        BLOCKS.put(new ResourceLocation(AcademyCraft.MOD_ID, "wind_gen_top_block"), WIND_GEN_TOP_BLOCK);
        BLOCKS.put(new ResourceLocation(AcademyCraft.MOD_ID, "wind_gen_pillar_block"), WIND_GEN_PILLAR_BLOCK);
        BLOCKS.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer_block"), ABILITY_DEVELOPER_BLOCK);
    }

    private Blocks() {
    }
}