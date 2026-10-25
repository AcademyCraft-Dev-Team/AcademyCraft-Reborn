package org.academy.api.common.structure.upgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** Immediate rectangular blade volume in host coordinates; hard materials need not stop a cut. */
public final class StructureCutDamage {
    private StructureCutDamage() {}

    /** Read-only material rule, rechecked before mutation. The view never loads missing chunks. */
    @FunctionalInterface
    public interface MaterialRule {
        boolean accepts(BlockState state, BlockGetter view, BlockPos position);
    }

    /**
     * planeDirection is perpendicular to the segment and determines the blade's width axis.
     * Real selection boxes are tested, with a unit-cell fallback for admitted empty shapes
     * such as fire. No per-cell callback may write the world or grant additional authority.
     * A cut shares the beam's per-call and per-player tick budgets; it never continues later.
     */
    public record Request(Vec3 start, Vec3 end, Vec3 planeDirection,
                          double halfWidth, double halfThickness, MaterialRule materials,
                          StructureDamage.DropPolicy drops, boolean simulate) {
        public Request {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            Objects.requireNonNull(planeDirection, "planeDirection");
            Objects.requireNonNull(materials, "materials");
            Objects.requireNonNull(drops, "drops");
            double length = start.distanceTo(end);
            if (!finite(start) || !finite(end) || !finite(planeDirection)
                    || !Double.isFinite(length) || length < 1.0e-6 || length > 2048
                    || !Double.isFinite(halfWidth) || !Double.isFinite(halfThickness)
                    || halfWidth <= 0 || halfThickness <= 0 || Math.hypot(halfWidth, halfThickness) > 32
                    || Math.abs(planeDirection.lengthSqr() - 1) > 1.0e-6
                    || Math.abs(planeDirection.dot(end.subtract(start).scale(1 / length))) > 1.0e-6) {
                throw new IllegalArgumentException("Invalid bounded blade volume");
            }
            planeDirection = planeDirection.normalize();
        }
    }

    public record Result(StructureOperation.Status status, String detail, StructureDamage.Receipt receipt) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(detail, "detail");
            Objects.requireNonNull(receipt, "receipt");
        }
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
