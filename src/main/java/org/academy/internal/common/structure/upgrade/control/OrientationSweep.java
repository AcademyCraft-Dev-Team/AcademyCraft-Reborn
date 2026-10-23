package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;

import java.util.List;
import java.util.Objects;

/**
 * Conservative continuous angular admission over an immutable obstacle snapshot. Each real
 * local box gets its own arc enclosure; empty space inside a structure's overall AABB is not
 * turned into geometry. The enclosure includes translation and the entire unwrapped arc.
 * Rejection can be conservative near contact; CLEAR is never inferred from endpoint samples.
 */
public final class OrientationSweep {
    private static final double SEGMENT_ANGLE = 0.05;
    private static final int MAX_SEGMENTS = 1_024;

    private OrientationSweep() {}

    public enum Status { CLEAR, BLOCKED, TERRAIN_PENDING, BUDGET_EXHAUSTED, STALE }

    public record Result(Status status, int shapeTests, String detail) {}

    public static Result check(List<AABB> localBoxes, StructurePose source, Vec3 localCenterOfMass,
                               Vec3 worldAngularVelocity, Vec3 linearDisplacement, double seconds,
                               long terrainRevision, int maxShapeTests,
                               ExtractionPlanner.ObstacleSnapshot obstacles) {
        return check(localBoxes, source, localCenterOfMass, worldAngularVelocity, linearDisplacement, 0.0,
                seconds, terrainRevision, maxShapeTests, obstacles);
    }

    /** Includes a supplied Euclidean envelope for known free-motion integration uncertainty. */
    public static Result check(List<AABB> localBoxes, StructurePose source, Vec3 localCenterOfMass,
                               Vec3 worldAngularVelocity, Vec3 linearDisplacement, double linearUncertainty,
                               double seconds, long terrainRevision, int maxShapeTests,
                               ExtractionPlanner.ObstacleSnapshot obstacles) {
        Objects.requireNonNull(localBoxes, "localBoxes");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(obstacles, "obstacles");
        finite(localCenterOfMass);
        finite(worldAngularVelocity);
        finite(linearDisplacement);
        if (localBoxes.isEmpty() || localBoxes.size() > ExtractionPlanner.MAX_LOCAL_BOXES
                || terrainRevision < 0 || maxShapeTests < 1 || maxShapeTests > 262_144
                || !Double.isFinite(linearUncertainty) || linearUncertainty < 0
                || !Double.isFinite(seconds) || seconds <= 0 || seconds > 0.25) {
            throw new IllegalArgumentException("Invalid angular sweep budget");
        }
        localBoxes = List.copyOf(localBoxes);
        double angle = worldAngularVelocity.length() * seconds;
        if (!Double.isFinite(angle)) throw new IllegalArgumentException("Invalid angular displacement");
        int segments = Math.max(1, (int) Math.ceil(angle / SEGMENT_ANGLE));
        if (segments > MAX_SEGMENTS || (long) segments * localBoxes.size() > maxShapeTests) {
            return new Result(Status.BUDGET_EXHAUSTED, 0, "angular_arc_budget");
        }
        int used = 0;
        for (int index = 0; index < segments; index++) {
            double middle = (index + 0.5) / segments;
            var rotation = OrientationTargets.integrateRotation(source, localCenterOfMass,
                    worldAngularVelocity, seconds * middle);
            var pose = new StructurePose(rotation.worldOrigin().add(linearDisplacement.scale(middle)),
                    rotation.rotation());
            for (var box : localBoxes) {
                // Every corner stays within this distance of its midpoint pose. Expanding all
                // three local axes contains that Euclidean ball, hence also the full swept box.
                double radius = radius(box, localCenterOfMass);
                double margin = 2 * radius * Math.sin(angle / segments / 4)
                        + linearDisplacement.length() / segments / 2 + linearUncertainty;
                var enclosure = TranslationSweep.Box.of(box.inflate(margin), pose);
                if (++used > maxShapeTests) return new Result(Status.BUDGET_EXHAUSTED, used - 1, "angular_query_budget");
                var query = obstacles.query(enclosure.bounds(), maxShapeTests - used);
                if (query.terrainRevision() != terrainRevision) return new Result(Status.STALE, used, "terrain_changed");
                if (query.coverage() != ExtractionPlanner.Coverage.COMPLETE) {
                    return new Result(Status.TERRAIN_PENDING, used, "terrain_unknown");
                }
                if (query.truncated() || query.obstacles().size() > maxShapeTests - used) {
                    return new Result(Status.BUDGET_EXHAUSTED, used, "angular_obstacle_budget");
                }
                for (var obstacle : query.obstacles()) {
                    used++;
                    if (TranslationSweep.firstHit(enclosure, Vec3.ZERO, obstacle.box()).isPresent()) {
                        return new Result(Status.BLOCKED, used,
                                obstacle.kind() == ExtractionPlanner.ObstacleKind.SOFT_REQUIRES_PERMISSION
                                        ? "soft_obstacle_requires_permission" : "angular_path_obstructed");
                    }
                }
            }
        }
        return new Result(Status.CLEAR, used, "angular_path_clear");
    }

    private static double radius(AABB box, Vec3 center) {
        Objects.requireNonNull(box, "localBox");
        finite(new Vec3(box.minX, box.minY, box.minZ));
        finite(new Vec3(box.maxX, box.maxY, box.maxZ));
        if (box.maxX <= box.minX || box.maxY <= box.minY || box.maxZ <= box.minZ) {
            throw new IllegalArgumentException("Empty collision box");
        }
        double x = Math.max(Math.abs(box.minX - center.x), Math.abs(box.maxX - center.x));
        double y = Math.max(Math.abs(box.minY - center.y), Math.abs(box.maxY - center.y));
        double z = Math.max(Math.abs(box.minZ - center.z), Math.abs(box.maxZ - center.z));
        double radius = Math.hypot(Math.hypot(x, y), z);
        if (!Double.isFinite(radius)) throw new IllegalArgumentException("Unbounded collision box");
        return radius;
    }

    private static void finite(Vec3 vector) {
        Objects.requireNonNull(vector, "vector");
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalArgumentException("Nonfinite sweep vector");
        }
    }
}
