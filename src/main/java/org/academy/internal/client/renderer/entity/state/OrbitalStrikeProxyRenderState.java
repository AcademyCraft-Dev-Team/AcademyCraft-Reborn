package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;

public final class OrbitalStrikeProxyRenderState extends ItemEntityRenderState {
    public boolean firing;
    public boolean hyper;
    public BlockPos impact = BlockPos.ZERO;
    public float spin;
    /** Entity-local vector from proxy to impact (full downlink length; not range-capped). */
    public final Vector3f beamEndRelative = new Vector3f();
}
