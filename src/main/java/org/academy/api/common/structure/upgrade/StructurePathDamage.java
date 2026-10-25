package org.academy.api.common.structure.upgrade;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.util.LevelUtil;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Immediate, bounded path damage. Geometry and returned distance are always in host space. */
public final class StructurePathDamage {
    private StructurePathDamage() {}

    /**
     * Keeps the existing beam convention: a segment against radius-inflated selection boxes.
     * A simulation computes obstruction only and never emits protection/loot callbacks.
     * Loot handlers run against the real storage cell inside the shared output scope; the
     * real player remains in the host level. A call is a new attack, not a replayable plan.
     */
    public record Request(Vec3 start, Vec3 end, float radius, int miningLevel,
                          StructureDamage.DropPolicy drops, boolean spawnParticles,
                          boolean canBlock, boolean simulate, boolean silentNeighbors,
                          boolean includeFluids, LevelUtil.@Nullable BlockDropHandler loot) {
        public Request {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            Objects.requireNonNull(drops, "drops");
            if (!finite(start) || !finite(end) || start.distanceToSqr(end) > 2048.0 * 2048.0
                    || !Float.isFinite(radius) || radius < 0 || radius > 32) {
                throw new IllegalArgumentException("Invalid bounded damage path");
            }
        }
    }

    /** Incomplete coverage/budget stops at the conservative frontier, never reports a clear ray. */
    public record Result(StructureOperation.Status status, String detail, double distance,
                         StructureDamage.Receipt receipt) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(detail, "detail");
            Objects.requireNonNull(receipt, "receipt");
            if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid path distance");
        }
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
