package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Read-only planner for safe staticization. It validates the complete target footprint of a
 * structured body against the world grid and reports explicit rejection codes; it never writes,
 * discards or detaches anything. The successful placement path itself is not implemented yet,
 * so a {@link Status#READY} plan must not be presented as a completed staticization.
 */
public final class StructureStaticization {
    private StructureStaticization() {
    }

    /**
     * One structure cell in its fixed local grid. {@code blockEntityData} marks cells whose
     * stored block-entity image cannot be rotated into another grid orientation.
     */
    public record LocalCell(BlockPos localPosition, boolean blockEntityData) {
        public LocalCell {
            localPosition = Objects.requireNonNull(localPosition, "localPosition").immutable();
        }
    }

    /**
     * Per-position world facts, supplied on the owning server thread by the selected backend's
     * live view. Implementations must fail closed for unavailable content.
     */
    public interface World {
        boolean isInWorldBounds(BlockPos position);

        boolean isLoaded(BlockPos position);

        /** True when an existing block or entity already owns the target cell. */
        boolean isOccupied(BlockPos position);

        /** True when this cell's state can be placed at the target for the requested rotation. */
        boolean canPlace(BlockPos position, LocalCell cell, int quarterTurns);
    }

    /** Actor-side placement permission; the server answers from its own player context. */
    @FunctionalInterface
    public interface Permission {
        boolean allows(BlockPos position);
    }

    public enum Status {
        READY,
        INVALID_STRUCTURE,
        CONTENT_UNAVAILABLE,
        DUPLICATE_TARGET,
        OUT_OF_WORLD,
        UNLOADED,
        PERMISSION_DENIED,
        OCCUPIED,
        ROTATION_UNSUPPORTED,
        NOT_PLACEABLE
    }

    public record Plan(Status status, @Nullable BlockPos problemPosition, int placements) {
        public Plan {
            Objects.requireNonNull(status, "status");
            problemPosition = problemPosition == null ? null : problemPosition.immutable();
            placements = Math.max(0, placements);
        }

        public boolean ready() {
            return status == Status.READY;
        }

        /** Machine-readable detail used by program action results and logs. */
        public String detail() {
            return status.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Validates every cell in caller order. An invalid pose alignment or a cell whose stored
     * block-entity image cannot rotate is rejected before any world query; all other checks are
     * deterministic per position. The first failing cell determines the reported position.
     */
    public static Plan plan(
            @Nullable BlockPos anchor,
            int quarterTurns,
            boolean poseAligned,
            List<LocalCell> cells,
            World world,
            Permission permission
    ) {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(permission, "permission");
        if (anchor == null || cells.isEmpty()) {
            return new Plan(Status.INVALID_STRUCTURE, null, 0);
        }
        if (!poseAligned) return new Plan(Status.ROTATION_UNSUPPORTED, anchor, 0);
        var targets = new HashSet<BlockPos>();
        for (var cell : cells) {
            var target = targetOf(anchor, cell.localPosition(), quarterTurns);
            if (cell.blockEntityData() && quarterTurns % 4 != 0) {
                return new Plan(Status.ROTATION_UNSUPPORTED, target, 0);
            }
            if (!targets.add(target)) {
                return new Plan(Status.DUPLICATE_TARGET, target, 0);
            }
            if (!world.isInWorldBounds(target)) {
                return new Plan(Status.OUT_OF_WORLD, target, 0);
            }
            if (!world.isLoaded(target)) {
                return new Plan(Status.UNLOADED, target, 0);
            }
            if (!permission.allows(target)) {
                return new Plan(Status.PERMISSION_DENIED, target, 0);
            }
            if (world.isOccupied(target)) {
                return new Plan(Status.OCCUPIED, target, 0);
            }
            if (!world.canPlace(target, cell, quarterTurns)) {
                return new Plan(quarterTurns % 4 == 0
                        ? Status.NOT_PLACEABLE : Status.ROTATION_UNSUPPORTED, target, 0);
            }
        }
        return new Plan(Status.READY, null, cells.size());
    }

    /** World grid cell of one local cell; the same mapping used by the yaw-aligned restore path. */
    public static BlockPos targetOf(BlockPos anchor, BlockPos localPosition, int quarterTurns) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(localPosition, "localPosition");
        return anchor.offset(BlockStructureGridAlignment.rotateOffset(
                localPosition, quarterTurns));
    }
}
