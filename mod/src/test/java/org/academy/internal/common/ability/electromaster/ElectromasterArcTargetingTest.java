package org.academy.internal.common.ability.electromaster;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElectromasterArcTargetingTest {
    @Test
    void protectsDroppedItemsAndExperienceOrbsFromArcDamage() {
        assertTrue(ElectromasterArcTargeting.isProtectedPickupType(ItemEntity.class));
        assertTrue(ElectromasterArcTargeting.isProtectedPickupType(ExperienceOrb.class));
    }

    @Test
    void keepsExistingCombatAndNonPickupTargetsEligible() {
        assertFalse(ElectromasterArcTargeting.isProtectedPickupType(LivingEntity.class));
        assertFalse(ElectromasterArcTargeting.isProtectedPickupType(Entity.class));
        assertFalse(ElectromasterArcTargeting.isProtectedPickupType(null));
    }
}
