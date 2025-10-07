package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class HighSpeedElectronBeamRenderState extends LivingEntityRenderState {
    public float progress = 0;
    public float length = 50f;
    public boolean isCharging = false;
}