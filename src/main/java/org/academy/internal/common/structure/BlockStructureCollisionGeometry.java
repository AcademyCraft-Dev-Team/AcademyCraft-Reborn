package org.academy.internal.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.api.common.structure.BlockStructureCollision;
import org.academy.api.common.structure.BlockStructureSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/** Cached section-partitioned collision boxes for one immutable structure snapshot. */
public final class BlockStructureCollisionGeometry {
    public static final BlockStructureCollisionGeometry EMPTY =
            new BlockStructureCollisionGeometry(List.of());
    private static final double MERGE_EPSILON = 1.0e-7;
    private static final int MAX_MERGE_PASSES = 6;

    private final List<AABB> localBoxes;
    private final AABB collisionLocalBounds;
    private Vec3 cachedPosition;
    private float cachedYaw = Float.NaN;
    private List<AABB> cachedWorldBoxes = List.of();
    private List<VoxelShape> cachedWorldShapes = List.of();

    private BlockStructureCollisionGeometry(List<AABB> localBoxes) {
        this.localBoxes = List.copyOf(localBoxes);
        AABB bounds = null;
        for (var box : localBoxes) bounds = bounds == null ? box : bounds.minmax(box);
        collisionLocalBounds = bounds;
    }

    public static BlockStructureCollisionGeometry create(BlockStructureSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return EMPTY;
        var partitions = new HashMap<Partition, List<AABB>>();
        for (var block : snapshot.blocks()) {
            var position = block.relativePosition();
            var partition = Partition.of(position);
            var boxes = partitions.computeIfAbsent(partition, ignored -> new ArrayList<>());
            for (var box : block.collisionBoxes()) {
                boxes.add(box.move(position));
            }
        }
        var merged = new ArrayList<AABB>();
        for (var boxes : partitions.values()) merged.addAll(mergeBoxes(boxes));
        merged.sort(Comparator.comparingDouble((AABB box) -> box.minX)
                .thenComparingDouble(box -> box.minY)
                .thenComparingDouble(box -> box.minZ)
                .thenComparingDouble(box -> box.maxX)
                .thenComparingDouble(box -> box.maxY)
                .thenComparingDouble(box -> box.maxZ));
        return merged.isEmpty() ? EMPTY : new BlockStructureCollisionGeometry(merged);
    }

    public boolean isEmpty() {
        return localBoxes.isEmpty();
    }

    public List<AABB> worldBoxes(
            Vec3 position,
            float yawDegrees,
            BlockStructureSnapshot snapshot
    ) {
        if (cachedPosition != null
                && cachedPosition.equals(position)
                && Float.compare(cachedYaw, yawDegrees) == 0) {
            return cachedWorldBoxes;
        }
        var transformed = new ArrayList<AABB>(localBoxes.size());
        for (var box : localBoxes) {
            transformed.add(rotateBox(
                    box,
                    snapshot.pivotX(),
                    snapshot.pivotZ(),
                    yawDegrees
            ).move(position));
        }
        cachedPosition = position;
        cachedYaw = yawDegrees;
        cachedWorldBoxes = List.copyOf(transformed);
        cachedWorldShapes = List.of();
        return cachedWorldBoxes;
    }

    public void collectCollisionShapes(
            AABB bounds,
            Vec3 position,
            float yawDegrees,
            BlockStructureSnapshot snapshot,
            Consumer<VoxelShape> output
    ) {
        var boxes = worldBoxes(position, yawDegrees, snapshot);
        if (cachedWorldShapes.size() != boxes.size()) {
            var shapes = new ArrayList<VoxelShape>(boxes.size());
            for (var box : boxes) shapes.add(Shapes.create(box));
            cachedWorldShapes = List.copyOf(shapes);
        }
        var query = bounds.inflate(MERGE_EPSILON);
        for (var index = 0; index < boxes.size(); index++) {
            if (boxes.get(index).intersects(query)) output.accept(cachedWorldShapes.get(index));
        }
    }

    public AABB worldBounds(
            Vec3 position,
            float yawDegrees,
            BlockStructureSnapshot snapshot
    ) {
        var localBounds = new AABB(
                0.0, 0.0, 0.0,
                snapshot.width(), snapshot.height(), snapshot.depth()
        );
        if (collisionLocalBounds != null) localBounds = localBounds.minmax(collisionLocalBounds);
        return rotateBox(
                localBounds,
                snapshot.pivotX(),
                snapshot.pivotZ(),
                yawDegrees
        ).move(position);
    }

