package org.academy.api.common.structure;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Complete, detached counts of non-air cells; never includes block-entity data or storage positions. */
public record StructureBlockSummary(Map<BlockState, Integer> counts, int scannedCells) {
    public static final int MAX_SCANNED_CELLS = 65_536;

    public StructureBlockSummary {
        counts = Map.copyOf(counts);
        if (scannedCells < 0 || scannedCells > MAX_SCANNED_CELLS) throw new IllegalArgumentException("Invalid scan size");
        long total = 0;
        for (var entry : counts.entrySet()) {
            if (entry.getKey().isAir() || entry.getValue() <= 0) throw new IllegalArgumentException("Invalid block count");
            total += entry.getValue();
        }
        if (total > scannedCells) throw new IllegalArgumentException("Counts exceed complete scan");
    }

    /** ALL requires a non-empty structure; air gaps are ignored, liquids and decorations are included. */
    public boolean matches(Predicate<BlockState> predicate, boolean all) {
        Objects.requireNonNull(predicate, "predicate");
        return !counts.isEmpty() && (all ? counts.keySet().stream().allMatch(predicate)
                : counts.keySet().stream().anyMatch(predicate));
    }

    public static void requireBudget(int maximumCells) {
        if (maximumCells < 1 || maximumCells > MAX_SCANNED_CELLS) throw new IllegalArgumentException("Invalid summary budget");
    }
}
