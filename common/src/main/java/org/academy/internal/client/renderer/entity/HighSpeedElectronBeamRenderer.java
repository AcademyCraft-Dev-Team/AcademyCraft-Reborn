package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.academy.AcademyCraft;
import org.academy.api.client.util.RenderUtil;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.jetbrains.annotations.NotNull;

public class HighSpeedElectronBeamRenderer extends EntityRenderer<HighSpeedElectronBeam> {
    public HighSpeedElectronBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull HighSpeedElectronBeam entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        RenderUtil.BallRenderer.renderBall(poseStack, buffer, entity.progress * 0.25f, 16, 0.906f, 0.827f, 0.694f, 1f);
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
