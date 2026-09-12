package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Vector3f;

public final class RelaySatelliteRenderState extends EntityRenderState {
    public float spin;
    public boolean crashing;
    public boolean launching;
    /** World-space velocity while launching/crashing; used for trail orientation. */
    public final Vector3f trailVelocity = new Vector3f();
}
