package org.academy.internal.common.structure.space;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.upgrade.control.OrientationBackendAccess;
import org.academy.internal.common.ability.program.ProgramQueryProfiling;

import static org.academy.internal.common.ability.program.ProgramQueryProfiling.Counter.*;
import static org.academy.internal.common.ability.program.ProgramQueryProfiling.Zone.*;

import java.util.*;

/** Reuses the native/Sable loaded collision observations without acquiring control or loading plots. */
public final class LoadedStructureProximity {
    public static final int MAX_CANDIDATES = 256;
    public static final Comparator<StructureProximity> ORDER = Comparator.comparingDouble(StructureProximity::distance)
            .thenComparing(value -> value.reference().backend().toString())
            .thenComparing(value -> value.reference().id().toString());

    private LoadedStructureProximity() {}

    public static List<StructureProximity> nearby(ServerLevel host, Vec3 center, double radius) {
        try (var profile = ProgramQueryProfiling.begin(NEARBY)) {
            requireQuery(host, center);
            if (!Double.isFinite(radius) || radius < 0 || radius > StructureQueries.MAX_RAY_LENGTH) {
                throw new IllegalArgumentException("Structure query radius must be between 0 and 256");
            }
            // AABB.intersects uses strict inequalities; retain exact boundary contacts and radius zero.
            var area = new AABB(center, center).inflate(radius + 1E-7);
            var candidates = new LinkedHashMap<StructureRef, StructureProximity>();
            int remaining = MAX_CANDIDATES;
            for (var backend : StructureBackendRouter.orientationBackends(host)) {
                profile.count(BACKENDS, 1);
                var observed = backend.nearby(host, area, remaining);
                profile.count(BODIES, observed.bodies().size());
                if (!observed.complete()) {
                    profile.count(COVERAGE_UNAVAILABLE, 1);
                    throw new IllegalStateException("structure_query_coverage_unavailable");
                }
                remaining -= observed.bodies().size();
                for (var body : observed.bodies()) {
                    var proximity = measure(body, center);
                    if (proximity.distance() <= radius) candidates.put(body.reference(), proximity);
                }
            }
            var result = candidates.values().stream().sorted(ORDER).toList();
            profile.count(RESULTS, result.size());
            return result;
        }
    }

    public static Optional<StructureProximity> proximity(ServerLevel host, StructureRef reference, Vec3 point) {
        try (var profile = ProgramQueryProfiling.begin(PROXIMITY)) {
            requireQuery(host, point);
            if (!host.dimension().identifier().equals(reference.dimension())) return Optional.empty();
            var result = StructureBackendRouter.orientationAccess(host, reference)
                    .flatMap(backend -> backend.observe(host, reference)).map(body -> measure(body, point));
            profile.count(result.isPresent() ? RESULTS : UNAVAILABLE, 1);
            return result;
        }
    }

    /** Distance to the union of rotated collision boxes, not the COM or enclosing world AABB. */
    public static StructureProximity measure(OrientationBackendAccess.Body body, Vec3 point) {
        try (var profile = ProgramQueryProfiling.begin(MEASURE)) {
            profile.count(BOXES, body.localBoxes().size());
            var pose = body.motion().pose();
            var local = pose.worldToLocalPoint(point);
            Vec3 nearest = null;
            double squared = Double.POSITIVE_INFINITY;
            for (var box : body.localBoxes()) {
                var clamped = new Vec3(Math.clamp(local.x, box.minX, box.maxX),
                        Math.clamp(local.y, box.minY, box.maxY), Math.clamp(local.z, box.minZ, box.maxZ));
                var distance = local.distanceToSqr(clamped);
                if (distance < squared) { squared = distance; nearest = clamped; }
            }
            return new StructureProximity(body.reference(), body.runtimeId(),
                    pose.localToWorldPoint(Objects.requireNonNull(nearest)), Math.sqrt(squared));
        }
    }

    private static void requireQuery(ServerLevel host, Vec3 point) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(point, "point");
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Structure query requires server thread");
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
            throw new IllegalArgumentException("Non-finite query point");
        }
        var mode = StructureSpaces.status(host.getServer()).mode();
        if (mode == StructureBackendStatus.Mode.INCOMPATIBLE || mode == StructureBackendStatus.Mode.MIGRATION_REQUIRED) {
            throw new IllegalStateException("structure_query_backend_unavailable");
        }
    }
}
