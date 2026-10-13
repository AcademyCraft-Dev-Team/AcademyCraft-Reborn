package org.academy.internal.common.structure.transaction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * First delivery subset of plain host blocks. Admission is an explicit vanilla starter set plus
 * semantic gates; block entities, fluids, waterlogging, pending or random tick semantics, gravity
 * blocks and non-full shapes are rejected with an explicit reason instead of being silently
 * dropped. Expanding the set is a deliberate data change, not an automatic tag rule.
 */
public final class NativeCaptureWhitelist {
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
        // Color collections: plain concrete and dyed terracotta are stable full cubes.
        blocks.addAll(Blocks.CONCRETE.asList());
        blocks.addAll(Blocks.DYED_TERRACOTTA.asList());
        BLOCKS = Set.copyOf(blocks);
    }

    private NativeCaptureWhitelist() {}

    public static Set<Block> blocks() {
        return BLOCKS;
    }

    /**
     * @return {@code null} when the cell is admissible, otherwise a stable machine-readable
     * reason that must be surfaced instead of dropping the cell content.
     */
    public static @Nullable String rejectionDetail(ServerLevel level, BlockPos position,
                                                   BlockState state, @Nullable BlockEntity blockEntity) {
        if (blockEntity != null || state.hasBlockEntity()) return "block_entity_not_supported";
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.WATERLOGGED)) return "waterlogged_not_supported";
        if (!state.getFluidState().isEmpty()) return "fluid_not_supported";
        if (state.isRandomlyTicking()) return "random_tick_not_supported";
        if (level.getBlockTicks().hasScheduledTick(position, state.getBlock())) return "scheduled_tick_pending";
        if (!BLOCKS.contains(state.getBlock())) return "not_in_plain_block_whitelist";
        if (!state.isCollisionShapeFullBlock(level, position)) return "not_full_block";
        if (state.getDestroySpeed(level, position) < 0.0f) return "unbreakable";
        if (state.getBlock() instanceof FallingBlock) return "gravity_block_not_supported";
        return null;
    }
}
