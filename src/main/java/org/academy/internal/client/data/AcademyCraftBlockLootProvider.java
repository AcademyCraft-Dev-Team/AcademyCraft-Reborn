package org.academy.internal.client.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import org.academy.internal.common.world.level.block.Blocks;

import java.util.List;
import java.util.Set;

public final class AcademyCraftBlockLootProvider extends BlockLootSubProvider {
    public AcademyCraftBlockLootProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        getKnownBlocks().forEach(this::dropSelf);
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return List.of(
                Blocks.LABORATORY_LIGHT_WALL_PANEL.get(),
                Blocks.LABORATORY_GRAY_WALL_PANEL.get(),
                Blocks.LABORATORY_DARK_WALL_PANEL.get(),
                Blocks.LABORATORY_LIGHT_BRICKS.get(),
                Blocks.LABORATORY_DARK_BRICKS.get(),
                Blocks.LABORATORY_LIGHT_GRATE.get(),
                Blocks.LABORATORY_GRAY_GRATE.get(),
                Blocks.LABORATORY_DARK_GRATE.get(),
                Blocks.LABORATORY_METAL_TRAPDOOR.get()
        );
    }
}
