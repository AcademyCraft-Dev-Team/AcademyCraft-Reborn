package org.academy.internal.common.ability.mentalout;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.server.ability.AbilitySystemServer;

public final class MentaloutControlCost {
    private static final double BASE_MAX_HEALTH_THRESHOLD = 10.0;
    private static final double THRESHOLD_GROWTH = 4.0;
    private static final float BASE_COST_MULTIPLIER = 0.5f;
    private static final float COST_GROWTH = 2.0f;

    private MentaloutControlCost() {
    }

    public static float multiplier(ServerPlayer controller, LivingEntity subject) {
        var abilityStrength = AbilitySystemServer.getSystem(controller)
                .getPlayerAbilityPowerMultiplier(controller.getUUID());
        return multiplierFor(subject.getMaxHealth(), abilityStrength);
    }

    static float multiplierFor(float maxHealth, float abilityStrength) {
        var safeHealth = Float.isFinite(maxHealth) ? Math.max(0.0, maxHealth) : Double.MAX_VALUE;
        var safeStrength = Float.isFinite(abilityStrength) && abilityStrength > 0.0f
                ? abilityStrength
                : 1.0f;
        var threshold = BASE_MAX_HEALTH_THRESHOLD * safeStrength;
        var multiplier = BASE_COST_MULTIPLIER;
        while (safeHealth > threshold && multiplier < Float.MAX_VALUE / COST_GROWTH) {
            threshold *= THRESHOLD_GROWTH;
            multiplier *= COST_GROWTH;
            if (!Double.isFinite(threshold)) break;
        }
        return multiplier;
    }
}
