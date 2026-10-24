package org.academy.internal.common.structure.upgrade.lifecycle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.BlockStructureGridAlignment;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.upgrade.control.OrientationTargets;

import java.util.List;

/** Pure snapping around the occupied-cell centroid, keeping restored content at its current site. */
public final class RestorationGrid {
    public static final int MAX_CLEARANCE_LIFT = 8;
    private RestorationGrid() {}

    public static Alignment align(StructurePose source, List<BlockPos> cells) {
        if (cells.isEmpty()) throw new IllegalArgumentException("Empty restoration grid");
        int quarter = 0;
        double best = Double.POSITIVE_INFINITY;
        StructurePose rotation = StructurePose.identity(Vec3.ZERO);
        for (int candidate = 0; candidate < 4; candidate++) {
            var pose = OrientationTargets.axisAngle(new Vec3(0, 1, 0), -candidate * Math.PI / 2.0);
            double error = OrientationTargets.rotationError(source, pose).lengthSqr();
            if (error < best - 1.0e-12) { best = error; quarter = candidate; rotation = pose; }
        }
        var center = Vec3.ZERO;
        for (var cell : cells) center = center.add(Vec3.atCenterOf(cell));
        center = center.scale(1.0 / cells.size());
        var raw = source.localToWorldPoint(center).subtract(rotation.localToWorldDirection(center));
        // Rotated unit cube minima differ from its rotated local corner by a whole cell.
        var anchor = new BlockPos((int) Math.floor(raw.x + .5), (int) Math.floor(raw.y + .5),
                (int) Math.floor(raw.z + .5));
        return new Alignment(new StructurePose(Vec3.atLowerCornerOf(anchor), rotation.rotation()), quarter);
    }

    public record Alignment(StructurePose pose, int quarterTurns) {
        public Alignment lifted(int blocks) {
            if (blocks < 0 || blocks > MAX_CLEARANCE_LIFT) throw new IllegalArgumentException("Unbounded restoration lift");
            return new Alignment(new StructurePose(pose.worldOrigin().add(0, blocks, 0), pose.rotation()), quarterTurns);
        }
        public BlockPos target(BlockPos cell) {
            var center = pose.localToWorldPoint(Vec3.atCenterOf(cell));
            return BlockPos.containing(center);
        }
        public net.minecraft.world.level.block.Rotation rotation() {
            return BlockStructureGridAlignment.rotation(quarterTurns);
        }
    }
}
