package org.academy.api.server.damage;

import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.server.damage.DefenseFeedbackSuppressionRuntime;

/**
 * Marks a fully defended subject so hurt feedback (damage packet, hurt animation, hurt sound,
 * knockback and hurt time) is skipped for the remainder of the settlement tick.
 */
public final class DefenseFeedbackSuppression {
    public static final int DEFAULT_TICKS = 2;

    private DefenseFeedbackSuppression() {
    }

    public static void suppress(LivingEntity subject) {
        suppress(subject, DEFAULT_TICKS);
    }

    public static void suppress(LivingEntity subject, int ticks) {
        DefenseFeedbackSuppressionRuntime.suppress(subject, ticks);
    }

    public static boolean isSuppressed(LivingEntity subject) {
        return DefenseFeedbackSuppressionRuntime.isSuppressed(subject);
    }

    public static void clear(LivingEntity subject) {
        DefenseFeedbackSuppressionRuntime.clear(subject);
    }
}
