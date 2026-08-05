package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.academy.internal.client.renderer.entity.state.ArcEffectRenderState;
import org.academy.internal.common.world.entity.skill.ArcEffect;

public class ArcEffectRenderer extends EntityRenderer<ArcEffect, ArcEffectRenderState> {
    public ArcEffectRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ArcEffectRenderState createRenderState() {
        return new ArcEffectRenderState();
    }
}
