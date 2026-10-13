package org.academy.api.common.ability.aeromanip;

import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructureTraction;

/** Shared air-control budgets for skills, precision programs and non-player ability actors. */
public final class PneumaticStructureControl {
    private PneumaticStructureControl() {}

    /**
     * Total body mass determines response and admission. Every body receives one force budget,
     * regardless of cell count or the selected face. Mass-scaled gains preserve response for
     * compound bodies; the level's absolute force ceiling still limits lift and acceleration.
     */
    public static StructureTraction.Settings settings(double totalMass, int skillLevel, int milestone, double range) {
        requireMass(totalMass);
        int level = Math.clamp(skillLevel, 0, 2);
        // Preserve the established response of light bodies (including the 2.25-mass native
        // fixture); extend the same control response only as the whole body grows heavier.
        double responseScale = Math.max(1, totalMass / 2.25);
        return new StructureTraction.Settings(range, 128 << level,
                Math.min(StructurePhysicsControl.MAX_GAIN, responseScale * (milestone >= 2 ? 144 : 120)),
                Math.min(StructurePhysicsControl.MAX_GAIN, responseScale * 36),
                Math.min(2000 << level, 1000 * responseScale), 3);
    }

    /** A whole structure pays once per control interval, with cost increasing by total mass. */
    public static float resourceMultiplier(double totalMass) {
        requireMass(totalMass);
        return (float) Math.clamp(totalMass / 32, 1, 16);
    }

    private static void requireMass(double value) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Body mass must be finite and positive");
    }
}
