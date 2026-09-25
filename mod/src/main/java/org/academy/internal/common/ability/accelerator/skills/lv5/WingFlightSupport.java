package org.academy.internal.common.ability.accelerator.skills.lv5;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.DamageComposition;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.util.ViewTargetScanner;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileRedirects;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileStateAdapter;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorRedirectKind;
import org.academy.internal.common.ability.accelerator.skills.WingFlightPose;
import org.academy.internal.common.ability.accelerator.skills.WingFlightRuntime;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.common.world.damagesource.*;
import org.academy.internal.server.ability.AbilitySystemServer;
import org.misaka.MisakaNetworkServer;

final class WingFlightSupport {
    static final double ATTACK_RANGE = 32.0;
    static final double FAN_COS_THRESHOLD = 0.35;
    static final float MAX_HEALTH_DAMAGE_RATIO = 0.01f;
    static final float FIXED_DAMAGE = 10.0f;
    static final float BLACK_SWEEP_MAX_HEALTH_DAMAGE_RATIO = 0.05f;
    static final float BLACK_SWEEP_FIXED_DAMAGE = 40.0f;

    private WingFlightSupport() {
    }

    static boolean tick(ServerPlayer player, Skill skill, AttachmentType<Boolean> attachment) {
        var active = skill.isEnabled(player) && player.isAlive() && !player.hasDisconnected();
        if (active) {
            var system = AbilitySystemServer.getSystem(player);
            active = system.ensurePermanentOccupation(
                    player.getUUID(),
                    skill.getMaintenanceCost(player),
                    skill
            );
            if (!active && skill.isEnabled(player)) skill.toggle(player);
            if (active && player.tickCount % 20 == 0
                    && !system.tryTimedOccupation(player.getUUID(), upkeepCost(skill), skill, 5)) {
                forceDeactivateSkill(player, skill);
                active = false;
            }
        }
        if (!active && skill.getRuntimeData(player).map(data -> data.isEnabled()).orElse(false)) {
            forceDeactivateSkill(player, skill);
        }
        sync(player, attachment, active);
        if (active) WingFlightRuntime.tick(player, skill);
        else WingFlightRuntime.clear(player, skill);
        return active;
    }

    static void forceDeactivateSkill(ServerPlayer player, Skill skill) {
        var data = skill.getRuntimeData(player).orElse(null);
        var system = AbilitySystemServer.getSystem(player);
        if (data != null && data.isEnabled()) {
            system.toggleSkill(player.getUUID(), skill.getKeyString());
        }
        system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
    }

    static void sync(ServerPlayer player, AttachmentType<Boolean> attachment, boolean active) {
        var wasActive = player.getData(attachment);
        if (wasActive != active) {
            player.setData(attachment, active);
            player.syncData(attachment);
        }
        if (!active) {
            // All wing skills call this every player tick. Shared pose cleanup must remain
            // transition-only so an inactive sibling wing cannot clear the active wing's pose.
            var skill = attachment == AttachmentTypes.ACTIVATED_BLACK_WING.get() ? Skills.BLACK_WING.get()
                    : attachment == AttachmentTypes.ACTIVATED_WHITE_WING.get() ? Skills.WHITE_WING.get()
                    : Skills.PLATINUM_WING.get();
            WingFlightRuntime.clear(player, skill);
            if (wasActive) WingFlightPose.sync(player, WingFlightPose.Pose.IDLE);
        }
    }

    static float calculateFanDamage(float baseDamage, float trueMaxHealth, float playerMultiplier,
                                    boolean platinumWing) {
        if (platinumWing) {
            return (baseDamage + trueMaxHealth * MAX_HEALTH_DAMAGE_RATIO + FIXED_DAMAGE) * playerMultiplier;
        }
        return (baseDamage + FIXED_DAMAGE) * playerMultiplier + trueMaxHealth * MAX_HEALTH_DAMAGE_RATIO;
    }

