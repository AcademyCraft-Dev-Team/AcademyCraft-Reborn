package org.academy.internal.common.world.level.block.entity;

import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.internal.common.world.level.block.Blocks;

import static org.academy.AcademyCraft.MODID;

@SuppressWarnings("DataFlowIssue")
public class BlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessNodeBlockEntity>> WIRELESS_NODE = BLOCK_ENTITY_TYPES.register(
            "wireless_node",
            () -> BlockEntityType.Builder.of(
                            WirelessNodeBlockEntity::new,
                            Blocks.WIRELESS_NODE.get())
                    .build(
                            Util.fetchChoiceType(
                                    References.BLOCK_ENTITY, "wireless_node"
                            )
                    )
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindGenBaseBlockEntity>> WIND_GEN_BASE = BLOCK_ENTITY_TYPES.register(
            "wind_gen_base",
            () -> BlockEntityType.Builder.of(
                            WindGenBaseBlockEntity::new,
                            Blocks.WIND_GEN_BASE.get())
                    .build(
                            Util.fetchChoiceType(
                                    References.BLOCK_ENTITY, "wind_gen_base"
                            )
                    )
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindGenTopBlockEntity>> WIND_GEN_TOP = BLOCK_ENTITY_TYPES.register(
            "wind_gen_top",
            () -> BlockEntityType.Builder.of(
                            WindGenTopBlockEntity::new,
                            Blocks.WIND_GEN_TOP.get())
                    .build(
                            Util.fetchChoiceType(
                                    References.BLOCK_ENTITY, "wind_gen_top"
                            )
                    )
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindGenPillarBlockEntity>> WIND_GEN_PILLAR = BLOCK_ENTITY_TYPES.register(
            "wind_gen_pillar",
            () -> BlockEntityType.Builder.of(
                            WindGenPillarBlockEntity::new,
                            Blocks.WIND_GEN_PILLAR.get())
                    .build(
                            Util.fetchChoiceType(
                                    References.BLOCK_ENTITY, "wind_gen_pillar"
                            )
                    )
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AbilityDeveloperBlockEntity>> ABILITY_DEVELOPER = BLOCK_ENTITY_TYPES.register(
            "ability_developer",
            () -> BlockEntityType.Builder.of(
                            AbilityDeveloperBlockEntity::new,
                            Blocks.ABILITY_DEVELOPER.get())
                    .build(
                            Util.fetchChoiceType(
                                    References.BLOCK_ENTITY, "ability_developer"
                            )
                    )
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OmniCraftingTableBlockEntity>> OMNI_CRAFTING_TABLE = BLOCK_ENTITY_TYPES.register(
            "omni_crafting_table",
            () -> BlockEntityType.Builder.of(
                            OmniCraftingTableBlockEntity::new,
                            Blocks.OMNI_CRAFTING_TABLE.get())
                    .build(
                            Util.fetchChoiceType(
                                    References.BLOCK_ENTITY, "omni_crafting_table"
                            )
                    )
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CatEngineBlockEntity>> CAT_ENGINE = BLOCK_ENTITY_TYPES.register(
            "cat_engine",
            () -> BlockEntityType.Builder.of(
                            CatEngineBlockEntity::new,
                            Blocks.CAT_ENGINE.get())
                    .build(
                            Util.fetchChoiceType(
                                    References.BLOCK_ENTITY, "cat_engine"
                            )
                    )
    );

    private BlockEntityTypes() {
    }
}