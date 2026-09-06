package org.academy.internal.client.renderer.blockentity.state;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.phys.Vec3;

public final class EnergyLaserTowerRenderState extends BlockEntityRenderState {
    public boolean beamActive;
    /** Relative end point from block origin; skyward default when no satellite entity is loaded. */
    public Vec3 beamEndRelative = new Vec3(0.5, 64.0, 0.5);
}
