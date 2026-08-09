package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.Vec3;

public class RailgunRayRenderState extends LivingEntityRenderState {
    public float length;
    public float widthMultiplier = 1.0f;
    public boolean reflectionActive;
    public float reflectionDistance;
    public float reflectionReturnLength;
    public Vec3 reflectionReturnDirection = Vec3.ZERO;
}
