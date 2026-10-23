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
    public static final Identifier MAGNETIC_ENTITIES = id("target/magnetic_entities");
    public static final Identifier ENERGY_DETECTION = id("logic/energy_detection");
    public static final Identifier ENERGY_LEVEL = id("query/energy_level");
    public static final Identifier REDSTONE_DETECTION = id("logic/redstone_detection");
    public static final Identifier ARC_DISCHARGE = id("action/arc_discharge");
    public static final Identifier CHAIN_DISCHARGE = id("action/chain_discharge");
    public static final Identifier MAGNETIC_MOVE = id("action/magnetic_move");
    public static final Identifier CURRENT_RECHARGE = id("action/current_recharge");
    public static final Identifier STRUCTURE_EXTRACT = id("action/structure/extract");
    public static final Identifier STRUCTURE_CONTROL = id("action/structure/control");
    public static final Identifier STRUCTURE_MOVE = id("action/structure/move");
    public static final Identifier STRUCTURE_ROTATE = id("action/structure/rotate");
    public static final Identifier STRUCTURE_RESET_ORIENTATION = id("action/structure/reset_orientation");
    public static final Identifier STRUCTURE_SPIN = id("action/structure/spin");
    public static final Identifier STRUCTURE_LAUNCH = id("action/structure/launch");
    public static final Identifier STRUCTURE_RELEASE = id("action/structure/release");
    public static final Identifier STRUCTURE_STATICIZE = id("action/structure/staticize");

    private ElectromasterProgramNodeIds() {
    }

    private static Identifier id(String path) {
        return AcademyCraft.academy("program/electromaster/" + path);
    }
}
