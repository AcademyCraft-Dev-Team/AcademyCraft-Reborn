package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class RailgunRayRenderer extends EntityRenderer<RailgunRay> {
    public RailgunRayRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(RailgunRay entity, float entityYaw, float partialTick, PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        entity.renderProgress = MathUtil.lerpStartEndFactor(entity.renderProgress, entity.progress, ClientUtil.animationFactor(MathUtil.PI / 2));
        poseStack.mulPoseMatrix(new Matrix4f()
                .rotateY((float) Math.toRadians(90 - entity.getYRot()))
                .rotateZ((float) Math.toRadians(90 + entity.getXRot()))
        );
        RenderUtil.CylinderRenderer.renderCylinder(poseStack, buffer, 0.906f, 0.827f, 0.694f, 1f, 0, 50, entity.renderProgress * 0.1f, 8);
        RenderUtil.CylinderRenderer.renderCylinder(poseStack, buffer, 0.906f, 0.827f, 0.694f, 0.25f, 0, 50, entity.renderProgress * 0.125f, 8);
        poseStack.popPose();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull RailgunRay entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}