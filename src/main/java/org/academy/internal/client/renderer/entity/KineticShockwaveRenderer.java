package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import org.academy.api.client.render.Render;
import org.academy.api.client.render.post.PostEffect;
import org.academy.internal.client.renderer.entity.state.KineticShockwaveRenderState;
import org.academy.internal.common.world.entity.skill.KineticShockwave;

public final class KineticShockwaveRenderer
        extends EntityRenderer<KineticShockwave, KineticShockwaveRenderState> {
    public KineticShockwaveRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(KineticShockwaveRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (state.radius <= 0.01f || state.progress >= 1.0f) return;
        var fade = 1.0f - state.progress;
        var strength = Mth.clamp(0.018f + state.intensity * 0.006f, 0.02f, 0.055f) * fade;
        var width = Mth.clamp(0.18f + state.intensity * 0.035f, 0.2f, 0.42f);
        var blur = 0.055f + state.progress * 0.04f;

        emitRing(poseStack, state.radius, strength, width, blur, 0.0f, 0.0f, 0.0f);
        emitRing(poseStack, state.radius, strength * 0.8f, width, blur, 90.0f, 0.0f, 0.0f);
        emitRing(poseStack, state.radius, strength * 0.8f, width, blur, 0.0f, 0.0f, 90.0f);
        emitRing(poseStack, state.radius, strength, width * 0.8f, blur,
                180.0f, 90.0f - state.yRot, 90.0f + state.xRot);
    }

    private static void emitRing(PoseStack poseStack, float radius, float strength,
                                 float width, float blur, float xRot, float yRot, float zRot) {
        poseStack.pushPose();
        if (yRot != 0.0f) poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        if (zRot != 0.0f) poseStack.mulPose(Axis.ZP.rotationDegrees(zRot));
        if (xRot != 0.0f) poseStack.mulPose(Axis.XP.rotationDegrees(xRot));
        var matrix = poseStack.last().pose();
        var consumer = PostEffect.getPre().getBuffer(Render.RenderTypes.DISTORTION_RING);
        consumer.addVertex(matrix, -radius, 0, -radius).setUv(0, 0).setNormal(strength, width, blur);
        consumer.addVertex(matrix, radius, 0, -radius).setUv(1, 0).setNormal(strength, width, blur);
        consumer.addVertex(matrix, radius, 0, radius).setUv(1, 1).setNormal(strength, width, blur);
        consumer.addVertex(matrix, -radius, 0, radius).setUv(0, 1).setNormal(strength, width, blur);
        poseStack.popPose();
    }

    @Override
    public KineticShockwaveRenderState createRenderState() {
        return new KineticShockwaveRenderState();
    }

    @Override
    public void extractRenderState(KineticShockwave entity, KineticShockwaveRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.progress = Mth.clamp((entity.tickCount + partialTick) / entity.getLifeTicks(), 0.0f, 1.0f);
        state.radius = entity.getMaxRadius() * state.progress;
        state.intensity = entity.getIntensity();
        state.xRot = entity.getXRot();
        state.yRot = entity.getYRot();
    }

    @Override
    protected boolean affectedByCulling(KineticShockwave entity) {
        return false;
    }
}