    public boolean supports(
            AABB entityBounds,
            double tolerance,
            Vec3 position,
            float yawDegrees,
            BlockStructureSnapshot snapshot
    ) {
        return supports(
                worldBoxes(position, yawDegrees, snapshot),
                entityBounds,
                tolerance
        );
    }

    public OptionalDouble supportSurfaceY(
            AABB entityBounds,
            double tolerance,
            Vec3 position,
            float yawDegrees,
            BlockStructureSnapshot snapshot
    ) {
        return supportSurfaceY(
                worldBoxes(position, yawDegrees, snapshot),
                entityBounds,
                tolerance
        );
    }

    static boolean supports(
            List<AABB> collisionBoxes,
            AABB entityBounds,
            double tolerance
    ) {
        return supportSurfaceY(collisionBoxes, entityBounds, tolerance).isPresent();
    }

    static OptionalDouble supportSurfaceY(
            List<AABB> collisionBoxes,
            AABB entityBounds,
            double tolerance
    ) {
        var safeTolerance = Math.max(0.0, tolerance);
        var bestSurface = Double.NaN;
        var bestDistance = Double.POSITIVE_INFINITY;
        for (var box : collisionBoxes) {
            var topDifference = entityBounds.minY - box.maxY;
            if (topDifference >= -safeTolerance
                    && topDifference <= safeTolerance
                    && entityBounds.maxX > box.minX + MERGE_EPSILON
                    && entityBounds.minX < box.maxX - MERGE_EPSILON
                    && entityBounds.maxZ > box.minZ + MERGE_EPSILON
                    && entityBounds.minZ < box.maxZ - MERGE_EPSILON) {
                var distance = Math.abs(topDifference);
                if (distance + MERGE_EPSILON < bestDistance
                        || (Math.abs(distance - bestDistance) <= MERGE_EPSILON
                        && box.maxY > bestSurface)) {
                    bestSurface = box.maxY;
                    bestDistance = distance;
                }
            }
        }
        return Double.isNaN(bestSurface)
                ? OptionalDouble.empty()
                : OptionalDouble.of(bestSurface);
    }

    public Vec3 collideWithWorld(
            Entity entity,
            Level level,
            Vec3 position,
            float yawDegrees,
            BlockStructureSnapshot snapshot,
            Vec3 requestedMovement
    ) {
        if (requestedMovement.lengthSqr() <= 1.0e-14) return Vec3.ZERO;
        var boxes = worldBoxes(position, yawDegrees, snapshot);
        if (boxes.isEmpty()) return requestedMovement;
        var searchBounds = worldBounds(position, yawDegrees, snapshot)
                .expandTowards(requestedMovement)
                .inflate(MERGE_EPSILON);
        var colliders = new ArrayList<VoxelShape>();
        level.getBlockCollisions(entity, searchBounds).forEach(colliders::add);
        var worldBorder = level.getWorldBorder();
        if (worldBorder.isInsideCloseToBorder(entity, searchBounds)) {
            colliders.add(worldBorder.getCollisionShape());
        }
        for (var candidate : level.getEntities(
                entity,
                searchBounds,
                candidate -> candidate instanceof BlockStructureCollision
                        && entity.canCollideWith(candidate)
        )) {
            ((BlockStructureCollision) candidate).collectCollisionShapes(
                    searchBounds,
                    colliders::add
            );
        }
        return collideBoxes(boxes, colliders, requestedMovement);
    }

    static Vec3 collideBoxes(
            List<AABB> boxes,
            List<VoxelShape> colliders,
            Vec3 requestedMovement
    ) {
        if (boxes.isEmpty() || colliders.isEmpty()) return requestedMovement;
        var resolvedMovement = Vec3.ZERO;
        for (var axis : Direction.axisStepOrder(requestedMovement)) {
            var axisMovement = requestedMovement.get(axis);
            if (axisMovement == 0.0) continue;
            for (var box : boxes) {
                axisMovement = Shapes.collide(
                        axis,
                        box.move(resolvedMovement),
                        colliders,
                        axisMovement
                );
                if (axisMovement == 0.0) break;
            }
            resolvedMovement = resolvedMovement.with(axis, axisMovement);
        }
        return resolvedMovement;
    }

