package org.academy.api.common.structure;

import net.minecraft.world.entity.Entity;
import org.academy.internal.common.structure.BlockStructureKineticRuntime;

/** Public entry point for applying transient, mass-aware propulsion to a structure. */
public final class BlockStructureKinetics {
    public static final double MINIMUM_COLLISION_DAMAGE_SPEED = 0.35;

    private BlockStructureKinetics() {
    }

    public static BlockStructureKineticHandle propel(
            BlockStructure structure,
            Entity controller,
            BlockStructureForceProvider forceProvider,
            BlockStructureKineticOptions options,
            BlockStructureImpactHandler impactHandler
    ) {
        return BlockStructureKineticRuntime.start(
                structure, controller, forceProvider, options, impactHandler);
    }

    public static boolean isActive(BlockStructure structure) {
        return BlockStructureKineticRuntime.isActive(structure);
    }

    public static void stop(BlockStructure structure) {
        BlockStructureKineticRuntime.stop(structure);
    }

    /** Shared mass/speed response used by skill-launched and freely moving structures. */
    public static float collisionDamage(int blockCount, double closingSpeed) {
        if (blockCount < 1 || !Double.isFinite(closingSpeed)
                || closingSpeed < MINIMUM_COLLISION_DAMAGE_SPEED) return 0.0f;
        return (float) Math.min(
                24.0,
                1.5 + 0.45 * Math.sqrt(blockCount) * closingSpeed
        );
    }

    /** Shared impact knockback before ability-specific power and target resistance. */
    public static double collisionKnockback(int blockCount, double closingSpeed) {
        if (blockCount < 1 || !Double.isFinite(closingSpeed)
                || closingSpeed < MINIMUM_COLLISION_DAMAGE_SPEED) return 0.0;
        return Math.min(
                2.4,
                0.25 + 0.35 * closingSpeed + 0.02 * Math.sqrt(blockCount)
        );
    }
}
