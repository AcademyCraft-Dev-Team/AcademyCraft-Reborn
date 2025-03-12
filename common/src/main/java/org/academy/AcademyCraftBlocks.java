package org.academy;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Now it's empty.
 */
public class AcademyCraftBlocks {
    public static final Map<ResourceLocation, Block> BLOCKS = new HashMap<>();

    static {
        init(BLOCKS);
    }

    @ExpectPlatform
    public static void init(Map<ResourceLocation, Block> blockMap) {
        throw new AssertionError();
    }

    private AcademyCraftBlocks() {
    }
}
