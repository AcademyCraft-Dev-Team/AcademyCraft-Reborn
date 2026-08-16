package org.academy.internal.common.world.level.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.internal.common.world.level.material.Fluids;

import static org.academy.AcademyCraft.MODID;

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
    public static final DeferredHolder<Block, OmniCraftingTableBlock> OMNI_CRAFTING_TABLE =
            BLOCKS.registerBlock("omni_crafting_table", OmniCraftingTableBlock::new);
    public static final DeferredHolder<Block, CatEngineBlock> CAT_ENGINE =
            BLOCKS.registerBlock("cat_engine", CatEngineBlock::new);
    public static final DeferredHolder<Block, SolarGenBlock> SOLAR_GEN =
            BLOCKS.registerBlock("solar_gen", SolarGenBlock::new);
    public static final DeferredHolder<Block, LiquidBlock> IMAG_PHASE =
            BLOCKS.registerBlock(
                    "imag_phase",
                    properties -> new LiquidBlock(
                            Fluids.IMAG_PHASE.get(),
                            properties
                    ),
                    () -> BlockBehaviour.Properties.of()
                            .replaceable()
                            .noCollision()
                            .randomTicks()
                            .strength(100.0F)
                            .pushReaction(PushReaction.DESTROY)
                            .noLootTable()
                            .liquid()
                            .sound(SoundType.EMPTY)
            );
    public static final DeferredHolder<Block, Block> IMAG_PHASE_VEGETATION =
            BLOCKS.registerSimpleBlock("imag_phase_vegetation", BlockBehaviour.Properties::of);
    public static final DeferredHolder<Block, ImagPhaseLeavesBlock> IMAG_PHASE_LEAVES =
            BLOCKS.registerBlock("imag_phase_leaves", ImagPhaseLeavesBlock::new);
    public static final DeferredHolder<Block, RotatedPillarBlock> IMAG_PHASE_LOG =
            BLOCKS.registerBlock(
                    "imag_phase_log",
                    RotatedPillarBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .instrument(NoteBlockInstrument.BASS)
                            .strength(3.0F, 6.0F)
                            .sound(SoundType.DEEPSLATE)
                            .ignitedByLava()
            );
    public static final DeferredHolder<Block, ImagPhaseLichenBlock> IMAG_PHASE_LICHEN =
            BLOCKS.registerBlock("imag_phase_lichen", ImagPhaseLichenBlock::new);

    private Blocks() {
    }
}
