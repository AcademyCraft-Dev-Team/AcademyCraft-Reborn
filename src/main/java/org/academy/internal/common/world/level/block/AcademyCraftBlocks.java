package org.academy.internal.common.world.level.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.academy.AcademyCraft;

import java.util.HashMap;
import java.util.Map;

public class AcademyCraftBlocks {
    public static final Map<ResourceLocation, Block> BLOCKS = new HashMap<>();
    public static final AbilityDeveloperBlock ABILITY_DEVELOPER_BLOCK = new AbilityDeveloperBlock(BlockBehaviour.Properties.of());

    static {
        BLOCKS.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer_block"), ABILITY_DEVELOPER_BLOCK);
    }

    private AcademyCraftBlocks() {
    }
}
