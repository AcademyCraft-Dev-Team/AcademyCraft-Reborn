package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.academy.internal.client.render.vfx.PlasmaVfx;
import org.academy.internal.client.renderer.entity.state.PlasmaRenderState;
import org.academy.internal.common.world.entity.skill.Plasma;

/**
 * Entity renderer intentionally stays empty; {@link PlasmaVfx} owns the visual.
 */
public final class PlasmaRenderer extends EntityRenderer<Plasma, PlasmaRenderState> {
    public PlasmaRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public PlasmaRenderState createRenderState() {
        return new PlasmaRenderState();
    }
}
