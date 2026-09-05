package org.academy.api.common.damage;

import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.world.damagesource.ReactionSlowdownRuntime;

/** A non-stacking, owner-held two-thirds reaction-speed contribution. Close on skill termination. */
public interface ReactionSlowdown extends AutoCloseable {
    static ReactionSlowdown acquire(LivingEntity target) {
        return ReactionSlowdownRuntime.acquire(target);
    }

    @Override void close();
}
