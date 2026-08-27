package org.academy.internal.common.ability.accelerator.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

/**
 * Stable identifiers for vector-manipulation program nodes.
 */
public final class AcceleratorProgramNodeIds {
    public static final Identifier CASTER = id("target/caster");
    public static final Identifier LOOK_TARGET = id("target/look_target");
    public static final Identifier INCOMING_PROJECTILES = id("target/incoming_projectiles");
    public static final Identifier APPLY_VECTOR = id("action/apply_vector");
    public static final Identifier KINETIC_IMPACT = id("action/kinetic_impact");
    public static final Identifier KINETIC_SHOCKWAVE = id("action/kinetic_shockwave");
    public static final Identifier REDIRECT_PROJECTILE = id("action/redirect_projectile");
    public static final Identifier DISPLACE_ENTITY = id("action/displace_entity");
    public static final Identifier DISPLACE_BLOCK = id("action/displace_block");

    private AcceleratorProgramNodeIds() {
    }

    private static Identifier id(String path) {
        return AcademyCraft.academy("program/accelerator/" + path);
    }
}
