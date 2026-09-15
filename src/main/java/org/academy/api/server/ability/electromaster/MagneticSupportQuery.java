package org.academy.api.server.ability.electromaster;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Collision-shape support from any direction, without loading chunks. Keep one cache per mover.
 *
 * <p>Two different questions are answered here. {@link #nearest} returns the closest collision surface in
 * any direction and drives admission plus boundary clamping, so ground, walls, ceilings and free-standing
 * blocks all qualify. {@link #groundBelow} returns only the upward-facing surface under the subject's
 * footprint and is the sole input to terrain-following height, so a sideways or overhead neighbour can
 * never be mistaken for the floor. {@link #supported} remains the plain any-direction admission test.</p>
 *
 * <p>Every refresh reuses the previous reference while it still qualifies, re-reading that single block
 * instead of rescanning, and a stale reference triggers a rescan rather than a one-tick false "no
 * support". A full refresh marches a fixed set of rays out of the subject's centre instead of sweeping the
 * enclosing cube: cost is linear in the radius rather than cubic, so it stays bounded at every proficiency
 * tier instead of peaking exactly when support is absent — the case that matters most, because losing the
 * field is what makes a mover fall.</p>
 */
public final class MagneticSupportQuery {
    /**
     * Total collision-shape reads allowed per refresh. The default covers the largest configured radius
     * with headroom (125 near-field + 26 rays x 33 samples = 983) so the outer edge of the range stays
     * honest; a smaller budget would silently report "no support" while support was still in range.
     */
    public static final int DEFAULT_MAX_SAMPLES = 1152;
    /** Extra horizontal reach when looking for ground under the subject. */
    private static final double GROUND_FOOTPRINT_TOLERANCE = 0.5;
    /** Ground may sit this far above the feet, which allows the top of a slab or stair to register. */
    private static final double GROUND_SURFACE_TOLERANCE = 0.55;
    /** Spacing of ray samples; fine enough that no unit-wide block is stepped over. */
    private static final double PROBE_STEP = 0.5;
    /** Blocks either side of the subject scanned exhaustively, so contact is never missed to an angle gap. */
    private static final int NEAR_FIELD = 2;
    private static final double MAX_RADIUS = 64.0;
    /** The 26 neighbouring directions, normalised, so coverage does not favour one axis. */
    private static final double[][] PROBE_DIRECTIONS = probeDirections();

    private final int maxSamples;
    private @Nullable SupportReference envelope;

    public MagneticSupportQuery() {
        this(DEFAULT_MAX_SAMPLES);
    }

    public MagneticSupportQuery(int maxSamples) {
        this.maxSamples = Math.max(16, maxSamples);
    }

    /** Any-direction support test: the mover has some collision surface within {@code radius}. */
    public boolean supported(ServerLevel level, AABB bounds, double radius) {
        return nearest(level, bounds, radius) != null;
    }

    /** Closest collision surface in any direction, reusing the previous reference while it still qualifies. */
    public @Nullable SupportReference nearest(ServerLevel level, AABB bounds, double radius) {
        if (!usable(radius)) return null;
        // A cheap six-face re-read of the remembered block usually still answers the question; only when the
        // world changed under it does the full probe run. The face may legitimately differ as the mover
        // rounds a corner, so distance alone decides here.
        if (envelope != null) envelope = reference(level, bounds, envelope.pos(), null, radius);
        // A stale reference means the world changed, not that support is gone, so rescan before reporting none.
        if (envelope == null) envelope = probe(level, bounds, radius);
        return envelope;
    }

    /**
     * Stateless probe for a speculative position, e.g. where the mover is about to be.
     *
     * <p>Deliberately does not touch the cache: boundary checks must ask "is there still any support
     * here", because re-testing one remembered block would measure distance from that block instead and
     * clamp once the mover simply travelled away from it, freezing tangential motion.</p>
     */
    public static @Nullable SupportReference probeAt(ServerLevel level, AABB bounds, double radius,
                                                     int maxSamples) {
        if (!usable(radius)) return null;
        return probe(level, bounds, radius, Math.max(16, maxSamples));
    }

    /**
     * Closest upward-facing surface below the subject, or null when it floats over a drop.
     *
     * <p>Always probed afresh rather than cached: the surface underfoot is the height reference, and a
     * stale lower one would read as extra clearance and let the solver sink the mover into ground that has
     * since risen underneath it. The probe stops at the first layer holding support, so it stays cheap.</p>
     */
    public @Nullable SupportReference groundBelow(ServerLevel level, AABB bounds, double radius) {
        if (!usable(radius)) return null;
        return searchGround(level, bounds, radius);
    }

    /** Drops the cached envelope reference, forcing a full probe on the next refresh. */
    public void invalidate() {
        envelope = null;
    }

    /**
     * Replaces the cached envelope reference with a freshly probed one.
     *
     * <p>Boundary checks probe the destination; adopting that result keeps admission and clamping judging
     * the field by the same reference, instead of admission trusting a remembered block that a later probe
     * cannot rediscover.</p>
     */
    public void adopt(SupportReference reference) {
        envelope = reference;
    }

    /**
     * Cheap in-field test for a known reference: is that same surface still within {@code radius}?
     *
     * <p>One block read instead of a probe, and deliberately the same evidence the cached admission path
     * uses — mixing a remembered reference with a from-scratch fan probe is what let admission report
     * "supported" while boundary checks reported "nothing nearby", truncating velocity every tick.</p>
     */
    public static boolean holds(ServerLevel level, AABB bounds, @Nullable SupportReference reference,
                                double radius) {
        if (reference == null || !usable(radius)) return false;
        var measured = reference(level, bounds, reference.pos(), null, radius);
        // The face may legitimately change as the mover rounds a corner, so distance alone decides.
        return measured != null;
    }

    private static boolean usable(double radius) {
        return Double.isFinite(radius) && radius > 0 && radius <= MAX_RADIUS;
    }

    /**
     * Resolves the nearest surface in two stages: an exact scan of the blocks immediately around the
     * subject, then the ray fan for the rest of the radius.
     *
     * <p>The near field is scanned exhaustively because that is where contact actually happens, so a wall,
     * floor or lone block right next to the subject is always found no matter which direction it sits in.
     * Beyond it the fan's angular resolution thins out with distance, which is the deliberate trade for
     * keeping cost linear: continuous terrain — the real use case — is always covered, while a lone block
     * drifting at the very edge of a large field may be missed. Missing an edge-case anchor is a far better
     * failure than the unbounded scan that made the field feel unreliable to begin with.</p>
     */
    private @Nullable SupportReference probe(ServerLevel level, AABB bounds, double radius) {
        return probe(level, bounds, radius, maxSamples);
    }

    private static @Nullable SupportReference probe(ServerLevel level, AABB bounds, double radius,
                                                    int maxSamples) {
        var samples = new int[]{0};
        var best = scanNearField(level, bounds, radius, samples, maxSamples);
        return probeRays(level, bounds, radius, samples, best, maxSamples);
    }

    /** Exact scan of the blocks touching the subject, where angular gaps would be most noticeable. */
    private static @Nullable SupportReference scanNearField(ServerLevel level, AABB bounds, double radius,
                                                            int[] samples, int maxSamples) {
        var center = BlockPos.containing(bounds.getCenter());
        SupportReference best = null;
        var pos = new BlockPos.MutableBlockPos();
        for (var x = -NEAR_FIELD; x <= NEAR_FIELD; x++) {
            for (var y = -NEAR_FIELD; y <= NEAR_FIELD; y++) {
                for (var z = -NEAR_FIELD; z <= NEAR_FIELD; z++) {
                    if (++samples[0] > maxSamples) return best;
                    best = closer(best, reference(level, bounds,
                            pos.setWithOffset(center, x, y, z), null, radius));
                }
            }
        }
        return best;
    }

    /**
     * Marches a fixed set of rays out of the subject's centre and keeps the nearest surface found.
     *
     * <p>Linear in the radius instead of cubic, which is what keeps a full refresh affordable at the
     * largest proficiency tier. Each ray stops at its first hit, so the first surface in each direction is
     * already the nearest one along it.</p>
     */
    private static @Nullable SupportReference probeRays(ServerLevel level, AABB bounds, double radius,
                                                        int[] samples, @Nullable SupportReference existing,
                                                        int maxSamples) {
        var origin = bounds.getCenter();
        var best = existing;
        var pos = new BlockPos.MutableBlockPos();
        for (var direction : PROBE_DIRECTIONS) {
            var previous = Long.MIN_VALUE;
            for (var distance = 0.0; distance <= radius; distance += PROBE_STEP) {
                var x = Mth.floor(origin.x + direction[0] * distance);
                var y = Mth.floor(origin.y + direction[1] * distance);
                var z = Mth.floor(origin.z + direction[2] * distance);
                var key = BlockPos.asLong(x, y, z);
                if (key == previous) continue;
                previous = key;
                if (++samples[0] > maxSamples) return best;
                var candidate = reference(level, bounds, pos.set(x, y, z), null, radius);
                if (candidate == null) continue;
                best = closer(best, candidate);
                // First surface along this ray is the nearest one, so the rest of it cannot improve.
                break;
            }
        }
        return best;
    }

    /**
     * Column probe downwards from the feet. The first layer holding any upward-facing surface is the
     * highest one under the footprint, which is what a subject would come to rest on.
     */
    private @Nullable SupportReference searchGround(ServerLevel level, AABB bounds, double radius) {
        var feetY = bounds.minY;
        var top = Mth.floor(feetY + GROUND_SURFACE_TOLERANCE);
        var bottom = Mth.floor(feetY - radius);
        var minX = Mth.floor(bounds.minX - GROUND_FOOTPRINT_TOLERANCE);
        var maxX = Mth.floor(bounds.maxX + GROUND_FOOTPRINT_TOLERANCE);
        var minZ = Mth.floor(bounds.minZ - GROUND_FOOTPRINT_TOLERANCE);
        var maxZ = Mth.floor(bounds.maxZ + GROUND_FOOTPRINT_TOLERANCE);
        var samples = 0;
        var pos = new BlockPos.MutableBlockPos();
        for (var y = top; y >= bottom; y--) {
            SupportReference best = null;
            for (var x = minX; x <= maxX; x++) {
                for (var z = minZ; z <= maxZ; z++) {
                    if (++samples > maxSamples) return best;
                    best = closer(best, reference(level, bounds, pos.set(x, y, z), Direction.UP, radius));
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static double[][] probeDirections() {
        var directions = new java.util.ArrayList<double[]>(26);
        for (var x = -1; x <= 1; x++) {
            for (var y = -1; y <= 1; y++) {
                for (var z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    var length = Math.sqrt(x * x + y * y + z * z);
                    directions.add(new double[]{x / length, y / length, z / length});
                }
            }
        }
        return directions.toArray(double[][]::new);
    }

    private static @Nullable SupportReference closer(@Nullable SupportReference best,
                                                     @Nullable SupportReference candidate) {
        if (candidate == null) return best;
        return best == null || candidate.distance() < best.distance() ? candidate : best;
    }

    private static @Nullable SupportReference reference(ServerLevel level, AABB bounds, BlockPos pos,
                                                        @Nullable Direction requiredFace, double limit) {
        if (!level.hasChunkAt(pos)) return null;
        var shape = level.getBlockState(pos).getCollisionShape(level, pos);
        if (shape.isEmpty()) return null;
        var limitSquared = limit * limit;
        SupportReference best = null;
        for (var box : shape.toAabbs()) {
            var moved = box.move(pos);
            var gap = distanceSquared(bounds, moved);
            if (gap > limitSquared) continue;
            var closest = closestPoint(bounds, moved);
            var face = faceOf(bounds, closest);
            if (requiredFace != null && face != requiredFace) continue;
            var candidate = new SupportReference(pos.immutable(), closest, face, Math.sqrt(gap));
            best = closer(best, candidate);
        }
        return best;
    }

    /** Closest point of {@code box} to {@code bounds}, clamping into the overlap on each axis. */
    private static Vec3 closestPoint(AABB bounds, AABB box) {
        return new Vec3(
                clampAxis(bounds.minX, bounds.maxX, box.minX, box.maxX),
                clampAxis(bounds.minY, bounds.maxY, box.minY, box.maxY),
                clampAxis(bounds.minZ, bounds.maxZ, box.minZ, box.maxZ));
    }

    private static double clampAxis(double min, double max, double boxMin, double boxMax) {
        if (max < boxMin) return boxMin;
        if (min > boxMax) return boxMax;
        return Mth.clamp(0.5 * (min + max), boxMin, boxMax);
    }

    /** Which side of the support the subject sits on: ground below reports UP, a ceiling above DOWN. */
    private static Direction faceOf(AABB bounds, Vec3 closest) {
        var center = bounds.getCenter();
        var dx = center.x - closest.x;
        var dy = center.y - closest.y;
        var dz = center.z - closest.z;
        var ax = Math.abs(dx);
        var ay = Math.abs(dy);
        var az = Math.abs(dz);
        if (ay >= ax && ay >= az) return dy >= 0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    public static double distanceSquared(AABB first, AABB second) {
        var x = Math.max(0, Math.max(first.minX - second.maxX, second.minX - first.maxX));
        var y = Math.max(0, Math.max(first.minY - second.maxY, second.minY - first.maxY));
        var z = Math.max(0, Math.max(first.minZ - second.maxZ, second.minZ - first.maxZ));
        return x * x + y * y + z * z;
    }
}
