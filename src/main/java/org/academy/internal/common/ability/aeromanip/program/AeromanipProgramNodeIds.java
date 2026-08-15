package org.academy.internal.common.ability.aeromanip.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

/** Stable identifiers for Aeromanip program nodes. */
public final class AeromanipProgramNodeIds {
    public static final Identifier CASTER = id("target/caster");
    public static final Identifier LOOK_TARGET = id("target/look_target");
    public static final Identifier AIRFLOW_PUSH = id("action/airflow_push");
    public static final Identifier LAMINAR_CUT = id("action/laminar_cut");

    private AeromanipProgramNodeIds() {
    }

    private static Identifier id(String path) {
        return AcademyCraft.academy("program/aeromanip/" + path);
    }
}
