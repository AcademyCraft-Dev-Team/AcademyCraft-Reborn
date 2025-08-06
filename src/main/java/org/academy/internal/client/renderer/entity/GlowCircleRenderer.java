package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.skill.GlowCircle;
import org.jetbrains.annotations.NotNull;

import static org.academy.AcademyCraft.getResourceLocation;

public class GlowCircleRenderer extends EntityRenderer<GlowCircle> {
    public static final ResourceLocation TEXTURE = getResourceLocation("textures/ability/generic/effect/glow_circle.png");

    public GlowCircleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull GlowCircle entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        entity.renderAlpha = MathUtil.lerpStartEndFactor(entity.renderAlpha, entity.alpha, ClientUtil.animationFactor(MathUtil.PI / 2));
        entity.renderRadius = MathUtil.lerpStartEndFactor(entity.renderRadius, entity.radius, ClientUtil.animationFactor(MathUtil.PI / 2));

        var shaderPackInUse = RenderUtil.IS_SHADER_PACK_IN_USE.get();
        var vertexConsumer = buffer.getBuffer(
                shaderPackInUse
                        ? RenderType.eyes(TEXTURE)
                        : RenderUtil.getPositionColorTexRenderType("glow_circle", TEXTURE, true)
        );

        var yaw = entity.getYRot();
        var pitch = entity.getXRot();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90 - yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90 + pitch));

        var matrix = poseStack.last().pose();
        vertexConsumer.addVertex(matrix, -entity.renderRadius, 0, -entity.renderRadius).setColor(1f, 1f, 1f, entity.renderAlpha).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, entity.renderRadius, 0, -entity.renderRadius).setColor(1f, 1f, 1f, entity.renderAlpha).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, entity.renderRadius, 0, entity.renderRadius).setColor(1f, 1f, 1f, entity.renderAlpha).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, -entity.renderRadius, 0, entity.renderRadius).setColor(1f, 1f, 1f, entity.renderAlpha).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90 - yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90 + pitch));
        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        matrix = poseStack.last().pose();
        vertexConsumer.addVertex(matrix, -entity.renderRadius, 0, -entity.renderRadius).setColor(1f, 1f, 1f, entity.renderAlpha).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, entity.renderRadius, 0, -entity.renderRadius).setColor(1f, 1f, 1f, entity.renderAlpha).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, entity.renderRadius, 0, entity.renderRadius).setColor(1f, 1f, 1f, entity.renderAlpha).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, -entity.renderRadius, 0, entity.renderRadius).setColor(1f, 1f, 1f, entity.renderAlpha).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        poseStack.popPose();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull GlowCircle entity) {
        return TEXTURE;
    }
}