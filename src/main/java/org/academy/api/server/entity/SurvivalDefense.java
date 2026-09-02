package org.academy.api.server.entity;

import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.server.entity.SurvivalDefenseRuntime;

/**
 * Public, entity-agnostic entry point for maintaining authoritative survival state.
 *
 * <p>Skills acquire an owner-bound lease and release that lease before they intentionally allow
 * death. Multiple skills may protect the same player or non-player entity without being able to
 * release one another's contribution.</p>
 */
public final class SurvivalDefense {
    private static final StackWalker CALLER_WALKER = StackWalker.getInstance(
            StackWalker.Option.RETAIN_CLASS_REFERENCE
    );

    private SurvivalDefense() {
    }

    public static SurvivalDefenseLease acquire(
            LivingEntity entity,
            SurvivalDefenseProfile profile
    ) {
        var owner = CALLER_WALKER.walk(frames -> frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .dropWhile(type -> type == SurvivalDefense.class)
                .findFirst()
                .orElse(SurvivalDefense.class));
        return SurvivalDefenseRuntime.acquire(entity, profile, owner);
    }

    public static int strength(LivingEntity entity, SurvivalDefenseAspect aspect) {
        return SurvivalDefenseRuntime.strength(entity, aspect);
    }

    public static boolean protects(LivingEntity entity, SurvivalDefenseAspect aspect) {
        return strength(entity, aspect) > 0;
    }

    /**
     * Returns whether the current defense meets or exceeds a competing mutation's strength.
     */
    public static boolean blocks(
            LivingEntity entity,
            SurvivalDefenseAspect aspect,
            int competingStrength
    ) {
        if (competingStrength < 0) {
            throw new IllegalArgumentException("Competing strength must be non-negative.");
        }
        var defenseStrength = strength(entity, aspect);
        return defenseStrength > 0 && defenseStrength >= competingStrength;
    }

    public static float minimumHealth(LivingEntity entity) {
        return SurvivalDefenseRuntime.minimumHealth(entity);
    }

    /**
     * Applies the effective profile immediately. This cannot create or remove protection.
     */
    public static boolean repairNow(LivingEntity entity) {
        return SurvivalDefenseRuntime.maintain(entity);
    }

    public static float clampHealthWrite(LivingEntity entity, float requestedHealth) {
        return SurvivalDefenseRuntime.clampHealthWrite(entity, requestedHealth);
    }

    public static float applyHealthReadGuard(LivingEntity entity, float observedHealth) {
        return SurvivalDefenseRuntime.applyHealthReadGuard(entity, observedHealth);
    }
}
