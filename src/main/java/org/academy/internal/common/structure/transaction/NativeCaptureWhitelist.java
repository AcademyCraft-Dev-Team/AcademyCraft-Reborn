package org.academy.internal.common.structure.transaction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * First delivery subset of plain host blocks. Admission is an explicit vanilla starter set plus
 * structural gates. Randomly ticking land and vegetation-family full cubes are admitted as a
 * deliberate policy change: a captured structure space does not run random ticks, so the copied
 * content stays inert. The safety boundary is this curated set itself, not a blanket random-tick
 * semantic gate. Block entities, fluids, waterlogging, pending scheduled ticks and non-full
 * shapes are still rejected with an explicit reason instead of being silently dropped. Gravity
 * blocks are admitted through the same curated set: whitelisted sand, red sand and gravel may
 * fall while unlisted gravity blocks fail the whitelist gate itself. Expanding the set remains a
 * deliberate data change, not an automatic tag rule.
 */
public final class NativeCaptureWhitelist {
    /**
     * Lazy holder so the pure admission decision below can be exercised by focused tests without
     * initializing the block registry.
     */
    private static final class CuratedSet {
        private static final Set<Block> BLOCKS;

        static {
            var blocks = new HashSet<Block>();
            // Stone and deepslate families.
            Collections.addAll(blocks, Blocks.STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
                    Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
                    Blocks.CHISELED_STONE_BRICKS, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE,
                    Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS,
                    Blocks.DEEPSLATE_TILES, Blocks.CHISELED_DEEPSLATE);
            // Igneous and tuff.
            Collections.addAll(blocks, Blocks.GRANITE, Blocks.POLISHED_GRANITE, Blocks.DIORITE,
                    Blocks.POLISHED_DIORITE, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.TUFF,
                    Blocks.POLISHED_TUFF, Blocks.CHISELED_TUFF);
            // Soils and mud.
            Collections.addAll(blocks, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL,
                    Blocks.MUD, Blocks.PACKED_MUD, Blocks.MUD_BRICKS);
            // Surface land and vegetation-family full cubes. Grass and mycelium spread by random
            // tick in a live world; a captured structure space does not tick. Sand-family and
            // gravel gravity blocks are ordinary terrain once the structure space stops ticking.
            Collections.addAll(blocks, Blocks.GRASS_BLOCK, Blocks.MYCELIUM, Blocks.MOSS_BLOCK,
                    Blocks.SNOW_BLOCK, Blocks.CLAY, Blocks.GRAVEL, Blocks.SAND, Blocks.RED_SAND);
            // Sandstone families.
            Collections.addAll(blocks, Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE,
                    Blocks.SMOOTH_SANDSTONE, Blocks.RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE,
                    Blocks.CHISELED_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE);
            // Nether, end and prismatic stones.
            Collections.addAll(blocks, Blocks.NETHERRACK, Blocks.BASALT, Blocks.POLISHED_BASALT,
                    Blocks.SMOOTH_BASALT, Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE,
                    Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.NETHER_BRICKS, Blocks.END_STONE,
                    Blocks.END_STONE_BRICKS, Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR);
            // Quartz and masonry.
            Collections.addAll(blocks, Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_BRICKS, Blocks.CHISELED_QUARTZ_BLOCK,
                    Blocks.QUARTZ_PILLAR, Blocks.SMOOTH_QUARTZ, Blocks.OBSIDIAN, Blocks.BRICKS,
                    Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE);
            // Simple mineral blocks without random-tick or redstone semantics.
            Collections.addAll(blocks, Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK,
                    Blocks.EMERALD_BLOCK, Blocks.LAPIS_BLOCK, Blocks.COAL_BLOCK, Blocks.NETHERITE_BLOCK,
                    Blocks.TERRACOTTA);
            // Logs, wood, stripped variants and planks.
            Collections.addAll(blocks, Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG,
                    Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG,
                    Blocks.PALE_OAK_LOG, Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_JUNGLE_LOG, Blocks.STRIPPED_ACACIA_LOG,
                    Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_CHERRY_LOG,
                    Blocks.STRIPPED_PALE_OAK_LOG, Blocks.OAK_WOOD, Blocks.SPRUCE_WOOD, Blocks.BIRCH_WOOD,
                    Blocks.JUNGLE_WOOD, Blocks.ACACIA_WOOD, Blocks.DARK_OAK_WOOD, Blocks.MANGROVE_WOOD,
                    Blocks.CHERRY_WOOD, Blocks.PALE_OAK_WOOD, Blocks.STRIPPED_OAK_WOOD,
                    Blocks.STRIPPED_SPRUCE_WOOD, Blocks.STRIPPED_BIRCH_WOOD, Blocks.STRIPPED_JUNGLE_WOOD,
                    Blocks.STRIPPED_ACACIA_WOOD, Blocks.STRIPPED_DARK_OAK_WOOD, Blocks.STRIPPED_MANGROVE_WOOD,
                    Blocks.STRIPPED_CHERRY_WOOD, Blocks.STRIPPED_PALE_OAK_WOOD, Blocks.OAK_PLANKS,
                    Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS,
                    Blocks.DARK_OAK_PLANKS, Blocks.MANGROVE_PLANKS, Blocks.CHERRY_PLANKS,
                    Blocks.PALE_OAK_PLANKS, Blocks.BAMBOO_PLANKS, Blocks.CRIMSON_PLANKS, Blocks.WARPED_PLANKS);
            // Whole leaf family; a captured structure space runs neither decay nor random ticks.
            Collections.addAll(blocks, Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES,
                    Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
                    Blocks.MANGROVE_LEAVES, Blocks.CHERRY_LEAVES, Blocks.PALE_OAK_LEAVES,
                    Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES);
            // Color collections: plain concrete and dyed terracotta are stable full cubes.
            blocks.addAll(Blocks.CONCRETE.asList());
            blocks.addAll(Blocks.DYED_TERRACOTTA.asList());
            BLOCKS = Set.copyOf(blocks);
        }

