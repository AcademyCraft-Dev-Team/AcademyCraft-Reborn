package org.academy.internal.common.ability.mentalout.control;

/**
 * Bridge for the coordinate-agnostic movement controller used by cube mobs.
 */
public interface CubeMobMoveControlAccess {
    void academy$setMentalControlDirection(float yRot, boolean aggressive);

    void academy$setMentalControlMovement(double speedModifier);
}
