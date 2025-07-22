package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import org.academy.api.client.renderer.BoxRenderer;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class HighSpeedElectronBeamRenderer extends EntityRenderer<HighSpeedElectronBeam> {
    public static final AABB HEAD = new AABB(-1, -1, -1, 1, 1, 1);
    public static final AABB RAY = new AABB(-0.5, 0, -0.5, 0.5, 1, 0.5);

    public HighSpeedElectronBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull HighSpeedElectronBeam entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        entity.smoothProgress = MathUtil.lerpStartEndFactor(entity.smoothProgress, entity.progress, partialTick);

        var ballRadius = entity.smoothProgress * 0.185f;

        var commonInitialOrientation = new Matrix4f()
                .rotateY((float) Math.toRadians(90 - entity.getYRot()))
                .rotateZ((float) Math.toRadians(90 + entity.getXRot()));

        poseStack.pushPose();
        poseStack.mulPoseMatrix(commonInitialOrientation);

        poseStack.pushPose();
        poseStack.mulPoseMatrix(new Matrix4f().scale(ballRadius));
        BoxRenderer.renderFilledBox(poseStack, HEAD, 0, 1, 0, 0.125f);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        BoxRenderer.renderFilledBox(poseStack, HEAD, 1, 1, 1, 1f);
        poseStack.popPose();

        var rayVisualProgress = entity.isCharging() ? 0f : entity.smoothProgress;

        poseStack.pushPose();
        poseStack.mulPoseMatrix(new Matrix4f().scale(rayVisualProgress * 0.25f,  entity.length, rayVisualProgress * 0.25f));
        BoxRenderer.renderFilledBox(poseStack, RAY, 0, 1, 0, 0.125f);
        poseStack.scale(0.75f, 1, 0.75f);
        BoxRenderer.renderFilledBox(poseStack, RAY, 1, 1, 1, 1f);
        poseStack.popPose();

        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(@NotNull HighSpeedElectronBeam livingEntity, @NotNull Frustum camera, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull HighSpeedElectronBeam entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}