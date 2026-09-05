package org.academy.internal.common.ability.electromaster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.team.TeamRelations;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackExecutor;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackPayload;
import org.academy.internal.common.ability.accelerator.reflection.LinearReflectionResolver;
import org.academy.internal.common.ability.accelerator.reflection.LinearSegment;

/** Shared damage and visual path for secondary electric arcs. */
public final class ElectromasterArcActions {
    private ElectromasterArcActions() {
    }

    public static boolean strikeChain(ServerLevel level, ServerPlayer attacker, SkillDamageSource source,
                                      LivingEntity origin, LivingEntity target, float damage) {
        var payload = LinearAttackPayload.builder(attacker, source.getSkill(), source, 0.125f)
                .damage(_ -> damage)
                .targetFilter(entity -> entity instanceof LivingEntity && entity != origin)
                .outboundTargetFilter(entity -> !TeamRelations.areAllied(attacker, entity))
                .build();
        var attack = LinearReflectionResolver.resolve(level, new LinearSegment(
                origin.getBoundingBox().getCenter(), target.getBoundingBox().getCenter()), payload);
        ElectromasterArcEffects.spawnChainArc(level, attack.outbound().start(), attack.outbound().end());
        attack.returnSegment().ifPresent(segment ->
                ElectromasterArcEffects.spawnChainArc(level, segment.start(), segment.end()));
        return LinearAttackExecutor.execute(level, attack, payload).outboundHits().contains(target);
    }
}
