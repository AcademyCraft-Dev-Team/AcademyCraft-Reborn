package org.academy.internal.common.world.level.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static org.academy.AcademyCraft.MODID;

@SuppressWarnings("unused")
public final class Blocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredHolder<Block, WirelessNodeBlock> WIRELESS_NODE =
            BLOCKS.registerBlock("wireless_node", WirelessNodeBlock::new);
    public static final DeferredHolder<Block, WindGenBaseBlock> WIND_GEN_BASE =
            BLOCKS.registerBlock("wind_gen_base", WindGenBaseBlock::new);
    public static final DeferredHolder<Block, WindGenTopBlock> WIND_GEN_TOP =
            BLOCKS.registerBlock("wind_gen_top", WindGenTopBlock::new);
    public static final DeferredHolder<Block, WindGenPillarBlock> WIND_GEN_PILLAR =
            BLOCKS.registerBlock("wind_gen_pillar", WindGenPillarBlock::new);
    public static final DeferredHolder<Block, AbilityDeveloperBlock> ABILITY_DEVELOPER =
            BLOCKS.registerBlock("ability_developer", AbilityDeveloperBlock::new);
/*    public static final DeferredHolder<Block, ImagiphasePlasma> IMAGIPHASE_PLASMA =
            BLOCKS.registerBlock("imagiphase_plasma", ImagiphasePlasma::new);*/
 /*   public static final DeferredHolder<Block, Block> IMAGIPHASE_VEGETATION =
            BLOCKS.registerBlock("imagiphase_vegetation", Block::new);
    public static final DeferredHolder<Block, ImagiphaseLeavesBlock> IMAGIPHASE_LEAVES =
            BLOCKS.registerBlock("imagiphase_leaves", ImagiphaseLeavesBlock::new);
    public static final DeferredHolder<Block, RotatedPillarBlock> IMAGIPHASE_LOG =
            BLOCKS.registerBlock("imagiphase_log",
                    properties -> new RotatedPillarBlock(properties
                            .instrument(NoteBlockInstrument.BASS)
                            .strength(3, 6)
                            .sound(SoundType.DEEPSLATE).ignitedByLava()
                    )
            );
    public static final DeferredHolder<Block, ImagiphaseLichenBlock> IMAGIPHASE_LICHEN =
            BLOCKS.registerBlock("imagiphase_lichen", ImagiphaseLichenBlock::new);*/
    public static final DeferredHolder<Block, OmniCraftingTableBlock> OMNI_CRAFTING_TABLE =
            BLOCKS.registerBlock("omni_crafting_table", OmniCraftingTableBlock::new);
    public static final DeferredHolder<Block, CatEngineBlock> CAT_ENGINE =
            BLOCKS.registerBlock("cat_engine", CatEngineBlock::new);
  /*  public static final DeferredHolder<Block, ImagiphaseAmethystBlock> IMAGIPHASE_AMETHYST_BLOCK =
            BLOCKS.registerBlock("imagiphase_amethyst_block", ImagiphaseAmethystBlock::new);
    public static final DeferredHolder<Block, ImagiphaseAmethystClusterBlock> IMAGIPHASE_AMETHYST_CLUSTER =
            BLOCKS.registerBlock("imagiphase_amethyst_cluster", properties ->
                    new ImagiphaseAmethystClusterBlock(7.0F, 10.0F,
                            properties
                                    .mapColor(MapColor.COLOR_PURPLE)
                                    .forceSolidOn()
                                    .noOcclusion()
                                    .strength(1.5F)
                                    .pushReaction(PushReaction.DESTROY)
                                    .sound(SoundType.AMETHYST_CLUSTER)
                                    .lightLevel(state -> 5)
                    ));*/
/*    public static final DeferredHolder<Block, BuddingImagiphaseAmethystBlock> BUDDING_IMAGIPHASE_AMETHYST =
            BLOCKS.registerBlock("budding_imagiphase_amethyst", BuddingImagiphaseAmethystBlock::new);*/
   /* public static final DeferredHolder<Block, Block> LARGE_IMAGIPHASE_AMETHYST_BUD =
            BLOCKS.registerBlock("large_imagiphase_amethyst_bud", properties ->
                    new ImagiphaseAmethystClusterBlock(5.0F, 10.0F,
                            properties
                                    .mapColor(MapColor.COLOR_PURPLE)
                                    .forceSolidOn()
                                    .noOcclusion()
                                    .strength(1.5F)
                                    .pushReaction(PushReaction.DESTROY)
                                    .sound(SoundType.LARGE_AMETHYST_BUD)
                                    .lightLevel(state -> 4)
                    )
            );
    public static final DeferredHolder<Block, ImagiphaseAmethystClusterBlock> MEDIUM_IMAGIPHASE_AMETHYST_BUD =
            BLOCKS.registerBlock("medium_imagiphase_amethyst_bud", properties ->
                    new ImagiphaseAmethystClusterBlock(4.0F, 10.0F,
                            properties
                                    .mapColor(MapColor.COLOR_PURPLE)
                                    .forceSolidOn()
                                    .noOcclusion()
                                    .strength(1.5F)
                                    .pushReaction(PushReaction.DESTROY)
                                    .sound(SoundType.MEDIUM_AMETHYST_BUD)
                                    .lightLevel(state -> 2)
                    )
            );
    public static final DeferredHolder<Block, ImagiphaseAmethystClusterBlock> SMALL_IMAGIPHASE_AMETHYST_BUD =
            BLOCKS.registerBlock("small_imagiphase_amethyst_bud", properties ->
                    new ImagiphaseAmethystClusterBlock(3.0F, 8.0F,
                            properties
                                    .mapColor(MapColor.COLOR_PURPLE)
                                    .forceSolidOn()
                                    .noOcclusion()
                                    .strength(1.5F)
                                    .pushReaction(PushReaction.DESTROY)
                                    .sound(SoundType.SMALL_AMETHYST_BUD)
                                    .lightLevel(state -> 1)
                    )
            );*/
/*    public static final DeferredHolder<Block, Block> IMAGIPHASE_METAL_BLOCK =
            BLOCKS.registerBlock("imagiphase_metal_block",
                    properties -> new Block(properties.noOcclusion()));*/
    public static final DeferredHolder<Block, SolarGenBlock> SOLAR_GEN =
            BLOCKS.registerBlock("solar_gen", SolarGenBlock::new);

    private Blocks() {
    }
}