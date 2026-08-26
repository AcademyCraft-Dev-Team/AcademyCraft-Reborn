package org.academy.internal.common.ability.meltdowner.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

/**
 * Stable identifiers for Meltdowner program nodes.
 */
public final class MeltdownerProgramNodeIds {
    public static final Identifier CASTER = id("target/caster");
    public static final Identifier LOOK_TARGET = id("target/look_target");
    public static final Identifier ELECTRON_BEAM = id("action/electron_beam");
    public static final Identifier MINING_BEAM = id("action/mining_beam");
    public static final Identifier ATOMIC_JET = id("action/atomic_jet");

    private MeltdownerProgramNodeIds() {
    }

    private static Identifier id(String path) {
        return AcademyCraft.academy("program/meltdowner/" + path);
    }
}
