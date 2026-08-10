package org.academy.internal.common.world.damagesource;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.academy.internal.common.entitycontrol.EntityControlApi;

/**
 * Applies an exact subtraction to the resolved combat-health pool after normal damage hooks run.
 */
public final class CTAEntityActuallyHurt {
    private static final float EPSILON = 0.05f;
    private final LivingEntity entity;

    public CTAEntityActuallyHurt(LivingEntity entity) {
        this.entity = entity;
    }

    public static float readTrueHealth(LivingEntity entity) {
        return EntityControlApi.getAuthoritativeHealth(entity);
    }

    public static void writeTrueHealth(LivingEntity entity, float value) {
        PlayerAttributeRuntime.runWithoutResistance(() -> EntityControlApi.forceSetTrueHealth(entity, value));
    }

    public static void simulateMarkedDeath(LivingEntity entity, ServerPlayer killer, DamageSource source) {
        if (entity == null || killer == null || source == null) return;
        var health = Math.max(1.0f, readTrueHealth(entity));
        new CTAEntityActuallyHurt(entity).actuallyHurt(source, health + 1.0f, true);
    }

    private static ServerPlayer resolveOwnerPlayer(DamageSource source) {
        var attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) return player;
        var direct = source.getDirectEntity();
        if (direct instanceof ServerPlayer player) return player;
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        var owner = FriendlyFireSetting.getOwnerEntity(direct);
        return owner instanceof ServerPlayer player ? player : null;
    }

    public void actuallyHurt(DamageSource source, float amount, boolean special) {
        if (entity == null || source == null || !(entity.level() instanceof ServerLevel level)) return;
        if (!Float.isFinite(amount) || amount <= 0.0f || entity.isDeadOrDying()) return;
        if (entity instanceof Player player && DamageTypes.isImmunePlayer(player)) return;
        if (shouldPreventFriendlyFire(source)) return;

        var adjustedAmount = entity instanceof Player player
                ? PlayerAttributeRuntime.reduceDamage(player, amount, 0.08)
                : amount;
        PlayerAttributeRuntime.runWithoutResistance(() -> apply(level, source, adjustedAmount));
    }

    private void apply(ServerLevel level, DamageSource source, float amount) {

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

    private boolean shouldPreventFriendlyFire(DamageSource source) {
        var owner = resolveOwnerPlayer(source);
        return owner != null && CtaFriendlyFireWhitelist.shouldProtect(owner, entity);
    }
}
