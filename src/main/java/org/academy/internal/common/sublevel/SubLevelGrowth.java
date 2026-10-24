package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Bounded edit geometry; the current content bounds are not a permanent building boundary. */
public final class SubLevelGrowth {
    public static final int MAX_SPAN = 256;
    public static final int MAX_SCAN_CELLS = 262_144;
    private SubLevelGrowth() {}

    static boolean blockBudgetAllows(int current, int additions, boolean occupied, int limit) {
        int extra = Math.max(0, additions - (occupied ? 1 : 0));
        return extra == 0 || current <= limit - extra;
    }

    static int placementCells(net.minecraft.world.item.BlockItem item) {
        var block = item.getBlock();
        return block instanceof net.minecraft.world.level.block.DoorBlock
                || block instanceof net.minecraft.world.level.block.BedBlock
                || block instanceof net.minecraft.world.level.block.DoublePlantBlock ? 2 : 1;
    }

    public static boolean valid(AABB bounds, int minY, int maxY) {
        return Double.isFinite(bounds.minX) && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ)
                && Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY) && Double.isFinite(bounds.maxZ)
                && bounds.minX >= -PlotLayout.SPACIOUS_RADIUS && bounds.minZ >= -PlotLayout.SPACIOUS_RADIUS
                && bounds.maxX <= PlotLayout.SPACIOUS_RADIUS && bounds.maxZ <= PlotLayout.SPACIOUS_RADIUS
                && bounds.minY >= minY && bounds.maxY <= maxY
                && bounds.getXsize() > 0 && bounds.getYsize() > 0 && bounds.getZsize() > 0
                && bounds.getXsize() <= MAX_SPAN && bounds.getYsize() <= MAX_SPAN && bounds.getZsize() <= MAX_SPAN
                && bounds.getXsize() * bounds.getYsize() * bounds.getZsize() <= MAX_SCAN_CELLS;
    }

    static AABB include(AABB current, BlockPos target, int padding, int minY, int maxY) {
        return current.minmax(new AABB(target.getX() - padding, Math.max(minY, target.getY() - padding), target.getZ() - padding,
                target.getX() + 1 + padding, Math.min(maxY, target.getY() + 1 + padding), target.getZ() + 1 + padding));
    }

    static AABB contentBounds(PlotStorage storage, PlotPos plot, AABB scan) {
        AABB result = null;
        for (var pos : BlockPos.betweenClosed((int) Math.floor(scan.minX), (int) Math.floor(scan.minY), (int) Math.floor(scan.minZ),
                (int) Math.ceil(scan.maxX) - 1, (int) Math.ceil(scan.maxY) - 1, (int) Math.ceil(scan.maxZ) - 1)) {
            if (!storage.blockState(plot, pos).isAir()) result = result == null ? new AABB(pos) : result.minmax(new AABB(pos));
        }
        return result == null ? new AABB(0, 0, 0, 1, 1, 1) : result;
    }
}
