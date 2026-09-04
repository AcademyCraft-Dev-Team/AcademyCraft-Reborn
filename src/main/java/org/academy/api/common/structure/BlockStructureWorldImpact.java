package org.academy.api.common.structure;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Immutable description of a structure coming to rest against world collision. */
public record BlockStructureWorldImpact(
        BlockStructure structure,
        Entity controller,
        Vec3 position,
        Vec3 incomingVelocity,
        boolean horizontalCollision,
        boolean verticalCollision
) {
}
