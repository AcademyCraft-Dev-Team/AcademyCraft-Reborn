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
    public static final Block IMAGIPHASE_PLASMA = register("imagiphase_plasma", new LiquidBlock(Fluids.IMAGIPHASE_PLASMA,
            BlockBehaviour.Properties.of()
                    .replaceable()
                    .noCollission()
                    .randomTicks()
                    .strength(100.0F)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY)));
    public static final Block IMAGIPHASE_VEGETATION = register("imagiphase_vegetation", new Block(BlockBehaviour.Properties.of()));
    public static final Block IMAGIPHASE_LEAVES = register("imagiphase_leaves", new ImagiphaseLeavesBlock());
    public static final Block IMAGIPHASE_LOG = register("imagiphase_log", new RotatedPillarBlock(
                    BlockBehaviour.Properties.of()
                            .instrument(NoteBlockInstrument.BASS)
                            .strength(3, 6)
                            .sound(SoundType.DEEPSLATE)
                            .ignitedByLava()
            )
    );
    public static final Block IMAGIPHASE_LICHEN = register("imagiphase_lichen", new ImagiphaseLichenBlock());
    public static final Block OMNI_CRAFTING_TABLE = register("omni_crafting_table", new OmniCraftingTableBlock());
    public static final Block CAT_ENGINE = register("cat_engine", new CatEngineBlock());
    public static final Block IMAGIPHASE_AMETHYST_BLOCK = register("imagiphase_amethyst_block", new Block(BlockBehaviour.Properties.of()));
    public static final Block IMAGIPHASE_METAL_BLOCK = register("imagiphase_metal_block", new Block(BlockBehaviour.Properties.of().noOcclusion()));

    public static <T extends Block> T register(String name, T block) {
        BLOCKS.put(name, block);
        return block;
    }

    private Blocks() {
    }
}