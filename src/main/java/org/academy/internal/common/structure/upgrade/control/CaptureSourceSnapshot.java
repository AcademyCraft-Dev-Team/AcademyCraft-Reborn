package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.core.BlockPos;
import org.academy.api.common.structure.StructureCaptureRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Canonical, bounded snapshot of the host cells a capture would remove. The digest binds a
 * pre-capture preview to the content actually committed into the new structure, so a binding
 * can prove that its plan belongs to the same source instead of trusting a caller's record.
 *
 * <p>Cells carry host-world positions and the exact loaded block states. Block-entity content
 * is unsupported by this snapshot and must be rejected at admission, never silently omitted.
 * Neighbour-dependent shapes are observed separately by the bounded planner. This value is
 * evidence only; it neither loads chunks nor grants a capture, a lease or a world write.</p>
 */
public record CaptureSourceSnapshot(List<Cell> cells) {
    public static final int MAX_CELLS = StructureCaptureRequest.MAXIMUM_BLOCKS;
    public static final int MAX_STATE_LENGTH = 128;
    private static final Comparator<Cell> ORDER = Comparator
            .comparingInt((Cell cell) -> cell.position().getX())
            .thenComparingInt(cell -> cell.position().getY())
            .thenComparingInt(cell -> cell.position().getZ());

    /** One host cell and the id of its current block state; positions are immutable copies. */
    public record Cell(BlockPos position, String state) {
        public Cell {
            position = Objects.requireNonNull(position, "position").immutable();
            Objects.requireNonNull(state, "state");
            if (state.isBlank() || state.length() > MAX_STATE_LENGTH) {
                throw new IllegalArgumentException("Invalid source state id");
            }
        }

        /** Stable text form; the digest must not depend on record formatting or hash order. */
        String canonical() {
            return position.getX() + "," + position.getY() + "," + position.getZ() + "=" + state;
        }
    }

    public CaptureSourceSnapshot {
        Objects.requireNonNull(cells, "cells");
        if (cells.isEmpty() || cells.size() > MAX_CELLS) {
            throw new IllegalArgumentException("A source snapshot requires 1 to " + MAX_CELLS + " cells");
        }
        var sorted = new ArrayList<Cell>(cells.size());
        var seen = new HashSet<Long>();
        for (var cell : cells) {
            Objects.requireNonNull(cell, "cell");
            if (!seen.add(cell.position().asLong())) {
                throw new IllegalArgumentException("Duplicate source cell");
            }
            sorted.add(cell);
        }
        sorted.sort(ORDER);
        cells = List.copyOf(sorted);
    }

    public static CaptureSourceSnapshot of(List<Cell> cells) {
        return new CaptureSourceSnapshot(cells);
    }

    /** Lowercase SHA-256 over the canonical cell text; equal sources produce equal digests. */
    public String digest() {
        var text = new StringBuilder(cells.size() * 48);
        for (var cell : cells) text.append(cell.canonical()).append('\n');
        try {
            var hash = MessageDigest.getInstance("SHA-256")
                    .digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required by the platform", unavailable);
        }
    }

    /**
     * Re-bases host cells onto the captured structure's local grid. The capture origin is the
     * minimum corner of the selection, so the same offset addresses the copied content later.
     */
    public CaptureSourceSnapshot localize(BlockPos origin) {
        Objects.requireNonNull(origin, "origin");
        var rebased = new ArrayList<Cell>(cells.size());
        for (var cell : cells) {
            var local = cell.position().subtract(origin);
            if (local.getX() < 0 || local.getY() < 0 || local.getZ() < 0) {
                throw new IllegalArgumentException("Source cell lies outside the capture origin");
            }
            rebased.add(new Cell(local, cell.state()));
        }
        return new CaptureSourceSnapshot(rebased);
    }

    /** The minimum corner every caller must use as the capture origin. */
    public BlockPos origin() {
        int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
        for (var cell : cells) {
            x = Math.min(x, cell.position().getX());
            y = Math.min(y, cell.position().getY());
            z = Math.min(z, cell.position().getZ());
        }
        return new BlockPos(x, y, z);
    }
}