    public BlockStructureSweepHit firstSweepHit(
            AABB target,
            Vec3 position,
            float yawDegrees,
            BlockStructureSnapshot snapshot,
            Vec3 movement
    ) {
        if (target == null || movement == null || movement.lengthSqr() <= 1.0e-14) return null;
        BlockStructureSweepHit earliest = null;
        for (var box : worldBoxes(position, yawDegrees, snapshot)) {
            var hit = sweep(box, target, movement);
            if (hit != null && (earliest == null || hit.time() < earliest.time())) {
                earliest = hit;
            }
        }
        return earliest;
    }

    static List<AABB> mergeBoxes(List<AABB> input) {
        if (input.isEmpty()) return List.of();
        var result = new ArrayList<>(input);
        for (var pass = 0; pass < MAX_MERGE_PASSES; pass++) {
            var oldSize = result.size();
            result = mergeAlong(result, Direction.Axis.X);
            result = mergeAlong(result, Direction.Axis.Y);
            result = mergeAlong(result, Direction.Axis.Z);
            if (result.size() == oldSize) break;
        }
        return List.copyOf(result);
    }

    static AABB rotateBox(
            AABB box,
            double pivotX,
            double pivotZ,
            float yawDegrees
    ) {
        if (!Float.isFinite(yawDegrees)) yawDegrees = 0.0f;
        var radians = Math.toRadians(yawDegrees);
        var sine = Math.sin(radians);
        var cosine = Math.cos(radians);
        var minimumX = Double.POSITIVE_INFINITY;
        var minimumZ = Double.POSITIVE_INFINITY;
        var maximumX = Double.NEGATIVE_INFINITY;
        var maximumZ = Double.NEGATIVE_INFINITY;
        for (var x : new double[]{box.minX, box.maxX}) {
            for (var z : new double[]{box.minZ, box.maxZ}) {
                var offsetX = x - pivotX;
                var offsetZ = z - pivotZ;
                var rotatedX = pivotX + offsetX * cosine - offsetZ * sine;
                var rotatedZ = pivotZ + offsetX * sine + offsetZ * cosine;
                minimumX = Math.min(minimumX, rotatedX);
                minimumZ = Math.min(minimumZ, rotatedZ);
                maximumX = Math.max(maximumX, rotatedX);
                maximumZ = Math.max(maximumZ, rotatedZ);
            }
        }
        return new AABB(
                minimumX, box.minY, minimumZ,
                maximumX, box.maxY, maximumZ
        );
    }

    static BlockStructureSweepHit sweep(AABB moving, AABB target, Vec3 movement) {
        if (moving.intersects(target)) {
            var normal = movement.lengthSqr() <= 1.0e-14
                    ? Vec3.ZERO
                    : movement.normalize().scale(-1.0);
            return new BlockStructureSweepHit(0.0, moving.getCenter(), normal);
        }
        var x = axisSweep(
                moving.minX, moving.maxX, target.minX, target.maxX, movement.x,
                new Vec3(-Math.signum(movement.x), 0.0, 0.0));
        var y = axisSweep(
                moving.minY, moving.maxY, target.minY, target.maxY, movement.y,
                new Vec3(0.0, -Math.signum(movement.y), 0.0));
        var z = axisSweep(
                moving.minZ, moving.maxZ, target.minZ, target.maxZ, movement.z,
                new Vec3(0.0, 0.0, -Math.signum(movement.z)));
        if (x == null || y == null || z == null) return null;
        var entry = Math.max(x.entry, Math.max(y.entry, z.entry));
        var exit = Math.min(x.exit, Math.min(y.exit, z.exit));
        if (entry > exit + MERGE_EPSILON || exit < 0.0 || entry > 1.0) return null;
        var time = Math.max(0.0, entry);
        var normal = x.entry >= y.entry && x.entry >= z.entry
                ? x.normal
                : y.entry >= z.entry ? y.normal : z.normal;
        return new BlockStructureSweepHit(
                time,
                moving.getCenter().add(movement.scale(time)),
                normal
        );
    }

