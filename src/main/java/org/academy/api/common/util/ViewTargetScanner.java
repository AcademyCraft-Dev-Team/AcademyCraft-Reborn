package org.academy.api.common.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Predicate;

/**
 * Performs broad-phase entity queries followed by precise, view-oriented shape tests.
 * Target eligibility remains caller-owned so skills can supply their own PvP, team,
 * visibility, and entity-type rules without coupling those policies to geometry.
 */
public final class ViewTargetScanner {
    private static final double MIN_DIRECTION_LENGTH_SQUARED = 1.0e-8;
    private static final double PARALLEL_EPSILON = 1.0e-9;

    private ViewTargetScanner() {
    }

    /** Tests one target AABB against a shape without querying a level. */
    public static boolean matches(
            Vec3 origin,
            Vec3 viewDirection,
            double range,
            Shape shape,
            AABB targetBounds
    ) {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(targetBounds, "targetBounds");
        var direction = normalizedDirection(origin, viewDirection, range);
        return direction != null && validMatchDistance(
                shape.matchDistance(origin, direction, range, targetBounds));
    }

    /**
     * Returns the nearest matching entity in {@code shape}, or {@code null} when the
     * view parameters are invalid or no entity matches.
     */
    public static <T extends Entity> @Nullable T findFirst(
            Level level,
            Class<T> entityType,
            Vec3 origin,
            Vec3 viewDirection,
            double range,
            Shape shape,
            Predicate<? super T> filter
    ) {
        return findFirst(level, null, entityType, origin, viewDirection, range, shape, filter);
    }

    /**
     * Returns the nearest match while excluding one entity from the broad-phase query.
     */
    public static <T extends Entity> @Nullable T findFirst(
            Level level,
            @Nullable Entity excludedEntity,
            Class<T> entityType,
            Vec3 origin,
            Vec3 viewDirection,
            double range,
            Shape shape,
            Predicate<? super T> filter
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(filter, "filter");
        var direction = normalizedDirection(origin, viewDirection, range);
        if (direction == null) return null;

        T nearest = null;
        var nearestDistance = Double.POSITIVE_INFINITY;
        var bounds = shape.searchBounds(origin, direction, range);
        for (var candidate : candidates(level, excludedEntity, entityType, bounds)) {
            var distance = shape.matchDistance(
                    origin, direction, range, candidate.getBoundingBox());
            if (!validMatchDistance(distance)
                    || distance >= nearestDistance
                    || !filter.test(candidate)) {
                continue;
            }
            nearestDistance = distance;
            nearest = candidate;
        }
        return nearest;
    }

    /**
     * Returns every matching entity in the level query's natural order.
     */
    public static <T extends Entity> List<T> scan(
            Level level,
            Class<T> entityType,
            Vec3 origin,
            Vec3 viewDirection,
            double range,
            Shape shape,
            Predicate<? super T> filter
    ) {
        return scan(level, null, entityType, origin, viewDirection, range, shape, filter);
    }

    /** Returns every match while excluding one entity from the broad-phase query. */
    public static <T extends Entity> List<T> scan(
            Level level,
            @Nullable Entity excludedEntity,
            Class<T> entityType,
            Vec3 origin,
            Vec3 viewDirection,
            double range,
            Shape shape,
            Predicate<? super T> filter
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(filter, "filter");
        var direction = normalizedDirection(origin, viewDirection, range);
        if (direction == null) return List.of();

        var results = new ArrayList<T>();
        var bounds = shape.searchBounds(origin, direction, range);
        for (var candidate : candidates(level, excludedEntity, entityType, bounds)) {
            var distance = shape.matchDistance(
                    origin, direction, range, candidate.getBoundingBox());
            if (validMatchDistance(distance) && filter.test(candidate)) {
                results.add(candidate);
            }
        }
        return List.copyOf(results);
    }

    /**
     * Returns every matching entity ordered by the shape's match-distance metric.
     */
    public static <T extends Entity> List<T> scanOrdered(
            Level level,
            Class<T> entityType,
            Vec3 origin,
            Vec3 viewDirection,
            double range,
            Shape shape,
            Predicate<? super T> filter
    ) {
        return scanOrdered(level, null, entityType, origin, viewDirection, range, shape, filter);
    }

