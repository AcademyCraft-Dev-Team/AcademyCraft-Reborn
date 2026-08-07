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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer defender)
                || !(event.getAmount() > 0.0f)
                || !Float.isFinite(event.getAmount())) {
            return;
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
        if (attack.attribution().nativeExact()) return;
        if (VectorInterceptionTickets.wasCommitted(defender, attack.fingerprint())) {
            event.setCanceled(true);
            VectorCompatibilityDiagnostics.record(attack, "duplicate_ticket");
            return;
        }

        var mirrorPoint = defender.getBoundingBox().getCenter();
        var incomingDirection = attack.direction();
        var damageOnly = !attack.confidence().atLeast(VectorAttackConfidence.MEDIUM);
        var continuous = attack.executionPolicy().continuous();
        var leaseKey = VectorAttackFingerprint.computeLeaseKey(
                defender.getId(),
                attack.source(),
                incomingDirection
        );
        var leasedKind = continuous
                ? consumeContinuousLease(defender, leaseKey, attack.damage())
                : null;
        if (leasedKind != null) {
            VectorInterceptionTickets.commit(defender, attack.fingerprint());
            event.setCanceled(true);
            var leasedDirection = redirectedDirection(leasedKind, defender, incomingDirection);
            VectorReflection.Server.spawnGlowCircle(defender, leasedDirection, mirrorPoint);
            VectorReflection.Server.playReflectionSound(defender);
            executeRedirect(
                    attack,
                    defender,
                    leasedKind,
                    mirrorPoint,
                    leasedDirection,
                    damageOnly,
                    "continuous_lease"
            );
            return;
        }

        VectorRedirectKind kind;
        Vec3 redirectedDirection;
        boolean activated;
        var activationDamage = continuous
                ? attack.damage() * VectorContinuousInterceptionLeases.LEASE_HITS
                : attack.damage();
        if (!Float.isFinite(activationDamage)) activationDamage = attack.damage();
        if (VectorReflection.Server.isActive(defender)) {
            kind = VectorRedirectKind.REFLECTION;
            redirectedDirection = redirectedDirection(kind, defender, incomingDirection);
            activated = VectorReflection.Server.tryReflectLinearAttack(
                    defender,
                    activationDamage,
                    mirrorPoint,
                    incomingDirection
            );
        } else if (VectorReduction.Server.isActive(defender)) {
            kind = VectorRedirectKind.REFRACTION;
            redirectedDirection = redirectedDirection(kind, defender, incomingDirection);
            activated = VectorReduction.Server.tryRefractLinearAttack(
                    defender,
                    activationDamage,
                    mirrorPoint,
                    incomingDirection
            );
        } else {
            VectorCompatibilityDiagnostics.record(attack, "no_vector_defense");
            return;
        }

        if (!activated) {
            VectorCompatibilityDiagnostics.record(attack, "insufficient_cp_or_inactive");
            return;
        }

        VectorInterceptionTickets.commit(defender, attack.fingerprint());
        if (continuous) {
            VectorContinuousInterceptionLeases.create(
                    defender,
                    leaseKey,
                    kind,
                    activationDamage,
                    attack.damage()
            );
        }
        event.setCanceled(true);
        executeRedirect(
                attack,
                defender,
                kind,
                mirrorPoint,
                redirectedDirection,
                damageOnly,
                continuous ? "continuous_prepaid" : "redirected"
        );
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        VectorInterceptionTickets.clear(player);
        VectorContinuousInterceptionLeases.clear(player);
    }

    private static VectorRedirectKind consumeContinuousLease(
            ServerPlayer defender,
            long leaseKey,
            float damage
    ) {
        if (VectorReflection.Server.canMaintainLinearReflectionLease(defender)
                && VectorContinuousInterceptionLeases.consume(
                defender, leaseKey, VectorRedirectKind.REFLECTION, damage)) {
            return VectorRedirectKind.REFLECTION;
        }
        if (VectorReduction.Server.canMaintain(defender)
                && VectorContinuousInterceptionLeases.consume(
                defender, leaseKey, VectorRedirectKind.REFRACTION, damage)) {
            return VectorRedirectKind.REFRACTION;
        }
        return null;
    }

    private static Vec3 redirectedDirection(
            VectorRedirectKind kind,
            ServerPlayer defender,
            Vec3 incomingDirection
    ) {
        return kind == VectorRedirectKind.REFLECTION
                ? incomingDirection.scale(-1.0)
                : VectorReduction.refractedDirection(defender.getLookAngle(), incomingDirection);
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
