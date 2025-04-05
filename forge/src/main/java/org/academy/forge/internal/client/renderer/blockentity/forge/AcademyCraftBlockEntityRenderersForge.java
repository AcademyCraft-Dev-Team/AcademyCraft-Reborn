package org.academy.forge.internal.client.renderer.blockentity.forge;

import org.academy.forge.internal.common.world.level.block.entity.forge.AcademyCraftBlockEntityTypesForge;
import org.academy.internal.client.renderer.blockentity.BlockEntityRenderers;

public class AcademyCraftBlockEntityRenderersForge {
    public static void init() {
        BlockEntityRenderers.BLOCK_ENTITY_RENDERERS.put(AcademyCraftBlockEntityTypesForge.ABILITY_DEVELOPER, new AbilityDeveloperBlockEntityRenderer());
    }

    private AcademyCraftBlockEntityRenderersForge() {
    }
}