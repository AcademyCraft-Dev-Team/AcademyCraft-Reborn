package org.academy.internal.client.renderer.blockentity.state;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.phys.Vec3;

public final class EnergyLaserTowerRenderState extends BlockEntityRenderState {
    public boolean beamActive;
    /** Relative end point from block origin; skyward default when no satellite target. */
    public Vec3 beamEndRelative = new Vec3(0.5, 64.0, 0.5);
    public boolean showSkySatellite;
    public boolean orbitHyper;
    /** Relative position of the sky satellite model from block origin. */
    public Vec3 skySatelliteRelative = Vec3.ZERO;
    public float skySatelliteSpin;
    public final ItemStackRenderState skySatelliteItem = new ItemStackRenderState();
}
