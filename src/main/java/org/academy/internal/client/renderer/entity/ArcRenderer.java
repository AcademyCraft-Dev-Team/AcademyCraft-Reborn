package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.academy.internal.client.renderer.entity.state.ArcRenderState;
import org.academy.internal.common.world.entity.skill.Arc;

public class ArcRenderer extends EntityRenderer<Arc, ArcRenderState> {
    public ArcRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ArcRenderState createRenderState() {
        return new ArcRenderState();
    }
}
