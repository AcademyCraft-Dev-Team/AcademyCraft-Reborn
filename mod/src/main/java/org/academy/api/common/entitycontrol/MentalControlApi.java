package org.academy.api.common.entitycontrol;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MentalControlApi {
    /**
     * Implementation-provided backend; registered once during common setup.
     */
    public interface Backend {
        ControlHandle apply(ControlRequest request);

        void registerAdapter(Identifier id, int priority, MentalControlAdapter adapter);

        Optional<MentalControlAdapter> findAdapter(LivingEntity subject);

        Optional<MentalControlAdapter> findAdapter(LivingEntity subject, ControlCapability capability);

        ControlEvaluation evaluate(LivingEntity subject, ControlCapability capability);

        Optional<ControlInspection> inspect(LivingEntity subject, ControlCapability capability);

        Optional<ControlDirective> effectiveDirective(LivingEntity subject, ControlCapability capability);

        AttackDecision attackDecision(LivingEntity subject, LivingEntity target);

        AttackDecision allianceDecision(LivingEntity subject, LivingEntity target);

        boolean isHostilityAllowed(LivingEntity subject, Entity target);

        void enforceTargetWhitelist(Mob subject);

        boolean isBossCost(LivingEntity subject);

        long resistanceRemainingTicks(LivingEntity subject);

        boolean hasActiveControl(LivingEntity subject);

        ControlSnapshot snapshot(LivingEntity subject);

        boolean hasAiTakeover(LivingEntity subject);

        void releaseByControllerSourceAndSubject(
                MinecraftServer server,
                UUID controllerId,
                Identifier source,
                UUID subjectId
        );
    }

    private static volatile @Nullable Backend backend;

    private MentalControlApi() {
    }

    public static void registerBackend(Backend backend) {
        MentalControlApi.backend = Objects.requireNonNull(backend);
    }

    public static ControlHandle apply(ControlRequest request) {
        return requireBackend().apply(request);
    }

    public static void registerAdapter(Identifier id, int priority, MentalControlAdapter adapter) {
        requireBackend().registerAdapter(id, priority, adapter);
    }

    public static Optional<MentalControlAdapter> findAdapter(LivingEntity subject) {
        return requireBackend().findAdapter(subject);
    }

    public static Optional<MentalControlAdapter> findAdapter(
            LivingEntity subject,
            ControlCapability capability
    ) {
        return requireBackend().findAdapter(subject, capability);
    }

    public static ControlEvaluation evaluate(LivingEntity subject, ControlCapability capability) {
        return requireBackend().evaluate(subject, capability);
    }

    public static Optional<ControlInspection> inspect(
            LivingEntity subject,
            ControlCapability capability
    ) {
        return requireBackend().inspect(subject, capability);
    }

    public static boolean supports(LivingEntity subject, ControlCapability capability) {
        return evaluate(subject, capability).supported();
    }

    public static Optional<ControlDirective> effectiveDirective(
            LivingEntity subject,
            ControlCapability capability
    ) {
        return requireBackend().effectiveDirective(subject, capability);
    }

    public static AttackDecision attackDecision(LivingEntity subject, LivingEntity target) {
        return requireBackend().attackDecision(subject, target);
    }

    public static AttackDecision allianceDecision(LivingEntity subject, LivingEntity target) {
        return requireBackend().allianceDecision(subject, target);
    }

    public static boolean isHostilityAllowed(LivingEntity subject, Entity target) {
        return requireBackend().isHostilityAllowed(subject, target);
    }

    public static void enforceTargetWhitelist(Mob subject) {
        requireBackend().enforceTargetWhitelist(subject);
    }

    public static boolean isBossCost(LivingEntity subject) {
        return requireBackend().isBossCost(subject);
    }

    /**
     * Remaining server game ticks of mental resistance, including manual player break-free.
     */
    public static long resistanceRemainingTicks(LivingEntity subject) {
        return requireBackend().resistanceRemainingTicks(subject);
    }

    /**
     * Includes tagged resistance before its automatic break-free timer has elapsed.
     */
    public static boolean hasMentalProtection(LivingEntity subject) {
        return subject != null && !MentalImmunity.isSuppressed(subject)
                && (MentalControlRuntime.isProtectedTarget(subject)
                || subject.getType().builtInRegistryHolder().is(MentalControlTags.RESISTANCE));
    }

    /**
     * Clears all mental defenses and blocks their registration for this entity's current lifecycle.
     */
    public static void suppressMentalProtection(LivingEntity subject) {
        MentalImmunity.suppress(subject);
    }

    public static boolean isMentalProtectionSuppressed(LivingEntity subject) {
        return MentalImmunity.isSuppressed(subject);
    }

    /**
     * Allows sources to register again; removed instance sources and old resistance do not return.
     */
    public static void restoreMentalProtection(LivingEntity subject) {
        MentalImmunity.restore(subject);
    }

    public static boolean hasActiveControl(LivingEntity subject) {
        return requireBackend().hasActiveControl(subject);
    }

    public static ControlSnapshot snapshot(LivingEntity subject) {
        return requireBackend().snapshot(subject);
    }

    public static boolean hasAiTakeover(LivingEntity subject) {
        return requireBackend().hasAiTakeover(subject);
    }

    public static void releaseByControllerSourceAndSubject(
            MinecraftServer server,
            UUID controllerId,
            Identifier source,
            UUID subjectId
    ) {
        requireBackend().releaseByControllerSourceAndSubject(server, controllerId, source, subjectId);
    }

    private static Backend requireBackend() {
        var current = backend;
        if (current == null) throw new IllegalStateException("Mental control backend is not registered");
        return current;
    }
}
