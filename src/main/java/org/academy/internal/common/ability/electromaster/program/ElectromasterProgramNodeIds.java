package org.academy.internal.common.ability.electromaster.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

/**
 * Stable identifiers for Electromaster program nodes.
 */
public final class ElectromasterProgramNodeIds {
    public static final Identifier CASTER = id("target/caster");
    public static final Identifier LOOK_TARGET = id("target/look_target");
    public static final Identifier CHARGEABLE_BLOCKS = id("target/chargeable_blocks");
    public static final Identifier ENERGY_DETECTION = id("logic/energy_detection");
    public static final Identifier REDSTONE_DETECTION = id("logic/redstone_detection");
    public static final Identifier ARC_DISCHARGE = id("action/arc_discharge");
    public static final Identifier MAGNETIC_MOVE = id("action/magnetic_move");
    public static final Identifier CURRENT_RECHARGE = id("action/current_recharge");

    private ElectromasterProgramNodeIds() {
    }

    private static Identifier id(String path) {
        return AcademyCraft.academy("program/electromaster/" + path);
    }
}
