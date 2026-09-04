package org.academy.api.common.structure;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Stable manipulation surface shared by ability skills, precision-operation nodes,
 * and non-player systems.
 */
public interface BlockStructure {
    Entity asEntity();

    BlockStructureSnapshot snapshot();

    double mass();

    Vec3 position();

    void setPosition(Vec3 position);

    Vec3 velocity();

    boolean gravityEnabled();

    void setGravityEnabled(boolean enabled);

    void setVelocity(Vec3 velocity);

    void addImpulse(Vec3 impulse);

    float yawDegrees();

    void setYawDegrees(float yawDegrees);

    /** Moves the entity pose to the nearest placement-compatible grid pose. */
    void alignToGrid();

    /**
     * Ends active propulsion and dematerializes at the nearest grid pose. Natural cells marked
     * for gravity become vanilla falling blocks from the lowest layer upward; fixed building
     * cells restore directly. Cells that cannot enter the world become item drops.
     */
    void beginGravitySettlement();

    default BlockStructureRestoreResult restoreToGrid() {
        return restoreToGrid(BlockStructurePlacementPolicy.AIR_ONLY);
    }

    BlockStructureRestoreResult restoreToGrid(BlockStructurePlacementPolicy placementPolicy);

    /**
     * Ends the entity representation at the nearest grid pose. Placeable cells
     * become world blocks and every remaining cell becomes a dropped block item.
     */
    default BlockStructureSettlementResult settleToGrid() {
        return settleToGrid(BlockStructurePlacementPolicy.AIR_ONLY);
    }

    BlockStructureSettlementResult settleToGrid(
            BlockStructurePlacementPolicy placementPolicy);
}
