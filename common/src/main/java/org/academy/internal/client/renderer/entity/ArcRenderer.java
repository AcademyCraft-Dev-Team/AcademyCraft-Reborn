package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.academy.internal.common.world.entity.skill.Arc;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class ArcRenderer extends EntityRenderer<Arc> {
    public ArcRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull Arc entity, float f, float g, @NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int i) {
        poseStack.pushPose();
        var matrix4f = new Matrix4f();
        matrix4f.rotateY((float) Math.toRadians(-entity.getYRot() - 90));
        matrix4f.rotateZ((float) Math.toRadians(-entity.getXRot() - 90));
        poseStack.mulPoseMatrix(matrix4f);
        org.academy.api.client.renderer.ArcRenderer.renderArc(poseStack, multiBufferSource, entity.random,
                0, 0, 0, 0, entity.getLength(), 0,
                0.1f, 16);
        poseStack.popPose();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull Arc entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}