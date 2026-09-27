package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.academy.internal.client.renderer.entity.state.SmokeRenderState;
import org.academy.internal.common.world.entity.skill.Smoke;

public class SmokeRenderer extends EntityRenderer<Smoke, SmokeRenderState> {
    public SmokeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public SmokeRenderState createRenderState() {
        return new SmokeRenderState();
    }
}
