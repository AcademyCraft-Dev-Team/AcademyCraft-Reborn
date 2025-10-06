package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.Resource;
import org.academy.internal.client.model.SolarGenModel;
import org.academy.internal.client.renderer.blockentity.state.SolarGenRenderState;
import org.academy.internal.common.world.level.block.entity.SolarGenBlockEntity;
import org.jetbrains.annotations.Nullable;

public final class SolarGenRenderer implements BlockEntityRenderer<SolarGenBlockEntity, SolarGenRenderState> {
    public static final SolarGenRenderer INSTANCE = new SolarGenRenderer();
    public static final SolarGenModel MODEL = new SolarGenModel(SolarGenModel.createBodyLayer().bakeRoot());

    private SolarGenRenderer() {
    }

    @Override
    public SolarGenRenderState createRenderState() {
        return new SolarGenRenderState();
    }

    @Override
    public void extractRenderState(SolarGenBlockEntity blockEntity, SolarGenRenderState renderState, float partialTick, Vec3 cameraPosition, @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPosition, breakProgress);
        renderState.ageInTicks = blockEntity.ticks + partialTick;
        renderState.facing = blockEntity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        renderState.idleState = blockEntity.idleState;
        renderState.unfoldingState = blockEntity.unfoldingState;
    }

    @Override
    public void submit(SolarGenRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        var facing = renderState.facing;
        var yRot = facing.getOpposite().toYRot();
        var packedLight = renderState.lightCoords;
        var packedOverlay = OverlayTexture.NO_OVERLAY;

        poseStack.pushPose();
        poseStack.translate(0.5f, 1.5f, 0.5f);
        poseStack.mulPose(Axis.XP.rotationDegrees(180));
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));

        MODEL.setupAnim(renderState);
        nodeCollector.submitModel(MODEL, renderState, poseStack, RenderType.entityCutout(Resource.Textures.SOLAR_GEN_MODEL), packedLight, packedOverlay, 0, renderState.breakProgress);

        poseStack.popPose();
    }
}