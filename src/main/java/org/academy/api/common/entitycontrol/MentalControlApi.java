package org.academy.api.common.entitycontrol;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.academy.internal.common.ability.mentalout.resistance.MentalResistanceManager;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;

import java.util.Optional;
import java.util.UUID;

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

    /** Remaining server game ticks of mental resistance, including manual player break-free. */
    public static long resistanceRemainingTicks(LivingEntity subject) {
        return MentalResistanceManager.remainingTicks(subject);
    }

    /** Includes tagged resistance before its automatic break-free timer has elapsed. */
    public static boolean hasMentalProtection(LivingEntity subject) {
        return subject != null && !MentalImmunity.isSuppressed(subject)
                && (MentalControlRuntime.isProtectedTarget(subject)
                || subject.getType().builtInRegistryHolder().is(MentalControlTags.RESISTANCE));
    }

    /** Clears all mental defenses and blocks their registration for this entity's current lifecycle. */
    public static void suppressMentalProtection(LivingEntity subject) {
        MentalImmunity.suppress(subject);
    }

    public static boolean isMentalProtectionSuppressed(LivingEntity subject) {
        return MentalImmunity.isSuppressed(subject);
    }

    /** Allows sources to register again; removed instance sources and old resistance do not return. */
    public static void restoreMentalProtection(LivingEntity subject) {
        MentalImmunity.restore(subject);
    }

    public static boolean hasActiveControl(LivingEntity subject) {
        return MentalControlRuntime.hasActiveControl(subject);
    }

    public static ControlSnapshot snapshot(LivingEntity subject) {
        return MentalControlRuntime.snapshot(subject);
    }

    public static boolean hasAiTakeover(LivingEntity subject) {
        return MentalControlRuntime.hasAiTakeover(subject);
    }

    public static void releaseByControllerSourceAndSubject(
            MinecraftServer server,
            UUID controllerId,
            Identifier source,
            UUID subjectId
    ) {
        MentalControlRuntime.releaseByControllerSourceAndSubject(server, controllerId, source, subjectId);
    }
}
