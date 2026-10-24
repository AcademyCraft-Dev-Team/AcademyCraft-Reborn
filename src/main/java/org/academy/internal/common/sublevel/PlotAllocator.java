package org.academy.internal.common.sublevel;

import java.util.BitSet;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic first-free allocator for a shared plot grid. Thread confinement is the caller's
 * responsibility; the server thread owns it in gameplay.
 */
public final class PlotAllocator {
    private final PlotLayout layout;
    private final BitSet occupied;
    private int allocated;

    public PlotAllocator(PlotLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.occupied = new BitSet(layout.maxPlots());
    }

    public PlotLayout layout() {
        return layout;
    }

    public Optional<PlotPos> allocate() {
        int index = occupied.nextClearBit(0);
        if (index < 0 || index >= layout.maxPlots()) return Optional.empty();
        occupied.set(index);
        allocated++;
        int side = layout.gridSide();
        return Optional.of(new PlotPos(index % side, index / side));
    }

    /** Reserves a specific plot for restart recovery; the persisted plot identity wins. */
    public boolean reserve(PlotPos plot) {
        Objects.requireNonNull(plot, "plot");
        int index = index(plot);
        if (index < 0 || index >= layout.maxPlots() || occupied.get(index)) return false;
        occupied.set(index);
        allocated++;
        return true;
    }

    public boolean free(PlotPos plot) {
        Objects.requireNonNull(plot, "plot");
        int index = index(plot);
        if (index < 0 || index >= layout.maxPlots() || !occupied.get(index)) return false;
        occupied.clear(index);
        allocated--;
        return true;
    }

    public boolean isOccupied(PlotPos plot) {
        Objects.requireNonNull(plot, "plot");
        int index = index(plot);
        return index >= 0 && index < layout.maxPlots() && occupied.get(index);
    }

    public int allocated() {
        return allocated;
    }

    public int capacity() {
        return layout.maxPlots();
    }

    private int index(PlotPos plot) {
        int side = layout.gridSide();
        if (plot.x() >= side || plot.z() >= side) return -1;
        return plot.z() * side + plot.x();
    }
}
