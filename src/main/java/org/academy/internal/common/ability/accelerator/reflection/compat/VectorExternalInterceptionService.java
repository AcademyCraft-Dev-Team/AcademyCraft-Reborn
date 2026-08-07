package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.accelerator.skills.lv3.VectorReduction;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class VectorExternalInterceptionService {
    private VectorExternalInterceptionService() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void recordIncomingAttribution(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer defender && event.getSource() != null) {
            VectorAttackAttributionResolver.rememberFromSource(defender, event.getSource());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer defender)
                || !(event.getAmount() > 0.0f)
                || !Float.isFinite(event.getAmount())
                || VectorReflection.Server.isLegitimateHealthMutation(defender)) {
            return;
        }

        if (VectorReflection.Server.canMaintainLinearReflectionLease(defender)
                || VectorReduction.Server.canMaintain(defender)) {
            var reflection = VectorReflection.Server.hurtServer(
                    defender,
                    (net.minecraft.server.level.ServerLevel) defender.level(),
                    event.getSource(),
                    event.getAmount()
            );
            if (reflection.getLeft()) {
                var remaining = reflection.getRight();
                if (remaining > 0.0f && Float.isFinite(remaining)) {
                    event.setAmount(remaining);
                } else {
                    event.setCanceled(true);
                }
                return;
            }
        }

        if (event.getSource().getDirectEntity() instanceof Projectile projectile) {
            if (VectorProjectileInterceptionService.intercept(defender, projectile)) {
                event.setCanceled(true);
            }
            return;
        }

        var classified = VectorExternalAttackClassifier.classify(
                defender,
                event.getSource(),
                event.getAmount()
        );
        if (classified.isEmpty()) {
            VectorCompatibilityDiagnostics.recordPassThrough(
                    defender,
                    event.getSource(),
                    VectorExternalAttackClassifier.rejectionReason(
                            defender,
                            event.getSource(),
                            event.getAmount()
                    )
            );
            return;
        }
        var attack = classified.get();
        if (VectorReflection.Server.isActive(defender)) {
            if (tryFullReflection(attack)) event.setCanceled(true);
            return;
        }
        if (VectorReduction.Server.isActive(defender)) {
            if (tryFullRefraction(attack)) event.setCanceled(true);
            return;
        }
        VectorCompatibilityDiagnostics.record(attack, "no_vector_defense");
    }

    public static boolean tryFullReflection(VectorAttackDescriptor attack) {
        if (attack == null) return false;
        var defender = attack.defender();
        if (!VectorReflection.Server.isActive(defender)) return false;
        if (VectorInterceptionTickets.wasCommitted(defender, attack.fingerprint())) return true;

        var mirrorPoint = defender.getBoundingBox().getCenter();
        var incomingDirection = attack.direction();
        var damageOnly = !attack.confidence().atLeast(VectorAttackConfidence.MEDIUM);
        var continuous = attack.executionPolicy().continuous();
        var leaseKey = VectorAttackFingerprint.computeLeaseKey(
                defender.getId(), attack.source(), incomingDirection);
        if (continuous
                && VectorReflection.Server.canMaintainLinearReflectionLease(defender)
                && VectorContinuousInterceptionLeases.consume(
                defender, leaseKey, VectorRedirectKind.REFLECTION, attack.damage())) {
            VectorInterceptionTickets.commit(defender, attack.fingerprint());
            var reflected = incomingDirection.scale(-1.0);
            VectorCompatibilityEffectLimiter.emit(defender, leaseKey, reflected, mirrorPoint);
            executeRedirect(
                    attack,
                    defender,
                    VectorRedirectKind.REFLECTION,
                    mirrorPoint,
                    reflected,
                    damageOnly,
                    "continuous_lease"
            );
            VectorDefenseFeedbackTickets.commitFull(defender, attack.source());
            return true;
        }

        var activationDamage = continuous
                ? attack.damage() * VectorContinuousInterceptionLeases.LEASE_HITS
                : attack.damage();
        if (!Float.isFinite(activationDamage)) activationDamage = attack.damage();
        var activated = tryActivate(
                VectorRedirectKind.REFLECTION,
                defender,
                activationDamage,
                mirrorPoint,
                incomingDirection,
                false
        );
        if (!activated && activationDamage > attack.damage() + 1.0E-5f) {
            activationDamage = attack.damage();
            activated = tryActivate(
                    VectorRedirectKind.REFLECTION,
                    defender,
                    activationDamage,
                    mirrorPoint,
                    incomingDirection,
                    false
            );
        }
        if (!activated) return false;

        VectorInterceptionTickets.commit(defender, attack.fingerprint());
        if (continuous) {
            VectorContinuousInterceptionLeases.create(
                    defender,
                    leaseKey,
                    VectorRedirectKind.REFLECTION,
                    activationDamage,
                    attack.damage()
            );
        }
        VectorCompatibilityEffectLimiter.emit(
                defender, leaseKey, incomingDirection.scale(-1.0), mirrorPoint);
        executeRedirect(
                attack,
                defender,
                VectorRedirectKind.REFLECTION,
                mirrorPoint,
                incomingDirection.scale(-1.0),
                damageOnly,
                continuous ? "continuous_prepaid" : "redirected"
        );
        VectorDefenseFeedbackTickets.commitFull(defender, attack.source());
        return true;
    }

    public static boolean tryFullRefraction(VectorAttackDescriptor attack) {
        if (attack == null) return false;
        var defender = attack.defender();
        if (!VectorReduction.Server.isActive(defender)
                || !attack.confidence().atLeast(VectorAttackConfidence.LOW)) return false;
        if (VectorInterceptionTickets.wasCommitted(defender, attack.fingerprint())) return true;

        var mirrorPoint = defender.getBoundingBox().getCenter();
        var incomingDirection = attack.direction();
        var redirected = VectorReduction.refractedDirection(defender.getLookAngle(), incomingDirection);
        if (redirected.lengthSqr() < 1.0E-8) return false;
        var damageOnly = !attack.confidence().atLeast(VectorAttackConfidence.MEDIUM);
        var continuous = attack.executionPolicy().continuous();
        var leaseKey = VectorAttackFingerprint.computeLeaseKey(
                defender.getId(), attack.source(), incomingDirection);
        if (continuous
                && VectorReduction.Server.canMaintain(defender)
                && VectorContinuousInterceptionLeases.consume(
                defender, leaseKey, VectorRedirectKind.REFRACTION, attack.damage())) {
            VectorInterceptionTickets.commit(defender, attack.fingerprint());
            VectorCompatibilityEffectLimiter.emit(defender, leaseKey, redirected, mirrorPoint);
            executeRedirect(
                    attack, defender, VectorRedirectKind.REFRACTION,
                    mirrorPoint, redirected, damageOnly, "continuous_lease");
            VectorDefenseFeedbackTickets.commitFull(defender, attack.source());
            return true;
        }

        var activationDamage = continuous
                ? attack.damage() * VectorContinuousInterceptionLeases.LEASE_HITS
                : attack.damage();
        if (!Float.isFinite(activationDamage)) activationDamage = attack.damage();
        var activated = tryActivate(
                VectorRedirectKind.REFRACTION,
                defender,
                activationDamage,
                mirrorPoint,
                incomingDirection,
                false
        );
        if (!activated && activationDamage > attack.damage() + 1.0E-5f) {
            activationDamage = attack.damage();
            activated = tryActivate(
                    VectorRedirectKind.REFRACTION,
                    defender,
                    activationDamage,
                    mirrorPoint,
                    incomingDirection,
                    false
            );
        }
        if (!activated) return false;

        VectorInterceptionTickets.commit(defender, attack.fingerprint());
        if (continuous) {
            VectorContinuousInterceptionLeases.create(
                    defender,
                    leaseKey,
                    VectorRedirectKind.REFRACTION,
                    activationDamage,
                    attack.damage()
            );
        }
        VectorCompatibilityEffectLimiter.emit(defender, leaseKey, redirected, mirrorPoint);
        executeRedirect(
                attack, defender, VectorRedirectKind.REFRACTION,
                mirrorPoint, redirected, damageOnly,
                continuous ? "continuous_prepaid" : "redirected");
        VectorDefenseFeedbackTickets.commitFull(defender, attack.source());
        return true;
    }

    public static boolean tryDirectRefraction(
            ServerPlayer defender,
            net.minecraft.world.damagesource.DamageSource source,
            float damage
    ) {
        if (!VectorReduction.Server.isActive(defender)) return false;
        var attack = VectorExternalAttackClassifier.classify(defender, source, damage).orElse(null);
        return attack != null && tryFullRefraction(attack);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        VectorInterceptionTickets.clear(player);
        VectorContinuousInterceptionLeases.clear(player);
        VectorAttackAttributionResolver.clear(player);
        VectorCompatibilityEffectLimiter.clear(player);
        VectorDefenseFeedbackTickets.clear(player);
        VectorReflectedDamageAccumulator.clear(player);
    }

    private static boolean tryActivate(
            VectorRedirectKind kind,
            ServerPlayer defender,
            float damage,
            Vec3 mirrorPoint,
            Vec3 incomingDirection,
            boolean emitFeedback
    ) {
        return kind == VectorRedirectKind.REFLECTION
                ? VectorReflection.Server.tryReflectLinearAttack(
                defender, damage, mirrorPoint, incomingDirection, emitFeedback)
                : VectorReduction.Server.tryRefractLinearAttack(
                defender, damage, mirrorPoint, incomingDirection, emitFeedback);
    }

    private static void executeRedirect(
            VectorAttackDescriptor attack,
            ServerPlayer defender,
            VectorRedirectKind kind,
            Vec3 mirrorPoint,
            Vec3 redirectedDirection,
            boolean damageOnly,
            String outcomePrefix
    ) {
        var plan = new VectorRedirectPlan(
                attack,
                defender,
                kind,
                mirrorPoint,
                redirectedDirection,
                attack.range(),
                damageOnly
        );
        if (damageOnly) {
            if (!kind.dealsRedirectedEntityDamage()) {
                VectorCompatibilityDiagnostics.record(attack, outcomePrefix + "_absorbed_no_return_damage");
                return;
            }
            var hits = VectorRedirectExecutor.executeDamageFallback(plan);
            VectorCompatibilityDiagnostics.record(
                    attack,
                    hits == 0
                            ? "no_return_target"
                            : outcomePrefix + "_damage_fallback_hits=" + hits
            );
            return;
        }
        if (!damageOnly && VectorMotionRedirects.redirectProfiledEntity(plan)) {
            VectorCompatibilityDiagnostics.record(attack, outcomePrefix + "_motion_redirect");
            return;
        }
        var execution = VectorRedirectExecutor.execute(plan);
        VectorRedirectEffectPacket.broadcast(plan, execution.redirectedLength());
        VectorCompatibilityDiagnostics.record(
                attack,
                damageOnly
                        ? outcomePrefix + "_damage_only"
                        : outcomePrefix + "_hits=" + execution.hitCount()
        );
    }
}
