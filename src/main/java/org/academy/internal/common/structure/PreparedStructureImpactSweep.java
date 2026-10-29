package org.academy.internal.common.structure;

import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructurePose;

import java.util.ArrayList;
import java.util.List;

/** Reuses one immutable set of oriented samples across every target of a path operation. */
public final class PreparedStructureImpactSweep {
    private final List<Group> groups;

    public PreparedStructureImpactSweep(List<AABB> localBoxes, StructurePose previous, StructurePose current) {
        double distance = previous.worldOrigin().distanceTo(current.worldOrigin());
        double radius = 0;
        for (var box : localBoxes) {
            double x = Math.max(Math.abs(box.minX), Math.abs(box.maxX));
            double y = Math.max(Math.abs(box.minY), Math.abs(box.maxY));
            double z = Math.max(Math.abs(box.minZ), Math.abs(box.maxZ));
            radius = Math.max(radius, Math.sqrt(x * x + y * y + z * z));
        }
        double dot = Math.abs(previous.rotationX() * current.rotationX()
                + previous.rotationY() * current.rotationY() + previous.rotationZ() * current.rotationZ()
                + previous.rotationW() * current.rotationW());
        distance += 2 * Math.acos(Math.min(1, dot)) * radius;
        int samples = previous.equals(current) ? 0 : Math.max(4, Math.min(128, (int) Math.ceil(distance / 0.1)));
        var poses = new ArrayList<StructurePose>(samples + 1);
        for (int sample = 0; sample <= samples; sample++) {
            poses.add(samples == 0 ? previous : previous.lerp(current, (double) sample / samples));
        }
        var prepared = new ArrayList<Group>(localBoxes.size());
        for (var box : localBoxes) {
            var cells = new ArrayList<BlockStructureOrientedBox>(poses.size());
            AABB bounds = null;
            for (var pose : poses) {
                var cell = BlockStructureOrientedBox.transform(box, pose);
                cells.add(cell);
                bounds = bounds == null ? cell.bounds() : bounds.minmax(cell.bounds());
            }
            prepared.add(new Group(bounds, List.copyOf(cells)));
        }
        groups = List.copyOf(prepared);
    }

    /** Only exact tests surviving both bounding checks spend the caller's narrow-phase budget. */
    public Result test(AABB target, int maximumTests) {
        if (maximumTests < 0) throw new IllegalArgumentException("Negative sweep test budget");
        int tests = 0;
        BlockStructureOrientedBox orientedTarget = null;
        for (var group : groups) {
            if (!group.bounds.intersects(target)) continue;
            for (var sample : group.samples) {
                if (!sample.bounds().intersects(target)) continue;
                if (tests == maximumTests) return new Result(false, tests, false);
                tests++;
                if (orientedTarget == null) orientedTarget = BlockStructureOrientedBox.axisAligned(target);
                if (sample.intersects(orientedTarget)) return new Result(true, tests, true);
            }
        }
        return new Result(false, tests, true);
    }

    public record Result(boolean intersects, int tests, boolean complete) {}
    private record Group(AABB bounds, List<BlockStructureOrientedBox> samples) {}
}
