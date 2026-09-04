package org.academy.api.common.structure;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.structure.BlockStructureKineticRuntime;

import java.util.Objects;

/** Public entry point for applying transient, mass-aware propulsion to a structure. */
public final class BlockStructureKinetics {
    public static final double MINIMUM_COLLISION_DAMAGE_SPEED = 0.35;
    private static final double HOLD_POSITION_GAIN = 0.22;
    private static final double HOLD_VELOCITY_DAMPING = 0.65;

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

    /**
     * Moves a structure toward a fixed world position and keeps it suspended there for the
     * supplied session duration. Holding always suppresses automatic settlement while active.
     */
    public static BlockStructureKineticHandle holdAt(
            BlockStructure structure,
            Entity controller,
            Vec3 targetPosition,
            BlockStructureKineticOptions options,
            BlockStructureImpactHandler impactHandler
    ) {
        Objects.requireNonNull(targetPosition, "targetPosition");
        Objects.requireNonNull(options, "options");
        if (!Double.isFinite(targetPosition.x)
                || !Double.isFinite(targetPosition.y)
                || !Double.isFinite(targetPosition.z)) {
            throw new IllegalArgumentException("targetPosition must be finite");
        }
        var holdingOptions = options.preventSettlementWhileActive()
                ? options
                : new BlockStructureKineticOptions(
                options.durationTicks(),
                options.maximumSpeed(),
                options.impactCooldownTicks(),
                options.gravityAfter(),
                true
        );
        return propel(
                structure,
                controller,
                (moving, _) -> targetPosition.subtract(moving.position())
                        .scale(HOLD_POSITION_GAIN)
                        .subtract(moving.velocity().scale(HOLD_VELOCITY_DAMPING))
                        .scale(moving.mass()),
                holdingOptions,
                impactHandler
        );
    }

    /**
     * Launches a structure with an initial velocity while retaining swept entity impacts.
     * The world-impact callback runs once on the first collision with world blocks.
     */
    public static BlockStructureKineticHandle launch(
            BlockStructure structure,
            Entity controller,
            Vec3 initialVelocity,
            BlockStructureKineticOptions options,
            BlockStructureImpactHandler entityImpactHandler,
            BlockStructureWorldImpactHandler worldImpactHandler
    ) {
        return BlockStructureKineticRuntime.launch(
                structure,
                controller,
                initialVelocity,
                options,
                entityImpactHandler,
                worldImpactHandler
        );
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
