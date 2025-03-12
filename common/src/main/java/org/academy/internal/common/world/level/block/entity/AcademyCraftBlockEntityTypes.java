package org.academy.internal.common.world.level.block.entity;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * Now it's empty.
 */
public class AcademyCraftBlockEntityTypes {
    public static final Map<ResourceLocation, BlockEntityType<?>> BLOCK_ENTITY_TYPES = new HashMap<>();

    @ExpectPlatform
    public static void init(Map<ResourceLocation, BlockEntityType<?>> blockEntityTypeMap) {
        throw new AssertionError();
    }

    private AcademyCraftBlockEntityTypes() {
    }
}