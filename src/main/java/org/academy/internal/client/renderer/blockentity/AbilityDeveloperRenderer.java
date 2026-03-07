package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.Render;
import org.academy.internal.client.model.AbilityDeveloperModel;
import org.academy.internal.client.renderer.blockentity.state.AbilityDeveloperRenderState;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jspecify.annotations.Nullable;

public final class AbilityDeveloperRenderer implements BlockEntityRenderer<AbilityDeveloperBlockEntity, AbilityDeveloperRenderState> {
    public static final AbilityDeveloperRenderer INSTANCE = new AbilityDeveloperRenderer();
    public static final AbilityDeveloperModel MODEL = new AbilityDeveloperModel(AbilityDeveloperModel.createBodyLayer().bakeRoot());

    private AbilityDeveloperRenderer() {
    }

    @Override
    public AbilityDeveloperRenderState createRenderState() {
        return new AbilityDeveloperRenderState();
    }

    @Override
    public void extractRenderState(AbilityDeveloperBlockEntity blockEntity, AbilityDeveloperRenderState renderState, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPos, crumblingOverlay);
        renderState.closingState = blockEntity.closingState;
        renderState.openingState = blockEntity.openingState;
        renderState.lyingDownState = blockEntity.lyingDownState;
        renderState.standingState = blockEntity.standingState;
        renderState.ageInTicks = blockEntity.ticks + partialTick;
        renderState.facing = blockEntity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        renderState.isMain = blockEntity.isMain();
    }

    @Override
    public void submit(AbilityDeveloperRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        if (renderState.isMain) {
            poseStack.pushPose();
            var facing = renderState.facing;
            var yRot = facing.getOpposite().toYRot();

            poseStack.translate(0.5f, 1.5f, 0.5f);
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
            poseStack.translate(0, 0, -1);

            submitNodeCollector.submitModel(MODEL, renderState, poseStack, Render.RenderTypes.ABILITY_DEVELOPER, renderState.lightCoords, OverlayTexture.NO_OVERLAY, 0, renderState.breakProgress);

            poseStack.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(AbilityDeveloperBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
    }
}