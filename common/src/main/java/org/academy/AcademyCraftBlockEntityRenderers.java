package org.academy;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.HashMap;
import java.util.Map;

public class AcademyCraftBlockEntityRenderers {
    public static final Map<BlockEntityType<?>, BlockEntityRenderer<?>> BLOCK_ENTITY_RENDERERS = new HashMap<>();

    static {
        init(BLOCK_ENTITY_RENDERERS);
    }

    @ExpectPlatform
    public static void init(Map<BlockEntityType<?>, BlockEntityRenderer<?>> rendererMap) {
        throw new AssertionError();
    }

    private AcademyCraftBlockEntityRenderers() {
    }
}
