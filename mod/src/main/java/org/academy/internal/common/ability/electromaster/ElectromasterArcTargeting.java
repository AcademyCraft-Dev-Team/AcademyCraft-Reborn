package org.academy.internal.common.ability.electromaster;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import org.academy.internal.common.world.entity.EntityTypes;

public final class ElectromasterArcTargeting {
    private ElectromasterArcTargeting() {
    }

    public static boolean canDamageAlongArc(Entity entity) {
        return entity != null
                && entity.getType() != EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get()
                && !isProtectedPickupType(entity.getClass());
    }

    static boolean isProtectedPickupType(Class<?> entityClass) {
        return entityClass != null
                && (ItemEntity.class.isAssignableFrom(entityClass)
                || ExperienceOrb.class.isAssignableFrom(entityClass));
    }
}
