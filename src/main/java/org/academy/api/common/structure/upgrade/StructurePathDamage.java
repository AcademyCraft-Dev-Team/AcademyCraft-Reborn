package org.academy.api.common.structure.upgrade;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.util.LevelUtil;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
                          boolean includeFluids, LevelUtil.@Nullable BlockDropHandler loot,
                          StructureDamage.Targets targets, Optional<Sweep> sweep) {
        public Request(Vec3 start, Vec3 end, float radius, int miningLevel,
                       StructureDamage.DropPolicy drops, boolean spawnParticles,
                       boolean canBlock, boolean simulate, boolean silentNeighbors,
                       boolean includeFluids, LevelUtil.@Nullable BlockDropHandler loot) {
            this(start, end, radius, miningLevel, drops, spawnParticles, canBlock, simulate,
                    silentNeighbors, includeFluids, loot, StructureDamage.Targets.ALL, Optional.empty());
        }
        public Request(Vec3 start, Vec3 end, float radius, int miningLevel,
                       StructureDamage.DropPolicy drops, boolean spawnParticles,
                       boolean canBlock, boolean simulate, boolean silentNeighbors,
                       boolean includeFluids, LevelUtil.@Nullable BlockDropHandler loot, StructureDamage.Targets targets) {
            this(start, end, radius, miningLevel, drops, spawnParticles, canBlock, simulate,
                    silentNeighbors, includeFluids, loot, targets, Optional.empty());
        }
        public Request {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            Objects.requireNonNull(drops, "drops");
            Objects.requireNonNull(targets, "targets");
            Objects.requireNonNull(sweep, "sweep");
            if (!finite(start) || !finite(end) || start.distanceToSqr(end) > 2048.0 * 2048.0
                    || !Float.isFinite(radius) || radius < 0 || radius > 32) {
                throw new IllegalArgumentException("Invalid bounded damage path");
            }
        }
    }

    /** Optional narrow phase for a moving body's actual occupied boxes, retaining hollow gaps. */
    public record Sweep(List<AABB> localBoxes, StructurePose previous, StructurePose current, Optional<SourceWitness> source) {
        public Sweep(List<AABB> localBoxes, StructurePose previous, StructurePose current) {
            this(localBoxes, previous, current, Optional.empty());
        }
        public Sweep {
            localBoxes = List.copyOf(localBoxes);
            Objects.requireNonNull(previous); Objects.requireNonNull(current);
            Objects.requireNonNull(source);
            if (localBoxes.isEmpty() || localBoxes.size() > 128) throw new IllegalArgumentException("Invalid sweep geometry budget");
            for (var box : localBoxes) {
                if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                        || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)
                        || box.getXsize() <= 0 || box.getYsize() <= 0 || box.getZsize() <= 0)
                    throw new IllegalArgumentException("Invalid sweep collision box");
            }
        }
    }

    /** Lightweight source observation; callbacks cannot continue a sweep after relocation or replacement. */
    public record SourceWitness(StructureRef reference, UUID runtimeId, long geometryRevision, StructurePose pose) {
        public SourceWitness {
            Objects.requireNonNull(reference); Objects.requireNonNull(runtimeId); Objects.requireNonNull(pose);
            if (geometryRevision < 0) throw new IllegalArgumentException("Invalid source geometry revision");
        }
        public boolean current(net.minecraft.server.level.ServerLevel host) {
            if (!host.dimension().identifier().equals(reference.dimension())) return false;
            try { return StructureSpaces.resolve(host.getServer(), reference).filter(this::matches).isPresent(); }
            catch (IllegalArgumentException | IllegalStateException unavailable) { return false; }
        }
        public boolean matches(StructureHandle handle) {
            return handle.lifecycle() == StructureLifecycle.ACTIVE && handle.reference().equals(reference)
                    && handle.runtimeId().equals(runtimeId) && handle.geometryRevision() == geometryRevision && handle.pose().equals(pose);
        }
    }

    /** Incomplete coverage/budget stops at the conservative frontier, never reports a clear ray. */
    public record Result(StructureOperation.Status status, String detail, double distance,
                         StructureDamage.Receipt receipt, Optional<StructureDamage.Cell> obstruction) {
        public Result(StructureOperation.Status status, String detail, double distance, StructureDamage.Receipt receipt) {
            this(status, detail, distance, receipt, Optional.empty());
        }
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(detail, "detail");
            Objects.requireNonNull(receipt, "receipt");
            Objects.requireNonNull(obstruction, "obstruction");
            if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid path distance");
        }
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
