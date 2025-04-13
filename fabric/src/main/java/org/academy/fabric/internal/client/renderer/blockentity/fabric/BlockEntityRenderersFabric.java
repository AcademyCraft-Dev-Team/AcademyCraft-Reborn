package org.academy.fabric.internal.client.renderer.blockentity.fabric;

import org.academy.fabric.internal.common.world.level.block.entity.fabric.BlockEntityTypesFabric;
import org.academy.internal.client.renderer.blockentity.AbilityDeveloperBlockEntityRenderer;
import org.academy.internal.client.renderer.blockentity.BlockEntityRenderers;

public class BlockEntityRenderersFabric {
    private BlockEntityRenderersFabric() {
    }

    public static void init() {
        BlockEntityRenderers.BLOCK_ENTITY_RENDERERS.put(BlockEntityTypesFabric.ABILITY_DEVELOPER, new AbilityDeveloperBlockEntityRenderer());
    }
}