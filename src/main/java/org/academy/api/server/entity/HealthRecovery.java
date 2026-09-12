package org.academy.api.server.entity;

import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.common.entitycontrol.HealthOffsetAccess;
import org.academy.internal.common.entitycontrol.TrueHealthOffsetRuntime;
import org.academy.internal.common.world.damagesource.TrueDamageCompatibility;

/** Explicitly accepted server-side recovery, shared by skills and non-player entities. */
public final class HealthRecovery {
    private HealthRecovery() {
    }

    /**
     * Restores health through {@link LivingEntity#setHealth(float)}, releasing CTA/DM projection
     * only by the accepted gain. Ordinary health-write guards still apply; this does not invoke
     * the heal event or turn unrelated direct health writes into accepted healing.
     *
     * @return the actual health restored, or zero for invalid, dead or rejected targets
     */
    public static float restore(LivingEntity entity, float amount) {
        if (entity == null || entity.level().isClientSide() || !entity.isAlive()
                || !Float.isFinite(amount) || amount <= 0.0f
                || TrueDamageCompatibility.isHurtNotification(entity)) return 0.0f;
        var before = entity.getHealth();
        var maximum = entity.getMaxHealth();
        if (!Float.isFinite(before) || !Float.isFinite(maximum) || before <= 0.0f
                || before >= maximum) return 0.0f;
        var requested = before + Math.min(amount, maximum - before);
        var state = entity instanceof HealthOffsetAccess access ? access.academy$getHealthOffset() : null;
        if (state != null && state.dead) return 0.0f;
        var saved = state == null ? 0L : state.encodedOffset;
        if (state != null) state.heal(requested - before);
        try {
            EntityControlApi.runWithAcceptedRecovery(
                    entity, requested - before, () -> entity.setHealth(requested));
        } finally {
            if (state != null) {
                var observed = entity.getHealth();
                var gained = Float.isFinite(observed)
                        ? Math.clamp(observed - before, 0.0f, requested - before) : 0.0f;
                state.encodedOffset = saved;
                state.heal(gained);
                TrueHealthOffsetRuntime.persistAndSync(entity);
            }
        }
        var observed = entity.getHealth();
        return Float.isFinite(observed) ? Math.max(0.0f, observed - before) : 0.0f;
    }
}
