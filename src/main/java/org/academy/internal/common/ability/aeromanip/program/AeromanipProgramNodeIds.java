package org.academy.internal.common.ability.aeromanip.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

/**
 * Stable identifiers for Aeromanip program nodes.
 */
public final class AeromanipProgramNodeIds {
    public static final Identifier CASTER = id("target/caster");
    public static final Identifier LOOK_TARGET = id("target/look_target");
    public static final Identifier AIRFLOW_PUSH = id("action/airflow_push");
    public static final Identifier CONVERGING_AIRFLOW = id("action/converging_airflow");
    public static final Identifier LAMINAR_CUT = id("action/laminar_cut");
    public static final Identifier PLACE_TEMPORARY_JET_NOZZLE =
            id("action/place_temporary_jet_nozzle");
    public static final Identifier FIRE_JETS = id("action/fire_jets");
    public static final Identifier OWNED_JET_NOZZLE_COUNT =
            id("query/owned_jet_nozzle_count");
    public static final Identifier LAUNCH_BLOCK_STRUCTURE =
            id("action/launch_block_structure");
    public static final Identifier STRUCTURE_EXTRACT = id("action/structure/extract");
    public static final Identifier STRUCTURE_CONTROL = id("action/structure/control");
    public static final Identifier STRUCTURE_MOVE = id("action/structure/move");
    public static final Identifier STRUCTURE_ROTATE = id("action/structure/rotate");
    public static final Identifier STRUCTURE_LAUNCH = id("action/structure/launch");
    public static final Identifier STRUCTURE_RELEASE = id("action/structure/release");
    public static final Identifier STRUCTURE_STATICIZE = id("action/structure/staticize");

    private AeromanipProgramNodeIds() {
    }

    private static Identifier id(String path) {
        return AcademyCraft.academy("program/aeromanip/" + path);
    }
}
