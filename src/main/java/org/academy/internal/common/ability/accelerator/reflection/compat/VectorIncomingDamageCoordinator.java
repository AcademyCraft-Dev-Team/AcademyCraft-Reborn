package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.academy.internal.common.ability.accelerator.skills.lv3.VectorReduction;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.world.damagesource.VectorRedirectedDamageSourceInfo;

public final class VectorIncomingDamageCoordinator {
    public static final float ANOMALOUS_DAMAGE_THRESHOLD = 100_000.0f;

    private VectorIncomingDamageCoordinator() {
    }

    public static VectorIncomingDamageResult interceptVectorDefense(
            ServerPlayer defender,
            DamageSource source,
            float damage
    ) {
        var reflection = interceptReflection(defender, source, damage);
        return reflection.handled() ? reflection : interceptReduction(defender, source, damage);
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
        if (!VectorReflection.Server.isActive(defender)) {
            VectorReflection.Server.deactivateUnavailableProtection(defender);
            return VectorIncomingDamageResult.passThrough(damage);
        }
        if (isAnomalousDamage(damage)
                && VectorReflection.Server.reflectAnomalousDamage(defender, source, damage)) {
            return VectorIncomingDamageResult.fullRedirect();
        }
        if (!VectorReflection.Server.shouldReflection(defender, source)) {
            return VectorIncomingDamageResult.passThrough(damage);
        }
        var classified = VectorExternalAttackClassifier.classify(defender, source, damage).orElse(null);
        if (VectorExternalInterceptionService.tryFullReflection(classified)) {
            return VectorIncomingDamageResult.fullRedirect();
        }
        return VectorReflection.Server.applyPartialReflection(
                defender,
                defender.level(),
                source,
                damage
        );
    }

    public static VectorIncomingDamageResult interceptReduction(
            ServerPlayer defender,
            DamageSource source,
            float damage
    ) {
        if (defender == null || source == null || !(damage > 0.0f) || !Float.isFinite(damage)) {
            return VectorIncomingDamageResult.passThrough(damage);
        }
        if (VectorReflection.Server.isLegitimateHealthMutation(defender)
                || !VectorReduction.Server.canRefractSource(defender, source)) {
            return VectorIncomingDamageResult.passThrough(damage);
        }
        if (isAnomalousDamage(damage)
                && VectorReduction.Server.canMaintain(defender)
                && VectorReduction.Server.absorbAnomalousDamage(defender, source, damage)) {
            return VectorIncomingDamageResult.fullRedirect();
        }
        if (!VectorReduction.Server.isActive(defender)) {
            return VectorIncomingDamageResult.passThrough(damage);
        }
        var classified = VectorExternalAttackClassifier.classify(defender, source, damage).orElse(null);
        if (VectorExternalInterceptionService.tryFullRefraction(classified)) {
            return VectorIncomingDamageResult.fullRedirect();
        }
        return VectorReduction.Server.applyPartialReduction(defender, source, damage);
    }

    public static boolean isAnomalousDamage(float damage) {
        return Float.isFinite(damage) && damage > ANOMALOUS_DAMAGE_THRESHOLD;
    }
}
