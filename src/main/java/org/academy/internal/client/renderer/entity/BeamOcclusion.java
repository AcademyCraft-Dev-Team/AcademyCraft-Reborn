package org.academy.internal.client.renderer.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class BeamOcclusion {
    private static final int MAX_BLOCKERS = 64;
    private static final double DIRECTION_EPSILON = 1.0e-8;
    private static final float SEGMENT_EPSILON = 1.0e-3f;

    private BeamOcclusion() {
    }

    static float[] visibleSegments(Entity source, Vec3 start, Vec3 direction, float length, double padding) {
        if (!Float.isFinite(length) || length <= SEGMENT_EPSILON || direction.lengthSqr() <= DIRECTION_EPSILON) {
            return new float[0];
        }

        var normalizedDirection = direction.normalize();
        var end = start.add(normalizedDirection.scale(length));
        var pathBounds = new AABB(start, end).inflate(Math.max(0.0, padding));
        var blocked = new ArrayList<Interval>();

        for (var candidate : source.level().getEntities(source, pathBounds, entity ->
                entity.isAlive() && !entity.isSpectator() && !(entity instanceof RenderOnlyEntity))) {
            var bounds = candidate.getBoundingBox().inflate(Math.max(0.0, padding));
            if (bounds.inflate(0.5).contains(start)) continue;
            var interval = intersect(start, normalizedDirection, length, bounds);
            if (interval == null) continue;
            blocked.add(interval);
            if (blocked.size() >= MAX_BLOCKERS) break;
        }

        if (blocked.isEmpty()) return new float[]{0.0f, length};
        blocked.sort(Comparator.comparingDouble(Interval::start));

        var merged = new ArrayList<Interval>();
        for (var interval : blocked) {
            if (merged.isEmpty()) {
                merged.add(interval);
                continue;
            }
            var previous = merged.getLast();
            if (interval.start() <= previous.end() + SEGMENT_EPSILON) {
                merged.set(merged.size() - 1, new Interval(previous.start(), Math.max(previous.end(), interval.end())));
            } else {
                merged.add(interval);
            }
        }

        List<Float> visible = new ArrayList<>((merged.size() + 1) * 2);
        var cursor = 0.0f;
        for (var interval : merged) {
            var startDistance = Math.clamp(interval.start(), 0.0f, length);
            var endDistance = Math.clamp(interval.end(), 0.0f, length);
            if (startDistance - cursor > SEGMENT_EPSILON) {
                visible.add(cursor);
                visible.add(startDistance);
            }
            cursor = Math.max(cursor, endDistance);
        }
        if (length - cursor > SEGMENT_EPSILON) {
            visible.add(cursor);
            visible.add(length);
        }

        var result = new float[visible.size()];
        for (var i = 0; i < visible.size(); i++) result[i] = visible.get(i);
        return result;
    }

    private static Interval intersect(Vec3 start, Vec3 direction, float length, AABB bounds) {
        var range = new double[]{0.0, length};
        if (!clipAxis(start.x, direction.x, bounds.minX, bounds.maxX, range)
                || !clipAxis(start.y, direction.y, bounds.minY, bounds.maxY, range)
                || !clipAxis(start.z, direction.z, bounds.minZ, bounds.maxZ, range)) {
            return null;
        }
        if (range[1] - range[0] <= SEGMENT_EPSILON) return null;
        return new Interval((float) range[0], (float) range[1]);
    }

    private static boolean clipAxis(double origin, double direction, double min, double max, double[] range) {
        if (Math.abs(direction) <= DIRECTION_EPSILON) return origin >= min && origin <= max;
        var first = (min - origin) / direction;
        var second = (max - origin) / direction;
        if (first > second) {
            var swap = first;
            first = second;
            second = swap;
        }
        range[0] = Math.max(range[0], first);
        range[1] = Math.min(range[1], second);
        return range[1] >= range[0];
    }

    private record Interval(float start, float end) {
    }
}
