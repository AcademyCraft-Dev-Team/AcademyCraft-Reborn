package org.academy.internal.common.ability.accelerator.reflection;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;
import org.academy.internal.common.world.damagesource.ReflectedSkillDamageSource;
import org.academy.internal.common.world.entity.EntityTypes;

import java.util.*;

public final class LinearAttackExecutor {
    private LinearAttackExecutor() {
    }

    public static ExecutionResult execute(
            ServerLevel level,
            ResolvedLinearAttack attack,
            LinearAttackPayload payload
    ) {
        if (level == null || attack == null || payload == null) return ExecutionResult.EMPTY;
        var outbound = executeOutbound(level, attack, payload);
        var returned = executeReturn(level, attack, payload, outbound);
        return new ExecutionResult(outbound.hits(), returned.hits());
    }

    public static SegmentExecutionResult executeOutbound(
            ServerLevel level,
            ResolvedLinearAttack attack,
            LinearAttackPayload payload
    ) {
        if (level == null || attack == null || payload == null) return SegmentExecutionResult.EMPTY;
        var candidate = attack.reflectionCandidate().orElse(null);
        return new SegmentExecutionResult(executeSegment(
                level,
                attack.outbound(),
                payload,
                false,
                candidate == null ? null : candidate.reflector(),
                candidate == null ? null : candidate.reflector(),
                null
        ));
    }

    public static SegmentExecutionResult executeReturn(
            ServerLevel level,
            ResolvedLinearAttack attack,
            LinearAttackPayload payload,
            SegmentExecutionResult outboundResult
    ) {
        if (level == null || attack == null || payload == null || outboundResult == null) {
            return SegmentExecutionResult.EMPTY;
        }
        var candidate = attack.reflectionCandidate().orElse(null);
        var returnSegment = attack.returnSegment().orElse(null);
        if (candidate == null || returnSegment == null) return SegmentExecutionResult.EMPTY;

        var foldHits = new HashSet<Entity>();
        for (var target : outboundResult.hits()) {
            if (target.getBoundingBox()
                    .inflate(payload.radius() + LinearReflectionResolver.RETURN_EPSILON)
                    .contains(attack.mirrorPoint())) {
                foldHits.add(target);
            }
        }
        return new SegmentExecutionResult(executeSegment(
                level,
                returnSegment,
                payload,
                true,
                candidate.reflector(),
                null,
                foldHits
        ));
    }

    private static List<Entity> executeSegment(
            ServerLevel level,
            LinearSegment segment,
            LinearAttackPayload payload,
            boolean reflected,
            ServerPlayer reflector,
            Entity excludedEntity,
            Set<Entity> excludedFoldHits
    ) {
        if (!segment.hasFiniteCoordinates()) return List.of();
        var pointSegment = !(segment.lengthSqr() > 1.0E-12);
        var hits = new ArrayList<Entity>();
        var pathBounds = new AABB(segment.start(), segment.end()).inflate(payload.radius());
        var candidates = level.getEntities(
                excludedEntity,
                pathBounds,
                entity -> entity.getType() != EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get()
                        && payload.canTarget(entity, reflected, reflector)
                        && (!reflected
                        || !(entity instanceof LivingEntity living)
                        || !CtaFriendlyFireWhitelist.shouldProtect(reflector, living))
        );
        candidates.sort(Comparator.comparingDouble(entity ->
                LinearReflectionResolver.intersectionProgress(
                        segment.start(), segment.end(), entity.getBoundingBox().inflate(payload.radius())
                ).orElse(Double.POSITIVE_INFINITY)
        ));

        var segmentHits = new HashSet<Entity>();
        var source = reflected
                ? ReflectedSkillDamageSource.from(
                payload.outgoingDamageSource(), reflector, payload.skill(), payload.attacker())
                : payload.outgoingDamageSource();
        for (var target : candidates) {
            if (!segmentHits.add(target)) continue;
            var hitBounds = target.getBoundingBox().inflate(payload.radius());
            if (pointSegment) {
                if (!hitBounds.contains(segment.start())) continue;
            } else if (LinearReflectionResolver.intersectionProgress(
                    segment.start(), segment.end(), hitBounds
            ).isEmpty()) continue;
            if (excludedFoldHits != null && excludedFoldHits.contains(target)) continue;

            var damage = payload.damage(target);
            if (!(damage > 0.0f)) continue;
            var hurt = target.hurtServer(level, source, damage);
            payload.afterHit(target, reflected, hurt);
            hits.add(target);
        }
        return List.copyOf(hits);
    }

    public record ExecutionResult(List<Entity> outboundHits, List<Entity> returnHits) {
        private static final ExecutionResult EMPTY = new ExecutionResult(List.of(), List.of());

        public ExecutionResult {
            outboundHits = List.copyOf(outboundHits);
            returnHits = List.copyOf(returnHits);
        }
    }

    public record SegmentExecutionResult(List<Entity> hits) {
        private static final SegmentExecutionResult EMPTY = new SegmentExecutionResult(List.of());

        public SegmentExecutionResult {
            hits = List.copyOf(hits);
        }
    }
}
