package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.entitycontrol.MentalPerceptionApi;
import org.academy.api.common.entitycontrol.PerceptionDecision;

/**
 * Binds the public {@link MentalPerceptionApi} facade to the internal perception runtime.
 */
public final class MentalPerceptionRuntimeBackend implements MentalPerceptionApi.Backend {
    @Override
    public PerceptionDecision decision(LivingEntity observer, LivingEntity target) {
        return MentalPerceptionRuntime.decision(observer, target);
    }
}
