package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class VectorAttackFingerprint {
    private VectorAttackFingerprint() {
    }

    public static long compute(
            long gameTime,
            int defenderId,
            DamageSource source,
            Vec3 origin,
            Vec3 direction
    ) {
        long value = gameTime;
        value = mix(value, defenderId);
        value = mix(value, source.getMsgId().hashCode());
        value = mix(value, entityId(source.getEntity()));
        value = mix(value, entityId(source.getDirectEntity()));
        value = mix(value, quantize(origin.x));
        value = mix(value, quantize(origin.y));
        value = mix(value, quantize(origin.z));
        value = mix(value, quantize(direction.x));
        value = mix(value, quantize(direction.y));
        value = mix(value, quantize(direction.z));
        return value;
    }

    public static long computeLeaseKey(
            int defenderId,
            DamageSource source,
            Vec3 direction
    ) {
        long value = defenderId;
        value = mix(value, source.getMsgId().hashCode());
        value = mix(value, entityId(source.getEntity()));
        value = mix(value, entityId(source.getDirectEntity()));
        value = mix(value, coarseQuantize(direction.x));
        value = mix(value, coarseQuantize(direction.y));
        value = mix(value, coarseQuantize(direction.z));
        return value;
    }

    private static int entityId(Entity entity) {
        return entity == null ? 0 : entity.getId();
    }

    private static int quantize(double value) {
        return Double.isFinite(value) ? (int) Math.round(value * 1024.0) : 0;
    }

    private static int coarseQuantize(double value) {
        return Double.isFinite(value) ? (int) Math.round(value * 32.0) : 0;
    }

    private static long mix(long value, long next) {
        value ^= next + 0x9E3779B97F4A7C15L + (value << 6) + (value >>> 2);
        return value;
    }
}
