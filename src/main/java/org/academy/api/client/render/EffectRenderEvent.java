package org.academy.api.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.neoforged.bus.api.Event;

public final class EffectRenderEvent extends Event {
    private final PoseStack poseStack;
    private final MultiBufferSource bufferSource;
    private final int packedLight;
    private final PlayerRenderState renderState;
    private final float yRot;
    private final float xRot;

    public EffectRenderEvent(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, PlayerRenderState renderState, float yRot, float xRot) {
        this.poseStack = poseStack;
        this.bufferSource = bufferSource;
        this.packedLight = packedLight;
        this.renderState = renderState;
        this.yRot = yRot;
        this.xRot = xRot;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public MultiBufferSource getBufferSource() {
        return bufferSource;
    }

    public int getPackedLight() {
        return packedLight;
    }

    public PlayerRenderState getRenderState() {
        return renderState;
    }

    public float getYRot() {
        return yRot;
    }

    public float getXRot() {
        return xRot;
    }
}