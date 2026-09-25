package org.academy.internal.common.ability.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Reads a short, human-readable summary of one chunk: what its surface is, where it is, and roughly
 * what it is made of.
 *
 * <p>The map raster carries colours only, which is enough to navigate but not enough to judge whether a
 * swap is a good idea. This reads the real blocks server-side — in any dimension, including ones the
 * client has no {@code ClientLevel} for — so the player can inspect a target before committing to it.
 *
 * <p>Deliberately cheap: a fixed 4×4 grid of columns, each scanned a few blocks down. A whole-chunk scan
 * would be 65536 block reads per request, which is neither necessary nor safe to do on demand.
 */
public final class ChunkLeapInspector {
    /**
     * Columns sampled per chunk, per axis.
     */
    private static final int SAMPLES_PER_AXIS = 4;
    /**
     * Non-air blocks recorded per sampled column, from the surface down.
     */
    private static final int BLOCKS_PER_COLUMN = 6;
    /**
     * Surface palette entries reported.
     */
    private static final int TOP_BLOCK_KINDS = 5;

    private ChunkLeapInspector() {
    }

    /**
     * What one chunk looks like, as sent to the client.
     */
    public record Report(String dimensionId, int chunkX, int chunkZ,
                         String surfaceBlock, int surfaceY, String biome,
                         int entityCount, List<BlockTally> palette, boolean loaded) {
        public static Report unloaded(String dimensionId, int chunkX, int chunkZ) {
            return new Report(dimensionId, chunkX, chunkZ, "", 0, "", 0, List.of(), false);
        }
    }

    /**
     * One entry of the surface palette.
     */
    public record BlockTally(String blockId, int count) {
    }

    public static Report inspect(ServerLevel level, int chunkX, int chunkZ) {
        var dimensionId = level.dimension().identifier().toString();
        var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return Report.unloaded(dimensionId, chunkX, chunkZ);
        }

        var baseX = chunkX << 4;
        var baseZ = chunkZ << 4;
        var step = 16 / SAMPLES_PER_AXIS;
        var tally = new HashMap<String, Integer>();
        var surfaceY = level.getMinY();
        var surfaceBlock = "";

        for (var sx = 0; sx < SAMPLES_PER_AXIS; sx++) {
            for (var sz = 0; sz < SAMPLES_PER_AXIS; sz++) {
                var blockX = baseX + sx * step + step / 2;
                var blockZ = baseZ + sz * step + step / 2;
                var height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ);
                if (height > surfaceY) {
                    surfaceY = height;
                    surfaceBlock = idOf(level.getBlockState(new BlockPos(blockX, height, blockZ)));
                }
                var recorded = 0;
                for (var y = height; y >= level.getMinY() && recorded < BLOCKS_PER_COLUMN; y--) {
                    var state = level.getBlockState(new BlockPos(blockX, y, blockZ));
                    if (state.isAir()) continue;
                    tally.merge(idOf(state), 1, Integer::sum);
                    recorded++;
                }
            }
        }

        var palette = new ArrayList<>(tally.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(TOP_BLOCK_KINDS)
                .map(entry -> new BlockTally(entry.getKey(), entry.getValue()))
                .toList());

        var biome = level.getBiome(new BlockPos(baseX + 8, surfaceY, baseZ + 8))
                .unwrapKey().map(key -> key.identifier().toString()).orElse("");
        var entityCount = level.getEntitiesOfClass(Entity.class,
                new AABB(baseX, level.getMinY(), baseZ,
                        baseX + 16.0, level.getMaxY(), baseZ + 16.0),
                candidate -> !candidate.isRemoved()).size();

        return new Report(dimensionId, chunkX, chunkZ, surfaceBlock, surfaceY, biome,
                entityCount, List.copyOf(palette), true);
    }

    private static String idOf(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /**
     * Chunk coordinates a block position falls in.
     */
    public static ChunkPos chunkOf(int blockX, int blockZ) {
        return new ChunkPos(blockX >> 4, blockZ >> 4);
    }
}