    private static AxisSweep axisSweep(
            double movingMinimum,
            double movingMaximum,
            double targetMinimum,
            double targetMaximum,
            double movement,
            Vec3 normal
    ) {
        if (Math.abs(movement) <= MERGE_EPSILON) {
            return movingMaximum > targetMinimum && movingMinimum < targetMaximum
                    ? new AxisSweep(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Vec3.ZERO)
                    : null;
        }
        var first = (targetMinimum - movingMaximum) / movement;
        var second = (targetMaximum - movingMinimum) / movement;
        return first <= second
                ? new AxisSweep(first, second, normal)
                : new AxisSweep(second, first, normal);
    }

    private static ArrayList<AABB> mergeAlong(
            List<AABB> boxes,
            Direction.Axis axis
    ) {
        var sorted = new ArrayList<>(boxes);
        sorted.sort(comparator(axis));
        var merged = new ArrayList<AABB>(sorted.size());
        for (var current : sorted) {
            if (!merged.isEmpty()) {
                var previous = merged.getLast();
                var combined = combine(previous, current, axis);
                if (combined != null) {
                    merged.set(merged.size() - 1, combined);
                    continue;
                }
            }
            merged.add(current);
        }
        return merged;
    }

    private static Comparator<AABB> comparator(Direction.Axis axis) {
        return switch (axis) {
            case X -> Comparator.comparingDouble((AABB box) -> box.minY)
                    .thenComparingDouble(box -> box.maxY)
                    .thenComparingDouble(box -> box.minZ)
                    .thenComparingDouble(box -> box.maxZ)
                    .thenComparingDouble(box -> box.minX)
                    .thenComparingDouble(box -> box.maxX);
            case Y -> Comparator.comparingDouble((AABB box) -> box.minX)
                    .thenComparingDouble(box -> box.maxX)
                    .thenComparingDouble(box -> box.minZ)
                    .thenComparingDouble(box -> box.maxZ)
                    .thenComparingDouble(box -> box.minY)
                    .thenComparingDouble(box -> box.maxY);
            case Z -> Comparator.comparingDouble((AABB box) -> box.minX)
                    .thenComparingDouble(box -> box.maxX)
                    .thenComparingDouble(box -> box.minY)
                    .thenComparingDouble(box -> box.maxY)
                    .thenComparingDouble(box -> box.minZ)
                    .thenComparingDouble(box -> box.maxZ);
        };
    }

    private static AABB combine(AABB first, AABB second, Direction.Axis axis) {
        return switch (axis) {
            case X -> same(first.minY, second.minY)
                    && same(first.maxY, second.maxY)
                    && same(first.minZ, second.minZ)
                    && same(first.maxZ, second.maxZ)
                    && second.minX <= first.maxX + MERGE_EPSILON
                    ? new AABB(
                    Math.min(first.minX, second.minX), first.minY, first.minZ,
                    Math.max(first.maxX, second.maxX), first.maxY, first.maxZ)
                    : null;
            case Y -> same(first.minX, second.minX)
                    && same(first.maxX, second.maxX)
                    && same(first.minZ, second.minZ)
                    && same(first.maxZ, second.maxZ)
                    && second.minY <= first.maxY + MERGE_EPSILON
                    ? new AABB(
                    first.minX, Math.min(first.minY, second.minY), first.minZ,
                    first.maxX, Math.max(first.maxY, second.maxY), first.maxZ)
                    : null;
            case Z -> same(first.minX, second.minX)
                    && same(first.maxX, second.maxX)
                    && same(first.minY, second.minY)
                    && same(first.maxY, second.maxY)
                    && second.minZ <= first.maxZ + MERGE_EPSILON
                    ? new AABB(
                    first.minX, first.minY, Math.min(first.minZ, second.minZ),
                    first.maxX, first.maxY, Math.max(first.maxZ, second.maxZ))
                    : null;
        };
    }

    private static boolean same(double first, double second) {
        return Math.abs(first - second) <= MERGE_EPSILON;
    }

    private record Partition(int x, int y, int z) {
        private static Partition of(BlockPos position) {
            return new Partition(
                    position.getX() >> 4,
                    position.getY() >> 4,
                    position.getZ() >> 4
            );
        }
    }

    public record BlockStructureSweepHit(double time, Vec3 point, Vec3 normal) {
    }

    private record AxisSweep(double entry, double exit, Vec3 normal) {
    }
}
