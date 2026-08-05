package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

public interface MentalControlMobAccess {
    @Nullable LivingEntity academy$getRawMentalControlTarget();
}
