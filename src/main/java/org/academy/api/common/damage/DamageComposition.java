package org.academy.api.common.damage;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;

/** Per-hit composition, also usable by non-player attacks and precision programs. */
public final class DamageComposition {
    private static final ThreadLocal<ArrayDeque<Hit>> HITS = ThreadLocal.withInitial(ArrayDeque::new);

    private DamageComposition() {
    }

    public static boolean hurt(Entity target, ServerLevel level, DamageSource source,
                               float total, float maximumHealthPart) {
        return withMaximumHealthPart(target, source, maximumHealthPart,
                () -> target.hurtServer(level, source, total));
    }

    public static boolean withMaximumHealthPart(Entity target, DamageSource source,
                                                float amount, BooleanSupplier action) {
        if (!Float.isFinite(amount) || amount < 0.0f) {
            throw new IllegalArgumentException("Maximum-health damage must be finite and non-negative");
        }
        var hits = HITS.get();
        hits.push(new Hit(target, source, amount));
        try {
            return action.getAsBoolean();
        } finally {
            hits.pop();
            if (hits.isEmpty()) HITS.remove();
        }
    }

    public static float maximumHealthPart(Entity target, DamageSource source) {
        for (var hit : HITS.get()) {
            if (hit.target == target && hit.source == source) return hit.amount;
        }
        return 0.0f;
    }

    private record Hit(Entity target, DamageSource source, float amount) {
    }
}
