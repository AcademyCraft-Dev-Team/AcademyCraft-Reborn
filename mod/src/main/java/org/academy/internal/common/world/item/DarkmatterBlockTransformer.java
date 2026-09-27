package org.academy.internal.common.world.item;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.Direction;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.CopyPropertiesProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedStateProvider;
import net.neoforged.neoforge.common.DataMapHooks;

import java.util.List;
import java.util.Map;

/**
 * 26.3 merged the old per-item tool abilities into a single {@code BLOCK_TRANSFORMER} component,
 * and only one transformer can be attached to an item. The darkmatter tool therefore needs a
 * combined transformer covering axe stripping/scraping, shovel path flattening and hoe tilling.
 */
final class DarkmatterBlockTransformer {
    private DarkmatterBlockTransformer() {
    }

    static final BlockTransformer BLOCK_TRANSFORMER = new BlockTransformer(
            ImmutableList.<BlockTransformer.BlockTransformData>builder()
                    .addAll(axeTransforms())
                    .addAll(shovelTransforms())
                    .addAll(hoeTransforms())
                    .build()
    );

    private static List<BlockTransformer.BlockTransformData> axeTransforms() {
        RuleBasedStateProvider.Builder scrape = RuleBasedStateProvider.builder();
        for (Map.Entry<Block, Block> entry : DataMapHooks.INVERSE_OXIDIZABLES_DATAMAP.entrySet()) {
            scrape.ifTrueThenProvide(BlockPredicate.matchesBlocks(entry.getKey()), new CopyPropertiesProvider(entry.getValue()));
        }

        RuleBasedStateProvider.Builder unwax = RuleBasedStateProvider.builder();
        for (Map.Entry<Block, Block> entry : DataMapHooks.INVERSE_WAXABLES_DATAMAP.entrySet()) {
            unwax.ifTrueThenProvide(BlockPredicate.matchesBlocks(entry.getKey()), new CopyPropertiesProvider(entry.getValue()));
        }

        RuleBasedStateProvider.Builder logs = RuleBasedStateProvider.builder();
        for (var log : BuiltInRegistries.BLOCK) {
            var key = BuiltInRegistries.BLOCK.getKey(log);
            if (key == null) continue;
            var path = key.getPath();
            if (!path.endsWith("_log") && !path.endsWith("_wood")) continue;
            var stripped = BuiltInRegistries.BLOCK.getOptional(
                    Identifier.withDefaultNamespace("stripped_" + path));
            stripped.ifPresent(block -> logs.ifTrueThenProvide(BlockPredicate.matchesBlocks(log), new CopyPropertiesProvider(block)));
        }

        return List.of(
                BlockTransformer.BlockTransformData.builder(logs.build())
                        .sound(SoundEvents.AXE_STRIP)
                        .build(),
                BlockTransformer.BlockTransformData.builder(scrape.build())
                        .sound(SoundEvents.AXE_SCRAPE)
                        .particle(BlockTransformer.TransformParticle.SCRAPE)
                        .build(),
                BlockTransformer.BlockTransformData.builder(unwax.build())
                        .sound(SoundEvents.AXE_WAX_OFF)
                        .particle(BlockTransformer.TransformParticle.WAX_OFF)
                        .build()
        );
    }

    private static List<BlockTransformer.BlockTransformData> shovelTransforms() {
        return List.of(
                BlockTransformer.BlockTransformData.builder(
                                RuleBasedStateProvider.builder()
                                        .ifTrueThenProvide(
                                                BlockPredicate.allOf(
                                                        BlockPredicate.matchesTag(BlockTags.TURNS_INTO_DIRT_PATH),
                                                        BlockPredicate.matchesTag(Direction.UP, BlockTags.AIR)
                                                ),
                                                new CopyPropertiesProvider(Blocks.DIRT_PATH)
                                        )
                                        .build()
                        )
                        .sound(SoundEvents.SHOVEL_FLATTEN)
                        .disallowedFaces(List.of(Direction.DOWN))
                        .build()
        );
    }

    private static List<BlockTransformer.BlockTransformData> hoeTransforms() {
        return List.of(
                BlockTransformer.BlockTransformData.builder(
                                RuleBasedStateProvider.builder()
                                        .ifTrueThenProvide(BlockPredicate.matchesBlocks(Blocks.DIRT_PATH, Blocks.GRASS_BLOCK), new CopyPropertiesProvider(Blocks.FARMLAND))
                                        .build()
                        )
                        .sound(SoundEvents.HOE_TILL)
                        .build(),
                BlockTransformer.BlockTransformData.builder(
                                RuleBasedStateProvider.builder()
                                        .ifTrueThenProvide(BlockPredicate.matchesBlocks(Blocks.ROOTED_DIRT), new CopyPropertiesProvider(Blocks.DIRT))
                                        .build()
                        )
                        .sound(SoundEvents.HOE_TILL)
                        .build()
        );
    }
}
