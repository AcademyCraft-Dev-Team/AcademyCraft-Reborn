package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.internal.common.world.level.block.Blocks;

import static org.academy.AcademyCraft.MODID;

public final class BlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessNodeBlockEntity>> WIRELESS_NODE = BLOCK_ENTITY_TYPES.register(
            "wireless_node",
            () -> new BlockEntityType<>(
                            WirelessNodeBlockEntity::new,
                            Blocks.WIRELESS_NODE.get())
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindGenBaseBlockEntity>> WIND_GEN_BASE = BLOCK_ENTITY_TYPES.register(
            "wind_gen_base",
            () -> new BlockEntityType<>(
                            WindGenBaseBlockEntity::new,
                            Blocks.WIND_GEN_BASE.get())
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindGenTopBlockEntity>> WIND_GEN_TOP = BLOCK_ENTITY_TYPES.register(
            "wind_gen_top",
            () -> new BlockEntityType<>(
                            WindGenTopBlockEntity::new,
                            Blocks.WIND_GEN_TOP.get())
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindGenPillarBlockEntity>> WIND_GEN_PILLAR = BLOCK_ENTITY_TYPES.register(
            "wind_gen_pillar",
            () -> new BlockEntityType<>(
                            WindGenPillarBlockEntity::new,
                            Blocks.WIND_GEN_PILLAR.get())
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AbilityDeveloperBlockEntity>> ABILITY_DEVELOPER = BLOCK_ENTITY_TYPES.register(
            "ability_developer",
            () -> new BlockEntityType<>(
                            AbilityDeveloperBlockEntity::new,
                            Blocks.ABILITY_DEVELOPER.get())
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OmniCraftingTableBlockEntity>> OMNI_CRAFTING_TABLE = BLOCK_ENTITY_TYPES.register(
            "omni_crafting_table",
            () -> new BlockEntityType<>(
                            OmniCraftingTableBlockEntity::new,
                            Blocks.OMNI_CRAFTING_TABLE.get())
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CatEngineBlockEntity>> CAT_ENGINE = BLOCK_ENTITY_TYPES.register(
            "cat_engine",
            () -> new BlockEntityType<>(
                            CatEngineBlockEntity::new,
                            Blocks.CAT_ENGINE.get())
    );

    private BlockEntityTypes() {
    }
}