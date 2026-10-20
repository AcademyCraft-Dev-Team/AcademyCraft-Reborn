package org.academy.api.common.ability.accelerator;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructureTraction;

/** Shared kinetic-lift budgets for skills, precision programs and non-player ability actors. */
public final class KineticStructureControl {
    /** Total mass admitted at charge tier 0; each higher tier doubles the budget. */
    public static final int BASE_MASS_BUDGET = 128;
    /** The established light-body response reference used by the shared aeromanip control. */
    private static final double REFERENCE_MASS = 2.25;
    /** Solver gravity used for the explicit hover feed-forward. */
    public static final double GRAVITY = 9.81;

    private KineticStructureControl() {
    }

    /** Charge tiers 0/1/2 admit 128/256/512 total mass, matching the capture budget growth. */
    public static int massBudget(int tier) {
        return BASE_MASS_BUDGET << Math.clamp(tier, 0, 2);
    }

    /**
     * Whole-body mass sets the force budget, keeping orientation-free lift response constant:
     * gains and force scale together, while the level absolute force ceiling still applies.
     * Calibration against the legacy per-tick PD: the reference body keeps the same
     * 120/2.25 stiffness (force per block) and 36/2.25 damping used by the audited shared
     * control, so the equilibrium sag under gravity stays below a quarter block.
     */
    public static StructureTraction.Settings settings(double totalMass, int tier, int milestone, double range) {
        requireMass(totalMass);
        int level = Math.clamp(tier, 0, 2);
        double responseScale = Math.max(1, totalMass / REFERENCE_MASS);
        return new StructureTraction.Settings(range, massBudget(level),
                Math.min(StructurePhysicsControl.MAX_GAIN, responseScale * (milestone >= 2 ? 144 : 120)),
                Math.min(StructurePhysicsControl.MAX_GAIN, responseScale * 36),
                Math.min(2000 << level, 1000 * responseScale), 20);
    }

    /**
     * Raises an anchor target by the exact static sag of the PD spring under gravity, so the
     * equilibrium center of mass reaches the requested height. Acceptance tolerances stay
     * separate from this compensation and are never widened to hide a short lift.
     */
    public static Vec3 gravityCompensatedTarget(Vec3 target, double mass, double stiffness) {
        requireMass(mass);
        if (!Double.isFinite(stiffness) || stiffness <= 0.0) {
            throw new IllegalArgumentException("Hover stiffness must be finite and positive");
        }
        return target.add(0.0, mass * GRAVITY / stiffness, 0.0);
    }

    private static void requireMass(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Body mass must be finite and positive");
        }
    }
}
