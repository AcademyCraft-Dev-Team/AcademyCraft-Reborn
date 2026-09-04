package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.RenderShape;
import org.academy.internal.client.renderer.entity.state.BlockStructureRenderState;
import org.academy.internal.common.world.entity.structure.BlockStructureEntity;

/** Renders the captured block models in the structure entity's local coordinate space. */
public final class BlockStructureRenderer
        extends EntityRenderer<BlockStructureEntity, BlockStructureRenderState> {
    public BlockStructureRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0f;
    }

    @Override
    public void submit(
            BlockStructureRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            CameraRenderState cameraRenderState
    ) {
        poseStack.pushPose();
        poseStack.translate(state.pivotX, 0.0, state.pivotZ);
        poseStack.mulPose(Axis.YN.rotationDegrees(state.yawDegrees));
        poseStack.translate(-state.pivotX, 0.0, -state.pivotZ);
        for (var block : state.blocks) {
            var position = block.relativePosition();
            poseStack.pushPose();
            poseStack.translate(position.getX(), position.getY(), position.getZ());
            nodeCollector.submitMovingBlock(poseStack, block.renderState(), state.lightCoords);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    @Override
    public BlockStructureRenderState createRenderState() {
        return new BlockStructureRenderState();
    }

    @Override
    public void extractRenderState(
            BlockStructureEntity entity,
            BlockStructureRenderState state,
            float partialTick
    ) {
        super.extractRenderState(entity, state, partialTick);
        state.blocks.clear();
        state.yawDegrees = entity.getYRot(partialTick);
        var snapshot = entity.snapshot();
        state.pivotX = snapshot.pivotX();
        state.pivotZ = snapshot.pivotZ();
        if (!(entity.level() instanceof ClientLevel level)) return;
        var sampleOrigin = entity.blockPosition();
        for (var block : snapshot.blocks()) {
            if (block.state().getRenderShape() != RenderShape.MODEL) continue;
            var samplePosition = sampleOrigin.offset(block.relativePosition());
            var movingState = new MovingBlockRenderState();
            movingState.randomSeedPos = samplePosition;
            movingState.blockPos = samplePosition;
            movingState.blockState = block.state();
            movingState.biome = level.getBiome(samplePosition);
            movingState.cardinalLighting = level.cardinalLighting();
            movingState.lightEngine = level.getLightEngine();
            state.blocks.add(new BlockStructureRenderState.BlockRenderData(
                    block.relativePosition(), movingState));
        }
    }
}
