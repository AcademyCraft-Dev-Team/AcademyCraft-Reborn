package org.academy.forge;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.academy.internal.client.renderer.blockentity.forge.AbilityDeveloperBlockEntityRenderer;

import java.util.Map;

public class AcademyCraftBlockEntityRenderersImpl {
    public static void init(Map<BlockEntityType<?>, BlockEntityRenderer<?>> rendererMap) {
        rendererMap.put(AcademyCraftBlockEntityTypesImpl.ABILITY_DEVELOPER, new AbilityDeveloperBlockEntityRenderer());
    }

    private AcademyCraftBlockEntityRenderersImpl() {
    }
}