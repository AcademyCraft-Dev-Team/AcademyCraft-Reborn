package org.academy.api.common.structure.upgrade;

/** Shared host/structure region damage for skills and precision actions. Server thread only. */
public final class StructureDamages {
    private StructureDamages() {}
    public static StructureDamage service() {
        return org.academy.internal.common.structure.upgrade.damage.LiveStructureDamage.INSTANCE;
    }
}
