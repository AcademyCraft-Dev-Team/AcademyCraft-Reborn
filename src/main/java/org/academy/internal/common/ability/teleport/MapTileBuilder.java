package org.academy.internal.common.ability.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Rasterises the 区块跃迁 map from real blocks: one texel per block, shaded for relief.
 *
 * <p>Sampling cannot simply take the highest non-air block. The Nether seals its build range with a
 * bedrock lid, so the highest block in every column is that lid and a naive top-down view shows a flat
 * sheet of bedrock instead of the terrain below — and because the lid often has rock attached directly
 * beneath it, a per-column "is this a thin layer?" test misclassifies those columns and still renders the
 * lid. Instead the roof is found <em>per chunk</em> as a near-complete solid plane with open space a few
 * blocks below it ({@link #ceilingY}); columns under such a plane are then resolved by descending through
 * whatever is attached, past the open space, to the first solid block: the ground a player would stand on.
 *
 * <p>Being chunk-level rather than column-level is what makes this dimension-agnostic and robust: it does
 * not assume a particular roof height, a particular block, or a particular number of attached layers.
 */
public final class MapTileBuilder {
    /** Packed texel meaning "this chunk is not loaded yet"; the client draws a placeholder. */
    public static final int UNSAMPLED = 0;
    /** Sentinel RGB for a loaded chunk with nothing to show (all air, e.g. over the void). */
    public static final int EMPTY_RGB = 0x101014;
    /** Texels per chunk axis. 16 gives one texel per block, which is what makes the map readable. */
    public static final int DETAIL = 16;
    private static final int SAMPLED_ALPHA = 0xFF;
    /** Columns per axis used to probe for a roof plane. */
    private static final int CEILING_SAMPLES = 4;
    /**
     * How far below the chunk's highest block a roof plane may sit.
     *
     * <p>This is the condition that separates a real roof from a cave. A lid is right at the top of the
     * column (bedrock at the build limit), so its underside is within a few blocks of the surface; a cave
     * under ordinary terrain is always deeper, and rejecting it here is what stops the map from showing
     * stone instead of the grass the player is standing on.
     */
    private static final int CEILING_MAX_DEPTH = 4;
    /** Fraction of probed columns that must be solid at a level for it to be a roof plane. */
    private static final float CEILING_SOLID_FRACTION = 0.90f;
    /**
     * Minimum mean open height under a plane for it to count as a roof.
     *
     * <p>A roof hides a large void; terrain immediately below a surface level is what a cave looks like,
     * and its gap is only a block or two tall.
     */
    private static final int CEILING_MIN_VOID = 12;
    /** Cap on how far a column search descends, so a pathological column cannot stall a tick. */
    private static final int MAX_COLUMN_DESCENT = 160;
    /** Height difference between neighbouring blocks that produces a full shade step. */
    private static final float SLOPE_FULL = 6.0f;
    /** How strong the slope (relief) shading is. */
    private static final float SLOPE_STRENGTH = 0.30f;
    private static final int NO_CEILING = Integer.MIN_VALUE;

    private MapTileBuilder() {
    }

    public static int texelCount() {
        return DETAIL * DETAIL;
    }

    /**
     * Rasterises {@code chunkX, chunkZ} into {@code out} as {@code 0xAARRGGBB} values, row-major.
     *
     * <p>Returns whether the chunk is <em>loaded</em>, not whether it has blocks: a loaded but empty chunk
     * is a valid result that must still be reported, or its tile would be retried forever and the client
     * would keep showing the previous dimension's pixels there.
     *
     * @return false only when the chunk is not loaded, in which case {@code out} is left untouched
     */
    public static boolean sampleChunk(ServerLevel level, int chunkX, int chunkZ, int[] out) {
        if (level == null || out == null || out.length < texelCount()) return false;
        var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) return false;

        var baseX = chunkX << 4;
        var baseZ = chunkZ << 4;
        var minY = level.getMinY();
        var maxY = level.getMaxY();
        var ceiling = ceilingY(level, chunk, baseX, baseZ, minY, maxY);

        // Two passes: heights first, so the colour pass can shade by slope as well as by altitude.
        var heights = new int[DETAIL * DETAIL];
        for (var subZ = 0; subZ < DETAIL; subZ++) {
            for (var subX = 0; subX < DETAIL; subX++) {
                heights[subZ * DETAIL + subX] =
                        resolveSurfaceY(level, baseX + subX, baseZ + subZ, ceiling, minY, maxY);
            }
        }
        for (var subZ = 0; subZ < DETAIL; subZ++) {
            for (var subX = 0; subX < DETAIL; subX++) {
                var index = subZ * DETAIL + subX;
                var y = heights[index];
                if (y < minY) {
                    out[index] = (SAMPLED_ALPHA << 24) | EMPTY_RGB;
                    continue;
                }
                var pos = new BlockPos(baseX + subX, y, baseZ + subZ);
                var state = level.getBlockState(pos);
                var rgb = state.getMapColor(level, pos).col;
                if (rgb == 0) rgb = EMPTY_RGB;
                out[index] = (SAMPLED_ALPHA << 24)
                        | shade(rgb, heights, subX, subZ, y, minY, maxY, state);
            }
        }
        return true;
    }

    /**
     * Y of the underside of a solid roof over this chunk, or {@link #NO_CEILING} when open to the sky.
     *
     * <p>A roof is a level that is almost entirely solid, sits within {@link #CEILING_MAX_DEPTH} of the
     * chunk's highest block, and has a substantial void beneath it. All three are needed:
     * <ul>
     *   <li>solidity alone matches any surface level;</li>
     *   <li>solidity plus a void below would also match a cave under flat ground — which is exactly the bug
     *       that made plains render as grey stone, because the map showed the cave's stone ceiling instead
     *       of the grass above it;</li>
     *   <li>proximity to the top is what a cave cannot satisfy, since a cave always lies deeper.</li>
     * </ul>
     *
     * <p>Fluids do not count as plane blocks, so an ocean surface is never mistaken for a lid.
     */
    static int ceilingY(ServerLevel level, LevelChunk chunk, int baseX, int baseZ, int minY, int maxY) {
        var step = 16 / CEILING_SAMPLES;
        var columns = CEILING_SAMPLES * CEILING_SAMPLES;
        var tops = new int[columns];
        var top = minY - 1;
        for (var sz = 0; sz < CEILING_SAMPLES; sz++) {
            for (var sx = 0; sx < CEILING_SAMPLES; sx++) {
                var height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE,
                        baseX + sx * step + step / 2, baseZ + sz * step + step / 2);
                tops[sz * CEILING_SAMPLES + sx] = height;
                if (height > top) top = height;
            }
        }
        if (top < minY) return NO_CEILING;

        var pos = new BlockPos.MutableBlockPos();
        var deepest = Math.max(minY + 1, top - CEILING_MAX_DEPTH);
        for (var y = top; y >= deepest; y--) {
            var plane = 0;
            var probed = 0;
            for (var sz = 0; sz < CEILING_SAMPLES; sz++) {
                for (var sx = 0; sx < CEILING_SAMPLES; sx++) {
                    if (tops[sz * CEILING_SAMPLES + sx] < minY) continue;
                    probed++;
                    var state = level.getBlockState(pos.set(
                            baseX + sx * step + step / 2, y, baseZ + sz * step + step / 2));
                    if (isPlaneBlock(state)) plane++;
                }
            }
            // Measure the void under this level: the mean drop to the first block in each column.
            long voidSum = 0;
            var measured = 0;
            for (var sz = 0; sz < CEILING_SAMPLES; sz++) {
                for (var sx = 0; sx < CEILING_SAMPLES; sx++) {
                    if (tops[sz * CEILING_SAMPLES + sx] < minY) continue;
                    var ground = firstBlockBelow(level, pos,
                            baseX + sx * step + step / 2, y - 1, baseZ + sz * step + step / 2, minY);
                    measured++;
                    if (ground >= minY) voidSum += y - ground;
                }
            }
            var meanVoid = measured == 0 ? 0 : (int) (voidSum / measured);
            if (isRoofPlane(plane, probed, meanVoid, top - y)) return y;
        }
        return NO_CEILING;
    }

    /**
     * Whether a level qualifies as a roof plane: nearly solid, near the top, with a large void beneath.
     *
     * <p>The void requirement is the one that keeps ordinary terrain out of this classification. Flat ground
     * is solid at and below the surface, so the space beneath its topmost level is a block or two of soil —
     * far short of the void a roof hides. A cave satisfies "solid level with air below" but is either too
     * deep or too thin-gapped to pass, which is what stops the map from drawing the cave ceiling instead of
     * the surface.
     *
     * @param solidColumns columns holding a solid block at this level
     * @param probedColumns columns examined
     * @param meanVoid     mean distance from this level down to the first block
     * @param depthFromTop how far below the column top this level sits
     */
    static boolean isRoofPlane(int solidColumns, int probedColumns, int meanVoid, int depthFromTop) {
        if (probedColumns <= 0) return false;
        if (depthFromTop > CEILING_MAX_DEPTH) return false;
        if (solidColumns < probedColumns * CEILING_SOLID_FRACTION) return false;
        return meanVoid >= CEILING_MIN_VOID;
    }
    /** A real solid block: air and fluids are not part of a lid. */
    private static boolean isPlaneBlock(net.minecraft.world.level.block.state.BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    /** The first non-air block at or below {@code fromY}, or {@code minY - 1} if there is none. */
    private static int firstBlockBelow(ServerLevel level, BlockPos.MutableBlockPos pos,
                                       int blockX, int fromY, int blockZ, int minY) {
        var floor = Math.max(minY, fromY - MAX_COLUMN_DESCENT);
        for (var y = fromY; y >= floor; y--) {
            if (!level.getBlockState(pos.set(blockX, y, blockZ)).isAir()) return y;
        }
        return minY - 1;
    }
    /**
     * The surface to draw for one column.
     *
     * <p>Without a roof this is the heightmap value. Under a roof the search descends through whatever is
     * attached to the lid and then through the open space beneath it, and returns the first solid block:
     * the ground the player walks on. Returning {@code minY - 1} means "nothing to show", and the caller
     * falls back to the lid so the column is not left blank.
     */
    static int resolveSurfaceY(ServerLevel level, int blockX, int blockZ, int ceiling, int minY, int maxY) {
        var chunk = level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4);
        if (chunk == null) return minY - 1;
        var top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ);
        var pos = new BlockPos.MutableBlockPos();
        return resolveColumnY(top, ceiling, minY,
                y -> !level.getBlockState(pos.set(blockX, y, blockZ)).isAir());
    }

    /**
     * The visible surface of one column, given whether the chunk has a roof.
     *
     * <p>Pure so the rule can be tested exactly, including the case that caused plains to render as grey
     * stone: a shallow cave is a solid level with a void below it, and only the roof plane test — which a
     * single column cannot perform — distinguishes it from a lid. With {@code ceiling} already resolved,
     * this function merely walks past the lid and the gap beneath it.
     *
     * @param top      highest non-air block in the column
     * @param ceiling  underside of the roof, or {@link #NO_CEILING}
     * @param solidAtY tests whether a Y holds a non-air block
     * @return the surface Y, or {@code minY - 1} (or the column top) when nothing better is available
     */
    static int resolveColumnY(int top, int ceiling, int minY, java.util.function.IntPredicate solidAtY) {
        if (top < minY) return minY - 1;
        if (ceiling == NO_CEILING) return top;
        var y = Math.min(top, ceiling) - 1;
        var floor = Math.max(minY, y - MAX_COLUMN_DESCENT);
        // Descend out of the lid and anything welded to it.
        while (y >= floor && solidAtY.test(y)) y--;
        // Cross the open space under it to the ground.
        while (y >= floor && !solidAtY.test(y)) y--;
        return y >= floor ? y : top;
    }
    /**
     * The Y a player looking straight down would see in this column, or {@code level.getMinY()} when the
     * column is empty.
     *
     * <p>Shared with teleport landing, so an entity arrives on the surface the map showed — including
     * under the Nether's roof.
     */
    public static int visibleSurfaceY(ServerLevel level, int blockX, int blockZ) {
        var minY = level.getMinY();
        var chunk = level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4);
        if (chunk == null) return minY;
        var ceiling = ceilingY(level, chunk, blockX & ~15, blockZ & ~15, minY, level.getMaxY());
        var surface = resolveSurfaceY(level, blockX, blockZ, ceiling, minY, level.getMaxY());
        return surface < minY ? minY : surface;
    }

    /** Shades a texel by altitude and by slope, so edges and cliffs read as relief rather than flat colour. */
    private static int shade(int rgb, int[] heights, int subX, int subZ, int height,
                             int minY, int maxY, BlockState state) {
        var stride = DETAIL;
        var span = Math.max(1, maxY - minY);
        var altitude = 0.80f + 0.40f * ((height - minY) / (float) span);

        // Slope: compare against the west and north neighbours, which is enough to draw a consistent light
        // from the north-west without needing a full normal.
        var slope = 0;
        if (subX > 0 && heights[subZ * stride + subX - 1] >= minY) {
            slope += height - heights[subZ * stride + subX - 1];
        }
        if (subZ > 0 && heights[(subZ - 1) * stride + subX] >= minY) {
            slope += height - heights[(subZ - 1) * stride + subX];
        }
        var slopeTerm = 1.0f + SLOPE_STRENGTH * Math.clamp(slope / SLOPE_FULL, -1.0f, 1.0f);

        // Fluids read better darker with depth: a submerged block under more fluid is deeper.
        var fluidTerm = 1.0f;
        if (!state.getFluidState().isEmpty()) fluidTerm = 0.85f;

        var factor = altitude * slopeTerm * fluidTerm;
        return channel(rgb, 16, factor) << 16 | channel(rgb, 8, factor) << 8 | channel(rgb, 0, factor);
    }

    private static int channel(int rgb, int shift, float factor) {
        return Math.max(0, Math.min(255, (int) (((rgb >> shift) & 0xFF) * factor)));
    }
}
