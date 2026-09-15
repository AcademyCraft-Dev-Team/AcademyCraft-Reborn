package org.academy.api.server.ability.electromaster;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.electromaster.IronSandTuning;
import org.academy.api.common.damage.AbilityHitEffects;
import org.academy.api.server.ability.AbilityActorContext;
import org.academy.api.server.ability.AreaEffectTargets;
import org.academy.api.server.ability.HostileTargets;
import org.academy.api.server.damage.AbilityDamageService;

/** Paid actions; callers own resource and CP transactions, once per cast rather than per target. */
public final class IronSandActions {
    private IronSandActions() {}

    public static int whip(AbilityActorContext actor, double radius) {
        var subject = actor.subject();
        var look = subject.getLookAngle();
        var forward = new Vec3(look.x, 0, look.z);
        if (forward.lengthSqr() < 1.0e-8) {
            var yaw = Math.toRadians(subject.getYRot());
            forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        } else forward = forward.normalize();
        var direction = forward;
        var bounds = subject.getBoundingBox().inflate(radius, 2, radius);
        var hits = 0;
        for (var target : actor.level().getEntitiesOfClass(LivingEntity.class, bounds,
                target -> HostileTargets.isHostile(subject, target) && subject.hasLineOfSight(target))) {
            if (!inWhip(subject.position(), direction, radius, subject.getBoundingBox(), target.getBoundingBox())) continue;
            var base = IronSandTuning.whipBaseDamage(actor.milestone(), AbilityHitEffects.electricalCharge(target) > 0);
            if (hit(actor, target, base).healthLost() > 0) hits++;
        }
        return hits;
    }

    public static boolean inWhip(Vec3 origin, Vec3 forward, double radius, AABB actor, AABB target) {
        if (!Double.isFinite(radius) || radius <= 0 || target.minY > actor.maxY + 2 || target.maxY < actor.minY - 2) return false;
        var delta = target.getCenter().subtract(origin).multiply(1, 0, 1);
        return delta.lengthSqr() <= radius * radius
                && (delta.lengthSqr() < 1.0e-8 || forward.dot(delta.normalize()) >= 0.5 - 1.0e-8);
    }

    public static int cloudPulse(AbilityActorContext actor, double radius) {
        var hits = 0;
        for (var target : AreaEffectTargets.inSphere(actor.level(), actor.subject().position(), radius,
                target -> HostileTargets.isHostile(actor.subject(), target) && actor.subject().hasLineOfSight(target))) {
            var result = hit(actor, target, 16);
            if (result.healthLost() <= 0) continue;
            hits++;
            if (actor.milestone() >= 3) {
                AbilityHitEffects.damageEquipment(target, AbilityHitEffects.equipmentWear(
                        (float) IronSandTuning.scaleDamage(16, actor.abilityPower(), actor.damageMultiplier())), false);
            }
        }
        return hits;
    }

    private static AbilityDamageService.Result hit(AbilityActorContext actor, LivingEntity target, double base) {
        var amount = (float) IronSandTuning.scaleDamage(base, actor.abilityPower(), actor.damageMultiplier());
        if (amount <= 0) return new AbilityDamageService.Result(false, 0, 0);
        return AbilityDamageService.apply(actor.subject(), target, actor.skill(), AbilityDamageService.Request.of(amount));
    }
}
