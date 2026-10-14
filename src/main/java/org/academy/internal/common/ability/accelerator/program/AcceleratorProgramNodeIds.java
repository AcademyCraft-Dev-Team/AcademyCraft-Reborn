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
    public static final Identifier OBSERVATION_INVERSE = id("logic/observation_inverse");
    public static final Identifier VECTOR_REFLECTION = id("logic/vector_reflection");
    public static final Identifier APPLY_VECTOR = id("action/apply_vector");
    public static final Identifier REWRITE_MOTION = id("action/rewrite_motion");
    public static final Identifier KINETIC_IMPACT = id("action/kinetic_impact");
    public static final Identifier KINETIC_SHOCKWAVE = id("action/kinetic_shockwave");
    public static final Identifier REDIRECT_PROJECTILE = id("action/redirect_projectile");
    public static final Identifier DISPLACE_ENTITY = id("action/displace_entity");
    public static final Identifier DISPLACE_BLOCK = id("action/displace_block");
    public static final Identifier STRUCTURE_EXTRACT = id("action/structure/extract");
    public static final Identifier STRUCTURE_CONTROL = id("action/structure/control");
    public static final Identifier STRUCTURE_MOVE = id("action/structure/move");
    public static final Identifier STRUCTURE_ROTATE = id("action/structure/rotate");
    public static final Identifier STRUCTURE_LAUNCH = id("action/structure/launch");
    public static final Identifier STRUCTURE_RELEASE = id("action/structure/release");
    public static final Identifier STRUCTURE_STATICIZE = id("action/structure/staticize");

    private AcceleratorProgramNodeIds() {
    }

    private static Identifier id(String path) {
        return AcademyCraft.academy("program/accelerator/" + path);
    }
}
