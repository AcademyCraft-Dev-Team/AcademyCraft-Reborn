package org.academy.api.common.structure.upgrade;

import java.util.Objects;

/** Constructor-injected service bundle. Replacing one port does not replace storage or a solver. */
public record StructureUpgradeServices(StructureCapabilities capabilities,
                                       StructureExtraction extraction, StructureOrientation orientation,
                                       StructureProvenance provenance, StructureGridRestoration restoration,
                                       StructureDamage damage, StructureImpactFeed impacts,
                                       StructureFracture fracture) {
    public StructureUpgradeServices {
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(extraction, "extraction");
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(restoration, "restoration");
        Objects.requireNonNull(damage, "damage");
        Objects.requireNonNull(impacts, "impacts");
        Objects.requireNonNull(fracture, "fracture");
    }
}
