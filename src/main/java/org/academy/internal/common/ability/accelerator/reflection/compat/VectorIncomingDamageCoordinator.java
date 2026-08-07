package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.world.damagesource.VectorRedirectedDamageSourceInfo;

public final class VectorIncomingDamageCoordinator {
    public static final float ANOMALOUS_DAMAGE_THRESHOLD = 100_000.0f;

    private VectorIncomingDamageCoordinator() {
    }

    public static VectorIncomingDamageResult interceptReflection(
            ServerPlayer defender,
            DamageSource source,
            float damage
    ) {
        if (defender == null || source == null || !(damage > 0.0f) || !Float.isFinite(damage)) {
            return VectorIncomingDamageResult.passThrough(damage);
        }
        if (VectorReflection.Server.isLegitimateHealthMutation(defender)
                || VectorRedirectedDamageSourceInfo.isRedirected(source)) {
            return VectorIncomingDamageResult.passThrough(damage);
        }
        if (isAnomalousDamage(damage)
                && VectorReflection.Server.canMaintainLinearReflectionLease(defender)
                && VectorReflection.Server.reflectAnomalousDamage(defender, source, damage)) {
            return VectorIncomingDamageResult.fullRedirect();
        }
        if (!VectorReflection.Server.isActive(defender)) {
            return VectorIncomingDamageResult.passThrough(damage);
        }
        if (!VectorReflection.Server.shouldReflection(defender, source)) {
            return VectorIncomingDamageResult.passThrough(damage);
        }
        var classified = VectorExternalAttackClassifier.classify(defender, source, damage).orElse(null);
        if (classified != null && VectorExternalInterceptionService.tryFullReflection(classified)) {
            return VectorIncomingDamageResult.fullRedirect();
        }
        return VectorReflection.Server.applyPartialReflection(
                defender,
                (ServerLevel) defender.level(),
                source,
                damage
        );
    }

    static boolean isAnomalousDamage(float damage) {
        return Float.isFinite(damage) && damage > ANOMALOUS_DAMAGE_THRESHOLD;
    }
}
