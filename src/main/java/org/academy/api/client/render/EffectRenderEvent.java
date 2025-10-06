package org.academy.api.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.neoforged.bus.api.Event;

public final class EffectRenderEvent extends Event {
    private final PoseStack poseStack;
    private final SubmitNodeCollector submitNodeCollector;
    private final int packedLight;
    private final AvatarRenderState renderState;
    private final float yRot;
    private final float xRot;

    public EffectRenderEvent(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AvatarRenderState renderState, float yRot, float xRot) {
        this.poseStack = poseStack;
        this.submitNodeCollector = submitNodeCollector;
        this.packedLight = packedLight;
        this.renderState = renderState;
        this.yRot = yRot;
        this.xRot = xRot;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public SubmitNodeCollector getSubmitNodeCollector() {
        return submitNodeCollector;
    }

    public int getPackedLight() {
        return packedLight;
    }

    public AvatarRenderState getRenderState() {
        return renderState;
    }

    public float getYRot() {
        return yRot;
    }

    public float getXRot() {
        return xRot;
    }
}