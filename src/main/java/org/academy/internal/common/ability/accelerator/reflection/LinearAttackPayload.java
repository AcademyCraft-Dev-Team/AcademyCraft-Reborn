package org.academy.internal.common.ability.accelerator.reflection;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.ability.Skill;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

public final class LinearAttackPayload {
    private final ServerPlayer attacker;
    private final Skill skill;
    private final DamageSource outgoingDamageSource;
    private final float radius;
    private final DamageCalculator damageCalculator;
    private final Predicate<Entity> targetFilter;
    private final Predicate<Entity> outboundTargetFilter;
    private final Predicate<Entity> returnTargetFilter;
    private final HitEffect hitEffect;

    private LinearAttackPayload(Builder builder) {
        attacker = builder.attacker;
        skill = builder.skill;
        outgoingDamageSource = builder.outgoingDamageSource;
        radius = builder.radius;
        damageCalculator = builder.damageCalculator;
        targetFilter = builder.targetFilter;
        outboundTargetFilter = builder.outboundTargetFilter;
        returnTargetFilter = builder.returnTargetFilter;
        hitEffect = builder.hitEffect;
    }

    public static Builder builder(
            ServerPlayer attacker,
            Skill skill,
            DamageSource outgoingDamageSource,
            float radius
    ) {
        return new Builder(attacker, skill, outgoingDamageSource, radius);
    }

    public ServerPlayer attacker() {
        return attacker;
    }

    public Skill skill() {
        return skill;
    }

    public DamageSource outgoingDamageSource() {
        return outgoingDamageSource;
    }

    public float radius() {
        return radius;
    }

    public float damage(Entity target) {
        var damage = damageCalculator.calculate(target);
        return Float.isFinite(damage) ? Math.max(0.0f, damage) : 0.0f;
    }

    boolean canTarget(Entity target, boolean reflected, @Nullable ServerPlayer reflector) {
        if (target == null || !target.isAlive() || !targetFilter.test(target)) return false;
        if (reflected) {
            return reflector != null
                    && target != reflector
                    && !reflector.isAlliedTo(target)
                    && returnTargetFilter.test(target);
        }
        return target != attacker && outboundTargetFilter.test(target);
    }

    void afterHit(Entity target, boolean reflected, boolean hurt) {
        hitEffect.onHit(target, reflected, hurt);
    }

    public static final class Builder {
        private final ServerPlayer attacker;
        private final Skill skill;
        private final DamageSource outgoingDamageSource;
        private final float radius;
        private DamageCalculator damageCalculator = _ -> 0.0f;
        private Predicate<Entity> targetFilter = _ -> true;
        private Predicate<Entity> outboundTargetFilter = _ -> true;
        private Predicate<Entity> returnTargetFilter = _ -> true;
        private HitEffect hitEffect = (_, _, _) -> {
        };

        private Builder(
                ServerPlayer attacker,
                Skill skill,
                DamageSource outgoingDamageSource,
                float radius
        ) {
            this.attacker = Objects.requireNonNull(attacker, "attacker");
            this.skill = Objects.requireNonNull(skill, "skill");
            this.outgoingDamageSource = Objects.requireNonNull(outgoingDamageSource, "outgoingDamageSource");
            if (!Float.isFinite(radius) || radius < 0.0f) {
                throw new IllegalArgumentException("radius must be finite and non-negative");
            }
            this.radius = radius;
        }

        public Builder damage(DamageCalculator damageCalculator) {
            this.damageCalculator = Objects.requireNonNull(damageCalculator, "damageCalculator");
            return this;
        }

        public Builder targetFilter(Predicate<Entity> targetFilter) {
            this.targetFilter = Objects.requireNonNull(targetFilter, "targetFilter");
            return this;
        }

        public Builder outboundTargetFilter(Predicate<Entity> targetFilter) {
            outboundTargetFilter = Objects.requireNonNull(targetFilter, "targetFilter");
            return this;
        }

        public Builder returnTargetFilter(Predicate<Entity> targetFilter) {
            returnTargetFilter = Objects.requireNonNull(targetFilter, "targetFilter");
            return this;
        }

        public Builder onHit(HitEffect hitEffect) {
            this.hitEffect = Objects.requireNonNull(hitEffect, "hitEffect");
            return this;
        }

        public LinearAttackPayload build() {
            return new LinearAttackPayload(this);
        }
    }

    @FunctionalInterface
    public interface DamageCalculator {
        float calculate(Entity target);
    }

    @FunctionalInterface
    public interface HitEffect {
        void onHit(Entity target, boolean reflected, boolean hurt);
    }
}
