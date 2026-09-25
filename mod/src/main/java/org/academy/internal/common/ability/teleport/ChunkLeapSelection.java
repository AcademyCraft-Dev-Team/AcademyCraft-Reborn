package org.academy.internal.common.ability.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * An arbitrary set of chunks, stored as offsets from the set's own top-left corner.
 *
 * <p>Chunk selection is not always a rectangle: the player may ctrl-click isolated chunks, and the
 * target is derived from the source's shape rather than selected independently. Storing offsets makes
 * both cases natural — the same offset list applied to a different origin <em>is</em> the derived target
 * — and it makes the two sides of a swap equal in count and identical in shape by construction, so those
 * can no longer be invalid states.
 *
 * <p>Pairing is by offset index, which is why the order is preserved and transmitted rather than
 * recomputed: the client and server must agree on which chunk maps to which.
 */
public record ChunkLeapSelection(ResourceKey<Level> dimension, int originChunkX, int originChunkZ,
                                 List<Offset> offsets) {
    /**
     * Largest selection, guarding both the wire and the amount of work a swap can represent.
     */
    public static final int MAX_CHUNKS = 1024;
    /**
     * How far an offset may reach from the origin corner; bounds the transmitted values.
     */
    public static final int MAX_OFFSET = 63;

    /**
     * One chunk's position relative to the selection origin.
     */
    public record Offset(int dx, int dz) {
        public boolean inBounds() {
            return dx >= 0 && dz >= 0 && dx <= MAX_OFFSET && dz <= MAX_OFFSET;
        }
    }

    public ChunkLeapSelection {
        // Normalise: offsets are never negative, or the origin would not be the top-left corner.
        offsets = List.copyOf(offsets.size() > MAX_CHUNKS ? offsets.subList(0, MAX_CHUNKS) : offsets);
    }

    public static ChunkLeapSelection single(ResourceKey<Level> dimension, ChunkPos chunk) {
        return new ChunkLeapSelection(dimension, chunk.x(), chunk.z(), List.of(new Offset(0, 0)));
    }

    public static ChunkLeapSelection rectangle(ResourceKey<Level> dimension, ChunkPos from, ChunkPos to) {
        var minX = Math.min(from.x(), to.x());
        var minZ = Math.min(from.z(), to.z());
        var width = Math.abs(to.x() - from.x()) + 1;
        var height = Math.abs(to.z() - from.z()) + 1;
        var offsets = new ArrayList<Offset>(width * height);
        for (var dz = 0; dz < height; dz++) {
            for (var dx = 0; dx < width; dx++) {
                offsets.add(new Offset(dx, dz));
                if (offsets.size() >= MAX_CHUNKS) break;
            }
        }
        return new ChunkLeapSelection(dimension, minX, minZ, offsets);
    }

    public int count() {
        return offsets.size();
    }

    public boolean isEmpty() {
        return offsets.isEmpty();
    }

    /**
     * Absolute chunk position of the offset at {@code index}.
     */
    public ChunkPos chunkAt(int index) {
        var offset = offsets.get(index);
        return new ChunkPos(originChunkX + offset.dx(), originChunkZ + offset.dz());
    }

    public List<ChunkPos> chunks() {
        var result = new ArrayList<ChunkPos>(offsets.size());
        for (var offset : offsets) {
            result.add(new ChunkPos(originChunkX + offset.dx(), originChunkZ + offset.dz()));
        }
        return result;
    }

    /**
     * The same shape re-anchored at {@code target}: the auto-derived target selection.
     *
     * <p>Because the offsets are unchanged, the target has exactly the source's size and layout; only its
     * position differs. This is what lets the player place a target with a single click.
     */
    public ChunkLeapSelection anchoredAt(ResourceKey<Level> targetDimension, ChunkPos targetOrigin) {
        return new ChunkLeapSelection(targetDimension, targetOrigin.x(), targetOrigin.z(), offsets);
    }

    /**
     * True when this selection's chunks fall inside {@code region}; used to size the view lease.
     */
    public ChunkLeapRegion bounds() {
        if (offsets.isEmpty()) return ChunkLeapRegion.ofChunks(dimension, originChunkX, originChunkZ, 1, 1);
        var maxDx = 0;
        var maxDz = 0;
        for (var offset : offsets) {
            maxDx = Math.max(maxDx, offset.dx());
            maxDz = Math.max(maxDz, offset.dz());
        }
        return ChunkLeapRegion.ofChunks(dimension, originChunkX, originChunkZ, maxDx + 1, maxDz + 1);
    }

    /**
     * Block position of a chunk's centre at the given surface height.
     */
    public static BlockPos centreBlock(ChunkPos chunk, int y) {
        return new BlockPos(chunk.getMinBlockX() + 8, y, chunk.getMinBlockZ() + 8);
    }

    /**
     * Rebuilds a selection from a sequence of chunk positions, normalising to a top-left origin.
     *
     * <p>Used by the client when the player edits the selection by hand; the result is what gets
     * transmitted, so both sides iterate the same offsets in the same order.
     */
    public static @Nullable ChunkLeapSelection of(ResourceKey<Level> dimension, Iterable<ChunkPos> chunks) {
        var unique = new LinkedHashSet<ChunkPos>();
        var minX = Integer.MAX_VALUE;
        var minZ = Integer.MAX_VALUE;
        for (var chunk : chunks) {
            unique.add(chunk);
            minX = Math.min(minX, chunk.x());
            minZ = Math.min(minZ, chunk.z());
            if (unique.size() >= MAX_CHUNKS) break;
        }
        if (unique.isEmpty()) return null;
        var offsets = new ArrayList<Offset>(unique.size());
        for (var chunk : unique) {
            var dx = chunk.x() - minX;
            var dz = chunk.z() - minZ;
            // A selection spanning more than MAX_OFFSET cannot be expressed; drop the far chunks.
            if (dx > MAX_OFFSET || dz > MAX_OFFSET) continue;
            offsets.add(new Offset(dx, dz));
        }
        // Sort for a deterministic wire order that both sides agree on.
        offsets.sort(Comparator.comparingInt(Offset::dz).thenComparingInt(Offset::dx));
        return new ChunkLeapSelection(dimension, minX, minZ, offsets);
    }

    public static ResourceKey<Level> dimensionKey(String id) {
        var parsed = Identifier.tryParse(id);
        return parsed == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, parsed);
    }
}
