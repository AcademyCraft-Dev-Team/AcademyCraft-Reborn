package org.academy.api.common.structure;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Supplies a world-space force for one tick of controlled structure movement. */
@FunctionalInterface
public interface BlockStructureForceProvider {
    Vec3 force(BlockStructure structure, Entity controller);
}
