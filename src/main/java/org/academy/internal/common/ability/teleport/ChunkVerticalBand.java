package org.academy.internal.common.ability.teleport;

import net.minecraft.server.level.ServerLevel;

/**
 * The absolute block-Y range two chunks can exchange during a 区块跃迁 swap.
 *
 * <p>A same-dimension swap moves the whole chunk column, so the band is the level's full height. A
 * cross-dimension swap cannot be whole-column: the Overworld spans Y -64..319 while the Nether spans
 * Y 0..127, and a section that exists on one side has nowhere to go on the other. Pairing is therefore
 * done by <em>absolute</em> Y over the intersection, and sections outside it stay where they are.
 *
 * <p>Because the band depends only on the two levels' height ranges — not on chunk contents — it is
 * identical before and after a swap. That keeps the operation self-inverse: swapping the same pair
 * twice restores the original world, including the untouched bands.
 */
public record ChunkVerticalBand(int minBlockY, int maxBlockY) {
    /** The whole vertical extent of {@code level}, used for same-dimension swaps. */
    public static ChunkVerticalBand full(ServerLevel level) {
        return new ChunkVerticalBand(level.getMinY(), level.getMaxY());
    }

    /** The absolute-Y intersection of two levels, used for cross-dimension swaps. */
    public static ChunkVerticalBand overlap(ServerLevel first, ServerLevel second) {
        return new ChunkVerticalBand(
                Math.max(first.getMinY(), second.getMinY()),
                Math.min(first.getMaxY(), second.getMaxY()));
    }

    /** True when the levels share no vertical range at all, so nothing could be exchanged. */
    public boolean isEmpty() {
        return maxBlockY < minBlockY;
    }

    public boolean contains(int blockY) {
        return blockY >= minBlockY && blockY <= maxBlockY;
    }

    public int height() {
        return isEmpty() ? 0 : maxBlockY - minBlockY + 1;
    }

    public int minSectionY() {
        return minBlockY >> 4;
    }

    public int maxSectionY() {
        return maxBlockY >> 4;
    }

    /** Section index of {@code sectionY} inside {@code level}'s section array. */
    public static int sectionIndex(ServerLevel level, int sectionY) {
        return sectionY - (level.getMinY() >> 4);
    }
}
