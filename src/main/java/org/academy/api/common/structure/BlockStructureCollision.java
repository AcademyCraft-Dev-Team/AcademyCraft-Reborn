package org.academy.api.common.structure;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Consumer;

/** Supplies the compound collision geometry represented by a structure entity. */
public interface BlockStructureCollision {
    /** Emits only collision shapes that can intersect the requested world-space bounds. */
    void collectCollisionShapes(AABB bounds, Consumer<VoxelShape> output);

    /** Returns whether the entity bounds are resting on one of the structure's top faces. */
    boolean supports(AABB entityBounds, double tolerance);
}
