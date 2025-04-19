package org.academy.internal.common.world.level.block.entity;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("DataFlowIssue")
public class BlockEntityTypes {
    public static final Map<ResourceLocation, BlockEntityType<?>> BLOCK_ENTITY_TYPES = new HashMap<>();
    public static final BlockEntityType<AdvancedWirelessNodeBlockEntity> ADVANCED_WIRELESS_NODE_BLOCK_ENTITY_BLOCK_ENTITY_TYPE =
            BlockEntityType.Builder.<AdvancedWirelessNodeBlockEntity>of(new BlockEntityType.BlockEntitySupplier<>() {
                @Override
                public @NotNull AdvancedWirelessNodeBlockEntity create(@NotNull BlockPos pos, @NotNull BlockState state) {
                    return new AdvancedWirelessNodeBlockEntity(ADVANCED_WIRELESS_NODE_BLOCK_ENTITY_BLOCK_ENTITY_TYPE, pos, state);
                }
            }, Blocks.ADVANCED_WIRELESS_NODE_BLOCK).build(Util.fetchChoiceType(References.BLOCK_ENTITY, "advanced_wireless_node"));
    public static final BlockEntityType<WindGenBaseBlockEntity> WIND_GEN_BASE_BLOCK_ENTITY_BLOCK_ENTITY_TYPE =
            BlockEntityType.Builder.<WindGenBaseBlockEntity>of(new BlockEntityType.BlockEntitySupplier<>() {
                @Override
                public @NotNull WindGenBaseBlockEntity create(@NotNull BlockPos pos, @NotNull BlockState state) {
                    return new WindGenBaseBlockEntity(WIND_GEN_BASE_BLOCK_ENTITY_BLOCK_ENTITY_TYPE, pos, state);
                }
            },Blocks.WIND_GEN_BASE_BLOCK).build(Util.fetchChoiceType(References.BLOCK_ENTITY, "wind_gen_base"));

    static {
        BLOCK_ENTITY_TYPES.put(new ResourceLocation(AcademyCraft.MOD_ID, "advanced_wireless_node"), ADVANCED_WIRELESS_NODE_BLOCK_ENTITY_BLOCK_ENTITY_TYPE);
        BLOCK_ENTITY_TYPES.put(new ResourceLocation(AcademyCraft.MOD_ID, "wind_gen_base"), WIND_GEN_BASE_BLOCK_ENTITY_BLOCK_ENTITY_TYPE);
    }

    private BlockEntityTypes() {
    }
}