        private CuratedSet() {}
    }

    private NativeCaptureWhitelist() {}

    public static Set<Block> blocks() {
        return CuratedSet.BLOCKS;
    }

    /**
     * @return {@code null} when the cell is admissible, otherwise a stable machine-readable
     * reason that must be surfaced instead of dropping the cell content.
     */
    public static @Nullable String rejectionDetail(ServerLevel level, BlockPos position,
                                                   BlockState state, @Nullable BlockEntity blockEntity) {
        return classifyAdmission(blockEntity != null || state.hasBlockEntity(),
                state.hasProperty(BlockStateProperties.WATERLOGGED)
                        && state.getValue(BlockStateProperties.WATERLOGGED),
                !state.getFluidState().isEmpty(),
                level.getBlockTicks().hasScheduledTick(position, state.getBlock()),
                CuratedSet.BLOCKS.contains(state.getBlock()),
                state.isCollisionShapeFullBlock(level, position),
                state.getDestroySpeed(level, position));
    }

    /**
     * Pure admission decision shared by the live preflight and focused tests. The caller resolves
     * every world- and registry-dependent fact first.
     *
     * @return {@code null} when the cell is admissible, otherwise a stable machine-readable
     * reason that must be surfaced instead of dropping the cell content.
     */
    static @Nullable String classifyAdmission(boolean blockEntity, boolean waterlogged, boolean fluid,
                                              boolean scheduledTickPending, boolean whitelisted,
                                              boolean fullBlock, float destroySpeed) {
        if (blockEntity) return "block_entity_not_supported";
        if (waterlogged) return "waterlogged_not_supported";
        if (fluid) return "fluid_not_supported";
        // A pending scheduled tick means the cell is mid-behavior and may mutate right after the
        // copy; random ticks are admitted because the structure space does not tick.
        if (scheduledTickPending) return "scheduled_tick_pending";
        // The curated set is the complete content boundary, including for gravity blocks.
        if (!whitelisted) return "not_in_plain_block_whitelist";
        if (!fullBlock) return "not_full_block";
        if (destroySpeed < 0.0f) return "unbreakable";
        return null;
    }
}
