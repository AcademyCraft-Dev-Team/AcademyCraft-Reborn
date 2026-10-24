package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Continuous contact escape for the bounded extraction servo. Every corner's projection is
 * minimized analytically over the full rigid rotation arc plus translation. A pre-existing
 * overlap may only keep or reduce its original minimum penetration throughout the step and
 * must strictly reduce it at the endpoint. New overlaps never receive this exemption.
 */
final class ExtractionContactSweep {
    // Sable's float COM mapped back to a double origin can differ by several nanometres
    // (observed 7.45E-9 blocks on an otherwise identity pose). This numerical contact band
    // never changes a collider and is far below an observable/physical block displacement.
    static final double CONTACT_ROUNDING = 1E-7;
    private ExtractionContactSweep() {}

    record Result(OrientationSweep.Status status, String detail) {}

    static Result check(OrientationBackendAccess.Body body, Vec3 displacement, double seconds, long revision,
                        int budget, ExtractionPlanner.ObstacleSnapshot obstacles) {
        return check(body, displacement, seconds, revision, budget, obstacles, Vec3.ZERO);
    }

    static Result check(OrientationBackendAccess.Body body, Vec3 displacement, double seconds, long revision,
                        int budget, ExtractionPlanner.ObstacleSnapshot obstacles, Vec3 positionRounding) {
        OrientationTargets.requireFinite(positionRounding, "positionRounding");
        if (positionRounding.x < 0 || positionRounding.y < 0 || positionRounding.z < 0) {
            throw new IllegalArgumentException("Position rounding must be nonnegative");
        }
        double angle = body.motion().angularVelocity().length() * seconds;
        if (budget < 1 || angle > Math.PI) return new Result(OrientationSweep.Status.BUDGET_EXHAUSTED, "contact_arc_budget");
        int used = 0;
        int contacts = 0;
        for (var local : body.localBoxes()) {
            var box = TranslationSweep.Box.of(local, body.motion().pose());
            var corners = corners(body, local);
            double radius = corners.stream().mapToDouble(Vec3::length).max().orElseThrow();
            var bounds = box.sweptBounds(displacement).inflate(2 * radius * Math.sin(angle / 2) + body.freeStepMargin(seconds));
            if (++used > budget) return new Result(OrientationSweep.Status.BUDGET_EXHAUSTED, "contact_query_budget");
            var query = obstacles.query(bounds, budget - used);
            if (query.terrainRevision() != revision) return new Result(OrientationSweep.Status.STALE, "contact_revision_changed");
            if (query.coverage() != ExtractionPlanner.Coverage.COMPLETE) return new Result(OrientationSweep.Status.TERRAIN_PENDING, "contact_coverage_unknown");
            if (query.truncated() || query.obstacles().size() > budget - used) return new Result(OrientationSweep.Status.BUDGET_EXHAUSTED, "contact_shape_budget");
            for (var obstacle : query.obstacles()) {
                used++;
                boolean hostContact = obstacle.id().startsWith("host:");
                var axes = box.separatingAxes(obstacle.box());
                double best = -Double.MAX_VALUE;
                Vec3 normal = Vec3.ZERO;
                for (var axis : axes) {
                    for (int sign : new int[]{1, -1}) {
                        var candidate = axis.scale(sign);
                        double gap = box.minimumProjection(candidate) - obstacle.box().maximumProjection(candidate);
                        if (gap > best) { best = gap; normal = candidate; }
                    }
                }
                double initialRounding = hostContact ? projectedRounding(normal, positionRounding) : 0;
                if (best < -CONTACT_ROUNDING - initialRounding) {
                    // Other bodies are represented by conservative future-motion envelopes.
                    // Their apparent initial overlap is not evidence of existing contact.
                    if (!hostContact) return new Result(OrientationSweep.Status.BLOCKED,
                            "moving_obstacle_envelope_not_clear obstacle=" + obstacle.id());
                    contacts++;
                    var curve = projected(body, corners, normal, displacement, seconds);
                    double before = box.minimumProjection(normal);
                    if (curve.minimum < before - 1E-10 || curve.endpoint <= before + 1E-10) {
                        return new Result(OrientationSweep.Status.BLOCKED, "initial_contact_no_escape gap=" + best
                                + " minimumDelta=" + (curve.minimum - before) + " endpointDelta=" + (curve.endpoint - before)
                                + " normal=" + normal + " obstacle=" + obstacle.id());
                    }
                } else {
                    boolean separated = false;
                    for (var axis : axes) {
                        for (int sign : new int[]{1, -1}) {
                            var candidate = axis.scale(sign);
                            double before = box.minimumProjection(candidate);
                            double wall = obstacle.box().maximumProjection(candidate);
                            double rounding = hostContact ? projectedRounding(candidate, positionRounding) : 0;
                            double tolerance = CONTACT_ROUNDING + rounding;
                            if (before < wall - tolerance) continue;
                            var curve = projected(body, corners, candidate, displacement, seconds);
                            // A pre-existing quantized offset may remain tangent or improve, but
                            // its uncertainty must never pay for motion further into the wall.
                            if (rounding > 0 && curve.minimum < Math.min(before, wall) - 1E-10) continue;
                            if (curve.minimum >= wall - tolerance) {
                                separated = true;
                                break;
                            }
                        }
                        if (separated) break;
                    }
                    if (!separated) return new Result(OrientationSweep.Status.BLOCKED, "new_contact_not_proven_clear obstacle=" + obstacle.id());
                }
            }
        }
        return new Result(OrientationSweep.Status.CLEAR, "contact_escape_proven:" + contacts);
    }

