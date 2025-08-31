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
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.entity.state.GlowCircleRenderState;
import org.academy.internal.common.world.entity.skill.GlowCircle;

public class GlowCircleRenderer extends EntityRenderer<GlowCircle, GlowCircleRenderState> {
    public static final ResourceLocation TEXTURE = AcademyCraft.academy("textures/ability/generic/effect/glow_circle.png");

    @Override
    public void render(GlowCircleRenderState renderState, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        renderState.renderAlpha = MathUtil.lerpStartEndFactor(renderState.renderAlpha, renderState.alpha, ClientUtil.animationFactor(MathUtil.PI / 2));
        renderState.renderRadius = MathUtil.lerpStartEndFactor(renderState.renderRadius, renderState.radius, ClientUtil.animationFactor(MathUtil.PI / 2));

        var vertexConsumer = bufferSource.getBuffer(RenderType.eyes(TEXTURE));

        var yaw = renderState.yRot;
        var pitch = renderState.xRot;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90 - yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90 + pitch));

        var matrix = poseStack.last().pose();
        vertexConsumer.addVertex(matrix, -renderState.renderRadius, 0, -renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, renderState.renderRadius, 0, -renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, renderState.renderRadius, 0, renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, -renderState.renderRadius, 0, renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90 - yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90 + pitch));
        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        matrix = poseStack.last().pose();
        vertexConsumer.addVertex(matrix, -renderState.renderRadius, 0, -renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, renderState.renderRadius, 0, -renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, renderState.renderRadius, 0, renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, -renderState.renderRadius, 0, renderState.renderRadius).setColor(1f, 1f, 1f, renderState.renderAlpha).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        poseStack.popPose();
    }

    @Override
    public GlowCircleRenderState createRenderState() {
        return new GlowCircleRenderState();
    }

    public GlowCircleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}