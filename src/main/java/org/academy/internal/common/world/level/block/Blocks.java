package org.academy.internal.common.world.level.block;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static org.academy.AcademyCraft.MODID;

@SuppressWarnings("unused")
public class Blocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, MODID);
    public static final DeferredHolder<Block, WirelessNodeBlock> WIRELESS_NODE = BLOCKS.register("wireless_node",
            () -> new WirelessNodeBlock(BlockBehaviour.Properties.of()));
    public static final DeferredHolder<Block, WindGenBaseBlock> WIND_GEN_BASE = BLOCKS.register("wind_gen_base",
            () -> new WindGenBaseBlock(BlockBehaviour.Properties.of()));
    public static final DeferredHolder<Block, WindGenTopBlock> WIND_GEN_TOP = BLOCKS.register("wind_gen_top",
            () -> new WindGenTopBlock(BlockBehaviour.Properties.of()));
    public static final DeferredHolder<Block, WindGenPillarBlock> WIND_GEN_PILLAR = BLOCKS.register("wind_gen_pillar",
            () -> new WindGenPillarBlock(BlockBehaviour.Properties.of()));
    public static final DeferredHolder<Block, AbilityDeveloperBlock> ABILITY_DEVELOPER = BLOCKS.register("ability_developer",
            () -> new AbilityDeveloperBlock(BlockBehaviour.Properties.of()));
    public static final DeferredHolder<Block, ImagiphasePlasma> IMAGIPHASE_PLASMA = BLOCKS.register("imagiphase_plasma",
            ImagiphasePlasma::new);
    public static final DeferredHolder<Block, Block> IMAGIPHASE_VEGETATION = BLOCKS.register("imagiphase_vegetation",
            () -> new Block(BlockBehaviour.Properties.of()));
    public static final DeferredHolder<Block, ImagiphaseLeavesBlock> IMAGIPHASE_LEAVES = BLOCKS.register("imagiphase_leaves",
            ImagiphaseLeavesBlock::new);
    public static final DeferredHolder<Block, RotatedPillarBlock> IMAGIPHASE_LOG = BLOCKS.register("imagiphase_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.BASS).strength(3, 6).sound(SoundType.DEEPSLATE).ignitedByLava()));
    public static final DeferredHolder<Block, ImagiphaseLichenBlock> IMAGIPHASE_LICHEN = BLOCKS.register("imagiphase_lichen",
            ImagiphaseLichenBlock::new);
    public static final DeferredHolder<Block, OmniCraftingTableBlock> OMNI_CRAFTING_TABLE = BLOCKS.register("omni_crafting_table",
            () -> new OmniCraftingTableBlock(BlockBehaviour.Properties.of()));
    public static final DeferredHolder<Block, CatEngineBlock> CAT_ENGINE = BLOCKS.register("cat_engine",
            () -> new CatEngineBlock(BlockBehaviour.Properties.of()));
    public static final DeferredHolder<Block, Block> IMAGIPHASE_AMETHYST_BLOCK = BLOCKS.register("imagiphase_amethyst_block",
            () -> new Block(BlockBehaviour.Properties.of()));
    public static final DeferredHolder<Block, Block> IMAGIPHASE_METAL_BLOCK = BLOCKS.register("imagiphase_metal_block",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion()));

    private Blocks() {
    }
}