package org.academy.internal.client.renderer.blockentity;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.academy.internal.common.world.level.block.entity.AcademyCraftBlockEntityTypes;

import java.util.HashMap;
import java.util.Map;

public class AcademyCraftBlockEntityRenderers {
    public static final Map<BlockEntityType<?>, BlockEntityRenderer<?>> BLOCK_ENTITY_RENDERERS = new HashMap<>();

    static {
        BLOCK_ENTITY_RENDERERS.put(AcademyCraftBlockEntityTypes.ABILITY_DEVELOPER, new AbilityDeveloperBlockEntityRenderer());
    }

    private AcademyCraftBlockEntityRenderers() {
    }
}
