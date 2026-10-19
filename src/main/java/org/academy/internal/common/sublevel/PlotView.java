package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.academy.internal.common.structure.space.NativeStructureView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds the bounded render view for a plot-backed SubLevel. */
public final class PlotView {
    private PlotView() {
    }

    public static NativeStructureView capture(PlotStorage storage, PlotPos plot, AABB localBounds) {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(plot, "plot");
        Objects.requireNonNull(localBounds, "localBounds");
        int minX = (int) Math.floor(localBounds.minX);
        int minY = (int) Math.floor(localBounds.minY);
        int minZ = (int) Math.floor(localBounds.minZ);
        int maxX = (int) Math.ceil(localBounds.maxX);
        int maxY = (int) Math.ceil(localBounds.maxY);
        int maxZ = (int) Math.ceil(localBounds.maxZ);
        var cells = new ArrayList<NativeStructureView.Cell>();
        var allFilled = true;
        var position = new BlockPos.MutableBlockPos();
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    position.set(x, y, z);
                    var state = storage.blockState(plot, position);
                    if (state.isAir() || !NativeStructureView.isSupportedState(state)) {
                        allFilled = false;
                        continue;
                    }
                    if (occluded(storage, plot, position)) continue;
                    if (cells.size() >= NativeStructureView.MAX_CELLS) {
                        throw new IllegalStateException("SubLevel view exceeds " + NativeStructureView.MAX_CELLS + " cells");
                    }
                    cells.add(new NativeStructureView.Cell(position.immutable(), state));
                }
            }
        }
        return cells.isEmpty() ? NativeStructureView.EMPTY
                : new NativeStructureView(List.copyOf(cells), allFilled);
    }

    /** Drops fully enclosed opaque cells; collision geometry remains complete in PlotGeometry. */
    private static boolean occluded(PlotStorage storage, PlotPos plot, BlockPos local) {
        int side = storage.layout().sideChunks() * 16;
        for (var direction : net.minecraft.core.Direction.values()) {
            var offset = local.relative(direction);
            if (offset.getX() < 0 || offset.getZ() < 0 || offset.getX() >= side || offset.getZ() >= side) {
                return false;
            }
            var neighbor = storage.blockState(plot, offset);
            if (!neighbor.canOcclude()) return false;
        }
        return true;
    }
}
