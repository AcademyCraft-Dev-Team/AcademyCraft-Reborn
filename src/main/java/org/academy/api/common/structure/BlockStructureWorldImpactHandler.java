package org.academy.api.common.structure;

/** Applies an ability or program effect when a launched structure stops on world geometry. */
@FunctionalInterface
public interface BlockStructureWorldImpactHandler {
    void onImpact(BlockStructureWorldImpact impact);
}
