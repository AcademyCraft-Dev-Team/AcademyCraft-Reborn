package org.academy.internal.common.world.level.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import org.academy.internal.common.world.level.material.Fluids;

import java.util.HashMap;
import java.util.Map;

public class Blocks {
    public static final Map<String, Block> BLOCKS = new HashMap<>();
    public static final Block WIRELESS_NODE = register("wireless_node_block", new WirelessNodeBlock(BlockBehaviour.Properties.of()));
    public static final Block WIND_GEN_BASE = register("wind_gen_base_block", new WindGenBaseBlock(BlockBehaviour.Properties.of()));
    public static final MultiBlock WIND_GEN_TOP = register("wind_gen_top_block", new WindGenTopBlock());
    public static final Block WIND_GEN_PILLAR = register("wind_gen_pillar_block", new WindGenPillarBlock());
    public static final MultiBlock ABILITY_DEVELOPER = register("ability_developer_block", new AbilityDeveloperBlock());
    public static final Block IMAG_PHASE = register("imag_phase", new LiquidBlock(Fluids.IMAG_PHASE,
            BlockBehaviour.Properties.of()
                    .replaceable()
                    .noCollission()
                    .randomTicks()
                    .strength(100.0F)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY)));

    public static <T extends Block> T register(String name, T block) {
        BLOCKS.put(name, block);
        return block;
    }

    private Blocks() {
    }
}