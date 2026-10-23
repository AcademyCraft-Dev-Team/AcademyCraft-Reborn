package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.upgrade.StructureExtraction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure, bounded translation planning. This class neither captures nor moves a structure and
 * never edits terrain. Its certificate belongs to one immutable geometry/terrain snapshot;
 * adapters must revalidate versions, actor permissions and the control lease before execution.
 * Rotation is held at currentPose throughout every path. Angular motion needs its own sweep.
 */
public final class ExtractionPlanner {
    public static final int MAX_LOCAL_BOXES = 4_096;
    public static final int MAX_OBSTACLE_SHAPES = 65_536;
    public static final int MAX_SOFT_OBSTACLES = StructureExtraction.MAX_BLOCKED_CELLS;
    private static final double CONTACT_GAP = 1.0e-6;
    private static final Vec3[] RETREAT_DIRECTIONS = {
            new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1),
            new Vec3(1, 0, 1).normalize(), new Vec3(1, 0, -1).normalize(),
            new Vec3(-1, 0, 1).normalize(), new Vec3(-1, 0, -1).normalize()
    };

    private ExtractionPlanner() {
    }

    public enum Status {
        CLEAR, NEEDS_AUTHORIZATION, BLOCKED, TERRAIN_PENDING, BUDGET_EXHAUSTED, STALE
    }

    public enum Coverage { COMPLETE, UNKNOWN }

    public enum ObstacleKind { HARD, SOFT_REQUIRES_PERMISSION }

    /**
     * Server-owned immutable snapshot. A query must include every obstacle whose real shape
     * intersects the given broad-phase bounds, excluding the captured source cells/this body
     * only after the adapter proves their identity. Unknown sections are never empty sections.
     * A live adapter must freeze a view or report a changed terrainRevision. Reading must not
     * load chunks, authorize destruction, remove soft blocks, or mutate physics.
     * maxObstacleShapes may be zero: only a genuinely empty COMPLETE result or a truncated
     * result is permitted then. The adapter must also respect MAX_OBSTACLE_SHAPES.
     */
    @FunctionalInterface
    public interface ObstacleSnapshot {
        Query query(AABB sweptBounds, int maxObstacleShapes);
    }

    /** Multiple collision cuboids may share the same stable obstacle id. */
    public record Obstacle(String id, ObstacleKind kind, TranslationSweep.Box box) {
        public Obstacle {
            Objects.requireNonNull(id, "id");
            if (id.isBlank() || id.length() > 256) throw new IllegalArgumentException("Invalid obstacle id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(box, "box");
        }
    }

    /** Soft obstacles may use their selection geometry when their collision shape is empty. */
    public record Query(long terrainRevision, Coverage coverage, boolean truncated, List<Obstacle> obstacles) {
        public Query {
            if (terrainRevision < 0) throw new IllegalArgumentException("Negative terrain revision");
            Objects.requireNonNull(coverage, "coverage");
            Objects.requireNonNull(obstacles, "obstacles");
            if (obstacles.size() > MAX_OBSTACLE_SHAPES) throw new IllegalArgumentException("Too many obstacle shapes");
            obstacles = List.copyOf(obstacles);
        }
    }

    /**
     * sourcePose is the persistent lift baseline, not the latest displaced/tilted pose. Boxes
     * are the actual positive-volume collision cuboids in the structure's local coordinates.
     * Plants and other zero-collision content remain an adapter/content responsibility.
     */
    public record Geometry(StructurePose sourcePose, StructurePose currentPose, Vec3 localCenterOfMass,
                           List<AABB> localBoxes, long contentRevision, long poseRevision) {
        public Geometry {
            Objects.requireNonNull(sourcePose, "sourcePose");
            Objects.requireNonNull(currentPose, "currentPose");
            TranslationSweep.vector(sourcePose.worldOrigin());
            TranslationSweep.vector(currentPose.worldOrigin());
            TranslationSweep.vector(localCenterOfMass);
            Objects.requireNonNull(localBoxes, "localBoxes");
            if (localBoxes.isEmpty() || localBoxes.size() > MAX_LOCAL_BOXES) {
                throw new IllegalArgumentException("A bounded, nonempty real collision geometry is required");
            }
            localBoxes.forEach(TranslationSweep::requireBox);
            localBoxes = List.copyOf(localBoxes);
            if (contentRevision < 0 || poseRevision < 0) throw new IllegalArgumentException("Negative revision");
        }
    }

    /**
     * maxShapeTests counts every body/obstacle pair, including broad-phase rejection; a dense
     * candidate list therefore cannot evade the work budget. Candidate/length limits bound the
     * deterministic search, not the existence of all possible paths. Retreat radius is measured
     * from sourcePose's original X/Z origin, so replanning cannot accumulate lateral drift.
     */
    public record Limits(int maxShapeTests, int maxObstacleShapes, int maxQueries, int maxCandidates,
                         double maxPathLength, double maxRetreatDistance, double retreatStep) {
        public Limits {
            bounded(maxShapeTests, StructureExtraction.MAX_SHAPE_TESTS, "shape tests");
            bounded(maxObstacleShapes, MAX_OBSTACLE_SHAPES, "obstacle shapes");
            bounded(maxQueries, StructureExtraction.MAX_PATH_POINTS, "queries");
            bounded(maxCandidates, 65, "candidates");
            if (!Double.isFinite(maxPathLength) || maxPathLength <= 0.0 || maxPathLength > 256.0
                    || !Double.isFinite(maxRetreatDistance) || maxRetreatDistance < 0.0
                    || maxRetreatDistance > StructureExtraction.MAX_RETREAT_DISTANCE
                    || !Double.isFinite(retreatStep) || retreatStep < 0.001
                    || retreatStep > StructureExtraction.MAX_RETREAT_DISTANCE) {
                throw new IllegalArgumentException("Invalid path or retreat budget");
            }
        }
    }

    /** H uses liftHeight 3/5/7 and hoverTicks 200. Hover time is never spent during planning. */
    public record Request(Geometry geometry, double liftHeight, int hoverTicks,
                          long expectedTerrainRevision, Limits limits) {
        public Request {
            Objects.requireNonNull(geometry, "geometry");
            Objects.requireNonNull(limits, "limits");
            if (!Double.isFinite(liftHeight) || liftHeight <= 0.0
                    || liftHeight > StructureExtraction.MAX_LIFT_HEIGHT) {
                throw new IllegalArgumentException("Invalid source-relative lift height");
            }
            if (hoverTicks < 0 || hoverTicks > StructureExtraction.MAX_DURATION_TICKS
                    || expectedTerrainRevision < 0) throw new IllegalArgumentException("Invalid time or revision");
        }
    }

    /** pathOrigin is the body's origin at impact, not a claimed surface contact point. */
    public record Blockage(String obstacleId, Vec3 pathOrigin, Vec3 normal, boolean initialOverlap) {
    }

    public record Usage(int candidates, int queries, int shapeTests) {
    }

    /**
     * originPath contains host-world StructurePose.worldOrigin values, never COM positions.
     * CLEAR is geometric clearance only, not permission to execute. NEEDS_AUTHORIZATION requires
     * the adapter to authorize/process the listed soft content and replan the new snapshot.
     * Other statuses carry no executable partial path. Existing penetration is never teleported
     * out. Target height always uses source COM, including after a lateral retreat.
     */
    public record Result(Status status, List<Vec3> originPath, List<String> softObstacleIds,
                         Optional<Blockage> blockage, double targetCenterOfMassY, int hoverTicks,
                         long contentRevision, long poseRevision, long terrainRevision, Usage usage) {
        public Result {
            Objects.requireNonNull(status, "status");
            originPath = List.copyOf(originPath);
            softObstacleIds = List.copyOf(softObstacleIds);
            Objects.requireNonNull(blockage, "blockage");
            Objects.requireNonNull(usage, "usage");
        }
    }

    public static Result plan(Request request, ObstacleSnapshot snapshot) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(snapshot, "snapshot");
        var geometry = request.geometry;
        var start = geometry.currentPose.worldOrigin();
        double targetY = geometry.sourcePose.localToWorldPoint(geometry.localCenterOfMass).y + request.liftHeight;
        double currentY = geometry.currentPose.localToWorldPoint(geometry.localCenterOfMass).y;
        var target = TranslationSweep.vector(start.add(0.0, targetY - currentY, 0.0));
        var boxes = geometry.localBoxes.stream().map(box -> TranslationSweep.Box.of(box, geometry.currentPose)).toList();
        var work = new Work(request, snapshot, boxes);
        var directPath = List.of(start, target);
        var direct = work.evaluate(directPath);
        if (successful(direct.status) || terminal(direct.status)
                || direct.blockage.filter(Blockage::initialOverlap).isPresent()) {
            return result(request, targetY, work, direct);
        }
        if (request.limits.maxRetreatDistance == 0.0) return result(request, targetY, work, direct);
        if (!withinRetreat(request, start)) return result(request, targetY, work, direct);

        // Reach the last verified point before the first obstruction, then retreat at that
        // height. This handles uneven sockets without moving their low cells through a step.
        var retreatStart = direct.blockage.map(Blockage::pathOrigin).orElse(start);
        double verticalLength = Math.abs(target.y - start.y);
        if (verticalLength > 0.0 && !retreatStart.equals(start)) {
            double backoff = Math.min(CONTACT_GAP, retreatStart.distanceTo(start));
            retreatStart = retreatStart.add(0.0, -Math.signum(target.y - start.y) * backoff, 0.0);
        }
        boolean unknown = direct.status == Status.TERRAIN_PENDING;
        int rings = (int) Math.ceil(request.limits.maxRetreatDistance / request.limits.retreatStep);
        for (int ring = 1; ring <= rings; ring++) {
            double radius = Math.min(ring * request.limits.retreatStep, request.limits.maxRetreatDistance);
            for (var direction : RETREAT_DIRECTIONS) {
                var offset = direction.scale(radius);
                if (!withinRetreat(request, target.add(offset))) continue;
                var path = path(start, retreatStart, retreatStart.add(offset), target.add(offset));
                var candidate = work.evaluate(path);
                if (successful(candidate.status) || terminal(candidate.status)) {
                    return result(request, targetY, work, candidate);
                }
                unknown |= candidate.status == Status.TERRAIN_PENDING;
            }
        }
        return result(request, targetY, work, new Evaluation(unknown ? Status.TERRAIN_PENDING : Status.BLOCKED,
                List.of(), List.of(), direct.blockage));
    }

    private static Result result(Request request, double targetY, Work work, Evaluation evaluation) {
        return new Result(evaluation.status, evaluation.path, evaluation.softIds, evaluation.blockage,
                targetY, request.hoverTicks, request.geometry.contentRevision, request.geometry.poseRevision,
                request.expectedTerrainRevision, new Usage(work.candidates, work.queries, work.shapeTests));
    }

    private static boolean successful(Status status) {
        return status == Status.CLEAR || status == Status.NEEDS_AUTHORIZATION;
    }

    private static boolean terminal(Status status) {
        return status == Status.BUDGET_EXHAUSTED || status == Status.STALE;
    }

    private static boolean withinRetreat(Request request, Vec3 origin) {
        var anchor = request.geometry.sourcePose.worldOrigin();
        return Math.hypot(origin.x - anchor.x, origin.z - anchor.z) <= request.limits.maxRetreatDistance;
    }

    private static List<Vec3> path(Vec3... points) {
        var result = new ArrayList<Vec3>();
        for (var point : points) {
            TranslationSweep.vector(point);
            if (result.isEmpty() || !result.getLast().equals(point)) result.add(point);
        }
        if (result.size() == 1) result.add(result.getFirst());
        return List.copyOf(result);
    }

    private static void bounded(int value, int maximum, String name) {
        if (value <= 0 || value > maximum) throw new IllegalArgumentException("Invalid " + name + " budget");
    }

    private record Evaluation(Status status, List<Vec3> path, List<String> softIds, Optional<Blockage> blockage) {
        private static Evaluation failure(Status status) {
            return new Evaluation(status, List.of(), List.of(), Optional.empty());
        }
    }

    private static final class Work {
        private final Request request;
        private final ObstacleSnapshot snapshot;
        private final List<TranslationSweep.Box> boxes;
        private int candidates;
        private int queries;
        private int shapeTests;

        private Work(Request request, ObstacleSnapshot snapshot, List<TranslationSweep.Box> boxes) {
            this.request = request;
            this.snapshot = snapshot;
            this.boxes = boxes;
        }

        private Evaluation evaluate(List<Vec3> path) {
            if (candidates >= request.limits.maxCandidates) return Evaluation.failure(Status.BUDGET_EXHAUSTED);
            candidates++;
            double length = 0.0;
            for (int i = 1; i < path.size(); i++) length += path.get(i).distanceTo(path.get(i - 1));
            if (length > request.limits.maxPathLength) return Evaluation.failure(Status.BUDGET_EXHAUSTED);
            var soft = new LinkedHashSet<String>();
            for (int i = 1; i < path.size(); i++) {
                if (queries >= request.limits.maxQueries) return Evaluation.failure(Status.BUDGET_EXHAUSTED);
                var origin = path.get(i - 1);
                var offset = origin.subtract(request.geometry.currentPose.worldOrigin());
                var displacement = path.get(i).subtract(origin);
                var moving = boxes.stream().map(box -> offset.equals(Vec3.ZERO) ? box : box.translated(offset)).toList();
                var bounds = moving.getFirst().sweptBounds(displacement);
                for (int box = 1; box < moving.size(); box++) bounds = bounds.minmax(moving.get(box).sweptBounds(displacement));
                queries++;
                var query = Objects.requireNonNull(snapshot.query(bounds, request.limits.maxObstacleShapes), "query");
                if (query.terrainRevision != request.expectedTerrainRevision) return Evaluation.failure(Status.STALE);
                if (query.truncated || query.obstacles.size() > request.limits.maxObstacleShapes) {
                    return Evaluation.failure(Status.BUDGET_EXHAUSTED);
                }
                if (query.coverage != Coverage.COMPLETE) return Evaluation.failure(Status.TERRAIN_PENDING);
                TranslationSweep.Hit first = null;
                Obstacle obstacle = null;
                for (var bodyBox : moving) {
                    for (var candidate : query.obstacles) {
                        if (shapeTests >= request.limits.maxShapeTests) return Evaluation.failure(Status.BUDGET_EXHAUSTED);
                        shapeTests++;
                        var hit = TranslationSweep.firstHit(bodyBox, displacement, candidate.box);
                        if (hit.isEmpty()) continue;
                        if (candidate.kind == ObstacleKind.SOFT_REQUIRES_PERMISSION) {
                            soft.add(candidate.id);
                            if (soft.size() > MAX_SOFT_OBSTACLES) return Evaluation.failure(Status.BUDGET_EXHAUSTED);
                        } else if (first == null || hit.get().timeOfImpact() < first.timeOfImpact()
                                || hit.get().initialOverlap() && !first.initialOverlap()) {
                            first = hit.get();
                            obstacle = candidate;
                        }
                    }
                }
                if (first != null) {
                    var blockage = new Blockage(obstacle.id, origin.add(displacement.scale(first.timeOfImpact())),
                            first.normal(), first.initialOverlap());
                    return new Evaluation(Status.BLOCKED, List.of(), List.of(), Optional.of(blockage));
                }
            }
            return new Evaluation(soft.isEmpty() ? Status.CLEAR : Status.NEEDS_AUTHORIZATION,
                    List.copyOf(path), List.copyOf(soft), Optional.empty());
        }
    }
}
