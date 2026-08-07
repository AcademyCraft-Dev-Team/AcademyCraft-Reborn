package org.academy.internal.common.ability.accelerator.reflection.compat;

public enum VectorCompatibilityTier {
    NATIVE_EXACT,
    STANDARD_PROJECTILE,
    PROFILED_LINEAR,
    INFERRED_HITSCAN,
    DAMAGE_FALLBACK,
    PASS_THROUGH
}