    /** Returns ordered matches while excluding one entity from the broad-phase query. */
    public static <T extends Entity> List<T> scanOrdered(
            Level level,
            @Nullable Entity excludedEntity,
            Class<T> entityType,
            Vec3 origin,
            Vec3 viewDirection,
            double range,
            Shape shape,
            Predicate<? super T> filter
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(filter, "filter");
        var direction = normalizedDirection(origin, viewDirection, range);
        if (direction == null) return List.of();

        var results = new ArrayList<ScoredTarget<T>>();
        var bounds = shape.searchBounds(origin, direction, range);
        for (var candidate : candidates(level, excludedEntity, entityType, bounds)) {
            var distance = shape.matchDistance(
                    origin, direction, range, candidate.getBoundingBox());
            if (validMatchDistance(distance) && filter.test(candidate)) {
                results.add(new ScoredTarget<>(candidate, distance));
            }
        }
        results.sort(Comparator.comparingDouble((ScoredTarget<T> result) -> result.distance));
        return results.stream().map(ScoredTarget::entity).toList();
    }

    /**
     * Creates the widened view-ray shape used by crosshair-based ability targeting.
     */
    public static Shape widenedRay(
            double searchHalfWidth,
            double searchHalfHeight,
            double targetBoundsInflation,
            double hitRadius
    ) {
        return new WidenedRay(
                searchHalfWidth, searchHalfHeight, targetBoundsInflation, hitRadius);
    }

    /** Creates a three-dimensional cone using target bounding-box centers. */
    public static Shape cone(double maximumDistance, double minimumDot) {
        return new Cone(maximumDistance, minimumDot, false);
    }

    /** Creates a cone whose angular test ignores vertical displacement. */
    public static Shape horizontalCone(double maximumDistance, double minimumDot) {
        return new Cone(maximumDistance, minimumDot, true);
    }

    /** Creates a cylinder that tests target centers and includes both end caps. */
    public static Shape centeredCylinder(double radius) {
        return new CenteredCylinder(radius, true);
    }

    /** Creates a cylinder that tests target centers and excludes both end caps. */
    public static Shape openCenteredCylinder(double radius) {
        return new CenteredCylinder(radius, false);
    }

    /** Creates a segment that intersects target AABBs after inflating them by {@code radius}. */
    public static Shape inflatedAabbSegment(double radius) {
        return new InflatedAabbSegment(radius, true, true);
    }

    /** Creates an inflated-AABB segment that rejects boxes containing its origin. */
    public static Shape inflatedAabbSegmentExcludingOrigin(double radius) {
        return new InflatedAabbSegment(radius, false, true);
    }

    /** Creates an inflated-AABB segment that excludes both endpoint intersections. */
    public static Shape openInflatedAabbSegment(double radius) {
        return new InflatedAabbSegment(radius, false, false);
    }

