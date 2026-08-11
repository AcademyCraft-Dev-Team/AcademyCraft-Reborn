package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorRedirectKind;

public class GlowCircleRenderState extends LivingEntityRenderState {
    public float radius;
    public int ownerEntityId = -1;
    public VectorRedirectKind redirectKind = VectorRedirectKind.REFLECTION;
}
