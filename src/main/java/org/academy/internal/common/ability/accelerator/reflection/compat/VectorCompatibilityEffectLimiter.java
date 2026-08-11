package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VectorCompatibilityEffectLimiter {
    static final long EFFECT_COOLDOWN_TICKS = 4L;
    private static final Map<Key, Long> LAST_EFFECT_TICKS = new HashMap<>();

    private VectorCompatibilityEffectLimiter() {
    }

    public static synchronized void emit(
            ServerPlayer defender,
            long attackKey,
            Vec3 direction,
            Vec3 position,
            VectorRedirectKind kind
    ) {
        if (defender == null || direction == null || position == null) return;
        var now = defender.level().getGameTime();
        var key = new Key(defender.getUUID(), attackKey);
        var previous = LAST_EFFECT_TICKS.get(key);
        if (previous != null && now - previous < EFFECT_COOLDOWN_TICKS) return;
        LAST_EFFECT_TICKS.put(key, now);
        if (LAST_EFFECT_TICKS.size() > 2048) {
            LAST_EFFECT_TICKS.entrySet().removeIf(entry -> now - entry.getValue() > 100L);
        }
        VectorReflection.Server.spawnGlowCircle(defender, direction, position, kind);
        VectorReflection.Server.playReflectionSound(defender);
    }

    public static synchronized void clear(ServerPlayer defender) {
        if (defender == null) return;
        var id = defender.getUUID();
        LAST_EFFECT_TICKS.keySet().removeIf(key -> key.defenderId.equals(id));
        VectorEnvironmentalFeedbackController.clear(defender);
    }

    static boolean shouldEmit(long now, long lastEmission) {
        return now - lastEmission >= EFFECT_COOLDOWN_TICKS;
    }

    private record Key(UUID defenderId, long attackKey) {
    }
}
