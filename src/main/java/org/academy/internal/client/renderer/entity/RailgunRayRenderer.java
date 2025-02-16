package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.academy.api.client.util.RenderUtil;
import org.academy.internal.common.world.entity.RailgunRay;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class RailgunRayRenderer extends EntityRenderer<RailgunRay> {
    protected RailgunRayRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull RailgunRay entity, float f, float g, @NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int i) {
        poseStack.pushPose();
        poseStack.mulPoseMatrix(new Matrix4f()
                .rotateY((float) Math.toRadians(90 - entity.getYRot()))
                .rotateZ((float) Math.toRadians(90 + entity.getXRot()))
        );
        RenderUtil.RayRenderer.renderRay(poseStack, multiBufferSource.getBuffer(RenderUtil.GLOWING_CYLINDER), 1f, 0.5f, 0, 1f, 0, 50, (((float) entity.currentLifetime / RailgunRay.defaultLifetime) * 0.125f), 32);
        RenderUtil.RayRenderer.renderRay(poseStack, multiBufferSource.getBuffer(RenderUtil.GLOWING_CYLINDER), 1f, 0.5f, 0, 0.25f, 0, 50, (((float) entity.currentLifetime / RailgunRay.defaultLifetime) * 0.15f), 32);
        poseStack.popPose();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull RailgunRay entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}