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
import org.academy.internal.client.model.OmniCraftingTableModel;
import org.academy.internal.client.renderer.blockentity.state.OmniCraftingTableRenderState;
import org.academy.internal.common.world.level.block.entity.OmniCraftingTableBlockEntity;
import org.jspecify.annotations.Nullable;

public final class OmniCraftingTableRenderer implements BlockEntityRenderer<OmniCraftingTableBlockEntity, OmniCraftingTableRenderState> {
    public static final OmniCraftingTableRenderer INSTANCE = new OmniCraftingTableRenderer();
    public static final OmniCraftingTableModel MODEL = new OmniCraftingTableModel(OmniCraftingTableModel.createBodyLayer().bakeRoot());

    private OmniCraftingTableRenderer() {
    }

    @Override
    public OmniCraftingTableRenderState createRenderState() {
        return new OmniCraftingTableRenderState();
    }

    @Override
    public void extractRenderState(OmniCraftingTableBlockEntity blockEntity, OmniCraftingTableRenderState renderState, float partialTick, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPosition, breakProgress);
        renderState.unfoldingState = blockEntity.unfoldingState;
        renderState.ageInTicks = blockEntity.ticks + partialTick;
        renderState.facing = blockEntity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        renderState.isMain = blockEntity.isMain();
    }

    @Override
    public void submit(OmniCraftingTableRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        if (!renderState.isMain) return;
        poseStack.pushPose();
        {
            var facing = renderState.facing;
            var yRot = facing.getOpposite().toYRot();

            poseStack.translate(0.5, 0, 0.5);
            poseStack.rotateAround(Axis.XP.rotationDegrees(180), 0, 0, 0);
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
            MODEL.setupAnim(renderState);
            MODEL.render(poseStack, nodeCollector, renderState.lightCoords, OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(OmniCraftingTableBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
    }
}