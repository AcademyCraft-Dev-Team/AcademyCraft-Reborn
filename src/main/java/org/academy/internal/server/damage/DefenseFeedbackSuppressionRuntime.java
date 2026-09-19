package org.academy.internal.server.damage;

import net.minecraft.world.entity.LivingEntity;

import java.util.WeakHashMap;

/** Server-thread, tick-scoped hurt feedback suppression for fully defended subjects. */
public final class DefenseFeedbackSuppressionRuntime {
    private static final WeakHashMap<LivingEntity, Long> SUPPRESSED_UNTIL = new WeakHashMap<>();

    private DefenseFeedbackSuppressionRuntime() {
    }

    public static void suppress(LivingEntity subject, int ticks) {
        if (subject == null || subject.level().isClientSide()) return;
        var until = subject.level().getGameTime() + Math.max(1, ticks);
        synchronized (SUPPRESSED_UNTIL) {
            SUPPRESSED_UNTIL.put(subject, until);
        }
    }

    public static boolean isSuppressed(LivingEntity subject) {
        if (subject == null) return false;
        synchronized (SUPPRESSED_UNTIL) {
            var until = SUPPRESSED_UNTIL.get(subject);
            if (until == null) return false;
            if (subject.level().getGameTime() > until) {
                SUPPRESSED_UNTIL.remove(subject);
                return false;
            }
            return true;
        }
    }

    public static void clear(LivingEntity subject) {
        if (subject == null) return;
        synchronized (SUPPRESSED_UNTIL) {
            SUPPRESSED_UNTIL.remove(subject);
        }
    }
}
