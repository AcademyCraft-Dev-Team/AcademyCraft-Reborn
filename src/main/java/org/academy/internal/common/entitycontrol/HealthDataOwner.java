package org.academy.internal.common.entitycontrol;

import net.minecraft.world.entity.LivingEntity;

/** Binds the authoritative vanilla health data item to its entity during data construction. */
public interface HealthDataOwner {
    void academy$bindHealthOwner(LivingEntity owner);
}