    private static double projectedRounding(Vec3 normal, Vec3 bounds) {
        return Math.abs(normal.x) * bounds.x + Math.abs(normal.y) * bounds.y + Math.abs(normal.z) * bounds.z;
    }

    private static List<Vec3> corners(OrientationBackendAccess.Body body, AABB box) {
        var result = new ArrayList<Vec3>(8);
        for (double x : new double[]{box.minX, box.maxX}) for (double y : new double[]{box.minY, box.maxY}) {
            for (double z : new double[]{box.minZ, box.maxZ}) result.add(body.motion().pose().localToWorldDirection(
                    new Vec3(x, y, z).subtract(body.motion().localCenterOfMass())));
        }
        return result;
    }

    private record Projection(double minimum, double endpoint) {}

    private static Projection projected(OrientationBackendAccess.Body body, List<Vec3> corners, Vec3 normal,
                                        Vec3 displacement, double seconds) {
        var omega = body.motion().angularVelocity();
        double speed = omega.length();
        var axis = speed == 0 ? Vec3.ZERO : omega.scale(1 / speed);
        double center = body.motion().pose().localToWorldPoint(body.motion().localCenterOfMass()).dot(normal);
        // Lower bound both velocity-first and continuous/subdivided gravity paths. The known
        // free-motion discrepancy projects only against gravity; upward escape is not inflated down.
        double rate = displacement.dot(normal) / seconds - Math.max(0, body.freeAcceleration().dot(normal)) * seconds;
        if (speed == 0) {
            double start = center + corners.stream().mapToDouble(corner -> corner.dot(normal)).min().orElseThrow();
            return new Projection(Math.min(start, start + rate * seconds), start + rate * seconds);
        }
        double minimum = Double.POSITIVE_INFINITY, endpoint = Double.POSITIVE_INFINITY;
        for (var corner : corners) {
            var parallel = axis.scale(corner.dot(axis));
            double constant = center + parallel.dot(normal);
            double cosine = corner.subtract(parallel).dot(normal);
            double sine = axis.cross(corner).dot(normal);
            double end = curve(constant, rate, cosine, sine, speed, seconds);
            double min = minimumCurve(constant, rate, cosine, sine, speed, seconds);
            minimum = Math.min(minimum, min);
            endpoint = Math.min(endpoint, end);
        }
        return new Projection(minimum, endpoint);
    }

    /** Exact minimum of c + v*t + a*cos(w*t) + b*sin(w*t), for a bounded half-turn. */
    static double minimumCurve(double constant, double rate, double cosine, double sine, double speed, double duration) {
        double min = Math.min(curve(constant, rate, cosine, sine, speed, 0), curve(constant, rate, cosine, sine, speed, duration));
        double amplitude = Math.hypot(cosine, sine);
        if (speed == 0 || amplitude == 0) return min;
        double ratio = -rate / (speed * amplitude);
        if (Math.abs(ratio) > 1) return min;
        double phase = Math.atan2(cosine, sine);
        double root = Math.acos(ratio);
        for (int sign : new int[]{-1, 1}) for (int turn = -2; turn <= 2; turn++) {
            double time = (sign * root - phase + turn * 2 * Math.PI) / speed;
            if (time > 0 && time < duration) min = Math.min(min, curve(constant, rate, cosine, sine, speed, time));
        }
        return min;
    }

    private static double curve(double constant, double rate, double cosine, double sine, double speed, double time) {
        return constant + rate * time + cosine * Math.cos(speed * time) + sine * Math.sin(speed * time);
    }
}
