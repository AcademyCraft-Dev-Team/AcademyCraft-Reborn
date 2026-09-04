package org.academy.api.common.structure;

/** Applies ability-specific effects to a generic structure impact. */
@FunctionalInterface
public interface BlockStructureImpactHandler {
    void onImpact(BlockStructureImpact impact);
}
