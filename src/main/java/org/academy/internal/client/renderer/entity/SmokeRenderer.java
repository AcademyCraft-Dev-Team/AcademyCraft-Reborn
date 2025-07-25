package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.skill.Smoke;
import org.jetbrains.annotations.NotNull;

import static org.academy.AcademyCraft.getResourceLocation;

public class SmokeRenderer extends EntityRenderer<Smoke> {
    public static final ResourceLocation TEXTURE = getResourceLocation("textures/ability/generic/effect/smokes.png");

    public SmokeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull Smoke entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        entity.renderCount++;
        entity.renderAlpha = MathUtil.lerpStartEndFactor(entity.renderAlpha, entity.alpha, ClientUtil.animationFactor(MathUtil.PI / 2));

        var size = 0.5f;
        var halfSize = 1f;
        var r = 1f;
        var g = 1f;
        var b = 1f;
        var a = entity.renderAlpha;

        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        var matrix = poseStack.last().pose();

        var shaderPackInUse = RenderUtil.IS_SHADER_PACK_IN_USE.get();
        var vertexConsumer = buffer.getBuffer(
                shaderPackInUse
                        ? RenderType.eyes(TEXTURE)
                        : RenderUtil.getPositionColorTexRenderTypeFull("smoke", TEXTURE, true)
        );

        var frame = Math.max(0, Math.min(entity.frame, 3));
        var col = frame % 2;
        var row = frame / 2;

        var u0 = col * size;
        var v0 = row * size;
        var u1 = u0 + size;
        var v1 = v0 + size;

        vertexConsumer.addVertex(matrix, -halfSize, -halfSize, 0).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, -halfSize, halfSize, 0).setColor(r, g, b, a).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, halfSize, halfSize, 0).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);
        vertexConsumer.addVertex(matrix, halfSize, -halfSize, 0).setColor(r, g, b, a).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 0);

        poseStack.popPose();
    }


    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull Smoke entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}