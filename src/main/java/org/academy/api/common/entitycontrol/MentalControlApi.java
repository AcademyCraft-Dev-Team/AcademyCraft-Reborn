package org.academy.api.common.entitycontrol;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;

import java.util.Optional;

public final class MentalControlApi {
    private MentalControlApi() {
    }

    public static ControlHandle apply(ControlRequest request) {
        return MentalControlRuntime.apply(request);
    }

    public static void registerAdapter(Identifier id, int priority, MentalControlAdapter adapter) {
        MentalControlRuntime.registerAdapter(id, priority, adapter);
    }

    public static Optional<MentalControlAdapter> findAdapter(LivingEntity subject) {
        return MentalControlRuntime.findAdapter(subject);
    }

    public static Optional<MentalControlAdapter> findAdapter(
            LivingEntity subject,
            ControlCapability capability
    ) {
        return MentalControlRuntime.findAdapter(subject, capability);
    }

    public static ControlEvaluation evaluate(LivingEntity subject, ControlCapability capability) {
        return MentalControlRuntime.evaluate(subject, capability);
    }

    public static Optional<ControlInspection> inspect(
            LivingEntity subject,
            ControlCapability capability
    ) {
        return MentalControlRuntime.inspect(subject, capability);
    }

    public static boolean supports(LivingEntity subject, ControlCapability capability) {
        return evaluate(subject, capability).supported();
    }

    public static Optional<ControlDirective> effectiveDirective(
            LivingEntity subject,
            ControlCapability capability
    ) {
        return MentalControlRuntime.effectiveDirective(subject, capability);
    }

    public static AttackDecision attackDecision(LivingEntity subject, LivingEntity target) {
        return MentalControlRuntime.attackDecision(subject, target);
    }

    public static AttackDecision allianceDecision(LivingEntity subject, LivingEntity target) {
        return MentalControlRuntime.allianceDecision(subject, target);
    }

    public static boolean isHostilityAllowed(LivingEntity subject, Entity target) {
        return MentalControlRuntime.isHostilityAllowed(subject, target);
    }

    public static void enforceTargetWhitelist(Mob subject) {
        MentalControlRuntime.enforceTargetWhitelist(subject);
    }

    public static boolean isBossCost(LivingEntity subject) {
        return MentalControlRuntime.isBossCost(subject);
    }
}
