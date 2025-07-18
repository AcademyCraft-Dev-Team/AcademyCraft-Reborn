package org.academy.internal.common.world.level.block.entity;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

import static org.academy.AcademyCraft.getResourceLocation;

@SuppressWarnings("DataFlowIssue")
public class BlockEntityTypes {
    public static final Map<ResourceLocation, BlockEntityType<?>> BLOCK_ENTITY_TYPES = new HashMap<>();

    public static final BlockEntityType<WirelessNodeBlockEntity> ADVANCED_WIRELESS_NODE = register(
            BlockEntityType.Builder.of(
                    WirelessNodeBlockEntity::new, Blocks.WIRELESS_NODE
            ),
            "advanced_wireless_node");

    public static final BlockEntityType<WindGenBaseBlockEntity> WIND_GEN_BASE = register(
            BlockEntityType.Builder.of(
                    WindGenBaseBlockEntity::new, Blocks.WIND_GEN_BASE
            ),
            "wind_gen_base");

    public static final BlockEntityType<WindGenTopBlockEntity> WIND_GEN_TOP = register(
            BlockEntityType.Builder.of(
                    WindGenTopBlockEntity::new, Blocks.WIND_GEN_TOP
            ),
            "wind_gen_top");

    public static final BlockEntityType<WindGenPillarBlockEntity> WIND_GEN_PILLAR = register(
            BlockEntityType.Builder.of(
                    WindGenPillarBlockEntity::new, Blocks.WIND_GEN_PILLAR
            ),
            "wind_gen_pillar");

    public static final BlockEntityType<AbilityDeveloperBlockEntity> ABILITY_DEVELOPER = register(
            BlockEntityType.Builder.of(
                    AbilityDeveloperBlockEntity::new, Blocks.ABILITY_DEVELOPER
            ),
            "ability_developer"
    );
    public static final BlockEntityType<OmniCraftingTableBlockEntity> OMNI_CRAFTING_TABLE = register(
            BlockEntityType.Builder.of(
                    OmniCraftingTableBlockEntity::new, Blocks.OMNI_CRAFTING_TABLE
            ),
            "omni_crafting_table"
    );
    public static final BlockEntityType<CatEngineBlockEntity> CAT_ENGINE = register(
            BlockEntityType.Builder.of(
                    CatEngineBlockEntity::new, Blocks.CAT_ENGINE
            ),
            "cat_engine"
    );

    public static <T extends BlockEntity> BlockEntityType<T> register(BlockEntityType.Builder<T> builder, String name) {
        BlockEntityType<T> result = builder.build(Util.fetchChoiceType(References.BLOCK_ENTITY, name));
        BLOCK_ENTITY_TYPES.put(getResourceLocation(name), result);
        return result;
    }

    private BlockEntityTypes() {
    }
}