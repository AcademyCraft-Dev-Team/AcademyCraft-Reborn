package org.academy.api.common.entitycontrol;

import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.ability.mentalout.control.MentalPerceptionRuntime;

public final class MentalPerceptionApi {
    private MentalPerceptionApi() {
    }

    public static PerceptionDecision perceptionDecision(LivingEntity observer, LivingEntity target) {
        return MentalPerceptionRuntime.decision(observer, target);
    }

    public static boolean canPerceive(LivingEntity observer, LivingEntity target) {
        return perceptionDecision(observer, target) != PerceptionDecision.HIDDEN;
    }
}
