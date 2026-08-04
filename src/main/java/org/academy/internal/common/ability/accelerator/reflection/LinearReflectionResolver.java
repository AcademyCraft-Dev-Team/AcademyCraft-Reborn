package org.academy.internal.common.ability.accelerator.reflection;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.world.damagesource.ReflectedSkillDamageSource;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Predicate;

public final class LinearReflectionResolver {
    public static final double RETURN_EPSILON = 1.0E-4;
    private static final double PARALLEL_EPSILON = 1.0E-9;

    private LinearReflectionResolver() {
    }

    public static ResolvedLinearAttack resolve(
            ServerLevel level,
            LinearSegment segment,
            LinearAttackPayload payload
    ) {
        var candidate = findCandidate(level, segment, payload);
        if (candidate.isEmpty() || !tryActivate(candidate.get())) {
            return ResolvedLinearAttack.unreflected(segment);
        }
        return createReflected(segment, candidate.get());
    }

    public static Optional<LinearReflectionCandidate> findCandidate(
            ServerLevel level,
            LinearSegment segment,
            LinearAttackPayload payload
    ) {
        return findCandidate(level, segment, payload, VectorReflection.Server::isActive);
    }

    public static Optional<LinearReflectionCandidate> findCandidate(
            ServerLevel level,
            LinearSegment segment,
            LinearAttackPayload payload,
            Predicate<ServerPlayer> reflectionEligibility
    ) {
        if (level == null || segment == null || payload == null || !segment.isFinite()) {
            return Optional.empty();
        }
        if (reflectionEligibility == null) return Optional.empty();
        if (payload.outgoingDamageSource() instanceof ReflectedSkillDamageSource) {
            return Optional.empty();
        }

        var radius = payload.radius();
        var pathBounds = new AABB(segment.start(), segment.end()).inflate(radius);
        return level.getEntitiesOfClass(
                        ServerPlayer.class,
                        pathBounds,
                        player -> payload.canTarget(player, false, null)
                                && reflectionEligibility.test(player)
                ).stream()
                .map(player -> createCandidate(segment, radius, payload, player))
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(LinearReflectionCandidate::expandedEntryProgress));
    }

    public static boolean tryActivate(LinearReflectionCandidate candidate) {
        if (candidate == null) return false;
        return VectorReflection.Server.tryReflectLinearAttack(
                candidate.reflector(),
                candidate.expectedDamage(),
                candidate.mirrorPoint(),
                candidate.incomingDirection()
        );
    }

    public static ResolvedLinearAttack createReflected(
            LinearSegment original,
            LinearReflectionCandidate candidate
    ) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(candidate, "candidate");
        if (!original.isFinite()) return ResolvedLinearAttack.unreflected(original);
        var mirrorPoint = candidate.mirrorPoint();
        var returned = fullRangeReturnSegment(original, mirrorPoint);
        if (returned.isEmpty()) return ResolvedLinearAttack.unreflected(original);
        var outbound = new LinearSegment(original.start(), mirrorPoint);
        return ResolvedLinearAttack.reflected(original, outbound, returned.get(), candidate);
    }

    static Vec3 fullRangeReturnEnd(LinearSegment original, Vec3 mirrorPoint) {
        if (original == null || mirrorPoint == null || !original.isFinite()) return mirrorPoint;
        return mirrorPoint.subtract(original.direction().scale(original.length()));
    }

    static Optional<LinearSegment> fullRangeReturnSegment(LinearSegment original, Vec3 mirrorPoint) {
        if (original == null || !original.isFinite() || !finite(mirrorPoint)) return Optional.empty();
        var incoming = original.direction();
        var returnStart = mirrorPoint;
        if (mirrorPoint.distanceToSqr(original.start()) > RETURN_EPSILON * RETURN_EPSILON) {
            returnStart = mirrorPoint.subtract(incoming.scale(RETURN_EPSILON));
        }
        return Optional.of(new LinearSegment(returnStart, fullRangeReturnEnd(original, mirrorPoint)));
    }

    static Optional<LinearReflectionCandidate> createCandidate(
            LinearSegment segment,
            float radius,
            LinearAttackPayload payload,
            ServerPlayer player
    ) {
        var expandedEntry = intersectionProgress(segment.start(), segment.end(),
                player.getBoundingBox().inflate(radius));
        if (expandedEntry.isEmpty()) return Optional.empty();

        var actualEntry = intersectionProgress(segment.start(), segment.end(), player.getBoundingBox());
        var visualProgress = actualEntry.isPresent()
                ? actualEntry.getAsDouble()
                : projectedProgress(segment.start(), segment.end(), player.getBoundingBox().getCenter());
        var mirrorPoint = segment.pointAt(visualProgress);
        var damage = payload.damage(player);
        if (!(damage > 0.0f) || !Float.isFinite(damage)) return Optional.empty();
        return Optional.of(new LinearReflectionCandidate(
                player,
                mirrorPoint,
                visualProgress,
                expandedEntry.getAsDouble(),
                damage,
                segment.direction()
        ));
    }

    public static OptionalDouble intersectionProgress(Vec3 start, Vec3 end, AABB bounds) {
        if (!finite(start) || !finite(end) || bounds == null) return OptionalDouble.empty();
        var direction = end.subtract(start);
        if (!(direction.lengthSqr() > 1.0E-12) || !Double.isFinite(direction.lengthSqr())) {
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

    static double projectedProgress(Vec3 start, Vec3 end, Vec3 point) {
        if (!finite(start) || !finite(end) || !finite(point)) return 0.0;
        var direction = end.subtract(start);
        var lengthSqr = direction.lengthSqr();
        if (!(lengthSqr > 1.0E-12) || !Double.isFinite(lengthSqr)) return 0.0;
        return Math.clamp(point.subtract(start).dot(direction) / lengthSqr, 0.0, 1.0);
    }

    private static boolean clipAxis(
            double start,
            double direction,
            double min,
            double max,
            double[] interval
    ) {
        if (Math.abs(direction) < PARALLEL_EPSILON) {
            return start >= min && start <= max;
        }
        var inverse = 1.0 / direction;
        var entry = (min - start) * inverse;
        var exit = (max - start) * inverse;
        if (entry > exit) {
            var swap = entry;
            entry = exit;
            exit = swap;
        }
        interval[0] = Math.max(interval[0], entry);
        interval[1] = Math.min(interval[1], exit);
        return interval[0] <= interval[1];
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
