package org.academy.api.client.render.vfxgraph.runtime;

import org.joml.Matrix4f;

/**
 * Entity-neutral world attachment for graph-authored shapes. An implementation may sample a
 * skeleton bone, an arbitrary entity, a tool, or a fixed transform; no player is required.
 */
@FunctionalInterface
public interface EffectAnchor {
    /** Write a uniformly scaled world transform; return false when the attachment no longer exists. */
    boolean sample(Matrix4f transform, float partialTick);
}
