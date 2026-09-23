package org.academy.internal.gui.map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.academy.internal.common.ability.teleport.chunk.ChunkLeapRegion;
import org.academy.internal.common.ability.teleport.chunk.ChunkLeapSelection;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Selection state for the 区块跃迁 map.
 *
 * <p>The source is a <em>set</em> of chunks rather than a rectangle, because the player can ctrl-click
 * isolated chunks and because the target is not chosen by hand: it is the source's own shape moved to a
 * new origin. Keeping the source as a normalised offset list means the target is derived, so the two can
 * never disagree in size or shape.
 *
 * <p>Each side records <em>its own</em> dimension. That is what makes a cross-dimension swap expressible:
 * the source can be selected in one dimension and the target in another, and switching the viewed page
 * never re-interprets either set of coordinates. Editing the source while looking at a different dimension
 * deliberately starts a new source there, since that is what the player is visibly pointing at.
 */
public final class ChunkMapSelection {
    /** The chunks the player is moving. Insertion order is preserved so pairing is deterministic. */
    private final LinkedHashSet<ChunkPos> source = new LinkedHashSet<>();
    private ResourceKey<Level> sourceDimension;
    /** Where the source shape is anchored, and in which dimension. */
    private ChunkPos targetOrigin;
    private ResourceKey<Level> targetDimension;
    private int entityId = -1;
    private int entityTargetX;
    private int entityTargetY;
    private int entityTargetZ;
    private boolean entityTargetSet;

    // ---------------------------------------------------------------- source set

    public boolean isEmpty() {
        return source.isEmpty();
    }

    /** Absolute chunks of the source, in insertion order (pairing order). */
    public Set<ChunkPos> sourceChunks() {
        return Collections.unmodifiableSet(source);
    }

    public int sourceCount() {
        return source.size();
    }

    public ResourceKey<Level> sourceDimension() {
        return sourceDimension;
    }

    public boolean isSelected(ChunkPos chunk) {
        return source.contains(chunk);
    }

    /**
     * True when the given dimension is where the source lives.
     *
     * <p>Used to decide whether the source should be drawn on the currently viewed page.
     */
    public boolean sourceIsIn(ResourceKey<Level> dimension) {
        return sourceDimension != null && sourceDimension.equals(dimension);
    }

    public boolean targetIsIn(ResourceKey<Level> dimension) {
        return targetDimension != null && targetDimension.equals(dimension);
    }

    /**
     * Re-points the source at {@code dimension}, discarding the old set if it belonged elsewhere.
     *
     * <p>Editing the source while viewing another dimension means the player is selecting there, so the
     * previous source (and the target placed against it) no longer applies.
     */
    private void rebaseIfNeeded(ResourceKey<Level> dimension) {
        if (dimension == null || dimension.equals(sourceDimension)) return;
        source.clear();
        sourceDimension = dimension;
        targetOrigin = null;
        targetDimension = null;
    }

    /** Adds a chunk (ctrl-click behaviour) in {@code dimension}. */
    public void add(ResourceKey<Level> dimension, ChunkPos chunk) {
        rebaseIfNeeded(dimension);
        if (source.size() < ChunkLeapSelection.MAX_CHUNKS) source.add(chunk);
    }

    /** Adds when absent, removes when present: one binding for ctrl-click toggling. */
    public boolean toggle(ResourceKey<Level> dimension, ChunkPos chunk) {
        rebaseIfNeeded(dimension);
        if (source.remove(chunk)) return false;
        if (source.size() < ChunkLeapSelection.MAX_CHUNKS) source.add(chunk);
        return true;
    }

    /** Replaces the whole source with a rectangle (drag behaviour). */
    public void setRectangle(ResourceKey<Level> dimension, ChunkPos from, ChunkPos to) {
        rebaseIfNeeded(dimension);
        source.clear();
        var minX = Math.min(from.x(), to.x());
        var minZ = Math.min(from.z(), to.z());
        var width = Math.abs(to.x() - from.x()) + 1;
        var height = Math.abs(to.z() - from.z()) + 1;
        for (var dz = 0; dz < height && source.size() < ChunkLeapSelection.MAX_CHUNKS; dz++) {
            for (var dx = 0; dx < width && source.size() < ChunkLeapSelection.MAX_CHUNKS; dx++) {
                source.add(new ChunkPos(minX + dx, minZ + dz));
            }
        }
    }

    /** Removes a chunk, used by ctrl-click on a selected chunk. */
    public void remove(ChunkPos chunk) {
        source.remove(chunk);
    }

    public void clearSource() {
        source.clear();
        sourceDimension = null;
        targetOrigin = null;
        targetDimension = null;
    }

    /**
     * The source as a normalised selection, in the source's own dimension.
     *
     * <p>The origin is the set's top-left corner and every offset is relative to it, which is exactly what
     * the wire format needs; the target is this same object re-anchored.
     */
    public ChunkLeapSelection sourceSelection() {
        if (source.isEmpty() || sourceDimension == null) return null;
        return ChunkLeapSelection.of(sourceDimension, source);
    }

    /** Bounding rectangle of the source, for drawing and for sizing the view lease. */
    public ChunkLeapRegion sourceBounds() {
        var selection = sourceSelection();
        return selection == null ? null : selection.bounds();
    }

    // ---------------------------------------------------------------- derived target

    public ChunkPos targetOrigin() {
        return targetOrigin;
    }

    public ResourceKey<Level> targetDimension() {
        return targetDimension;
    }

    /** Places (or moves) the target's anchor corner in {@code dimension}. */
    public void setTargetOrigin(ResourceKey<Level> dimension, ChunkPos origin) {
        if (dimension == null || origin == null) return;
        targetOrigin = origin;
        targetDimension = dimension;
    }

    public boolean hasTarget() {
        return targetOrigin != null && targetDimension != null && !source.isEmpty();
    }

    /**
     * The target selection: the source shape anchored at {@link #targetOrigin} in the target's dimension.
     *
     * <p>Derived, never stored, so it stays correct when the source is edited afterwards.
     */
    public ChunkLeapSelection targetSelection() {
        if (!hasTarget()) return null;
        var sourceIn = sourceSelection();
        return sourceIn == null ? null : sourceIn.anchoredAt(targetDimension, targetOrigin);
    }

    /** Absolute chunks of the derived target, for drawing. */
    public Set<ChunkPos> targetChunks() {
        var selection = targetSelection();
        return selection == null ? Collections.emptySet() : new LinkedHashSet<>(selection.chunks());
    }

    // ---------------------------------------------------------------- entity

    public void setEntity(int id) {
        entityId = id;
    }

    public int entityId() {
        return entityId;
    }

    public void setEntityTarget(int x, int y, int z) {
        entityTargetX = x;
        entityTargetY = y;
        entityTargetZ = z;
        entityTargetSet = true;
    }

    public boolean hasEntityTarget() {
        return entityTargetSet;
    }

    public net.minecraft.core.BlockPos entityTarget() {
        return entityTargetSet
                ? new net.minecraft.core.BlockPos(entityTargetX, entityTargetY, entityTargetZ) : null;
    }

    public void clear() {
        source.clear();
        sourceDimension = null;
        targetOrigin = null;
        targetDimension = null;
        entityId = -1;
        entityTargetSet = false;
    }
}
