package org.academy.internal.common.world.level.block;

import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

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

    private Blocks() {
    }
}