    static float calculateBlackSweepDamage(float meleeAttack, float trueMaxHealth, float playerMultiplier) {
        return (meleeAttack + BLACK_SWEEP_FIXED_DAMAGE) * playerMultiplier
                + trueMaxHealth * BLACK_SWEEP_MAX_HEALTH_DAMAGE_RATIO;
    }

    static int fanAttack(ServerPlayer player, Skill skill) {
        var level = player.level();
        var origin = player.getEyePosition();
        var forward = player.getLookAngle().normalize();
        var advancedBlackSweep = skill == Skills.BLACK_WING.get()
                && skill.hasProficiencyMilestone(player, 2);
        var range = skill.scaledRange(player, advancedBlackSweep
                ? 36.0
                : ATTACK_RANGE);
        var cosThreshold = advancedBlackSweep
                ? Math.cos(Math.acos(FAN_COS_THRESHOLD) + Math.toRadians(10.0))
                : FAN_COS_THRESHOLD;
        var attackShape = ViewTargetScanner.cone(range, cosThreshold);
        var blackWing = skill == Skills.BLACK_WING.get();
        var attackDamage = blackWing
                ? (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE)
                : (float) player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        var multiplier = AbilitySystemServer.getSystem(player).getPlayerDamageMultiplier(player.getUUID());
        var source = SkillDamageSource.of(
                player,
                skill,
                DamageTypes.CTA
        );
        var hitCount = new int[1];

        level.playSound(null, player, SoundEvents.PLAYER_ATTACK_NODAMAGE,
                SoundSource.PLAYERS, 0.85f, 0.9f + player.getRandom().nextFloat() * 0.15f);
        CTADamageUtil.runGuarded(player, () -> {
            for (var target : ViewTargetScanner.scan(
                    level,
                    LivingEntity.class,
                    origin,
                    forward,
                    range,
                    attackShape,
                    entity -> entity != player
                            && entity.isAlive()
                            && !CtaFriendlyFireWhitelist.shouldProtect(player, entity)
                            && entity.getBoundingBox().getCenter().distanceToSqr(origin) > 1.0E-6
            )) {
                var targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
                var trueMaxHealth = EntityControlApi.getTrueMaxHealth(target);
                if (!Float.isFinite(trueMaxHealth) || trueMaxHealth <= 0.0f) {
                    trueMaxHealth = target.getMaxHealth();
                }
                var platinumWing = skill == Skills.PLATINUM_WING.get();
                var damage = blackWing
                        ? calculateBlackSweepDamage(attackDamage, trueMaxHealth, multiplier)
                        : calculateFanDamage(attackDamage, trueMaxHealth, multiplier, platinumWing);
                if (!Float.isFinite(damage) || damage <= 0) continue;
                // Percentage max-health damage must not be scaled by the ordinary damage multiplier,
                // so tell the damage pipeline how much of this hit came from true max health.
                var maxHealthPart = trueMaxHealth
                        * (blackWing ? BLACK_SWEEP_MAX_HEALTH_DAMAGE_RATIO : MAX_HEALTH_DAMAGE_RATIO)
                        * (platinumWing ? multiplier : 1.0f);
                DamageComposition.withMaximumHealthPart(
                        target, source, maxHealthPart,
                        () -> new CTAEntityActuallyHurt(target).actuallyHurt(source, damage, true));
                if (skill == Skills.BLACK_WING.get() && skill.hasProficiencyMilestone(player, 3)) {
                    var now = level.getGameTime();
                    var marked = TimedSkillEffectRuntime.consume(
                            player.getUUID(), target.getUUID(), skill, "vector_mark", now);
                    if (marked.isPresent()) {
                        var secondarySource = SkillDamageSource.of(player, skill, DamageTypes.VEC);
                        // Carry over the same percentage portion into the follow-up hit.
                        DamageComposition.withMaximumHealthPart(
                                target, secondarySource, maxHealthPart * 0.3f,
                                () -> SkillDamageUtil.applyVerifiedTrueHealth(
                                        target, secondarySource, damage * 0.3f));
                        var pull = origin.subtract(targetCenter);
                        if (pull.lengthSqr() > 1.0E-6) {
                            target.setDeltaMovement(target.getDeltaMovement().add(pull.normalize().scale(0.75)));
                            target.syncVelocity = true;
                        }
                    } else {
                        TimedSkillEffectRuntime.put(
                                player, target.getUUID(), skill, "vector_mark", 100, damage);
                    }
                }
                hitCount[0]++;
            }
        });
        if (skill == Skills.WHITE_WING.get() && skill.hasProficiencyMilestone(player, 3)
                && hitCount[0] > 0) {
            var system = AbilitySystemServer.getSystem(player);
            var uuid = player.getUUID();
            var refund = Math.min(15.0f, hitCount[0] * 3.0f);
            system.setPlayerAvailableCP(
                    uuid,
                    Math.min(system.getPlayerMaxCP(uuid), system.getPlayerAvailableCP(uuid) + refund)
            );
        }
        return hitCount[0];
    }

