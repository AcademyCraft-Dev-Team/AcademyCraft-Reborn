package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.api.client.Render;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.entity.state.GlowCircleRenderState;
import org.academy.internal.common.world.entity.skill.GlowCircle;

public class GlowCircleRenderer extends EntityRenderer<GlowCircle, GlowCircleRenderState> {
    public static final ResourceLocation TEXTURE = AcademyCraft.academy("textures/ability/generic/effect/glow_circle.png");

    static {
        PostEffect.addFixedBuffer(Render.RenderTypes.DISTORTION_RING);
    }

    @Override
    public void render(GlowCircleRenderState renderState, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (IrisCompat.isShadowRendererActive()) return;
        renderState.renderAlpha = MathUtil.lerpStartEndFactor(renderState.renderAlpha, renderState.alpha, ClientUtil.animationFactor(MathUtil.PI / 2));
        renderState.renderRadius = MathUtil.lerpStartEndFactor(renderState.renderRadius, renderState.radius, ClientUtil.animationFactor(MathUtil.PI / 2));

        var yaw = renderState.yRot;
        var pitch = renderState.xRot;

        var distortionStrength = 0.025f;
        var ringWidth = 0.5f;
        var ringEdgeBlur = 0.05f;

        poseStack.pushPose();

        var matrix = poseStack.last().pose();

        poseStack.mulPose(Axis.YP.rotationDegrees(90 - yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90 + pitch));
        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        var vertexConsumer = PostEffect.BUFFER_SOURCE_PRE.getBuffer(Render.RenderTypes.DISTORTION_RING);
        vertexConsumer.addVertex(matrix, -renderState.renderRadius, 0, -renderState.renderRadius).setUv(0, 0).setNormal(distortionStrength, ringWidth, ringEdgeBlur);
        vertexConsumer.addVertex(matrix, renderState.renderRadius, 0, -renderState.renderRadius).setUv(1, 0).setNormal(distortionStrength, ringWidth, ringEdgeBlur);
        vertexConsumer.addVertex(matrix, renderState.renderRadius, 0, renderState.renderRadius).setUv(1, 1).setNormal(distortionStrength, ringWidth, ringEdgeBlur);
        vertexConsumer.addVertex(matrix, -renderState.renderRadius, 0, renderState.renderRadius).setUv(0, 1).setNormal(distortionStrength, ringWidth, ringEdgeBlur);

        matrix.translate(0, -0.01f, 0);
        vertexConsumer = PostEffect.BUFFER_SOURCE_POST.getBuffer(RenderType.eyes(TEXTURE));
        vertexConsumer.addVertex(matrix, -renderState.renderRadius, 0, -renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, renderState.renderRadius, 0, -renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, renderState.renderRadius, 0, renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, -renderState.renderRadius, 0, renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);

        matrix.translate(0, 0.02f, 0);
        vertexConsumer.addVertex(matrix, -renderState.renderRadius, 0, renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, renderState.renderRadius, 0, renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, renderState.renderRadius, 0, -renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, -renderState.renderRadius, 0, -renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);

        poseStack.popPose();
    }

    @Override
    public GlowCircleRenderState createRenderState() {
        return new GlowCircleRenderState();
    }


    @Override
    public void extractRenderState(GlowCircle entity, GlowCircleRenderState reusedState, float partialTick) {
        super.extractRenderState(entity, reusedState, partialTick);
        reusedState.alpha = entity.alpha;
        reusedState.radius = entity.radius;
    }

    public GlowCircleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}