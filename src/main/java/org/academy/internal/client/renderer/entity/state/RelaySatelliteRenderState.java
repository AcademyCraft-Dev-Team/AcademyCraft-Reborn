package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.joml.Vector3f;

public final class RelaySatelliteRenderState extends ItemEntityRenderState {
    public float spin;
    public boolean crashing;
    public boolean launching;
    public boolean showOrbitRing;
    public final Vector3f orbitAnchor = new Vector3f();
    /** World-space velocity while launching/crashing; used for trail orientation. */
    public final Vector3f trailVelocity = new Vector3f();
}
