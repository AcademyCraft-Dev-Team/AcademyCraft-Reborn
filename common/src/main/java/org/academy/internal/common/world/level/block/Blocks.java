package org.academy.internal.common.world.level.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.PushReaction;
import org.academy.internal.common.world.level.material.Fluids;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class Blocks {
    public static final Map<String, Block> BLOCKS = new HashMap<>();
    public static final Block WIRELESS_NODE = register("wireless_node", new WirelessNodeBlock(BlockBehaviour.Properties.of()));
    public static final Block WIND_GEN_BASE = register("wind_gen_base", new WindGenBaseBlock(BlockBehaviour.Properties.of()));
    public static final MultiBlock WIND_GEN_TOP = register("wind_gen_top", new WindGenTopBlock());
    public static final Block WIND_GEN_PILLAR = register("wind_gen_pillar", new WindGenPillarBlock());
    public static final MultiBlock ABILITY_DEVELOPER = register("ability_developer", new AbilityDeveloperBlock());
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
    public static final Block IMAG_PHASE_VEGETATION = register("imag_phase_vegetation", new Block(BlockBehaviour.Properties.of()));
    public static final Block IMAG_PHASE_LEAVES = register("imag_phase_leaves", new ImagPhaseLeavesBlock());
    public static final Block IMAG_PHASE_LOG = register("imag_phase_log", new RotatedPillarBlock(BlockBehaviour.Properties.of()
            .instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava()));
    public static final Block IMAG_PHASE_LICHEN = register("imag_phase_lichen", new ImagPhaseLichenBlock());

    public static <T extends Block> T register(String name, T block) {
        BLOCKS.put(name, block);
        return block;
    }

    private Blocks() {
    }
}