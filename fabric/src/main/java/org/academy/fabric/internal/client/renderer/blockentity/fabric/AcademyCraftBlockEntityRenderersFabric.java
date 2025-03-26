package org.academy.fabric.internal.client.renderer.blockentity.fabric;

import org.academy.fabric.internal.common.world.level.block.entity.fabric.AcademyCraftBlockEntityTypesFabric;
import org.academy.internal.client.renderer.blockentity.AcademyCraftBlockEntityRenderers;

public class AcademyCraftBlockEntityRenderersFabric {
    public static void init() {
        AcademyCraftBlockEntityRenderers.BLOCK_ENTITY_RENDERERS.put(AcademyCraftBlockEntityTypesFabric.ABILITY_DEVELOPER, new AbilityDeveloperBlockEntityRenderer());
    }

    private AcademyCraftBlockEntityRenderersFabric() {
    }
}