    static void deflectFrontalProjectile(ServerPlayer player, Skill skill) {
        if (!skill.hasProficiencyMilestone(player, 2) || player.tickCount % 20 != 0) return;
        var look = player.getLookAngle();
        var center = player.getBoundingBox().getCenter();
        var projectile = player.level().getEntitiesOfClass(
                Projectile.class,
                player.getBoundingBox().inflate(6.0),
                candidate -> candidate.isAlive()
                        && candidate.getOwner() != player
                        && !VectorProjectileRedirects.isRedirected(candidate)
                        && candidate.getBoundingBox().getCenter().subtract(center).dot(look) > 0.0
        ).stream().findFirst().orElse(null);
        if (projectile == null) return;
        var speed = projectile.getDeltaMovement().length();
        var away = projectile.getBoundingBox().getCenter().subtract(center);
        if (!Double.isFinite(speed) || speed <= 1.0E-6 || away.lengthSqr() <= 1.0E-6) return;
        VectorProjectileRedirects.mark(projectile, player, VectorRedirectKind.REFRACTION);
        VectorProjectileStateAdapter.applyRedirect(projectile, away.normalize().scale(speed));
    }

    static boolean trySweepCost(ServerPlayer player, Skill skill) {
        return AbilitySystemServer.getSystem(player)
                .tryTimedAttackOccupation(player.getUUID(), 20.0f, skill, 10);
    }

    private static float upkeepCost(Skill skill) {
        if (skill instanceof PlatinumWing) return 80.0f;
        if (skill instanceof WhiteWing) return 40.0f;
        return 20.0f;
    }

    static void broadcastSweep(ServerPlayer player, AdvancedWingSweepPacket.WingKind kind) {
        var random = player.getRandom();
        var packet = new AdvancedWingSweepPacket(
                kind,
                player.getId(),
                random.nextBoolean(),
                -24.0f + random.nextFloat() * 48.0f,
                -8.0f + random.nextFloat() * 16.0f
        );
        for (var other : player.level().players()) {
            if (other.distanceToSqr(player) <= 128.0 * 128.0) {
                MisakaNetworkServer.send(other, packet);
            }
        }
    }

    static void broadcastBlackToWhiteTransition(ServerPlayer player) {
        var packet = new AdvancedWingTransitionPacket(player.getId());
        for (var other : player.level().players()) {
            if (other.distanceToSqr(player) <= 128.0 * 128.0) {
                MisakaNetworkServer.send(other, packet);
            }
        }
    }

    static boolean isInFan(Vec3 origin, Vec3 forward, Vec3 target, double range, double cosThreshold) {
        if (origin == null || forward == null || target == null || range <= 0) return false;
        return target.distanceToSqr(origin) > 1.0E-6
                && ViewTargetScanner.matches(
                origin,
                forward,
                range,
                ViewTargetScanner.cone(range, cosThreshold),
                new AABB(target, target)
        );
    }
}
