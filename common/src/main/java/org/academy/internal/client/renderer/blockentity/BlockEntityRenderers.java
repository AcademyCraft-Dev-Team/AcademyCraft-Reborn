package org.academy.internal.client.renderer.blockentity;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;

import java.util.HashMap;
import java.util.Map;

public class BlockEntityRenderers {
    public static final Map<BlockEntityType<?>, BlockEntityRenderer<?>> BLOCK_ENTITY_RENDERERS = new HashMap<>();

    static {
        BLOCK_ENTITY_RENDERERS.put(BlockEntityTypes.WIND_GEN_BASE, WindGenBaseBlockEntityRenderer.INSTANCE);
        BLOCK_ENTITY_RENDERERS.put(BlockEntityTypes.WIND_GEN_TOP, WindGenTopBlockEntityRenderer.INSTANCE);
        BLOCK_ENTITY_RENDERERS.put(BlockEntityTypes.ABILITY_DEVELOPER, AbilityDeveloperBlockEntityRenderer.INSTANCE);
        BLOCK_ENTITY_RENDERERS.put(BlockEntityTypes.WIND_GEN_PILLAR, WindGenPillarBlockEntityRenderer.INSTANCE);
        BLOCK_ENTITY_RENDERERS.put(BlockEntityTypes.ADVANCED_WIRELESS_NODE, WirelessNodeBlockEntityRenderer.INSTANCE);
        BLOCK_ENTITY_RENDERERS.put(BlockEntityTypes.OMNI_CRAFTING_TABLE, OmniCraftingTableBlockEntityRenderer.INSTANCE);
        BLOCK_ENTITY_RENDERERS.put(BlockEntityTypes.CAT_ENGINE, CatEngineBlockEntityRenderer.INSTANCE);
    }

    private BlockEntityRenderers() {
    }
}