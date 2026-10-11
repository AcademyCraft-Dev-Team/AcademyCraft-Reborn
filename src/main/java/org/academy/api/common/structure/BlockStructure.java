package org.academy.api.common.structure;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Stable manipulation surface shared by ability skills, precision-operation nodes,
 * and non-player systems.
 */
public interface BlockStructure {
    Entity asEntity();

    /** Legacy entities keep their UUID and use identity generation zero. */
    default StructureRef reference() {
        var entity = asEntity();
        return new StructureRef(entity.getUUID(), entity.level().dimension().identifier(),
                StructureRef.LEGACY_ENTITY_BACKEND, 0L);
    }

    /** Converts the legacy pivot-based yaw to the world pose of the fixed local grid. */
    default StructurePose pose() {
        var snapshot = snapshot();
        return StructurePose.fromYawAroundPivot(position(), yawDegrees(),
                new Vec3(snapshot.pivotX(), 0.0, snapshot.pivotZ()));
    }

    default Vec3 velocityPerSecond() {
        return LegacyMotionUnits.velocityPerSecond(velocity());
    }

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
     * cells restore directly. Failed admission or placement retains the complete structure.
     * Blocks carrying block-entity data always restore as fixed cells.
     */
    void beginGravitySettlement();

    default BlockStructureRestoreResult restoreToGrid() {
        return restoreToGrid(BlockStructurePlacementPolicy.AIR_ONLY);
    }

    BlockStructureRestoreResult restoreToGrid(BlockStructurePlacementPolicy placementPolicy);

    /**
     * Attempts to end the entity representation at the nearest grid pose. Every cell must
     * be placeable; otherwise the complete structure remains available for a later attempt.
     */
    default BlockStructureSettlementResult settleToGrid() {
        return settleToGrid(BlockStructurePlacementPolicy.AIR_ONLY);
    }

    BlockStructureSettlementResult settleToGrid(
            BlockStructurePlacementPolicy placementPolicy);
}
