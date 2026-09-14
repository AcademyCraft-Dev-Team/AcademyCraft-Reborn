package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.academy.internal.client.renderer.entity.state.RailgunRayRenderState;
import org.academy.internal.common.world.entity.skill.RailgunRay;

/** The resolved ray is visualized by the railgun_shot VFX graph. */
public final class RailgunRayRenderer extends EntityRenderer<RailgunRay, RailgunRayRenderState> {
    public RailgunRayRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public RailgunRayRenderState createRenderState() {
        return new RailgunRayRenderState();
    }
}
