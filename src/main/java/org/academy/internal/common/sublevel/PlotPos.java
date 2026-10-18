package org.academy.internal.common.sublevel;

/** Stable, non-negative plot-grid coordinate. Plots are addressed in chunk-sized units. */
public record PlotPos(int x, int z) {
    public PlotPos {
        if (x < 0 || z < 0) throw new IllegalArgumentException("Plot coordinates cannot be negative");
    }
}
