package org.academy.api.common.structure;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Immutable description of a swept structure-to-entity collision. */
public record BlockStructureImpact(
        BlockStructure structure,
        Entity controller,
        Entity target,
        Vec3 point,
        Vec3 normal,
        Vec3 movement,
        double closingSpeed
) {
}
