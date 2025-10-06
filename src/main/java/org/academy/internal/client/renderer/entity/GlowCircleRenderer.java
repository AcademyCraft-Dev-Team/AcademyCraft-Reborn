package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.api.client.Render;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.api.client.render.post.PostEffect;
import org.academy.internal.client.renderer.entity.state.GlowCircleRenderState;
import org.academy.internal.common.world.entity.skill.GlowCircle;

public class GlowCircleRenderer extends EntityRenderer<GlowCircle, GlowCircleRenderState> {
    public static final ResourceLocation TEXTURE = AcademyCraft.academy("textures/ability/generic/effect/glow_circle.png");
    private static final float MAX_RADIUS = 1.5f;

    static {
        PostEffect.addFixedBuffer(Render.RenderTypes.DISTORTION_RING);
    }

    public GlowCircleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private float alphaCurve(float p) {
        return (float) Math.sqrt(Math.sin(p * Math.PI));
    }

    private float sizeCurve(float p) {
        return MAX_RADIUS * (float) Math.sin(p * Math.PI);
    }

    @Override
    public void submit(GlowCircleRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        if (IrisCompat.isShadowRendererActive()) return;

        var packedLight = renderState.lightCoords;
        var yaw = renderState.yRot;
        var pitch = renderState.xRot;
        var distortionStrength = 0.025f;
        var ringWidth = 0.5f;
        var ringEdgeBlur = 0.05f;

        poseStack.pushPose();

        var matrix = poseStack.last().pose();
        var radius = renderState.radius;
        var alpha = renderState.alpha;

        poseStack.mulPose(Axis.YP.rotationDegrees(90 - yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90 + pitch));
        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        var vertexConsumer = PostEffect.BUFFER_SOURCE_PRE.getBuffer(Render.RenderTypes.DISTORTION_RING);
        vertexConsumer.addVertex(matrix, -radius, 0, -radius).setUv(0, 0).setNormal(distortionStrength, ringWidth, ringEdgeBlur);
        vertexConsumer.addVertex(matrix, radius, 0, -radius).setUv(1, 0).setNormal(distortionStrength, ringWidth, ringEdgeBlur);
        vertexConsumer.addVertex(matrix, radius, 0, radius).setUv(1, 1).setNormal(distortionStrength, ringWidth, ringEdgeBlur);
        vertexConsumer.addVertex(matrix, -radius, 0, radius).setUv(0, 1).setNormal(distortionStrength, ringWidth, ringEdgeBlur);

        matrix.translate(0, -0.01f, 0);
        vertexConsumer = PostEffect.BUFFER_SOURCE_POST.getBuffer(RenderType.eyes(TEXTURE));
        vertexConsumer.addVertex(matrix, -radius, 0, -radius).setColor(1f, 1f, 1f, alpha).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, radius, 0, -radius).setColor(1f, 1f, 1f, alpha).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, radius, 0, radius).setColor(1f, 1f, 1f, alpha).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, -radius, 0, radius).setColor(1f, 1f, 1f, alpha).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);

        matrix.translate(0, 0.02f, 0);
        vertexConsumer.addVertex(matrix, -radius, 0, radius).setColor(1f, 1f, 1f, alpha).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, radius, 0, radius).setColor(1f, 1f, 1f, alpha).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, radius, 0, -radius).setColor(1f, 1f, 1f, alpha).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, -radius, 0, -radius).setColor(1f, 1f, 1f, alpha).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);

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
        reusedState.alpha = this.alphaCurve(progress);
        reusedState.radius = this.sizeCurve(progress);
    }
}