package org.academy.internal.common.world.damagesource;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.academy.internal.common.entitycontrol.EntityControlApi;

/** Applies an exact subtraction to the resolved combat-health pool after normal damage hooks run. */
public final class CTAEntityActuallyHurt {
    private static final float EPSILON = 0.05f;
    private final LivingEntity entity;

    public CTAEntityActuallyHurt(LivingEntity entity) {
        this.entity = entity;
    }

    public void actuallyHurt(DamageSource source, float amount, boolean special) {
        if (entity == null || source == null || !(entity.level() instanceof ServerLevel level)) return;
        if (!Float.isFinite(amount) || amount <= 0.0f || entity.isDeadOrDying()) return;
        if (shouldPreventFriendlyFire(source)) return;

        var before = readTrueHealth(entity);
        if (!Float.isFinite(before) || before <= 0.0f) return;
        var expected = Math.max(0.0f, before - amount);
        EntityControlApi.capTrueHealthTemporarily(entity, expected, 2L);

        entity.invulnerableTime = 0;
        try {
            entity.hurtServer(level, source, amount);
        } catch (Throwable ignored) {
        }

        var observed = readTrueHealth(entity);
        if (!Float.isFinite(observed) || observed > expected + EPSILON) {
            EntityControlApi.forceSetTrueHealth(entity, expected);
        }
        if (expected > EPSILON || !entity.isAlive()) return;

        EntityControlApi.forceSetTrueHealth(entity, 0.0f);
        entity.invulnerableTime = 0;
        try {
            entity.hurtServer(level, source, Float.MAX_VALUE);
        } catch (Throwable ignored) {
        }
        if (entity.isAlive()) EntityControlApi.forceSetTrueHealth(entity, 0.0f);
    }

    public static float readTrueHealth(LivingEntity entity) {
        return EntityControlApi.getAuthoritativeHealth(entity);
    }

    public static void writeTrueHealth(LivingEntity entity, float value) {
        EntityControlApi.forceSetTrueHealth(entity, value);
    }

    public static void simulateMarkedDeath(LivingEntity entity, ServerPlayer killer, DamageSource source) {
        if (entity == null || killer == null || source == null) return;
        var health = Math.max(1.0f, readTrueHealth(entity));
        new CTAEntityActuallyHurt(entity).actuallyHurt(source, health + 1.0f, true);
    }

    private boolean shouldPreventFriendlyFire(DamageSource source) {
        var owner = resolveOwnerPlayer(source);
        return owner != null && CtaFriendlyFireWhitelist.shouldProtect(owner, entity);
    }

    private static ServerPlayer resolveOwnerPlayer(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) return player;
        Entity direct = source.getDirectEntity();
        if (direct instanceof ServerPlayer player) return player;
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        var owner = FriendlyFireSetting.getOwnerEntity(direct);
        return owner instanceof ServerPlayer player ? player : null;
    }
}
