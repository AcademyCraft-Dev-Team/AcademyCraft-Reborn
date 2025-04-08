package org.academy.internal.client.renderer.blockentity;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.HashMap;
import java.util.Map;

public class BlockEntityRenderers {
    public static final Map<BlockEntityType<?>, BlockEntityRenderer<?>> BLOCK_ENTITY_RENDERERS = new HashMap<>();

    private BlockEntityRenderers() {
    }
}