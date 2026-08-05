package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.academy.internal.client.renderer.entity.state.HighSpeedElectronBeamRenderState;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;

public class HighSpeedElectronBeamRenderer extends EntityRenderer<HighSpeedElectronBeam, HighSpeedElectronBeamRenderState> {
    public HighSpeedElectronBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public HighSpeedElectronBeamRenderState createRenderState() {
        return new HighSpeedElectronBeamRenderState();
    }
}
