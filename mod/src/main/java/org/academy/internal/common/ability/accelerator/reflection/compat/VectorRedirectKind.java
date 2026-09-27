package org.academy.internal.common.ability.accelerator.reflection.compat;

public enum VectorRedirectKind {
    REFLECTION,
    REFRACTION;

    public boolean dealsRedirectedEntityDamage() {
        return this == REFLECTION;
    }
}
