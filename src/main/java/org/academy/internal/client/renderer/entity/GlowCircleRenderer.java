package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.academy.api.client.Render;
import org.academy.api.client.render.post.PostEffect;
import org.academy.internal.client.renderer.entity.state.GlowCircleRenderState;
import org.academy.internal.common.world.entity.skill.GlowCircle;

public class GlowCircleRenderer extends EntityRenderer<GlowCircle, GlowCircleRenderState> {
    private static final float MAX_RADIUS = 1.5f;

    static {
        PostEffect.addFixedBuffer(Render.RenderTypes.DISTORTION_RING);
    }

    public GlowCircleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private float sizeCurve(float p) {
        return MAX_RADIUS * (float) Math.sin(p * Math.PI);
    }

    @Override
    public void submit(GlowCircleRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        var yaw = renderState.yRot;
        var pitch = renderState.xRot;
        var distortionStrength = 0.025f;
        var ringWidth = 0.5f;
        var ringEdgeBlur = 0.05f;

        poseStack.pushPose();

        var matrix = poseStack.last().pose();
        var radius = renderState.radius / 2f;

        poseStack.mulPose(Axis.YP.rotationDegrees(90 - yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90 + pitch));
        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        var vertexConsumer = PostEffect.BUFFER_SOURCE_PRE.getBuffer(Render.RenderTypes.DISTORTION_RING);
        vertexConsumer.addVertex(matrix, -radius, 0, -radius).setUv(0, 0).setNormal(distortionStrength, ringWidth, ringEdgeBlur);
        vertexConsumer.addVertex(matrix, radius, 0, -radius).setUv(1, 0).setNormal(distortionStrength, ringWidth, ringEdgeBlur);
        vertexConsumer.addVertex(matrix, radius, 0, radius).setUv(1, 1).setNormal(distortionStrength, ringWidth, ringEdgeBlur);
        vertexConsumer.addVertex(matrix, -radius, 0, radius).setUv(0, 1).setNormal(distortionStrength, ringWidth, ringEdgeBlur);

        poseStack.popPose();
    }

    @Override
    public GlowCircleRenderState createRenderState() {
        return new GlowCircleRenderState();
    }

    @Override
    public void extractRenderState(GlowCircle entity, GlowCircleRenderState reusedState, float partialTick) {
        super.extractRenderState(entity, reusedState, partialTick);
        var progress = Math.min((entity.ticks + partialTick) / GlowCircle.LIFE_TICKS, 1.0f);
        reusedState.radius = sizeCurve(progress);
        reusedState.xRot = entity.getXRot();
        reusedState.yRot = entity.getYRot();
    }
}