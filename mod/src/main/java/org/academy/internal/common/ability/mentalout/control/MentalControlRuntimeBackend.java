package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.academy.api.common.entitycontrol.*;
import org.academy.internal.common.ability.mentalout.MentalResistanceManager;

import java.util.Optional;
import java.util.UUID;

/**
 * Binds the public {@link MentalControlApi} facade to the internal control runtime.
 */
public final class MentalControlRuntimeBackend implements MentalControlApi.Backend {
    @Override
    public ControlHandle apply(ControlRequest request) {
        return MentalControlRuntime.apply(request);
    }

    @Override
    public void registerAdapter(Identifier id, int priority, MentalControlAdapter adapter) {
        MentalControlRuntime.registerAdapter(id, priority, adapter);
    }

    @Override
    public Optional<MentalControlAdapter> findAdapter(LivingEntity subject) {
        return MentalControlRuntime.findAdapter(subject);
    }

    @Override
    public Optional<MentalControlAdapter> findAdapter(LivingEntity subject, ControlCapability capability) {
        return MentalControlRuntime.findAdapter(subject, capability);
    }

    @Override
    public ControlEvaluation evaluate(LivingEntity subject, ControlCapability capability) {
        return MentalControlRuntime.evaluate(subject, capability);
    }

    @Override
    public Optional<ControlInspection> inspect(LivingEntity subject, ControlCapability capability) {
        return MentalControlRuntime.inspect(subject, capability);
    }

    @Override
    public Optional<ControlDirective> effectiveDirective(LivingEntity subject, ControlCapability capability) {
        return MentalControlRuntime.effectiveDirective(subject, capability);
    }

    @Override
    public AttackDecision attackDecision(LivingEntity subject, LivingEntity target) {
        return MentalControlRuntime.attackDecision(subject, target);
    }

    @Override
    public AttackDecision allianceDecision(LivingEntity subject, LivingEntity target) {
        return MentalControlRuntime.allianceDecision(subject, target);
    }

    @Override
    public boolean isHostilityAllowed(LivingEntity subject, Entity target) {
        return MentalControlRuntime.isHostilityAllowed(subject, target);
    }

    @Override
    public void enforceTargetWhitelist(Mob subject) {
        MentalControlRuntime.enforceTargetWhitelist(subject);
    }

    @Override
    public boolean isBossCost(LivingEntity subject) {
        return MentalControlRuntime.isBossCost(subject);
    }

    @Override
    public long resistanceRemainingTicks(LivingEntity subject) {
        return MentalResistanceManager.remainingTicks(subject);
    }

    @Override
    public boolean hasActiveControl(LivingEntity subject) {
        return MentalControlRuntime.hasActiveControl(subject);
    }

    @Override
    public ControlSnapshot snapshot(LivingEntity subject) {
        return MentalControlRuntime.snapshot(subject);
    }

    @Override
    public boolean hasAiTakeover(LivingEntity subject) {
        return MentalControlRuntime.hasAiTakeover(subject);
    }

    @Override
    public void releaseByControllerSourceAndSubject(
            MinecraftServer server,
            UUID controllerId,
            Identifier source,
            UUID subjectId
    ) {
        MentalControlRuntime.releaseByControllerSourceAndSubject(server, controllerId, source, subjectId);
    }
}
