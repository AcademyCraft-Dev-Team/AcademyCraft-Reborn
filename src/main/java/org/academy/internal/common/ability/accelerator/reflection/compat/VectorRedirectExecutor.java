package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import org.academy.api.common.util.LevelUtil;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.damagesource.VectorRedirectedDamageSource;

import java.util.Comparator;

public final class VectorRedirectExecutor {
    private static final double START_EPSILON = 1.0E-3;

    private VectorRedirectExecutor() {
    }

    public static ExecutionResult execute(VectorRedirectPlan plan) {
        if (!plan.hasWorldPath()) return new ExecutionResult(0, 0.0);
        var level = (ServerLevel) plan.redirector().level();
        var direction = plan.redirectedDirection().normalize();
        var start = plan.mirrorPoint().add(direction.scale(START_EPSILON));
        var end = start.add(direction.scale(plan.redirectedLength()));
        var blockPolicy = plan.attack().executionPolicy().blockPolicy();
        if (blockPolicy == VectorBlockPolicy.BREAK_ALLOWED
                && DestroyBlocksSetting.canDestroyBlocks(plan.redirector())) {
            var blockResult = LevelUtil.destroyBlocksAlongPath(
                    level,
                    start,
                    end,
                    (float) plan.attack().radius(),
                    3,
                    false,
                    true,
                    true,
                    false,
                    plan.redirector()
            );
            end = start.add(direction.scale(blockResult.getRight()));
        } else if (blockPolicy != VectorBlockPolicy.PASS_THROUGH) {
            var blockHit = level.clip(new ClipContext(
                    start,
                    end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    plan.redirector()
            ));
            if (blockHit.getType() != HitResult.Type.MISS) end = blockHit.getLocation();
        }

        var radius = plan.attack().radius();
        var pathBox = new AABB(start, end).inflate(radius);
        var finalEnd = end;
        var targets = level.getEntitiesOfClass(
                        LivingEntity.class,
                        pathBox,
                        target -> target.isAlive()
                                && target != plan.redirector()
                                && !plan.redirector().isAlliedTo(target)
                                && !CtaFriendlyFireWhitelist.shouldProtect(plan.redirector(), target)
                                && target.getBoundingBox().inflate(radius).clip(start, finalEnd).isPresent()
                ).stream()
                .limit(VectorExecutionPolicy.HARD_MAXIMUM_TARGETS)
                .sorted(Comparator.comparingDouble(target ->
                        target.getBoundingBox().getCenter().distanceToSqr(start)))
                .limit(plan.attack().executionPolicy().maximumTargets())
                .toList();

        var source = VectorRedirectedDamageSource.from(
                plan.attack().source(),
                plan.redirector(),
                plan.attack().attribution().originalAttacker(),
                plan.kind()
        );
        var hitCount = 0;
        for (var target : targets) {
            if (target.hurtServer(level, source, plan.attack().damage())) hitCount++;
            if (!plan.attack().executionPolicy().piercing()) break;
        }
        return new ExecutionResult(hitCount, start.distanceTo(end));
    }

    public record ExecutionResult(int hitCount, double redirectedLength) {
    }
}
