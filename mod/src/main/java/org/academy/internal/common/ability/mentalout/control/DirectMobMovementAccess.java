package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.world.phys.Vec3;

/**
 * Bridge for mobs whose movement is driven without {@code PathNavigation}.
 */
public interface DirectMobMovementAccess {
    void academy$moveDirectly(Vec3 destination, double speedModifier);

    void academy$stopDirectMovement();
}
