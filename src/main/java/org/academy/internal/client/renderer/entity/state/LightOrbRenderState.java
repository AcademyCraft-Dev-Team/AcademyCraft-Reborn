package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Vector3f;

public class LightOrbRenderState extends EntityRenderState {
    public float scale;
    public Vector3f color = new Vector3f(1, 1, 1);
    public float xRot;
    public float yRot;
}