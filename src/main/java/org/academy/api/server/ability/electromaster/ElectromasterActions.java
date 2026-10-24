package org.academy.api.server.ability.electromaster;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.structure.StructureQueries;
import org.academy.api.server.ability.*;
import org.academy.internal.common.ability.shared.linear.*;
import org.academy.internal.common.ability.electromaster.arc.ElectromasterArcTargeting;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.DamageTypes;
import java.util.*;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicInteger;

/** Electric effects shared by player skills, entity skills and precision-operation adapters.
 * Callers authorize casts and pay once; every target is admitted again at impact. */
public final class ElectromasterActions {
    private static final AtomicInteger VISUAL_IDS = new AtomicInteger();
    private ElectromasterActions() {}

    public static LinearAttackExecutor.ExecutionResult linearStrike(LivingEntity caster, Skill skill,
            SkillDamageSource source, Vec3 start, Vec3 end, float radius, float damage,
            Predicate<LivingEntity> allowed, ElectromasterGraphEffects.BoltStyle style) {
        var level = (ServerLevel) caster.level();
        var payload = LinearAttackPayload.builder(caster, skill, source, radius).damage(ignored -> damage)
                .targetFilter(ElectromasterArcTargeting::canDamageAlongArc)
                .outboundTargetFilter(entity -> !(entity instanceof LivingEntity living) || allowed.test(living))
                .build();
        var resolved = LinearReflectionResolver.resolve(level, new LinearSegment(start, end), payload);
        ElectromasterGraphEffects.spawnBolt(level, resolved.outbound().start(), resolved.outbound().end(), style);
        resolved.returnSegment().ifPresent(segment -> ElectromasterGraphEffects.spawnBolt(level, segment.start(), segment.end(), style));
        caster.playSound(SoundEvents.ARC_WEAK.get(), 1, 1);
        return LinearAttackExecutor.execute(level, resolved, payload);
    }

    public static void fireArc(AbilityActorContext actor, LivingEntity target, Predicate<LivingEntity> allowed) {
        var source = SkillDamageSource.of(actor.subject(), actor.skill()).withElectricalChargePoints(1);
        linearStrike(actor.subject(), actor.skill(), source, actor.subject().getEyePosition(),
                target.getBoundingBox().getCenter(), .125f, damage(actor, 8), allowed, ElectromasterGraphEffects.BoltStyle.ARC);
    }

    public static void fireLance(AbilityActorContext actor, LivingEntity target, Predicate<LivingEntity> allowed) {
        var start = actor.subject().getEyePosition();
        var direction = target.getBoundingBox().getCenter().subtract(start).normalize();
        var length = 32 * SkillTuning.rangeMultiplier(actor.level(), actor.skill());
        // Clip to the first obstruction: entities behind a wall cannot be hit by an NPC lance.
        var end = start.add(direction.scale(length));
        var hit = actor.level().clip(new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, actor.subject()));
        end = hit.getLocation();
        linearStrike(actor.subject(), actor.skill(), SkillDamageSource.of(actor.subject(), actor.skill()).withElectricalChargePoints(1),
                start, end, 2, damage(actor, 20), candidate -> allowed.test(candidate)
                        && StructureQueries.clearSightline(actor.level(), start, candidate.getBoundingBox().getCenter()),
                ElectromasterGraphEffects.BoltStyle.LANCE);
    }

    public static float damage(AbilityActorContext actor, float base) {
        return (float) (base * actor.abilityPower() * actor.damageMultiplier());
    }

    public static boolean contactHit(LivingEntity caster, LivingEntity target, Skill skill, float damage,
                                     Predicate<LivingEntity> allowed, boolean particles) {
        if (!target.isAlive() || !allowed.test(target)) return false;
        var level = (ServerLevel) caster.level();
        var applied = target.hurtServer(level, SkillDamageSource.of(caster, skill, DamageTypes.ELECTRO_DAMAGE), damage);
        if (particles) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(.6), target.getZ(),
                8, .25, .45, .25, .04);
        return applied;
    }

    public static AbilityExecutionHandle electricalContact(AbilityActorContext actor, Predicate<LivingEntity> allowed) {
        return new AbilityExecutionHandle() {
            private int ticks;
            private boolean stopped;
            public boolean tick() {
                if (stopped) return false;
                if (++ticks % 20 == 0) {
                    double radius = 2 * SkillTuning.rangeMultiplier(actor.level(), actor.skill());
                    for (var target : actor.level().getEntitiesOfClass(LivingEntity.class,
                            actor.subject().getBoundingBox().inflate(radius), allowed)) {
                        if (StructureQueries.clearSightline(actor.level(), actor.subject().getEyePosition(), target.getBoundingBox().getCenter()))
                            contactHit(actor.subject(), target, actor.skill(), damage(actor, 2), allowed, true);
                    }
                }
                return true;
            }
            public void stop() { stopped = true; }
        };
    }

    public static AbilityExecutionHandle lightningNova(AbilityActorContext actor, Predicate<LivingEntity> allowed) {
        return new Nova(actor, allowed);
    }

    private static final class Nova implements AbilityExecutionHandle {
        private final AbilityActorContext actor;
        private final Predicate<LivingEntity> allowed;
        private final int visualId = VISUAL_IDS.getAndIncrement() & 0xFFFFFF;
        private final long seed;
        private final int duration;
        private final float radius;
        private final Set<UUID> hits = new HashSet<>();
        private int ticks, phaseTicks, nextVisual = 1;
        private boolean echo, ended;
        private Nova(AbilityActorContext actor, Predicate<LivingEntity> allowed) {
            this.actor = actor;
            this.allowed = allowed;
            seed = actor.level().getRandom().nextLong();
            duration = actor.milestone() >= 2 ? Math.round(200 * 1.25f / 1.15f) : 200;
            radius = Math.round((actor.milestone() >= 2 ? 20 : 16) * SkillTuning.rangeMultiplier(actor.level(), actor.skill()));
        }
        public boolean tick() {
            if (ended || !actor.subject().isAlive()) return false;
            ticks++;
            if (++phaseTicks >= duration) {
                if (echo || actor.milestone() < 3) return false;
                echo = true;
                phaseTicks = 0;
                hits.clear();
                nextVisual = ticks;
            }
            float progress = (float) phaseTicks / duration;
            float current = echo ? radius * (1 - progress) : 1 + progress * radius;
            float inner = Math.max(0, current - 1.5f), outer = echo ? current + 1.5f : current;
            if (ticks >= nextVisual) {
                int visualTicks = Math.min(10, duration - phaseTicks);
                ElectromasterGraphEffects.spawnNovaRing(actor.level(), actor.subject().position(), actor.subject().getId(), 1,
                        current, (echo ? -1 : 1) * radius * 20 / duration, visualTicks / 20f, ticks / 20f, seed, visualId);
                nextVisual = ticks + visualTicks;
            }
            var source = SkillDamageSource.of(actor.subject(), actor.skill(), DamageTypes.ELECTRO_DAMAGE)
                    .withElectricalChargePoints(echo ? 1 : 2);
            for (var target : actor.level().getEntitiesOfClass(LivingEntity.class,
                    actor.subject().getBoundingBox().inflate(outer), candidate -> allowed.test(candidate)
                            && !hits.contains(candidate.getUUID()))) {
                double distance = actor.subject().distanceToSqr(target);
                if (distance < inner * inner || distance > outer * outer) continue;
                hits.add(target.getUUID());
                target.hurtServer(actor.level(), source, damage(actor, echo ? 2 : 4));
            }
            return true;
        }
        public void stop() { ended = true; hits.clear(); }
    }
}
