package org.academy.api.common.structure;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.academy.AcademyCraft;

/** Public block tags used by the movable-structure system. */
public final class BlockStructureTags {
    /** Geological and terrain blocks that may use falling-block physics during settlement. */
    public static final TagKey<Block> NATURAL_SETTLEMENT = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(
                    AcademyCraft.MOD_ID,
                    "natural_structure_settlement"
            )
    );

    private BlockStructureTags() {
    }
}
