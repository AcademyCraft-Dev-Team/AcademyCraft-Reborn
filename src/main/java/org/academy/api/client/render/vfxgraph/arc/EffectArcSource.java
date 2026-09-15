package org.academy.api.client.render.vfxgraph.arc;

import org.academy.api.common.arc.ArcPath;

import java.util.List;

/**
 * Supplies procedural paths in effect-local coordinates to an electric_paths graph block.
 * Called on the simulation/render thread. Return an empty list when temporarily inactive.
 * Paths carry geometry, modifiers and branches; the graph controls width, palette and material.
 */
@FunctionalInterface
public interface EffectArcSource {
    List<ArcPath> sample(float timeSeconds);
}
