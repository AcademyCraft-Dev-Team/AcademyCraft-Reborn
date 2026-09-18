package org.academy.api.server.damage;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.server.ability.AbilityResourceAccount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.DoublePredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Resource-backed protection at health submission, shared by normal and true-health writers. */
public final class HealthLossGuards {
    private static final Map<LivingEntity, Map<Identifier, Protection>> PROTECTIONS = new WeakHashMap<>();
    private static final ThreadLocal<Set<LivingEntity>> WRITING = ThreadLocal.withInitial(
            () -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private HealthLossGuards() {}

    public record Resolution(double health, double absorbed, double cost) {}

    public static Resolution resolve(double current, double requested, double available, double costPerHealth) {
        if (!Double.isFinite(current) || !Double.isFinite(requested)
                || !Double.isFinite(available) || !Double.isFinite(costPerHealth) || costPerHealth <= 0) {
            return new Resolution(requested, 0, 0);
        }
        var loss = Math.max(0, current - Math.max(0, requested));
        var absorbed = Math.min(loss, Math.max(0, available) / costPerHealth);
        return new Resolution(Math.max(0, requested) + absorbed, absorbed, absorbed * costPerHealth);
    }

    public static void set(LivingEntity subject, Identifier source, AbilityResourceAccount account,
                           double costPerHealth, boolean enabled) {
        set(subject, source, account, costPerHealth, enabled, _ -> {});
    }

    /** Reports only successfully committed protection, never previews or refunded health writes. */
    public static void set(LivingEntity subject, Identifier source, AbilityResourceAccount account,
                           double costPerHealth, boolean enabled, Consumer<Resolution> onAbsorbed) {
        if (subject.level().isClientSide()) return;
        if (!enabled) {
            var protections = PROTECTIONS.get(subject);
            if (protections != null) {
                protections.remove(source);
                if (protections.isEmpty()) PROTECTIONS.remove(subject);
            }
            return;
        }
        if (!Double.isFinite(costPerHealth) || costPerHealth <= 0) throw new IllegalArgumentException("Invalid health cost");
        PROTECTIONS.computeIfAbsent(subject, _ -> new LinkedHashMap<>())
                .put(source, new Protection(account, costPerHealth, onAbsorbed));
    }

    /** Side-effect-free; suitable for admission checks. */
    public static float preview(LivingEntity subject, float current, float requested) {
        if (WRITING.get().contains(subject) || subject.level().isClientSide()) return requested;
        var protections = PROTECTIONS.get(subject);
        if (protections == null) return requested;
        current = effectiveCurrent(subject, current);
        var result = requested;
        for (var protection : protections.values()) {
            result = (float) resolve(current, result, protection.account.current(), protection.costPerHealth).health();
        }
        return result;
    }

    /** Writer returns true only when it accepted the protected value. Nested writers do not repay. */
    public static boolean commit(LivingEntity subject, float current, float requested, DoublePredicate writer) {
        if (subject.level().isClientSide() || WRITING.get().contains(subject)
                || !Float.isFinite(current) || !Float.isFinite(requested) || requested >= current) {
            return writer.test(requested);
        }
        var protections = PROTECTIONS.get(subject);
        if (protections == null || protections.isEmpty()) return writer.test(requested);
        current = effectiveCurrent(subject, current);
        if (requested >= current) return writer.test(requested);
        var paid = new ArrayList<Payment>();
        var result = requested;
        var success = false;
        WRITING.get().add(subject);
        try {
            for (var protection : java.util.List.copyOf(protections.values())) {
                var resolution = resolve(current, result, protection.account.current(), protection.costPerHealth);
                if (resolution.cost() > 0 && protection.account.tryConsume(resolution.cost())) {
                    paid.add(new Payment(protection, resolution));
                    result = (float) resolution.health();
                }
            }
            success = writer.test(result);
            return success;
        } finally {
            if (!success) for (var payment : paid) payment.protection.account.recover(payment.resolution.cost());
            WRITING.get().remove(subject);
            if (WRITING.get().isEmpty()) WRITING.remove();
            if (success) for (var payment : paid) payment.protection.onAbsorbed.accept(payment.resolution);
        }
    }

    /** Internal maintenance/reconciliation; callers must not use this to submit a new attack. */
    public static <T> T maintenance(LivingEntity subject, Supplier<T> action) {
        var added = WRITING.get().add(subject);
        try { return action.get(); }
        finally {
            if (added) WRITING.get().remove(subject);
            if (WRITING.get().isEmpty()) WRITING.remove();
        }
    }

    private static float effectiveCurrent(LivingEntity subject, float rawCurrent) {
        if (((org.academy.internal.common.entitycontrol.HealthOffsetAccess) subject).academy$getHealthOffset() == null) return rawCurrent;
        // An existing projection is already committed damage, not a fresh bill for this resource.
        return Math.min(rawCurrent, org.academy.internal.common.entitycontrol.TrueHealthOffsetRuntime.effectiveHealth(subject));
    }

    private record Protection(AbilityResourceAccount account, double costPerHealth, Consumer<Resolution> onAbsorbed) {}
    private record Payment(Protection protection, Resolution resolution) {}
}
