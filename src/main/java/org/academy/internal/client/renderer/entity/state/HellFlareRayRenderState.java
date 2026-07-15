package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Vector3f;

public class HellFlareRayRenderState extends EntityRenderState {
    public final Vector3f startPos = new Vector3f();
    public final Vector3f endPos = new Vector3f();
    public boolean isValid;
    public int targetId;
    public int phase;
    public Vector3f direction = new Vector3f();
    public float length;
    public float age;
}
