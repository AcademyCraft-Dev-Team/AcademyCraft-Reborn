package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class HighSpeedElectronBeamRenderState extends LivingEntityRenderState {
    public float progress = 0;
    public float length = 50f;
    public float beamScale = 1.0f;
    public float visualSideOffset = 0.0f;
    public boolean isCharging = false;
}