    /** Creates a shape matching candidates accepted by any supplied child shape. */
    public static Shape union(Shape first, Shape... remaining) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(remaining, "remaining");
        var shapes = new ArrayList<Shape>(remaining.length + 1);
        shapes.add(first);
        for (var shape : remaining) shapes.add(Objects.requireNonNull(shape, "shape"));
        return new Union(List.copyOf(shapes));
    }

    /**
     * A view-oriented region. Implementations provide a world-space AABB for the
     * broad-phase query and return a non-negative ordering metric for precise matches.
     * Returning a non-finite or negative value rejects the candidate.
     */
    public interface Shape {
        AABB searchBounds(Vec3 origin, Vec3 normalizedDirection, double range);

        double matchDistance(
                Vec3 origin,
                Vec3 normalizedDirection,
                double range,
                AABB targetBounds
        );
    }

    /**
     * A line-shaped view region with independently configurable query padding,
     * target-box inflation, and final hit tolerance.
     */
    public record WidenedRay(
            double searchHalfWidth,
            double searchHalfHeight,
            double targetBoundsInflation,
            double hitRadius
    ) implements Shape {
        public WidenedRay {
            requireNonNegativeFinite(searchHalfWidth, "searchHalfWidth");
            requireNonNegativeFinite(searchHalfHeight, "searchHalfHeight");
            requireNonNegativeFinite(targetBoundsInflation, "targetBoundsInflation");
            requireNonNegativeFinite(hitRadius, "hitRadius");
        }

        @Override
        public AABB searchBounds(Vec3 origin, Vec3 normalizedDirection, double range) {
            var end = origin.add(normalizedDirection.scale(range));
            return new AABB(origin, end)
                    .inflate(searchHalfWidth, searchHalfHeight, searchHalfWidth);
        }

        @Override
        public double matchDistance(
                Vec3 origin,
                Vec3 normalizedDirection,
                double range,
                AABB targetBounds
        ) {
            var expandedBounds = targetBounds.inflate(targetBoundsInflation);
            var projection = expandedBounds.getCenter().subtract(origin).dot(normalizedDirection);
            if (projection < 0.0 || projection > range) return Double.POSITIVE_INFINITY;
            var closestPoint = origin.add(normalizedDirection.scale(projection));
            return distanceToBoxSqr(closestPoint, expandedBounds) <= hitRadius * hitRadius
                    ? projection
                    : Double.POSITIVE_INFINITY;
        }
    }

    /** A spherical-sector cone evaluated against the center of each target AABB. */
    public record Cone(
            double maximumDistance,
            double minimumDot,
            boolean horizontal
    ) implements Shape {
        public Cone {
            requireNonNegativeFinite(maximumDistance, "maximumDistance");
            if (!Double.isFinite(minimumDot) || minimumDot < -1.0 || minimumDot > 1.0) {
                throw new IllegalArgumentException("minimumDot must be finite and within [-1, 1]");
            }
        }

        @Override
        public AABB searchBounds(Vec3 origin, Vec3 normalizedDirection, double range) {
            return new AABB(origin, origin).inflate(maximumDistance);
        }

        @Override
        public double matchDistance(
                Vec3 origin,
                Vec3 normalizedDirection,
                double range,
                AABB targetBounds
        ) {
            var delta = targetBounds.getCenter().subtract(origin);
            var distanceSqr = delta.lengthSqr();
            if (distanceSqr > maximumDistance * maximumDistance) {
                return Double.POSITIVE_INFINITY;
            }
            if (distanceSqr <= MIN_DIRECTION_LENGTH_SQUARED) return 0.0;
            var angularDirection = normalizedDirection;
            var angularDelta = delta;
            if (horizontal) {
                angularDirection = new Vec3(normalizedDirection.x, 0.0, normalizedDirection.z);
                angularDelta = new Vec3(delta.x, 0.0, delta.z);
                if (angularDirection.lengthSqr() < MIN_DIRECTION_LENGTH_SQUARED
                        || angularDelta.lengthSqr() < MIN_DIRECTION_LENGTH_SQUARED) {
                    return Double.POSITIVE_INFINITY;
                }
                angularDirection = angularDirection.normalize();
            }
            return angularDirection.dot(angularDelta.normalize()) >= minimumDot
                    ? Math.sqrt(distanceSqr)
                    : Double.POSITIVE_INFINITY;
        }
    }

    /** A constant-radius segment evaluated against the center of each target AABB. */
    public record CenteredCylinder(double radius, boolean includeEndCaps) implements Shape {
        public CenteredCylinder {
            requireNonNegativeFinite(radius, "radius");
        }

        @Override
        public AABB searchBounds(Vec3 origin, Vec3 normalizedDirection, double range) {
            return new AABB(origin, origin.add(normalizedDirection.scale(range))).inflate(radius);
        }

        @Override
        public double matchDistance(
                Vec3 origin,
                Vec3 normalizedDirection,
                double range,
                AABB targetBounds
        ) {
            var center = targetBounds.getCenter();
            var projection = center.subtract(origin).dot(normalizedDirection);
            if (includeEndCaps) {
                projection = Math.clamp(projection, 0.0, range);
            } else if (projection <= 0.0 || projection >= range) {
                return Double.POSITIVE_INFINITY;
            }
            var closest = origin.add(normalizedDirection.scale(projection));
            return center.distanceToSqr(closest) <= radius * radius
                    ? center.distanceToSqr(origin)
                    : Double.POSITIVE_INFINITY;
        }
    }

    /** A segment evaluated against each target AABB expanded by a constant radius. */
    public record InflatedAabbSegment(
            double radius,
            boolean includeOrigin,
            boolean includeEnd
    ) implements Shape {
        public InflatedAabbSegment {
            requireNonNegativeFinite(radius, "radius");
        }

        @Override
        public AABB searchBounds(Vec3 origin, Vec3 normalizedDirection, double range) {
            return new AABB(origin, origin.add(normalizedDirection.scale(range))).inflate(radius);
        }

        @Override
        public double matchDistance(
                Vec3 origin,
                Vec3 normalizedDirection,
                double range,
                AABB targetBounds
        ) {
            var end = origin.add(normalizedDirection.scale(range));
            var expandedBounds = targetBounds.inflate(radius);
            if (!(range > 1.0e-12)) {
                return includeOrigin && expandedBounds.contains(origin)
                        ? 0.0
                        : Double.POSITIVE_INFINITY;
            }
            if (expandedBounds.contains(origin)) {
                return includeOrigin ? 0.0 : Double.POSITIVE_INFINITY;
            }
            var progress = intersectionProgress(origin, end, expandedBounds);
            if (progress.isEmpty() || !includeEnd && progress.getAsDouble() >= 1.0) {
                return Double.POSITIVE_INFINITY;
            }
            return progress.getAsDouble() * range;
        }
    }

    /** A composite shape using the union of its child regions. */
    public record Union(List<Shape> shapes) implements Shape {
        public Union {
            shapes = List.copyOf(shapes);
            if (shapes.isEmpty()) throw new IllegalArgumentException("shapes cannot be empty");
        }

        @Override
        public AABB searchBounds(Vec3 origin, Vec3 normalizedDirection, double range) {
            var bounds = shapes.getFirst().searchBounds(origin, normalizedDirection, range);
            for (var index = 1; index < shapes.size(); index++) {
                bounds = bounds.minmax(shapes.get(index).searchBounds(
                        origin, normalizedDirection, range));
            }
            return bounds;
        }

        @Override
        public double matchDistance(
                Vec3 origin,
                Vec3 normalizedDirection,
                double range,
                AABB targetBounds
        ) {
            var nearest = Double.POSITIVE_INFINITY;
            for (var shape : shapes) {
                nearest = Math.min(nearest, shape.matchDistance(
                        origin, normalizedDirection, range, targetBounds));
            }
            return nearest;
        }
    }

    static double distanceToBoxSqr(Vec3 point, AABB box) {
        var dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        var dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        var dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /** Returns the squared distance from a point to its nearest point on a segment. */
    public static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        var segment = end.subtract(start);
        var lengthSqr = segment.lengthSqr();
        if (!(lengthSqr > 1.0e-12)) return point.distanceToSqr(start);
        var progress = Math.clamp(point.subtract(start).dot(segment) / lengthSqr, 0.0, 1.0);
        return point.distanceToSqr(start.add(segment.scale(progress)));
    }

    /** Returns the normalized entry progress of a segment through an AABB. */
    public static OptionalDouble intersectionProgress(Vec3 start, Vec3 end, AABB bounds) {
        if (!finite(start) || !finite(end) || bounds == null) return OptionalDouble.empty();
        var direction = end.subtract(start);
        var lengthSqr = direction.lengthSqr();
        if (!(lengthSqr > 1.0e-12) || !Double.isFinite(lengthSqr)) {
            return OptionalDouble.empty();
        }

        var interval = new double[]{0.0, 1.0};
        if (!clipAxis(start.x, direction.x, bounds.minX, bounds.maxX, interval)
                || !clipAxis(start.y, direction.y, bounds.minY, bounds.maxY, interval)
                || !clipAxis(start.z, direction.z, bounds.minZ, bounds.maxZ, interval)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(interval[0]);
    }

    private static boolean validMatchDistance(double distance) {
        return Double.isFinite(distance) && distance >= 0.0;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> List<T> candidates(
            Level level,
            @Nullable Entity excludedEntity,
            Class<T> entityType,
            AABB bounds
    ) {
        if (excludedEntity == null) return level.getEntitiesOfClass(entityType, bounds);
        return (List<T>) (List<?>) level.getEntities(
                excludedEntity, bounds, entityType::isInstance);
    }

    private static @Nullable Vec3 normalizedDirection(
            Vec3 origin,
            Vec3 viewDirection,
            double range
    ) {
        if (!finite(origin)
                || !finite(viewDirection)
                || !Double.isFinite(range)
                || range < 0.0
                || viewDirection.lengthSqr() < MIN_DIRECTION_LENGTH_SQUARED) {
            return null;
        }
        return viewDirection.normalize();
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static boolean clipAxis(
            double start,
            double direction,
            double minimum,
            double maximum,
            double[] interval
    ) {
        if (Math.abs(direction) < PARALLEL_EPSILON) {
            return start >= minimum && start <= maximum;
        }
        var inverse = 1.0 / direction;
        var near = (minimum - start) * inverse;
        var far = (maximum - start) * inverse;
        if (near > far) {
            var swap = near;
            near = far;
            far = swap;
        }
        interval[0] = Math.max(interval[0], near);
        interval[1] = Math.min(interval[1], far);
        return interval[0] <= interval[1];
    }

    private record ScoredTarget<T extends Entity>(T entity, double distance) {
    }
}
