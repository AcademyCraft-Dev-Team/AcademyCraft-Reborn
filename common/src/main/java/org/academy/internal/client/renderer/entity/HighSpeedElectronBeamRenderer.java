package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.client.util.VertexUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class HighSpeedElectronBeamRenderer extends EntityRenderer<HighSpeedElectronBeam> {
    public static final float[][][] BALL_BUFFER = RenderUtil.BallRenderer.getBallVertexBuffer(1, 16);
    public static final float[][] RAY_BUFFER = VertexUtil.Cylinder.getCylinderVertexBuffer(0, 1, 1, 8, true);

    public HighSpeedElectronBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull HighSpeedElectronBeam entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPoseMatrix(new Matrix4f()
                .rotateY((float) Math.toRadians(90 - entity.getYRot()))
                .rotateZ((float) Math.toRadians(90 + entity.getXRot()))
        );
        entity.smoothProgress = MathUtil.lerpStartEndFactor(entity.smoothProgress, entity.progress, partialTick);
        entity.smoothRayProgress = MathUtil.lerpStartEndFactor(entity.smoothRayProgress, entity.rayProgress, partialTick);
        poseStack.pushPose();
        poseStack.mulPoseMatrix(new Matrix4f().scale(entity.smoothProgress * 0.25f));
        RenderUtil.BallRenderer.renderBall(poseStack, buffer, BALL_BUFFER, 0.906f, 0.827f, 0.694f, 1f);
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.mulPoseMatrix(new Matrix4f().scale(entity.smoothRayProgress * 0.125f,  entity.length, entity.smoothRayProgress * 0.125f));
        RenderUtil.CylinderRenderer.renderCylinder(poseStack, buffer, RAY_BUFFER, 0.906f, 0.827f, 0.694f, 1f);
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