package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.skill.GlowCircle;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class GlowCircleRenderer extends EntityRenderer<GlowCircle> {
    public static final ResourceLocation TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/ability/generic/effect/glow_circle.png");

    public GlowCircleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull GlowCircle entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        entity.renderAlpha = MathUtil.lerpStartEndFactor(entity.renderAlpha, entity.alpha, MathUtil.animationFactor(MathUtil.PI / 2, Minecraft.instance.getDeltaFrameTime()));
        entity.renderRadius = MathUtil.lerpStartEndFactor(entity.renderRadius, entity.radius, MathUtil.animationFactor(MathUtil.PI / 2, Minecraft.instance.getDeltaFrameTime()));

        boolean shaderPackInUse = RenderUtil.IS_SHADER_PACK_IN_USE.get();
        VertexConsumer vertexConsumer = buffer.getBuffer(
                shaderPackInUse
                        ? RenderType.eyes(TEXTURE)
                        : RenderUtil.getPositionColorTexRenderTypeFull("glow_circle", TEXTURE, true)
        );

        float yaw = entity.getYRot();
        float pitch = entity.getXRot();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90 - yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90 + pitch));

        Matrix4f matrix = poseStack.last().pose();
        vertexConsumer.vertex(matrix, -entity.renderRadius, 0, -entity.renderRadius).color(1f, 1f, 1f, entity.renderAlpha).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 0).endVertex();
        vertexConsumer.vertex(matrix, entity.renderRadius, 0, -entity.renderRadius).color(1f, 1f, 1f, entity.renderAlpha).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 0).endVertex();
        vertexConsumer.vertex(matrix, entity.renderRadius, 0, entity.renderRadius).color(1f, 1f, 1f, entity.renderAlpha).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 0).endVertex();
        vertexConsumer.vertex(matrix, -entity.renderRadius, 0, entity.renderRadius).color(1f, 1f, 1f, entity.renderAlpha).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 0).endVertex();
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90 - yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90 + pitch));
        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        matrix = poseStack.last().pose();
        vertexConsumer.vertex(matrix, -entity.renderRadius, 0, -entity.renderRadius).color(1f, 1f, 1f, entity.renderAlpha).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 0).endVertex();
        vertexConsumer.vertex(matrix, entity.renderRadius, 0, -entity.renderRadius).color(1f, 1f, 1f, entity.renderAlpha).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 0).endVertex();
        vertexConsumer.vertex(matrix, entity.renderRadius, 0, entity.renderRadius).color(1f, 1f, 1f, entity.renderAlpha).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 0).endVertex();
        vertexConsumer.vertex(matrix, -entity.renderRadius, 0, entity.renderRadius).color(1f, 1f, 1f, entity.renderAlpha).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 0).endVertex();
        poseStack.popPose();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull GlowCircle entity) {
        return TEXTURE;
    }
}